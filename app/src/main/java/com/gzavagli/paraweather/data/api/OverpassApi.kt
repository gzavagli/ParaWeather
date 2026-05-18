package com.gzavagli.paraweather.data.api

import com.gzavagli.paraweather.data.model.OverpassResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface OverpassApi {
    @Headers(
        "User-Agent: ParaWeatherApp/1.0 (Android; gzavagli)",
        "Accept: application/json"
    )
    @GET("api/interpreter")
    suspend fun getNearbyLaunches(
        @Query("data") query: String
    ): OverpassResponse

    companion object {
        const val BASE_URL = "https://overpass-api.de/"
    }
}
