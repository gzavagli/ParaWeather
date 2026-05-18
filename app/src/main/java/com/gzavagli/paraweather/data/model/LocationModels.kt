package com.gzavagli.paraweather.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SavedLocation(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "isCurrentGps") val isCurrentGps: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OverpassResponse(
    @Json(name = "elements") val elements: List<OverpassElement>
)

@JsonClass(generateAdapter = true)
data class OverpassElement(
    @Json(name = "id") val id: Long,
    @Json(name = "lat") val lat: Double?,
    @Json(name = "lon") val lon: Double?,
    @Json(name = "center") val center: OverpassCenter?,
    @Json(name = "tags") val tags: Map<String, String>?
)

@JsonClass(generateAdapter = true)
data class OverpassCenter(
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double
)

// Simple Helper to round doubles cleanly across location and map screens
fun Double.coerceDecimals(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10.0 }
    return kotlin.math.round(this * multiplier) / multiplier
}

