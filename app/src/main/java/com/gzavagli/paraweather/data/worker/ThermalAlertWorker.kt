package com.gzavagli.paraweather.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gzavagli.paraweather.MainActivity
import com.gzavagli.paraweather.data.preferences.UserPreferencesRepository
import com.gzavagli.paraweather.data.repository.WeatherRepository
import com.gzavagli.paraweather.domain.AssessFlyabilityUseCase
import com.gzavagli.paraweather.domain.model.ThermalChance
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ThermalAlertWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // Define Hilt EntryPoint interface to statically inject Singletons in CoroutineWorker
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ThermalWorkerEntryPoint {
        fun weatherRepository(): WeatherRepository
        fun userPreferencesRepository(): UserPreferencesRepository
        fun assessFlyabilityUseCase(): AssessFlyabilityUseCase
    }

    override suspend fun doWork(): Result {
        android.util.Log.d("ParaWeatherAlerts", "ThermalAlertWorker.doWork started!")
        try {
            // Resolve entry point accessors statically
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ThermalWorkerEntryPoint::class.java
            )
            val weatherRepository = entryPoint.weatherRepository()
            val userPreferencesRepository = entryPoint.userPreferencesRepository()
            val assessFlyabilityUseCase = entryPoint.assessFlyabilityUseCase()

            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val alertIds = prefs.alertLocationIds
            android.util.Log.d("ParaWeatherAlerts", "Loaded user preferences. Scan Days: ${prefs.alertInspectionPeriodDays}. Active Alert Location IDs: $alertIds")
            
            if (alertIds.isEmpty()) {
                android.util.Log.d("ParaWeatherAlerts", "No locations selected for alerts. Exiting worker.")
                return Result.success()
            }

            val scanDays = prefs.alertInspectionPeriodDays
            val limitHours = scanDays * 24

            for (locId in alertIds) {
                val loc = prefs.savedLocations.find { it.id == locId } ?: continue
                if (loc.isCurrentGps) continue

                android.util.Log.d("ParaWeatherAlerts", "Starting weather scan for flight site: ${loc.name}...")
                val response = weatherRepository.getForecast(loc.latitude, loc.longitude)
                val hourly = response.hourly

                val currentHourStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
                var startIndex = hourly.time.indexOfFirst { it.startsWith(currentHourStr) }
                if (startIndex == -1) startIndex = 0

                val maxLimit = minOf(startIndex + limitHours, hourly.time.size)
                android.util.Log.d("ParaWeatherAlerts", "Scanning ${maxLimit - startIndex} hourly intervals from index $startIndex to $maxLimit...")

                var highThermalFound = false
                for (i in startIndex until maxLimit) {
                    val assessment = assessFlyabilityUseCase(
                        averageWindSpeed = hourly.windSpeed10m[i],
                        gustSpeed = hourly.windGusts10m[i],
                        windDirection = hourly.windDirection10m[i],
                        precipitationProbability = hourly.precipitationProbability[i],
                        precipitation = hourly.precipitation[i],
                        cape = hourly.cape[i],
                        liftedIndex = hourly.liftedIndex[i],
                        shortwaveRadiation = hourly.shortwaveRadiation[i],
                        boundaryLayerHeight = hourly.boundaryLayerHeight[i],
                        preferences = prefs
                    )

                    if (assessment.thermalChance == ThermalChance.HIGH) {
                        highThermalFound = true
                        val rawTime = hourly.time[i]
                        val parsedTime = LocalDateTime.parse(rawTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        val formattedDay = parsedTime.format(DateTimeFormatter.ofPattern("E, MMM d"))
                        val formattedHour = parsedTime.format(DateTimeFormatter.ofPattern("HH:00"))

                        android.util.Log.d("ParaWeatherAlerts", "MATCH FOUND! HIGH convective lift at ${loc.name} on $formattedDay at $formattedHour! Triggering notification...")
                        triggerLocalNotification(
                            siteName = loc.name,
                            dayLabel = formattedDay,
                            hourLabel = formattedHour,
                            boundaryHeight = assessment.boundaryLayerHeight.toInt()
                        )
                        break // Single notification per site limit
                    }
                }
                
                if (!highThermalFound) {
                    android.util.Log.d("ParaWeatherAlerts", "No HIGH soaring windows found for ${loc.name} inside scan period.")
                }
            }
            android.util.Log.d("ParaWeatherAlerts", "ThermalAlertWorker completed successfully!")
            return Result.success()
        } catch (e: Exception) {
            android.util.Log.e("ParaWeatherAlerts", "ThermalAlertWorker failed with exception: ${e.localizedMessage}", e)
            return Result.retry()
        }
    }

    private fun triggerLocalNotification(siteName: String, dayLabel: String, hourLabel: String, boundaryHeight: Int) {
        val channelId = "thermal_alerts_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Convective Flight Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies pilots when exceptional thermal soaring windows are forecasted."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = "Outstanding thermal lift (ceiling ${boundaryHeight}m) is forecasted at $siteName on $dayLabel at $hourLabel!"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🚀 Exceptional Soaring Forecasted!")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(siteName.hashCode(), notification)
    }
}
