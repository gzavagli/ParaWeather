package com.gzavagli.paraweather.ui.dashboard

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

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(
        val locationName: String,
        val currentAssessment: FlyabilityAssessment,
        val nextHoursForecast: List<HourlyPreviewItem>
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

data class HourlyPreviewItem(
    val formattedTime: String,
    val temperature: Double,
    val windSpeed: Double,
    val gustSpeed: Double,
    val windDirection: Double,
    val assessment: FlyabilityAssessment
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val assessFlyabilityUseCase: AssessFlyabilityUseCase,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Expose saved preferences and location lists to the UI
    val userPreferences = userPreferencesRepository.userPreferencesFlow

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Reactively reload weather calculations whenever locations list or active location changes!
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect {
                loadWeatherData()
            }
        }
    }

    fun selectActiveLocation(id: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateActiveLocationId(id)
        }
    }

    fun loadWeatherData() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val (lat, lon, locName) = fetchLocation(prefs)

                val response = weatherRepository.getForecast(lat, lon)
                val hourly = response.hourly

                // Find index matching current hour or first hour
                val currentHourStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
                var currentIndex = hourly.time.indexOfFirst { it.startsWith(currentHourStr) }
                if (currentIndex == -1) currentIndex = 0

                val currentAssessment = assessFlyabilityUseCase(
                    averageWindSpeed = hourly.windSpeed10m[currentIndex],
                    gustSpeed = hourly.windGusts10m[currentIndex],
                    windDirection = hourly.windDirection10m[currentIndex],
                    precipitationProbability = hourly.precipitationProbability[currentIndex],
                    precipitation = hourly.precipitation[currentIndex],
                    cape = hourly.cape[currentIndex],
                    liftedIndex = hourly.liftedIndex[currentIndex],
                    shortwaveRadiation = hourly.shortwaveRadiation[currentIndex],
                    boundaryLayerHeight = hourly.boundaryLayerHeight[currentIndex],
                    preferences = prefs
                )

                // Build next 6 hours preview
                val previewList = mutableListOf<HourlyPreviewItem>()
                val maxLimit = minOf(currentIndex + 7, hourly.time.size)
                for (i in (currentIndex + 1) until maxLimit) {
                    val rawTime = hourly.time[i]
                    val parsedTime = LocalDateTime.parse(rawTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val formattedTime = parsedTime.format(DateTimeFormatter.ofPattern("HH:00"))

                    val hourAssessment = assessFlyabilityUseCase(
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


                    previewList.add(
                        HourlyPreviewItem(
                            formattedTime = formattedTime,
                            temperature = hourly.temperature2m[i],
                            windSpeed = hourly.windSpeed10m[i],
                            gustSpeed = hourly.windGusts10m[i],
                            windDirection = hourly.windDirection10m[i],
                            assessment = hourAssessment
                        )
                    )
                }

                _uiState.value = DashboardUiState.Success(
                    locationName = locName,
                    currentAssessment = currentAssessment,
                    nextHoursForecast = previewList
                )
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error("Error loading weather: ${e.localizedMessage}")
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
                // Fall through to default if task fails
            }
        }

        // Default to Torrey Pines (highly famous paragliding spot in San Diego, CA)
        return Triple(32.8872, -117.2519, "Torrey Pines, CA (GPS Unavailable)")
    }

}
