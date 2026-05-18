package com.gzavagli.paraweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gzavagli.paraweather.data.preferences.TakeoffDirection
import com.gzavagli.paraweather.data.preferences.UserPreferences
import com.gzavagli.paraweather.data.preferences.UserPreferencesRepository
import com.gzavagli.paraweather.data.preferences.WindUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences?> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun updateMinWindSpeed(speed: Double) {
        viewModelScope.launch {
            userPreferencesRepository.updateMinWindSpeed(speed)
        }
    }

    fun updateMaxWindSpeed(speed: Double) {
        viewModelScope.launch {
            userPreferencesRepository.updateMaxWindSpeed(speed)
        }
    }

    fun updateMaxGustSpeed(speed: Double) {
        viewModelScope.launch {
            userPreferencesRepository.updateMaxGustSpeed(speed)
        }
    }

    fun updateWindUnit(unit: WindUnit) {
        viewModelScope.launch {
            userPreferencesRepository.updateWindUnit(unit)
        }
    }

    fun updateTakeoffDirection(direction: TakeoffDirection) {
        viewModelScope.launch {
            userPreferencesRepository.updateTakeoffDirection(direction)
        }
    }

    fun updateAlertInspectionPeriodDays(days: Int) {
        viewModelScope.launch {
            userPreferencesRepository.updateAlertInspectionPeriodDays(days)
        }
    }
}
