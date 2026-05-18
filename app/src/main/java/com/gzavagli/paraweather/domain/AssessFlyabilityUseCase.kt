package com.gzavagli.paraweather.domain

import com.gzavagli.paraweather.data.preferences.TakeoffDirection
import com.gzavagli.paraweather.data.preferences.UserPreferences
import com.gzavagli.paraweather.domain.model.FlyabilityAssessment
import com.gzavagli.paraweather.domain.model.FlyabilityStatus
import com.gzavagli.paraweather.domain.model.ThermalChance
import javax.inject.Inject
import kotlin.math.abs

class AssessFlyabilityUseCase @Inject constructor() {

    operator fun invoke(
        averageWindSpeed: Double,
        gustSpeed: Double,
        windDirection: Double,
        precipitationProbability: Int,
        precipitation: Double,
        cape: Double,
        liftedIndex: Double,
        shortwaveRadiation: Double,
        boundaryLayerHeight: Double,
        preferences: UserPreferences
    ): FlyabilityAssessment {
        val reasons = mutableListOf<String>()
        var status = FlyabilityStatus.EXCELLENT

        // 1. Wind Speed check
        if (averageWindSpeed > preferences.maxWindSpeed) {
            status = FlyabilityStatus.UNFLYABLE
            reasons.add("Too windy (avg ${averageWindSpeed.toInt()} ${preferences.windUnit.label} exceeds limit of ${preferences.maxWindSpeed.toInt()})")
        } else if (averageWindSpeed < preferences.minWindSpeed) {
            // Light wind is flyable but might be harder to stay up or launch
            if (status != FlyabilityStatus.UNFLYABLE) {
                status = FlyabilityStatus.MARGINAL
            }
            reasons.add("Light wind (under ${preferences.minWindSpeed.toInt()} ${preferences.windUnit.label})")
        }

        // 2. Gust Speed check
        if (gustSpeed > preferences.maxGustSpeed) {
            status = FlyabilityStatus.UNFLYABLE
            reasons.add("Dangerous gusts (${gustSpeed.toInt()} ${preferences.windUnit.label} exceeds limit of ${preferences.maxGustSpeed.toInt()})")
        } else {
            val spread = gustSpeed - averageWindSpeed
            if (spread > 10.0) {
                if (status != FlyabilityStatus.UNFLYABLE) {
                    status = FlyabilityStatus.MARGINAL
                }
                reasons.add("Gusty (spread of ${spread.toInt()} ${preferences.windUnit.label})")
            }
        }

        // 3. Precipitation check
        if (precipitation > 0.1 || precipitationProbability > 30) {
            status = FlyabilityStatus.UNFLYABLE
            reasons.add("Precipitation expected (${precipitationProbability}% chance)")
        }

        // 4. Atmospheric stability / Thunderstorm Risk (CAPE & Lifted Index)
        if (cape > 500.0) {
            status = FlyabilityStatus.UNFLYABLE
            reasons.add("High thunderstorm risk (CAPE = ${cape.toInt()} J/kg)")
        } else if (cape > 200.0) {
            if (status != FlyabilityStatus.UNFLYABLE) {
                status = FlyabilityStatus.MARGINAL
            }
            reasons.add("Moderate convective potential (CAPE = ${cape.toInt()} J/kg)")
        }

        if (liftedIndex < -1.0) {
            status = FlyabilityStatus.UNFLYABLE
            reasons.add("Atmospheric instability (Lifted Index = $liftedIndex)")
        }

        // 5. Takeoff Wind Alignment check
        val preferredHeading = preferences.takeoffDirection.heading
        if (preferredHeading != null) {
            val angleDiff = getAngleDifference(windDirection, preferredHeading)
            if (angleDiff > 135.0) {
                status = FlyabilityStatus.UNFLYABLE
                reasons.add("Dangerous Tailwind at launch (approx ${angleDiff.toInt()}° off takeoff heading)")
            } else if (angleDiff > 45.0) {
                if (status != FlyabilityStatus.UNFLYABLE) {
                    status = FlyabilityStatus.MARGINAL
                }
                reasons.add("Crosswind risk at launch (${angleDiff.toInt()}° off takeoff heading)")
            }
        }

        // 6. Calculate Thermal Soaring Quality Score
        val thermalChance = when {
            status == FlyabilityStatus.UNFLYABLE -> ThermalChance.NONE // Forced none if unsafe
            shortwaveRadiation > 500.0 && boundaryLayerHeight > 800.0 && averageWindSpeed < 15.0 -> ThermalChance.HIGH
            shortwaveRadiation > 250.0 && boundaryLayerHeight > 400.0 && averageWindSpeed < 18.0 -> ThermalChance.MEDIUM
            shortwaveRadiation > 100.0 && boundaryLayerHeight > 200.0 && averageWindSpeed < 22.0 -> ThermalChance.LOW
            else -> ThermalChance.NONE
        }

        // Adjust to IDEAL if no unflyable or marginal factors and average wind is perfectly in sweet spot
        if (status == FlyabilityStatus.EXCELLENT && reasons.isEmpty()) {
            val sweetSpotMin = (preferences.minWindSpeed + preferences.maxWindSpeed) / 3.0
            val sweetSpotMax = (preferences.minWindSpeed + preferences.maxWindSpeed) * 0.75
            if (averageWindSpeed in sweetSpotMin..sweetSpotMax) {
                status = FlyabilityStatus.IDEAL
            }
        }

        return FlyabilityAssessment(
            status = status,
            reasons = reasons,
            averageWindSpeed = averageWindSpeed,
            gustSpeed = gustSpeed,
            windDirection = windDirection,
            isRainExpected = precipitation > 0.1 || precipitationProbability > 30,
            cape = cape,
            liftedIndex = liftedIndex,
            thermalChance = thermalChance,
            boundaryLayerHeight = boundaryLayerHeight
        )
    }

    private fun getAngleDifference(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360
        return if (diff > 180.0) 360.0 - diff else diff
    }
}
