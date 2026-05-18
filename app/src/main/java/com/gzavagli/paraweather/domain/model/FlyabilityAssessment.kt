package com.gzavagli.paraweather.domain.model

enum class FlyabilityStatus {
    EXCELLENT,
    IDEAL,
    MARGINAL,
    UNFLYABLE
}

data class FlyabilityAssessment(
    val status: FlyabilityStatus,
    val reasons: List<String>,
    val averageWindSpeed: Double,
    val gustSpeed: Double,
    val windDirection: Double,
    val isRainExpected: Boolean,
    val cape: Double,
    val liftedIndex: Double,
    val thermalChance: ThermalChance,
    val boundaryLayerHeight: Double
)
