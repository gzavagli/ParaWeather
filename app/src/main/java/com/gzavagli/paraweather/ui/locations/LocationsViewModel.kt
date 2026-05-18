package com.gzavagli.paraweather.ui.locations

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import com.gzavagli.paraweather.data.api.OverpassApi
import com.gzavagli.paraweather.data.model.SavedLocation
import com.gzavagli.paraweather.data.preferences.UserPreferences
import com.gzavagli.paraweather.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface NearbyLaunchesState {
    object Idle : NearbyLaunchesState
    object Loading : NearbyLaunchesState
    data class Success(val launches: List<SavedLocation>) : NearbyLaunchesState
    data class Error(val message: String) : NearbyLaunchesState
}

@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val overpassApi: OverpassApi,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences?> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _nearbyState = MutableStateFlow<NearbyLaunchesState>(NearbyLaunchesState.Idle)
    val nearbyState: StateFlow<NearbyLaunchesState> = _nearbyState.asStateFlow()

    fun selectActiveLocation(id: String) {
        viewModelScope.launch {
            userPreferencesRepository.updateActiveLocationId(id)
        }
    }

    fun addCustomLocation(name: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val newLoc = SavedLocation(
                id = UUID.randomUUID().toString(),
                name = name,
                latitude = latitude,
                longitude = longitude
            )
            userPreferencesRepository.addSavedLocation(newLoc)
        }
    }

    fun deleteLocation(id: String) {
        viewModelScope.launch {
            userPreferencesRepository.deleteSavedLocation(id)
        }
    }

    fun searchNearbyLaunches() {
        viewModelScope.launch {
            _nearbyState.value = NearbyLaunchesState.Loading
            try {
                // Get active coordinates to search around
                val (activeLat, activeLon) = getActiveCoords()

                // Complete, perfect OSM query mapping paragliding, free-flying, and hang-gliding launches
                val overpassQuery = """
                    [out:json][timeout:25];
                    (
                      node["sport"="paragliding"](around:100000,$activeLat,$activeLon);
                      way["sport"="paragliding"](around:100000,$activeLat,$activeLon);
                      relation["sport"="paragliding"](around:100000,$activeLat,$activeLon);
                      node["sport"="free_flying"](around:100000,$activeLat,$activeLon);
                      way["sport"="free_flying"](around:100000,$activeLat,$activeLon);
                      relation["sport"="free_flying"](around:100000,$activeLat,$activeLon);
                      node["sport"="hang_gliding"](around:100000,$activeLat,$activeLon);
                      way["sport"="hang_gliding"](around:100000,$activeLat,$activeLon);
                      relation["sport"="hang_gliding"](around:100000,$activeLat,$activeLon);
                      node["paragliding"](around:100000,$activeLat,$activeLon);
                      way["paragliding"](around:100000,$activeLat,$activeLon);
                      relation["paragliding"](around:100000,$activeLat,$activeLon);
                    );
                    out center;
                """.trimIndent()

                val response = overpassApi.getNearbyLaunches(overpassQuery)
                val filteredAndSortedLaunches = response.elements.mapNotNull { element ->
                    val resolvedLat = element.lat ?: element.center?.lat
                    val resolvedLon = element.lon ?: element.center?.lon

                    if (resolvedLat == null || resolvedLon == null) return@mapNotNull null

                    // Calculate distance to active location locally
                    val distanceResults = FloatArray(1)
                    android.location.Location.distanceBetween(activeLat, activeLon, resolvedLat, resolvedLon, distanceResults)
                    val distanceInMeters = distanceResults[0]

                    // Filter: within 100km radius
                    if (distanceInMeters > 100000) return@mapNotNull null

                    val rawName = element.tags?.get("name") ?: "Unnamed launch"
                    val type = element.tags?.get("paragliding") ?: "site"
                    val orientation = element.tags?.get("direction") ?: element.tags?.get("takeoff_direction")
                    val suffix = if (!orientation.isNullOrBlank()) " ($orientation)" else " (${type.uppercase()})"
                    val formattedName = "$rawName$suffix"

                    val distanceInKm = (distanceInMeters / 1000.0).toInt()

                    Pair(
                        SavedLocation(
                            id = "osm_${element.id}",
                            name = "$formattedName - ${distanceInKm}km",
                            latitude = resolvedLat,
                            longitude = resolvedLon
                        ),
                        distanceInMeters
                    )
                }
                .sortedBy { it.second } // Sort closest first!
                .map { it.first }

                _nearbyState.value = NearbyLaunchesState.Success(filteredAndSortedLaunches)
            } catch (e: Exception) {
                _nearbyState.value = NearbyLaunchesState.Error("Failed to load launches: ${e.localizedMessage}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getActiveCoords(): Pair<Double, Double> {
        val prefs = userPreferencesRepository.userPreferencesFlow.stateIn(viewModelScope).value
        val activeId = prefs?.activeLocationId ?: "current_gps"

        if (activeId != "current_gps") {
            val selected = prefs?.savedLocations?.find { it.id == activeId }
            if (selected != null) {
                return Pair(selected.latitude, selected.longitude)
            }
        }

        // If current GPS, resolve live location
        val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                val resolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val locationTask = fusedLocationProviderClient.lastLocation
                    val cachedLoc = Tasks.await(locationTask)
                    if (cachedLoc != null) {
                        return@withContext Pair(cachedLoc.latitude, cachedLoc.longitude)
                    }

                    val tokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                    val liveLocationTask = fusedLocationProviderClient.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                        tokenSource.token
                    )
                    val liveLocation = Tasks.await(liveLocationTask)
                    if (liveLocation != null) {
                        return@withContext Pair(liveLocation.latitude, liveLocation.longitude)
                    }
                    null
                }
                if (resolved != null) return resolved
            } catch (e: Exception) {
                // Ignore and fall through
            }
        }
        return Pair(32.8872, -117.2519) // Default fallback (Torrey Pines)
    }

    fun toggleLocationAlert(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val currentSet = prefs.alertLocationIds.toMutableSet()
            if (enabled) {
                currentSet.add(id)
            } else {
                currentSet.remove(id)
            }
            userPreferencesRepository.updateAlertLocationIds(currentSet)
        }
    }
}

