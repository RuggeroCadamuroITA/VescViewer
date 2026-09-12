package com.ruggerocadamuro.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ruggerocadamuro.myapplication.MainActivity
import com.ruggerocadamuro.myapplication.R
import com.ruggerocadamuro.myapplication.ServiceLocator
import com.ruggerocadamuro.myapplication.data.VescRepository
import com.ruggerocadamuro.myapplication.data.settings.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service che mantiene vivo, anche a schermo spento o app in
 * background:
 *  - la connessione BLE (una sola, condivisa con la UI via VescRepository)
 *  - il monitoraggio RSSI dell'allarme anti-allontanamento
 *  - la sirena quando l'allarme scatta
 *
 * La notifica persistente e' obbligatoria per un foreground service: qui
 * informativa ("Monitoraggio anti-allontanamento attivo") e aggiornata in
 * "ALLARME" quando la sirena parte.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val ACTION_START = "vesc.START_ALARM"
        const val ACTION_STOP = "vesc.STOP_ALARM"
        private const val CHANNEL_ID = "alarm_monitor"
        private const val NOTIF_ID = 42
        private const val WAKELOCK_TAG = "vesc:alarm_monitor"
        private const val TAG = "AlarmService"

        /** Avvio comodo dal resto dell'app. */
        fun start(context: Context) {
            val intent = Intent(context, AlarmForegroundService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        /**
         * Unico ingresso per accendere/spegnere la protezione: avvia o ferma il
         * foreground service (necessario su Android 12+ per il tipo
         * connectedDevice) e attiva/disattiva il monitoraggio nel repository.
         * Usato sia dalla dashboard sia dalle impostazioni.
         */
        fun setEnabled(context: Context, enabled: Boolean) {
            if (enabled) {
                start(context)
                ServiceLocator.vescRepository.enableAlarm()
            } else {
                ServiceLocator.vescRepository.disableAlarm()
                stop(context)
            }
        }
    }

    /**
     * Anche la notifica persistente deve usare la lingua scelta dall'utente:
     * il Service non eredita la Configuration dell'Activity, quindi la
     * applichiamo qui al suo Context.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private val repo: VescRepository by lazy { ServiceLocator.vescRepository }
    private val siren by lazy { SirenPlayer(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var collectJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifiedStatus: VescRepository.AlarmStatus? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                repo.disableAlarm()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startAsForeground()

        // wake lock parziale: mantiene la CPU viva per il tick RSSI a schermo spento.
        // L'utente deve comunque escludere l'app dall'ottimizzazione batteria
        // (vedi schermata impostazioni) per affidabilita' massima.
        // L'acquisizione e' protetta: se il permesso WAKE_LOCK manca (installazioni
        // precedenti alla dichiarazione nel manifest) il monitoraggio deve
        // continuare lo stesso, non far crashare il processo.
        if (wakeLock == null) {
            wakeLock = runCatching {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onFailure {
                Log.w(TAG, "Wake lock non acquisibile: monitoraggio senza CPU wake lock", it)
            }.getOrNull()
        }

        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            repo.alarmState.collect { state ->
                when (state.status) {
                    VescRepository.AlarmStatus.ALARM -> {
                        siren.start()
                        notifyStatus(VescRepository.AlarmStatus.ALARM)
                    }
                    VescRepository.AlarmStatus.MONITORING -> {
                        siren.stop()
                        notifyStatus(VescRepository.AlarmStatus.MONITORING)
                    }
                    VescRepository.AlarmStatus.OFF -> {
                        siren.stop()
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(false), type)
    }

    private fun buildNotification(alarm: Boolean): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AlarmForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(
                getString(
                    if (alarm) R.string.alarm_active_title
                    else R.string.alarm_notification_title
                )
            )
            .setContentText(
                getString(
                    if (alarm) R.string.alarm_active_text
                    else R.string.alarm_notification_text
                )
            )
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (alarm) {
                    setPriority(NotificationCompat.PRIORITY_MAX)
                    setCategory(NotificationCompat.CATEGORY_ALARM)
                } else {
                    addAction(0, getString(R.string.alarm_stop_action), stopIntent)
                }
            }
            .build()
    }

    private fun notifyStatus(status: VescRepository.AlarmStatus) {
        if (lastNotifiedStatus == status) return
        lastNotifiedStatus = status
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(status == VescRepository.AlarmStatus.ALARM))
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.alarm_notification_channel_desc)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        siren.stop()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
