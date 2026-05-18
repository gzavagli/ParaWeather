package com.gzavagli.paraweather.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gzavagli.paraweather.data.model.SavedLocation
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class WindUnit(val label: String) {
    KMH("km/h"),
    KNOTS("kt"),
    MPH("mph"),
    MS("m/s");

    companion object {
        fun fromLabel(label: String): WindUnit = values().find { it.label == label } ?: KMH
    }
}

enum class TakeoffDirection(val heading: Double?, val label: String) {
    ANY(null, "Any"),
    N(0.0, "N (North)"),
    NE(45.0, "NE (North-East)"),
    E(90.0, "E (East)"),
    SE(135.0, "SE (South-East)"),
    S(180.0, "S (South)"),
    SW(225.0, "SW (South-West)"),
    W(270.0, "W (West)"),
    NW(315.0, "NW (North-West)");

    companion object {
        fun fromLabel(label: String): TakeoffDirection = values().find { it.label == label } ?: ANY
    }
}

data class UserPreferences(
    val minWindSpeed: Double,
    val maxWindSpeed: Double,
    val maxGustSpeed: Double,
    val windUnit: WindUnit,
    val takeoffDirection: TakeoffDirection,
    val activeLocationId: String,
    val savedLocations: List<SavedLocation>,
    val alertLocationIds: Set<String>,
    val alertInspectionPeriodDays: Int
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    private object PreferencesKeys {
        val MIN_WIND_SPEED = doublePreferencesKey("min_wind_speed")
        val MAX_WIND_SPEED = doublePreferencesKey("max_wind_speed")
        val MAX_GUST_SPEED = doublePreferencesKey("max_gust_speed")
        val WIND_UNIT = stringPreferencesKey("wind_unit")
        val TAKEOFF_DIRECTION = stringPreferencesKey("takeoff_direction")
        val ACTIVE_LOCATION_ID = stringPreferencesKey("active_location_id")
        val SAVED_LOCATIONS_JSON = stringPreferencesKey("saved_locations_json")
        val ALERT_LOCATION_IDS = stringSetPreferencesKey("alert_location_ids")
        val ALERT_INSPECTION_PERIOD_DAYS = intPreferencesKey("alert_inspection_period_days")
    }

    private val listType = Types.newParameterizedType(List::class.java, SavedLocation::class.java)
    private val jsonAdapter = moshi.adapter<List<SavedLocation>>(listType)

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        val minWind = preferences[PreferencesKeys.MIN_WIND_SPEED] ?: 5.0
        val maxWind = preferences[PreferencesKeys.MAX_WIND_SPEED] ?: 20.0
        val maxGust = preferences[PreferencesKeys.MAX_GUST_SPEED] ?: 25.0
        val unitLabel = preferences[PreferencesKeys.WIND_UNIT] ?: WindUnit.KMH.label
        val takeoffLabel = preferences[PreferencesKeys.TAKEOFF_DIRECTION] ?: TakeoffDirection.ANY.label
        val activeLocId = preferences[PreferencesKeys.ACTIVE_LOCATION_ID] ?: "current_gps"
        val locationsJson = preferences[PreferencesKeys.SAVED_LOCATIONS_JSON] ?: "[]"
        val alertLocIds = preferences[PreferencesKeys.ALERT_LOCATION_IDS] ?: emptySet()
        val inspectionDays = preferences[PreferencesKeys.ALERT_INSPECTION_PERIOD_DAYS] ?: 3

        val locationsList = try {
            jsonAdapter.fromJson(locationsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Ensure current_gps is always at the top of the locations list, or we add it conceptually
        val fullLocationsList = if (locationsList.none { it.id == "current_gps" }) {
            listOf(SavedLocation("current_gps", "Current GPS", 0.0, 0.0, true)) + locationsList
        } else {
            locationsList
        }

        UserPreferences(
            minWindSpeed = minWind,
            maxWindSpeed = maxWind,
            maxGustSpeed = maxGust,
            windUnit = WindUnit.fromLabel(unitLabel),
            takeoffDirection = TakeoffDirection.fromLabel(takeoffLabel),
            activeLocationId = activeLocId,
            savedLocations = fullLocationsList,
            alertLocationIds = alertLocIds,
            alertInspectionPeriodDays = inspectionDays
        )
    }

    suspend fun updateMinWindSpeed(speed: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MIN_WIND_SPEED] = speed
        }
    }

    suspend fun updateMaxWindSpeed(speed: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_WIND_SPEED] = speed
        }
    }

    suspend fun updateMaxGustSpeed(speed: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_GUST_SPEED] = speed
        }
    }

    suspend fun updateWindUnit(unit: WindUnit) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIND_UNIT] = unit.label
        }
    }

    suspend fun updateTakeoffDirection(direction: TakeoffDirection) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TAKEOFF_DIRECTION] = direction.label
        }
    }

    suspend fun updateActiveLocationId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACTIVE_LOCATION_ID] = id
        }
    }

    suspend fun updateAlertLocationIds(ids: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_LOCATION_IDS] = ids
        }
        
        // Dynamically schedule exact repeating 6:00 AM/PM alarms if at least one site has alerts enabled
        if (ids.isNotEmpty()) {
            com.gzavagli.paraweather.data.receiver.AlertReceiver.scheduleRepeatingAlarms(context)
        } else {
            com.gzavagli.paraweather.data.receiver.AlertReceiver.cancelAlarms(context)
        }
    }

    suspend fun updateAlertInspectionPeriodDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALERT_INSPECTION_PERIOD_DAYS] = days
        }
    }

    suspend fun addSavedLocation(location: SavedLocation) {
        context.dataStore.edit { preferences ->
            val locationsJson = preferences[PreferencesKeys.SAVED_LOCATIONS_JSON] ?: "[]"
            val currentList = try {
                jsonAdapter.fromJson(locationsJson)?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            // Remove if already exists with same ID or coordinates to avoid duplicates
            currentList.removeAll { it.id == location.id || (it.latitude == location.latitude && it.longitude == location.longitude) }
            currentList.add(location)

            preferences[PreferencesKeys.SAVED_LOCATIONS_JSON] = jsonAdapter.toJson(currentList)
        }
    }

    suspend fun deleteSavedLocation(id: String) {
        if (id == "current_gps") return // Cannot delete GPS location
        context.dataStore.edit { preferences ->
            val locationsJson = preferences[PreferencesKeys.SAVED_LOCATIONS_JSON] ?: "[]"
            val currentList = try {
                jsonAdapter.fromJson(locationsJson)?.toMutableList() ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            currentList.removeAll { it.id == id }
            preferences[PreferencesKeys.SAVED_LOCATIONS_JSON] = jsonAdapter.toJson(currentList)

            // If active was deleted, reset active to GPS
            val activeLocId = preferences[PreferencesKeys.ACTIVE_LOCATION_ID] ?: "current_gps"
            if (activeLocId == id) {
                preferences[PreferencesKeys.ACTIVE_LOCATION_ID] = "current_gps"
            }

            // Also clean up deleted location from alerts set!
            val alertIds = preferences[PreferencesKeys.ALERT_LOCATION_IDS] ?: emptySet()
            if (alertIds.contains(id)) {
                preferences[PreferencesKeys.ALERT_LOCATION_IDS] = alertIds.toMutableSet().apply { remove(id) }
            }
        }
    }
}
