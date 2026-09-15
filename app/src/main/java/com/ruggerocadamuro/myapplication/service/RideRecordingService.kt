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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ruggerocadamuro.myapplication.MainActivity
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.recording.LocationStatus
import com.ruggerocadamuro.myapplication.data.recording.RideRecorder
import com.ruggerocadamuro.myapplication.data.settings.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RideRecordingService : Service() {
    private val recorder by lazy { RecorderHolder.recorder(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var notificationJob: Job? = null
    private var foregroundStarted = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private lateinit var locationManager: LocationManager
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val ageMs = System.currentTimeMillis() - location.time
            recorder.setLocationStatus(
                if (ageMs <= 30_000L && (!location.hasAccuracy() || location.accuracy <= 100f)) {
                    LocationStatus.READY
                } else {
                    LocationStatus.STALE
                }
            )
            recorder.onLocation(location)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated by Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
        serviceScope.launch {
            val recovered = runCatching {
                ServiceLocator.database.rideDao().finalizeIncompleteSessions(System.currentTimeMillis())
            }.getOrElse { error ->
                Log.e(TAG, "Impossibile finalizzare sessioni interrotte", error)
                0
            }
            if (recovered > 0) Log.i(TAG, "Finalizzate $recovered sessioni dopo il riavvio")
        }
        notificationJob = serviceScope.launch {
            recorder.state.collect {
                if (foregroundStarted) updateNotification()
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                recorder.stop { success ->
                    if (success) {
                        stopLocation()
                        stopSelf()
                    } else {
                        updateNotification()
                    }
                }
            }
            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
            else -> {
                startForegroundCompat()
                recorder.start()
                requestLocation()
            }
        }
        return START_STICKY
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            recorder.setLocationStatus(LocationStatus.PERMISSION_MISSING)
            return
        }
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            recorder.setLocationStatus(LocationStatus.PROVIDER_DISABLED)
            return
        }
        runCatching {
            locationManager.requestLocationUpdates(provider, 1000L, 2f, listener)
            val lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown != null) {
                val ageMs = System.currentTimeMillis() - lastKnown.time
                recorder.setLocationStatus(
                    if (ageMs <= 30_000L) LocationStatus.READY else LocationStatus.STALE
                )
                if (ageMs <= 30_000L) recorder.onLocation(lastKnown)
            } else {
                recorder.setLocationStatus(LocationStatus.WAITING)
            }
        }.onFailure {
            Log.w(TAG, "Impossibile avviare il provider di posizione", it)
            recorder.setLocationStatus(LocationStatus.UNAVAILABLE)
        }
    }

    private fun stopLocation() = runCatching { locationManager.removeUpdates(listener) }

    private fun startForegroundCompat() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0
        foregroundStarted = true
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(action: String, label: String, requestCode: Int): NotificationCompat.Action =
            NotificationCompat.Action.Builder(
                0,
                label,
                PendingIntent.getService(
                    this, requestCode,
                    Intent(this, RideRecordingService::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        val recordingState = recorder.state.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(
                when {
                    recordingState.error == com.ruggerocadamuro.myapplication.data.recording.RecordingError.PERSISTENCE ->
                        getString(R.string.recording_notification_error)
                    recordingState.locationStatus == LocationStatus.PERMISSION_MISSING ->
                        getString(R.string.recording_location_permission_missing)
                    recordingState.locationStatus == LocationStatus.PROVIDER_DISABLED ->
                        getString(R.string.recording_location_provider_disabled)
                    recordingState.locationStatus == LocationStatus.STALE ->
                        getString(R.string.recording_location_stale)
                    else -> getString(R.string.recording_notification_text)
                }
            )
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                action(
                    if (recordingState.paused) ACTION_RESUME else ACTION_PAUSE,
                    getString(if (recordingState.paused) R.string.recording_resume else R.string.recording_pause),
                    1
                )
            )
            .addAction(action(ACTION_STOP, getString(R.string.recording_stop), 2))
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onDestroy() {
        foregroundStarted = false
        notificationJob?.cancel()
        serviceScope.cancel()
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
        private const val TAG = "RideRecordingService"

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
