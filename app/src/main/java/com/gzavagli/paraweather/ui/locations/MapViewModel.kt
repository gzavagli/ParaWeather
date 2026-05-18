package com.gzavagli.paraweather.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gzavagli.paraweather.data.api.OverpassApi
import com.gzavagli.paraweather.data.model.SavedLocation
import com.gzavagli.paraweather.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface MapUiState {
    object Idle : MapUiState
    object Loading : MapUiState
    data class Success(val visibleSites: List<SavedLocation>) : MapUiState
    data class Error(val message: String) : MapUiState
}

data class CachedArea(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    // Check if the requested viewport is fully inside our cached expanded area
    fun contains(s: Double, w: Double, n: Double, e: Double): Boolean {
        return s >= south && n <= north && w >= west && e <= east
    }
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val overpassApi: OverpassApi
) : ViewModel() {

    private val _mapState = MutableStateFlow<MapUiState>(MapUiState.Idle)
    val mapState: StateFlow<MapUiState> = _mapState.asStateFlow()

    // Local Viewport cache properties
    private var cachedArea: CachedArea? = null
    private var cachedSites = listOf<SavedLocation>()

    fun searchSitesInViewport(south: Double, west: Double, north: Double, east: Double) {
        viewModelScope.launch {
            // 1. Check if requested area is already fully cached in memory
            cachedArea?.let { cache ->
                if (cache.contains(south, west, north, east)) {
                    // Cache Hit! Filter in-memory list locally to the exact viewport and return immediately
                    val filteredSites = cachedSites.filter { site ->
                        site.latitude in south..north && site.longitude in west..east
                    }
                    _mapState.value = MapUiState.Success(filteredSites)
                    return@launch
                }
            }

            // 2. Cache Miss: We expanded the viewport by 0.5 degrees (~50km buffer) in every direction to pre-fetch larger area
            _mapState.value = MapUiState.Loading
            try {
                val expandedSouth = south - 0.5
                val expandedWest = west - 0.5
                val expandedNorth = north + 0.5
                val expandedEast = east + 0.5

                val bboxQuery = """
                    [out:json][timeout:25];
                    (
                      node["sport"="paragliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      way["sport"="paragliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      relation["sport"="paragliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      node["sport"="free_flying"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      way["sport"="free_flying"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      relation["sport"="free_flying"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      node["sport"="hang_gliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      way["sport"="hang_gliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      relation["sport"="hang_gliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      node["paragliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      way["paragliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                      relation["paragliding"]($expandedSouth,$expandedWest,$expandedNorth,$expandedEast);
                    );
                    out center;
                """.trimIndent()

                val response = overpassApi.getNearbyLaunches(bboxQuery)
                val mappedSites = response.elements.mapNotNull { element ->
                    val resolvedLat = element.lat ?: element.center?.lat
                    val resolvedLon = element.lon ?: element.center?.lon

                    if (resolvedLat == null || resolvedLon == null) return@mapNotNull null

                    val rawName = element.tags?.get("name") ?: "Unnamed launch"
                    val type = element.tags?.get("paragliding") ?: element.tags?.get("sport") ?: "site"
                    val orientation = element.tags?.get("direction") ?: element.tags?.get("takeoff_direction")
                    val suffix = if (!orientation.isNullOrBlank()) " ($orientation)" else " (${type.uppercase()})"
                    val formattedName = "$rawName$suffix"

                    SavedLocation(
                        id = "osm_${element.id}",
                        name = formattedName,
                        latitude = resolvedLat,
                        longitude = resolvedLon
                    )
                }

                // Save to cache memory
                cachedSites = mappedSites
                cachedArea = CachedArea(expandedSouth, expandedWest, expandedNorth, expandedEast)

                // Filter to exact active viewport for current display State
                val activeSites = mappedSites.filter { site ->
                    site.latitude in south..north && site.longitude in west..east
                }

                _mapState.value = MapUiState.Success(activeSites)
            } catch (e: Exception) {
                _mapState.value = MapUiState.Error("Failed to query map viewport: ${e.localizedMessage}")
            }
        }
    }

    fun saveLocation(name: String, latitude: Double, longitude: Double) {
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
}
