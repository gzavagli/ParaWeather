package com.gzavagli.paraweather.domain

import com.gzavagli.paraweather.data.preferences.TakeoffDirection
import com.gzavagli.paraweather.data.preferences.UserPreferences
import com.gzavagli.paraweather.data.preferences.WindUnit
import com.gzavagli.paraweather.domain.model.FlyabilityStatus
import com.gzavagli.paraweather.domain.model.ThermalChance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssessFlyabilityUseCaseTest {

    private lateinit var useCase: AssessFlyabilityUseCase
    private lateinit var defaultPrefs: UserPreferences

    @Before
    fun setUp() {
        useCase = AssessFlyabilityUseCase()
        defaultPrefs = UserPreferences(
            minWindSpeed = 5.0,
            maxWindSpeed = 20.0,
            maxGustSpeed = 25.0,
            windUnit = WindUnit.KMH,
            takeoffDirection = TakeoffDirection.ANY,
            activeLocationId = "current_gps",
            savedLocations = emptyList(),
            alertLocationIds = emptySet(),
            alertInspectionPeriodDays = 3
        )
    }

    @Test
    fun `when conditions are perfect then return IDEAL or EXCELLENT`() {
        val result = useCase(
            averageWindSpeed = 12.0,
            gustSpeed = 15.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = defaultPrefs
        )

        assertTrue(result.status == FlyabilityStatus.IDEAL || result.status == FlyabilityStatus.EXCELLENT)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `when wind speed is above limit then return UNFLYABLE`() {
        val result = useCase(
            averageWindSpeed = 25.0,
            gustSpeed = 28.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = defaultPrefs
        )

        assertEquals(FlyabilityStatus.UNFLYABLE, result.status)
        assertTrue(result.reasons.any { it.contains("Too windy") })
    }

    @Test
    fun `when wind speed is too light then return MARGINAL`() {
        val result = useCase(
            averageWindSpeed = 2.0,
            gustSpeed = 4.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = defaultPrefs
        )

        assertEquals(FlyabilityStatus.MARGINAL, result.status)
        assertTrue(result.reasons.any { it.contains("Light wind") })
    }

    @Test
    fun `when gust exceeds limit then return UNFLYABLE`() {
        val result = useCase(
            averageWindSpeed = 12.0,
            gustSpeed = 30.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = defaultPrefs
        )

        assertEquals(FlyabilityStatus.UNFLYABLE, result.status)
        assertTrue(result.reasons.any { it.contains("Dangerous gusts") })
    }

    @Test
    fun `when rain is expected then return UNFLYABLE`() {
        val result = useCase(
            averageWindSpeed = 10.0,
            gustSpeed = 12.0,
            windDirection = 180.0,
            precipitationProbability = 50,
            precipitation = 0.5,
            cape = 10.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = defaultPrefs
        )

        assertEquals(FlyabilityStatus.UNFLYABLE, result.status)
        assertTrue(result.reasons.any { it.contains("Precipitation expected") })
    }

    @Test
    fun `when CAPE is extremely high then return UNFLYABLE`() {
        val result = useCase(
            averageWindSpeed = 10.0,
            gustSpeed = 12.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 600.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = defaultPrefs
        )

        assertEquals(FlyabilityStatus.UNFLYABLE, result.status)
        assertTrue(result.reasons.any { it.contains("High thunderstorm risk") })
    }

    @Test
    fun `when takeoff orientation is South and wind is North then return UNFLYABLE tailwind`() {
        val prefsWithOrientation = defaultPrefs.copy(takeoffDirection = TakeoffDirection.S) // 180°
        
        val result = useCase(
            averageWindSpeed = 10.0,
            gustSpeed = 12.0,
            windDirection = 0.0, // North (opposite of South)
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = prefsWithOrientation
        )

        assertEquals(FlyabilityStatus.UNFLYABLE, result.status)
        assertTrue(result.reasons.any { it.contains("Tailwind at launch") })
    }

    @Test
    fun `when takeoff orientation is South and wind is East then return MARGINAL crosswind`() {
        val prefsWithOrientation = defaultPrefs.copy(takeoffDirection = TakeoffDirection.S) // 180°
        
        val result = useCase(
            averageWindSpeed = 10.0,
            gustSpeed = 12.0,
            windDirection = 90.0, // East (90 degrees crosswind)
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 2.0,
            shortwaveRadiation = 300.0,
            boundaryLayerHeight = 500.0,
            preferences = prefsWithOrientation
        )

        assertEquals(FlyabilityStatus.MARGINAL, result.status)
        assertTrue(result.reasons.any { it.contains("Crosswind risk at launch") })
    }

    // --- Thermal Soaring Quality Tests ---

    @Test
    fun `when sun is strong, mixing ceiling is high, and winds are light then thermal chance is HIGH`() {
        val result = useCase(
            averageWindSpeed = 10.0,
            gustSpeed = 12.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 100.0,
            liftedIndex = -0.5,
            shortwaveRadiation = 600.0, // Strong solar
            boundaryLayerHeight = 1200.0, // High ceiling
            preferences = defaultPrefs
        )

        assertEquals(ThermalChance.HIGH, result.thermalChance)
    }

    @Test
    fun `when sun is moderate and ceiling is moderate then thermal chance is MEDIUM`() {
        val result = useCase(
            averageWindSpeed = 12.0,
            gustSpeed = 14.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 50.0,
            liftedIndex = 0.0,
            shortwaveRadiation = 350.0, // Moderate solar
            boundaryLayerHeight = 600.0, // Moderate ceiling
            preferences = defaultPrefs
        )

        assertEquals(ThermalChance.MEDIUM, result.thermalChance)
    }

    @Test
    fun `when sun is weak and ceiling is shallow then thermal chance is LOW`() {
        val result = useCase(
            averageWindSpeed = 8.0,
            gustSpeed = 10.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 1.0,
            shortwaveRadiation = 150.0, // Weak solar
            boundaryLayerHeight = 300.0, // Shallow ceiling
            preferences = defaultPrefs
        )

        assertEquals(ThermalChance.LOW, result.thermalChance)
    }

    @Test
    fun `when it is overcast or night then thermal chance is NONE`() {
        val result = useCase(
            averageWindSpeed = 8.0,
            gustSpeed = 10.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 10.0,
            liftedIndex = 1.0,
            shortwaveRadiation = 20.0, // Very low solar (night/overcast)
            boundaryLayerHeight = 100.0,
            preferences = defaultPrefs
        )

        assertEquals(ThermalChance.NONE, result.thermalChance)
    }

    @Test
    fun `when safety status is UNFLYABLE then thermal chance is forced to NONE`() {
        val result = useCase(
            averageWindSpeed = 30.0, // Too windy (Unflyable!)
            gustSpeed = 35.0,
            windDirection = 180.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            cape = 100.0,
            liftedIndex = -0.5,
            shortwaveRadiation = 600.0, // Excellent thermal conditions theoretically
            boundaryLayerHeight = 1200.0,
            preferences = defaultPrefs
        )

        assertEquals(FlyabilityStatus.UNFLYABLE, result.status)
        assertEquals(ThermalChance.NONE, result.thermalChance) // Forced to none for safety!
    }
}
