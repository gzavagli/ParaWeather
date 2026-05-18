package com.gzavagli.paraweather.data.repository

import com.gzavagli.paraweather.data.model.WeatherResponse

interface WeatherRepository {
    suspend fun getForecast(latitude: Double, longitude: Double): WeatherResponse
}
