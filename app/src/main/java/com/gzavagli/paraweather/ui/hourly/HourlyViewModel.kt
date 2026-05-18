package com.gzavagli.paraweather.ui.hourly

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import com.gzavagli.paraweather.data.preferences.UserPreferencesRepository
import com.gzavagli.paraweather.data.repository.WeatherRepository
import com.gzavagli.paraweather.domain.AssessFlyabilityUseCase
import com.gzavagli.paraweather.domain.model.FlyabilityAssessment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed interface HourlyUiState {
    object Loading : HourlyUiState
    data class Success(
        val locationName: String,
        val hourlyItems: List<DetailedHourlyItem>
    ) : HourlyUiState
    data class Error(val message: String) : HourlyUiState
}

data class DetailedHourlyItem(
    val dayOfWeek: String,
    val timeLabel: String,
    val temperature: Double,
    val windSpeed: Double,
    val gustSpeed: Double,
    val windDirection: Double,
    val precipitationProb: Int,
    val cape: Double,
    val shortwaveRadiation: Double,
    val assessment: FlyabilityAssessment
)

@HiltViewModel
class HourlyViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val assessFlyabilityUseCase: AssessFlyabilityUseCase,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<HourlyUiState>(HourlyUiState.Loading)
    val uiState: StateFlow<HourlyUiState> = _uiState.asStateFlow()

    fun loadHourlyForecast() {
        viewModelScope.launch {
            _uiState.value = HourlyUiState.Loading
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val (lat, lon, locName) = fetchLocation(prefs)

                val response = weatherRepository.getForecast(lat, lon)
                val hourly = response.hourly

                val itemsList = mutableListOf<DetailedHourlyItem>()

                // Show from current hour onwards
                val currentHourStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
                var startIndex = hourly.time.indexOfFirst { it.startsWith(currentHourStr) }
                if (startIndex == -1) startIndex = 0

                val maxLimit = minOf(startIndex + 168, hourly.time.size) // Next 7 Days (168 Hours)

                for (i in startIndex until maxLimit) {
                    val rawTime = hourly.time[i]
                    val parsedTime = LocalDateTime.parse(rawTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val dayOfWeek = parsedTime.format(DateTimeFormatter.ofPattern("E, MMM d"))
                    val timeLabel = parsedTime.format(DateTimeFormatter.ofPattern("HH:00"))

                    val assessment = assessFlyabilityUseCase(
                        averageWindSpeed = hourly.windSpeed10m[i],
                        gustSpeed = hourly.windGusts10m[i],
                        windDirection = hourly.windDirection10m[i],
                        precipitationProbability = hourly.precipitationProbability[i],
                        precipitation = hourly.precipitation[i],
                        cape = hourly.cape[i],
                        liftedIndex = hourly.liftedIndex[i],
                        shortwaveRadiation = hourly.shortwaveRadiation[i],
                        boundaryLayerHeight = hourly.boundaryLayerHeight[i],
                        preferences = prefs
                    )

                    itemsList.add(
                        DetailedHourlyItem(
                            dayOfWeek = dayOfWeek,
                            timeLabel = timeLabel,
                            temperature = hourly.temperature2m[i],
                            windSpeed = hourly.windSpeed10m[i],
                            gustSpeed = hourly.windGusts10m[i],
                            windDirection = hourly.windDirection10m[i],
                            precipitationProb = hourly.precipitationProbability[i],
                            cape = hourly.cape[i],
                            shortwaveRadiation = hourly.shortwaveRadiation[i],
                            assessment = assessment
                        )
                    )
                }

                _uiState.value = HourlyUiState.Success(
                    locationName = locName,
                    hourlyItems = itemsList
                )
            } catch (e: Exception) {
                _uiState.value = HourlyUiState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocation(prefs: com.gzavagli.paraweather.data.preferences.UserPreferences): Triple<Double, Double, String> {
        val activeId = prefs.activeLocationId
        if (activeId != "current_gps") {
            val selected = prefs.savedLocations.find { it.id == activeId }
            if (selected != null) {
                return Triple(selected.latitude, selected.longitude, selected.name)
            }
        }

        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val locationTask = fusedLocationProviderClient.lastLocation
                    val cachedLoc = Tasks.await(locationTask)
                    if (cachedLoc != null) {
                        return@withContext Triple(cachedLoc.latitude, cachedLoc.longitude, "Current GPS Location")
                    }

                    val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                    val liveLocationTask = fusedLocationProviderClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                        tokenSource.token
                    )
                    val liveLocation = Tasks.await(liveLocationTask)
                    if (liveLocation != null) {
                        return@withContext Triple(liveLocation.latitude, liveLocation.longitude, "Current GPS Location")
                    }
                    null
                }
                if (resolved != null) return resolved
            } catch (e: Exception) {
                // Fall through to default
            }
        }
        return Triple(32.8872, -117.2519, "Torrey Pines, CA (GPS Unavailable)")
    }
}
