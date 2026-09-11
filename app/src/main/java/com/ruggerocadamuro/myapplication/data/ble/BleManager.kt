package com.ruggerocadamuro.myapplication.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.ruggerocadamuro.myapplication.data.vesc.VescPacket
import com.ruggerocadamuro.myapplication.data.vesc.VescPacketReassembler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Client BLE specializzato per il bridge UART del VESC.
 *
 * Supporta due moduli comunemente usati:
 *  - Nordic UART Service (NUS): firmware ufficiale "nrf51_vesc"/"nrf52_vesc"
 *    di Vedder, e' il servizio esposto anche da molti bridge commerciali.
 *  - HM-10 / moduli "AT-09": servizio FFE0 / caratteristica FFE1.
 *
 * Responsabilita':
 *  - scan dei dispositivi nelle vicinanze
 *  - connessione GATT, discovery, abilitazione notifiche, MTU
 *  - scrittura comandi (con serializzazione delle operazioni GATT)
 *  - lettura periodica RSSI (per l'allarme anti-allontanamento)
 *  - riconnessione automatica con backoff in caso di caduta del link
 */
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "VescBle"
        /** Nordic UART Service usato dal firmware BLE di VESC. */
        val NUS_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX_CHAR: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // phone -> VESC
        val NUS_TX_CHAR: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // VESC -> phone
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Servizio usato dai moduli HM-10 / AT-09. */
        val HM10_SERVICE: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val HM10_CHAR: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val OP_TIMEOUT_MS = 2_000L
        private const val RSSI_PERIOD_MS = 1_000L
        private const val REQUEST_MTU = 247
        private const val CCCD_TIMEOUT_MS = 4_000L
    }

    data class ScanDevice(val address: String, val name: String?, val rssi: Int) {
        /** Nomi tipici dei bridge VESC, per evidenziarli in UI. */
        val looksLikeVesc: Boolean
            get() {
                val n = (name ?: "").uppercase()
                return n.contains("VESC") || n.contains("NRF") ||
                    n.contains("FLIPSKY") || n.contains("NUS")
            }
    }

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    /** Frame VESC completi (payload con CRC verificato) non richiesti esplicitamente. */
    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hasBluetoothPermissions(): Boolean = has(Manifest.permission.BLUETOOTH_CONNECT) &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || has(Manifest.permission.BLUETOOTH_SCAN))

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val bluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    // ------------------------------------------------------------------
    // SCAN
    // ------------------------------------------------------------------

    /** Flow freddo: parte lo scan alla raccolta e lo ferma alla chiusura. */
    fun scan(): Flow<ScanDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !hasBluetoothPermissions()) {
            close(RuntimeException("Bluetooth non disponibile o permessi mancanti"))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                trySend(
                    ScanDevice(
                        address = dev.address,
                        name = result.scanRecord?.deviceName ?: dev.name,
                        rssi = result.rssi
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(RuntimeException("Scan fallito, codice $errorCode"))
            }
        }
        try {
            scanner.startScan(callback)
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // permesso revocato durante lo scan: nulla da fare
            }
        }
    }

    // ------------------------------------------------------------------
    // CONNESSIONE
    // ------------------------------------------------------------------

    private var gattRef: BluetoothGatt? = null
    private val gattLock = Any()
    private val gattMutex = Mutex() // serializza le operazioni GATT (write/rssi)

    private var txChar: BluetoothGattCharacteristic? = null
    private var mtu: Int = 23
    private val reassembler = VescPacketReassembler()

    /** Deferred in attesa di risposta, indicizzati per COMM_ID. */
    private val pending = ConcurrentHashMap<Int, kotlinx.coroutines.CompletableDeferred<ByteArray>>()

    private var manualDisconnect = false
    private var connectionLoopJob: kotlinx.coroutines.Job? = null
    private var disconnectSignal = Channel<Unit>(Channel.CONFLATED)
    private var serviceRetryCount = 0

    // Coda dei CCCD da scrivere in sequenza (config notifiche GATT)
    private val cccdQueue = ArrayDeque<BluetoothGattDescriptor>()
    private var cccdTimeoutJob: kotlinx.coroutines.Job? = null
    private val cccdResolved = AtomicBoolean(false)

    private var lastAddress: String? = null
    private var rssiJob: kotlinx.coroutines.Job? = null

    /** Richiede la connessione (o riconnessione) al dispositivo dato. */
    fun connect(address: String) {
        if (!BluetoothAdapter.checkBluetoothAddress(address.uppercase())) return
        if (connectionLoopJob?.isActive == true && _state.value != ConnectionState.DISCONNECTED) return
        manualDisconnect = false
        lastAddress = address
        connectionLoopJob?.cancel()
        connectionLoopJob = scope.launch {
            var attempt = 0
            while (kotlinx.coroutines.currentCoroutineContext().isActive && !manualDisconnect) {
                _state.value =
                    if (attempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
                // canale fresco a ogni tentativo: evita segnali di disconnessione "stale"
                val signal = Channel<Unit>(Channel.CONFLATED)
                disconnectSignal = signal
                val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        if (!startConnect(address, cont)) {
                            cont.resume(false)
                        }
                    }
                } ?: false
                if (ok && _state.value == ConnectionState.CONNECTED) {
                    attempt = 0
                    // resta qui finche' il link non cade
                    signal.receive()
                    if (manualDisconnect) break
                    notifyLinkLost()
                } else {
                    notifyLinkLost()
                    closeCurrentGatt()
                    if (manualDisconnect) break
                }
                // backoff esponenziale con tetto: 1s, 2s, 4s, 8s, 15s, ...
                val backoff = (1000L shl attempt.coerceAtMost(4)).coerceAtMost(15_000L)
                attempt++
                delay(backoff)
            }
        }
    }

    private fun notifyLinkLost() {
        _rssi.value = null
        txChar = null
        linkLostListener?.invoke()
    }

    /** Callback invocata a ogni caduta del link (anche durante riconnessioni). */
    var linkLostListener: (() -> Unit)? = null

    fun disconnect() {
        manualDisconnect = true
        connectionLoopJob?.cancel()
        connectionLoopJob = null
        rssiJob?.cancel()
        rssiJob = null
        disconnectSignal.trySend(Unit)
        closeCurrentGatt()
        _state.value = ConnectionState.DISCONNECTED
        _rssi.value = null
    }

    private fun closeCurrentGatt() {
        synchronized(gattLock) {
            try {
                gattRef?.disconnect()
                gattRef?.close()
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            gattRef = null
            txChar = null
        }
    }

    private fun startConnect(address: String, cont: kotlinx.coroutines.CancellableContinuation<Boolean>): Boolean {
        val a = adapter ?: return false
        if (!hasBluetoothPermissions() || !a.isEnabled) return false
        val device: BluetoothDevice = try {
            a.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val resumed = AtomicBoolean(false)
        fun resumeOnce(value: Boolean) {
            if (resumed.compareAndSet(false, true) && cont.isActive) {
                cont.resume(value)
            }
        }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                // Callback tardivo di un tentativo gia' sostituito: chiudilo e ignora,
                // altrimenti uno stale disconnectcancelrebbe la sessione viva (vescape).
                synchronized(gattLock) {
                    if (gattRef != null && gatt !== gattRef) {
                        Log.w(TAG, "Callback stale da gatt precedente, ignoro")
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        try {
                            gatt.requestMtu(REQUEST_MTU)
                        } catch (_: SecurityException) {
                            resumeOnce(false)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        resumeOnce(false)
                        try {
                            gatt.close()
                        } catch (_: Exception) {
                        }
                        if (gattRef === gatt) {
                            synchronized(gattLock) {
                                if (gattRef === gatt) gattRef = null
                                txChar = null
                            }
                        }
                        disconnectSignal.trySend(Unit)
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                this@BleManager.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
                try {
                    gatt.discoverServices()
                } catch (_: SecurityException) {
                    resumeOnce(false)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "onServicesDiscovered fallito: status=$status")
                    resumeOnce(false)
                    return
                }
                val services = gatt.services
                if (services.isEmpty()) {
                    // Cache GATT non ancora pronta (comune su moduli lenti dopo la
                    // riconnessione): riproviamo la discovery fino a 3 volte.
                    if (serviceRetryCount++ < 3) {
                        Log.w(TAG, "GATT DB vuota, riprovo discovery ($serviceRetryCount/3)")
                        scope.launch {
                            delay(400)
                            try {
                                gatt.discoverServices()
                            } catch (_: SecurityException) {
                                resumeOnce(false)
                            }
                        }
                    } else {
                        serviceRetryCount = 0
                        resumeOnce(false)
                    }
                    return
                }
                serviceRetryCount = 0
                // LOG diagnostico della GATT DB completa: mostra gli UUID esposti
                // dal modulo BLE (essenziale per diagnosticare moduli non standard)
                for (s in services) {
                    Log.i(TAG, "Servizio GATT: ${s.uuid}")
                    for (c in s.characteristics) {
                        val descs = c.descriptors.joinToString { d -> d.uuid.toString() }
                        Log.i(TAG, "  char ${c.uuid} props=${c.properties} descriptors=[$descs]")
                    }
                }
                val endpoints = findUartEndpoints(services)
                if (endpoints == null) {
                    Log.e(TAG, "Nessuna caratteristica notify/write utilizzabile trovata")
                    resumeOnce(false)
                    return
                }
                txChar = endpoints.tx
                try {
                    // Intervallo di connessione alto (7.5-15ms): telemetria piu' reattiva,
                    // come fa l'implementazione di riferimento vescape.
                    val highPriority = gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    Log.d(TAG, "requestConnectionPriority HIGH=$highPriority")

                    // Il firmware di alcuni moduli VESC (Floatwheel ADV2, Flipsky integrati)
                    // notifica sulla caratteristica di WRITE (6e400002) invece che su quella
                    // standard di notifica (6e400003): va subscribed ENTRAMBE con CCCD in
                    // cascata, e accettiamo notifiche da qualunque UUID (vedi dispatchIncoming).
                    if (endpoints.tx.uuid != endpoints.rx.uuid) {
                        gatt.setCharacteristicNotification(endpoints.tx, true)
                    }
                    val notified = gatt.setCharacteristicNotification(endpoints.rx, true)
                    Log.d(TAG, "setCharacteristicNotification rx=$notified")

                    // coda di CCCD da scrivere in sequenza (GATT: una sola write di
                    // descriptor in volo alla volta; la seconda parte da onDescriptorWrite)
                    cccdQueue.clear()
                    endpoints.rxCCCD?.let { cccdQueue.add(it) }
                    if (endpoints.tx.uuid != endpoints.rx.uuid) {
                        endpoints.tx.getDescriptor(CCCD)?.let { cccdQueue.add(it) }
                    }

                    if (cccdQueue.isEmpty()) {
                        // cloni HM-10 e bridge economici: nessun CCCD esposto, si procede
                        Log.w(TAG, "Nessun CCCD 2902 esposto: procedo senza abilitarlo")
                        goConnected(gatt)
                        resumeOnce(true)
                        return
                    }

                    // fallback: se gli ack CCCD non arrivano mai (edge case su alcuni stati
                    // bonded), dopo 4s consideriamo comunque il link pronto
                    cccdResolved.set(false)
                    cccdTimeoutJob?.cancel()
                    cccdTimeoutJob = scope.launch {
                        delay(CCCD_TIMEOUT_MS)
                        if (cccdResolved.compareAndSet(false, true)) {
                            Log.w(TAG, "Timeout ack CCCD ($CCCD_TIMEOUT_MS ms): considero il link pronto comunque")
                            goConnected(gatt)
                            resumeOnce(true)
                        }
                    }
                    val started = writeNextCccd(gatt)
                    Log.d(TAG, "CCCD writes pending=${cccdQueue.size + if (started) 1 else 0}")
                    if (!started) {
                        cccdTimeoutJob?.cancel()
                        cccdResolved.set(true)
                        resumeOnce(false)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException nella config notify: ${e.message}")
                    resumeOnce(false)
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (descriptor.uuid != CCCD) return
                Log.d(TAG, "onDescriptorWrite ${descriptor.characteristic.uuid} status=$status queue=${cccdQueue.size}")
                if (cccdQueue.isNotEmpty()) {
                    // prossimo CCCD della catena
                    if (writeNextCccd(gatt)) return
                    Log.w(TAG, "Impossibile avviare il prossimo CCCD, procedo come pronto")
                }
                if (cccdResolved.compareAndSet(false, true)) {
                    cccdTimeoutJob?.cancel()
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        goConnected(gatt)
                        resumeOnce(true)
                    } else {
                        Log.e(TAG, "writeDescriptor CCCD fallito: status=$status")
                        resumeOnce(false)
                    }
                }
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                // aggiornato dal loop RSSI (il callback utilizzabile e' solo quello
                // registrato alla connectGatt, non oggetti transienti)
                if (status == BluetoothGatt.GATT_SUCCESS) _rssi.value = rssi
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "onCharacteristicWrite status=$status (char=${characteristic.uuid})")
                }
                writeContinuation?.let { wc ->
                    writeContinuation = null
                    if (wc.isActive) wc.resume(status == BluetoothGatt.GATT_SUCCESS)
                }
            }

            private var rawRxLogged = 0

            // API >= 33: i dati arrivano come array
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (rawRxLogged++ < 10) {
                    // log dei byte GREZZI notificati: distingue "silenzio totale"
                    // (VESC non risponde) da "arriva spazzatura" (problema parsing)
                    Log.i(TAG, "RXraw #$rawRxLogged: ${value.toHexString(20)}")
                }
                dispatchIncoming(value)
            }

            // API < 33: i dati sono nella caratteristica
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                if (rawRxLogged++ < 10) {
                    Log.i(TAG, "RXraw #$rawRxLogged: ${value.toHexString(20)}")
                }
                dispatchIncoming(value)
            }
        }
        try {
            // Overload deprecato in API 35 in favore della variante con Executor:
            // qui serve la callback su thread dedicato e la versione semplificata
            // funziona identica su tutte le API, la teniamo con soppressione.
            @Suppress("DEPRECATION")
            val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            synchronized(gattLock) {
                gattRef?.close()
                gattRef = gatt
            }
            reassembler.clearSession() // stato pulito per la nuova sessione
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    private var writeContinuation: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

    /** Endpoint UART individuati sul dispositivo. rxCCCD puo' mancare (cloni HM-10). */
    private data class UartEndpoints(
        val rx: BluetoothGattCharacteristic,              // dispositivo -> phone (notify)
        val rxCCCD: BluetoothGattDescriptor?,             // descriptor 2902, se esiste
        val tx: BluetoothGattCharacteristic               // phone -> dispositivo (write)
    )

    /**
     * Individua gli endpoint UART tra i servizi esposti, con strategia a cascata:
     * 1. Nordic UART Service (firmware VESC nrf5x)
     * 2. HM-10 / AT-09 / cloni (FFE0/FFE1, caratteristica unica bidirezionale)
     * 3. Fallback universale: prima coppia notify + write di QUALSIASI servizio
     *    (copre i moduli proprietari, es. BT integrato Flipsky)
     */
    private fun findUartEndpoints(services: List<BluetoothGattService>): UartEndpoints? {
        // 1) NUS esplicito
        services.firstOrNull { it.uuid == NUS_SERVICE }?.let { svc ->
            val rx = svc.getCharacteristic(NUS_TX_CHAR)
            val tx = svc.getCharacteristic(NUS_RX_CHAR)
            if (rx != null && tx != null) {
                Log.i(TAG, "Nordic UART Service trovato: rx=${rx.uuid} tx=${tx.uuid}")
                return UartEndpoints(rx, rx.getDescriptor(CCCD), tx)
            }
        }
        // 2) HM-10 / cloni: una sola caratteristica bidirezionale
        services.firstOrNull { it.uuid == HM10_SERVICE }?.let { svc ->
            val ch = svc.getCharacteristic(HM10_CHAR)
            if (ch != null) {
                Log.i(TAG, "Servizio HM-10/FFE0 trovato: char=${ch.uuid}")
                return UartEndpoints(ch, ch.getDescriptor(CCCD), ch)
            }
        }
        // 3) Fallback universale: notify + write in qualunque servizio
        for (svc in services) {
            val rx = svc.characteristics.firstOrNull {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
            }
            val tx = svc.characteristics.firstOrNull {
                (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            if (rx != null && tx != null) {
                Log.w(TAG, "Fallback universale su servizio ${svc.uuid}: rx=${rx.uuid} tx=${tx.uuid}")
                return UartEndpoints(rx, rx.getDescriptor(CCCD), tx)
            }
            // 4) caratteristica unica con entrambe le proprieta'
            if (rx != null && (rx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                Log.w(TAG, "Caratteristica unica bidirezionale su ${svc.uuid}: ${rx.uuid}")
                return UartEndpoints(rx, rx.getDescriptor(CCCD), rx)
            }
        }
        return null
    }

    /**
     * Scrive il prossimo CCCD della coda (GATT ammette una sola descriptor write
     * in volo). Ritorna true se la scrittura e' partita (o se un retry e' stato
     * schedulato); false se la coda era vuota.
     *
     * "Short write retry": un GATT busy transiente si risolve con un retry corto
     * (~100ms), non con lunghi delay di connessione (lezione da vescape).
     */
    private fun writeNextCccd(gatt: BluetoothGatt): Boolean {
        val descriptor = cccdQueue.firstOrNull() ?: return false
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException writeCccd: ${e.message}")
            false
        }
        if (started) {
            cccdQueue.removeFirst()
            Log.d(TAG, "writeCccd avviata su ${descriptor.characteristic.uuid}")
        } else {
            Log.w(TAG, "writeCccd rifiutata subito, retry tra 100ms")
            scope.launch {
                delay(100)
                if (!cccdResolved.get()) writeNextCccd(gatt)
            }
        }
        return started
    }

    /** Completa la transizione a CONNECTED (notify gia' configurate). */
    private fun goConnected(gatt: BluetoothGatt) {
        // la vera transizione e' gestita da resumeOnce nel callback; qui centralizziamo
        // solo lo stato visibile e il loop RSSI. resumeOnce e' locale a startConnect,
        // quindi viene invocato dal chiamante.
        _state.value = ConnectionState.CONNECTED
        _deviceName.value = try {
            gatt.device.name
        } catch (_: SecurityException) {
            null
        }
        Log.i(TAG, "Collegamento pienamente operativo (MTU=$mtu)")
        startRssiLoop()
    }

    /** Alimenta il reassembler e smista le risposte ai chiamanti in attesa. */
    private var rxFrameCount = 0

    private val _rxCount = MutableStateFlow(0)

    private val fullTelemetryLogged = AtomicBoolean(false)

    /** Numero totale di frame VESC ricevuti (per la logica di fallback SPP). */
    val rxCount: StateFlow<Int> = _rxCount.asStateFlow()

    private fun dispatchIncoming(bytes: ByteArray) {
        val frames = reassembler.process(bytes)
        for (frame in frames) {
            if (frame.isEmpty()) continue
            val commId = frame[0].toInt() and 0xFF
            _rxCount.value = _rxCount.value + 1
            // log dei primi frame: essenziale per verificare cosa risponde il modulo
            if (rxFrameCount++ < 8) {
                Log.i(TAG, "RX #$rxFrameCount: commId=$commId ${frame.toHexString(16)}")
            }
            // una tantum per sessione: frame COMPLETO della telemetria, per allineare
            // il parsing ai layout firmware non standard (es. FSESC 74 byte)
            if (commId == VescPacket.COMM_GET_VALUES && !fullTelemetryLogged.getAndSet(true)) {
                Log.i(TAG, "RX full telemetry (${frame.size}B): ${frame.toHexString(Int.MAX_VALUE)}")
            }
            val waiter = pending.remove(commId)
            if (waiter != null && waiter.isActive) {
                waiter.complete(frame)
            } else {
                _frames.tryEmit(frame)
            }
        }
    }

    // ------------------------------------------------------------------
    // OPERAZIONI (scrittura comandi, RSSI)
    // ------------------------------------------------------------------

    /**
     * Invia un pacchetto VESC gia' incapsulato e attende la risposta con il
     * COMM_ID atteso, o [timeoutMs] di timeout. Restituisce il payload oppure
     * null (timeout / link caduto).
     */
    suspend fun request(packet: ByteArray, expectedCommId: Int, timeoutMs: Long = 800): ByteArray? {
        if (_state.value != ConnectionState.CONNECTED) return null
        val deferred = kotlinx.coroutines.CompletableDeferred<ByteArray>()
        pending[expectedCommId] = deferred
        // timeout anche sulla scrittura: se onCharacteristicWrite non arriva mai
        // (modulo in panne) il chiamante non deve restare appeso
        val sent = withTimeoutOrNull(1500L) { writePacket(packet) } ?: false
        if (!sent) {
            pending.remove(expectedCommId)
            Log.w(TAG, "TX fallito/bloccato per commId=$expectedCommId")
            return null
        }
        return try {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(expectedCommId)
        }
    }

    /** Rappresentazione esadecimale compatta per i log diagnostici. */
    private fun ByteArray.toHexString(max: Int = 10): String =
        take(max.coerceAtMost(size)).joinToString(" ") { "%02X".format(it) } +
            if (size > max) "... (${size}B)" else ""

    /**
     * Scrive il pacchetto, spezzandolo in scritture BLE di dimensione MTU-3.
     *
     * Sempre WRITE_TYPE_DEFAULT (write-with-response), come l'implementazione di
     * riferimento vescape: e' il tipo piu' affidabile verso i bridge UART VESC.
     * Se Android rifiuta la scrittura all'avvio (GATT busy transiente), un retry
     * corto dopo 100ms la recupera invece di far fallire il poll.
     */
    private suspend fun writePacket(packet: ByteArray): Boolean = gattMutex.withLock {
        val gatt = gattRef ?: return false
        val char = txChar ?: return false
        try {
            var offset = 0
            val chunk = (mtu - 3).coerceAtLeast(20)
            Log.d(TAG, "TX: ${packet.toHexString()}")
            while (offset < packet.size) {
                val end = (offset + chunk).coerceAtMost(packet.size)
                val slice = packet.copyOfRange(offset, end)
                val ok = writeChunkWithRetry(gatt, char, slice)
                if (!ok) return false
                offset = end
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in TX: ${e.message}")
            false
        }
    }

    /** Un chunk con un solo retry corto se Android lo rifiuta all'avvio. */
    private suspend fun writeChunkWithRetry(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        slice: ByteArray
    ): Boolean {
        var attempt = 0
        while (attempt < 2) {
            val ok = suspendCancellableCoroutine { cont ->
                writeContinuation = cont
                val res: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+: ritorna un codice di stato (0 = SUCCESS)
                    gatt.writeCharacteristic(char, slice, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
                } else {
                    @Suppress("DEPRECATION")
                    char.value = slice
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }
                if (!res) {
                    writeContinuation = null
                    Log.w(TAG, "writeCharacteristic rifiutato subito (tentativo ${attempt + 1})")
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (ok) return true
            attempt++
            if (attempt < 2) delay(100)
        }
        return false
    }

    private fun startRssiLoop() {
        rssiJob?.cancel()
        rssiJob = scope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive &&
                _state.value == ConnectionState.CONNECTED
            ) {
                readRssiOnce()
                delay(RSSI_PERIOD_MS)
            }
        }
    }

    private suspend fun readRssiOnce() {
        val gatt = gattRef ?: return
        try {
            gattMutex.withLock {
                // il risultato arrivera' in onReadRemoteRssi del callback di connessione
                gatt.readRemoteRssi()
            }
        } catch (_: SecurityException) {
        }
    }
}
