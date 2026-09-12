package com.ruggerocadamuro.myapplication.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ruggerocadamuro.myapplication.MainActivity
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.data.recording.RideRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RideRecordingService : Service() {
    private val recorder by lazy { RecorderHolder.recorder(this) }
    private lateinit var locationManager: LocationManager
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = recorder.onLocation(location)
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                recorder.stop()
                stopLocation()
                stopSelf()
            }
            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
            else -> {
                recorder.start()
                startForegroundCompat()
                requestLocation()
            }
        }
        return START_NOT_STICKY
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        runCatching {
            locationManager.requestLocationUpdates(provider, 1000L, 2f, listener)
            locationManager.getLastKnownLocation(provider)?.let(recorder::onLocation)
        }
    }

    private fun stopLocation() = runCatching { locationManager.removeUpdates(listener) }

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(action: String, label: String, requestCode: Int): Notification.Action =
            Notification.Action.Builder(
                null,
                label,
                PendingIntent.getService(
                    this, requestCode,
                    Intent(this, RideRecordingService::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("VescViewer — registrazione attiva")
            .setContentText("GPS e telemetria vengono salvati in background")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(action(ACTION_PAUSE, "Pausa", 1))
            .addAction(action(ACTION_STOP, "Termina", 2))
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Registrazione uscite", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        stopLocation()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "vesc.START_RECORDING"
        const val ACTION_STOP = "vesc.STOP_RECORDING"
        const val ACTION_PAUSE = "vesc.PAUSE_RECORDING"
        const val ACTION_RESUME = "vesc.RESUME_RECORDING"
        private const val CHANNEL_ID = "ride_recording"
        private const val NOTIFICATION_ID = 43

        fun start(context: Context) {
            val intent = Intent(context, RideRecordingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) = context.startService(
            Intent(context, RideRecordingService::class.java).setAction(ACTION_STOP)
        )

        fun pause(context: Context) = context.startService(
            Intent(context, RideRecordingService::class.java).setAction(ACTION_PAUSE)
        )

        fun resume(context: Context) = context.startService(
            Intent(context, RideRecordingService::class.java).setAction(ACTION_RESUME)
        )
    }
}

object RecorderHolder {
    @Volatile private var instance: RideRecorder? = null
    fun recorder(context: Context): RideRecorder = instance ?: synchronized(this) {
        instance ?: RideRecorder().also { instance = it }
    }
    fun state(): StateFlow<com.ruggerocadamuro.myapplication.data.recording.RecordingState> =
        instance?.state ?: MutableStateFlow(com.ruggerocadamuro.myapplication.data.recording.RecordingState()).asStateFlow()
}
