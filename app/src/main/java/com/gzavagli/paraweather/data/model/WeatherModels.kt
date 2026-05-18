package com.gzavagli.paraweather.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "hourly_units") val hourlyUnits: HourlyUnits,
    @Json(name = "hourly") val hourly: HourlyData
)

@JsonClass(generateAdapter = true)
data class HourlyUnits(
    @Json(name = "time") val time: String,
    @Json(name = "temperature_2m") val temperature2m: String,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: String,
    @Json(name = "precipitation_probability") val precipitationProbability: String,
    @Json(name = "precipitation") val precipitation: String,
    @Json(name = "cloud_cover") val cloudCover: String,
    @Json(name = "wind_speed_10m") val windSpeed10m: String,
    @Json(name = "wind_direction_10m") val windDirection10m: String,
    @Json(name = "wind_gusts_10m") val windGusts10m: String,
    @Json(name = "lifted_index") val liftedIndex: String,
    @Json(name = "cape") val cape: String,
    @Json(name = "shortwave_radiation") val shortwaveRadiation: String,
    @Json(name = "boundary_layer_height") val boundaryLayerHeight: String
)

@JsonClass(generateAdapter = true)
data class HourlyData(
    @Json(name = "time") val time: List<String>,
    @Json(name = "temperature_2m") val temperature2m: List<Double>,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: List<Double>,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>,
    @Json(name = "precipitation") val precipitation: List<Double>,
    @Json(name = "cloud_cover") val cloudCover: List<Int>,
    @Json(name = "wind_speed_10m") val windSpeed10m: List<Double>,
    @Json(name = "wind_direction_10m") val windDirection10m: List<Double>,
    @Json(name = "wind_gusts_10m") val windGusts10m: List<Double>,
    @Json(name = "lifted_index") val liftedIndex: List<Double>,
    @Json(name = "cape") val cape: List<Double>,
    @Json(name = "shortwave_radiation") val shortwaveRadiation: List<Double>,
    @Json(name = "boundary_layer_height") val boundaryLayerHeight: List<Double>
)
