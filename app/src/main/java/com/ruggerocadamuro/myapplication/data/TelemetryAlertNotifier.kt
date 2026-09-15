package com.ruggerocadamuro.myapplication.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ruggerocadamuro.myapplication.R

/** Delivers debounced telemetry alerts without coupling the evaluator to Android APIs. */
object TelemetryAlertNotifier {
    private const val CHANNEL_ID = "telemetry_alerts"
    private const val NOTIFICATION_ID = 44

    fun notify(context: Context, type: TelemetryAlertType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.telemetry_alert_title))
            .setContentText(context.getString(messageRes(type)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + type.ordinal, notification)
    }

    private fun messageRes(type: TelemetryAlertType): Int = when (type) {
        TelemetryAlertType.LOW_BATTERY -> R.string.event_alert_low_battery
        TelemetryAlertType.HIGH_TEMPERATURE -> R.string.event_alert_high_temperature
        TelemetryAlertType.HIGH_CURRENT -> R.string.event_alert_high_current
        TelemetryAlertType.LOW_VOLTAGE -> R.string.event_alert_low_voltage
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.telemetry_alert_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.telemetry_alert_channel_desc)
            }
        )
    }
}
