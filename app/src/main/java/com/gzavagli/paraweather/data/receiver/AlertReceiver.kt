package com.gzavagli.paraweather.data.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gzavagli.paraweather.data.preferences.UserPreferencesRepository
import com.gzavagli.paraweather.data.worker.ThermalAlertWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class AlertReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        android.util.Log.d("ParaWeatherAlerts", "AlertReceiver.onReceive called with action: $action")

        // 1. Run scans on exact 6 AM / 6 PM triggers or via manual ADB developer broadcasts
        if (action == "com.gzavagli.paraweather.RUN_SCAN" || action == "com.gzavagli.paraweather.TRIGGER_ALERT_SCAN") {
            android.util.Log.d("ParaWeatherAlerts", "Enqueuing ThermalAlertWorker task inside WorkManager...")
            val workRequest = OneTimeWorkRequestBuilder<ThermalAlertWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        } 

        // 2. Re-register alarms on device reboot so they are never lost
        else if (action == Intent.ACTION_BOOT_COMPLETED) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ReceiverEntryPoint::class.java
            )
            val prefsRepo = entryPoint.userPreferencesRepository()

            CoroutineScope(Dispatchers.IO).launch {
                val prefs = prefsRepo.userPreferencesFlow.first()
                if (prefs.alertLocationIds.isNotEmpty()) {
                    scheduleRepeatingAlarms(context)
                }
            }
        }
    }

    companion object {
        fun scheduleRepeatingAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Schedule 6:00 AM Alarm
            val alarmIntent6AM = Intent(context, AlertReceiver::class.java).apply {
                action = "com.gzavagli.paraweather.RUN_SCAN"
            }
            val pendingIntent6AM = PendingIntent.getBroadcast(
                context,
                1001,
                alarmIntent6AM,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val calendar6AM = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1) // If already past 6 AM, schedule for tomorrow
                }
            }

            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar6AM.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent6AM
            )

            // Schedule 6:00 PM Alarm
            val alarmIntent6PM = Intent(context, AlertReceiver::class.java).apply {
                action = "com.gzavagli.paraweather.RUN_SCAN"
            }
            val pendingIntent6PM = PendingIntent.getBroadcast(
                context,
                1002,
                alarmIntent6PM,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val calendar6PM = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1) // If already past 6 PM, schedule for tomorrow
                }
            }

            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar6PM.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent6PM
            )
        }

        fun cancelAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val alarmIntent6AM = Intent(context, AlertReceiver::class.java).apply {
                action = "com.gzavagli.paraweather.RUN_SCAN"
            }
            val pendingIntent6AM = PendingIntent.getBroadcast(
                context,
                1001,
                alarmIntent6AM,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent6AM)

            val alarmIntent6PM = Intent(context, AlertReceiver::class.java).apply {
                action = "com.gzavagli.paraweather.RUN_SCAN"
            }
            val pendingIntent6PM = PendingIntent.getBroadcast(
                context,
                1002,
                alarmIntent6PM,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent6PM)
        }
    }
}
