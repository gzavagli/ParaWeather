package com.gzavagli.paraweather.data.repository

import com.gzavagli.paraweather.data.api.OpenMeteoApi
import com.gzavagli.paraweather.data.model.WeatherResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val openMeteoApi: OpenMeteoApi
) : WeatherRepository {
    override suspend fun getForecast(latitude: Double, longitude: Double): WeatherResponse {
        return openMeteoApi.getForecast(latitude, longitude)
    }
}
