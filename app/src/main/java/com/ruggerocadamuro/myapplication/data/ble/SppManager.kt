package com.ruggerocadamuro.myapplication.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ruggerocadamuro.myapplication.data.vesc.VescPacketReassembler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Transport Bluetooth Classic (SPP / RFCOMM) per i moduli VESC che espongono
 * solo il profilo seriale classico -- tipico dei moduli Flipsky integrati e
 * di molti HC-05/JDY-31: via BLE si connettono ma non inoltrano nulla.
 *
 * SPP non offre readRemoteRssi: il flow rssi resta sempre null e la UI
 * mostra "-- dBm" (l'allarme RSSI non e' disponibile su questo transport).
 *
 * Riusa lo stesso VescPacketReassembler del BLE: il protocollo VESC sopra
 * UART e' identico, cambia solo il condotto.
 */
class SppManager(private val context: Context) {

    companion object {
        private const val TAG = "VescSpp"
        /** UUID standard del profilo seriale Bluetooth Classic. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(BleManager.ConnectionState.DISCONNECTED)
    val state: StateFlow<BleManager.ConnectionState> = _state.asStateFlow()

    /** SPP non espone RSSI: sempre null. */
    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _frames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    private val rxCount = AtomicLong(0)
    fun totalFrames(): Int = rxCount.get().toInt()

    private var socket: BluetoothSocket? = null
    private var manualDisconnect = false
    private var connectJob: kotlinx.coroutines.Job? = null
    private val pending = java.util.concurrent.ConcurrentHashMap<Int, CompletableDeferred<ByteArray>>()
    private val reassembler = VescPacketReassembler()

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Avvia la connessione RFCOMM in background (non blocca il chiamante). */
    fun connect(address: String) {
        manualDisconnect = false
        connectJob?.cancel()
        connectJob = scope.launch {
            _state.value = BleManager.ConnectionState.CONNECTING
            val ok = withContext(Dispatchers.IO) { connectBlocking(address) }
            if (!ok && !manualDisconnect) {
                _state.value = BleManager.ConnectionState.DISCONNECTED
            }
        }
    }

    private fun connectBlocking(address: String): Boolean {
        val a = adapter ?: return false
        if (!hasPermission() || !a.isEnabled) {
            Log.e(TAG, "Bluetooth non disponibile o permessi mancanti")
            return false
        }
        var sock: BluetoothSocket?
        try {
            val device = a.getRemoteDevice(address)
            _deviceName.value = try { device.name } catch (_: SecurityException) { null }
            // "insecure": nessuna crittografia, standard per i moduli embedded
            sock = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile creare socket SPP: ${e.message}")
            return false
        }
        try {
            // il discovery attivo blocca il connect: va annullato
            a.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        try {
            _state.value = BleManager.ConnectionState.CONNECTING
            sock.connect()
        } catch (e: Exception) {
            Log.e(TAG, "connect() SPP fallito: ${e.message}")
            try { sock.close() } catch (_: Exception) {}
            return false
        }
        synchronized(this) { socket = sock }
        reassembler.clearSession()
        _state.value = BleManager.ConnectionState.CONNECTED
        Log.i(TAG, "SPP connesso a $address")

        // loop lettura: alimenta il reassembler con i byte in arrivo dal VESC
        thread(name = "spp-reader") {
            val buf = ByteArray(1024)
            try {
                val input = sock.inputStream
                while (!manualDisconnect && !sock.inputStream.let { it.hashCode() < 0 }) {
                    val n = input.read(buf)
                    if (n < 0) break
                    val frames = reassembler.process(buf.copyOf(n))
                    for (f in frames) {
                        if (f.isEmpty()) continue
                        val commId = f[0].toInt() and 0xFF
                        val count = rxCount.incrementAndGet()
                        if (count <= 8) Log.i(TAG, "RX #$count: commId=$commId ${f.joinToString(" ") { "%02X".format(it) }}")
                        val waiter = pending.remove(commId)
                        if (waiter != null && waiter.isActive) waiter.complete(f)
                        else _frames.tryEmit(f)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "lettura SPP interrotta: ${e.message}")
            } finally {
                if (!manualDisconnect) {
                    _state.value = BleManager.ConnectionState.DISCONNECTED
                }
                try { sock.close() } catch (_: Exception) {}
                synchronized(this) { if (socket === sock) socket = null }
            }
        }
        return true
    }

    fun disconnect() {
        manualDisconnect = true
        connectJob?.cancel()
        connectJob = null
        try { socket?.close() } catch (_: Exception) {}
        synchronized(this) { socket = null }
        _state.value = BleManager.ConnectionState.DISCONNECTED
        _rssi.value = null
    }

    /**
     * Invia un pacchetto VESC e attende la risposta con il COMM_ID atteso.
     * Stesso contratto di BleManager.request().
     */
    suspend fun request(packet: ByteArray, expectedCommId: Int, timeoutMs: Long = 800): ByteArray? {
        if (_state.value != BleManager.ConnectionState.CONNECTED) return null
        val deferred = CompletableDeferred<ByteArray>()
        pending[expectedCommId] = deferred
        return try {
            withContext(Dispatchers.IO) {
                try {
                    val out = socket?.outputStream ?: return@withContext false
                    out.write(packet)
                    out.flush()
                    Log.d(TAG, "TX: ${packet.joinToString(" ") { "%02X".format(it) }}")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "TX SPP fallito: ${e.message}")
                    false
                }
            }
            if (!_state.value.toString().contains("CONNECTED")) return null
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(expectedCommId)
        }
    }

    private fun hasSelfPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
