package com.ruggerocadamuro.myapplication.data

import android.content.Context
import android.util.Log
import com.ruggerocadamuro.myapplication.data.ble.BleManager
import com.ruggerocadamuro.myapplication.data.ble.SppManager
import com.ruggerocadamuro.myapplication.data.settings.AppSettings
import com.ruggerocadamuro.myapplication.data.settings.SettingsRepository
import com.ruggerocadamuro.myapplication.data.vesc.VescPacket
import com.ruggerocadamuro.myapplication.data.vesc.VescTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Funzioni di conversione fisica VESC -> unita' umane.
 * VESC ci da' ERPM e un tachimetro "grezzo": per avere km/h reali servono i
 * parametri meccanici impostati dall'utente (coppie polari, ruota, riduzione).
 */
object VescMath {

    /** Velocita' in km/h da ERPM. */
    fun speedKmh(t: VescTelemetry, p: VehicleParams): Float {
        val mechRpm = t.erpm / p.polePairs / p.gearRatio
        val wheelCircM = p.wheelDiameterCm / 100f * PI.toFloat()
        return mechRpm * wheelCircM * 60f / 1000f
    }

    /**
     * Distanza percorsa in km dal tachimetro assoluto.
     * Convenzione VESC: il tachimetro conta 3 per ogni rivoluzione del motore.
     */
    fun distanceKm(t: VescTelemetry, p: VehicleParams): Float {
        val motorRevs = t.tachometerAbs / 3f
        val wheelRevs = motorRevs / p.gearRatio
        val wheelCircM = p.wheelDiameterCm / 100f * PI.toFloat()
        return wheelRevs * wheelCircM / 1000f
    }

    /**
     * Stima della carica residua della batteria in %, dalla tensione del pack.
     * Mappa lineare 3.3 V (vuota) -> 4.2 V (piena) per cella: e' un'approssimazione
     * ma sufficiente per un indicatore informativo.
     */
    fun batteryPercent(voltage: Float, cells: Int): Int {
        if (voltage <= 0f || cells <= 0) return 0
        val perCell = voltage / cells
        val pct = ((perCell - 3.3f) / (4.2f - 3.3f) * 100f)
        return pct.coerceIn(0f, 100f).roundToInt()
    }
}

/** Parametri meccanici del veicolo, impostabili dall'utente. */
data class VehicleParams(
    val polePairs: Int,
    val wheelDiameterCm: Float,
    val gearRatio: Float,
    val batteryCells: Int
)

/** Punto di storico per il grafico in tempo reale. */
data class HistoryPoint(val timestampMs: Long, val powerW: Float, val erpm: Float)

/**
 * Repository centrale dell'app (singleton): possiede il [BleManager], esegue il
 * polling della telemetria e implementa la logica dell'allarme anti-allontanamento.
 *
 * ViewModel e ForegroundService parlano entrambi con questa classe: cosi' la
 * connessione BLE resta unica quando l'app va in background.
 */
class VescRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        /** Periodo di polling telemetria (200-300 ms come da specifica). */
        private const val POLL_PERIOD_MS = 250L
        private const val REQUEST_TIMEOUT_MS = 500L
        private const val ALARM_TICK_MS = 1000L
        private const val HISTORY_WINDOW_MS = 60_000L
    }

    val ble = BleManager(context)

    /** Transport Bluetooth Classic (SPP) per i moduli che non bridgeano via BLE. */
    val spp = SppManager(context)

    enum class Transport { BLE, SPP }

    private val _transport = MutableStateFlow(Transport.BLE)

    /** Canale attivo (esposto alla UI per la diagnostica). */
    val transport: StateFlow<Transport> = _transport.asStateFlow()

    // Stato connessione/RSSI/nome del transport ATTIVO (BLE o SPP)
    private val _connectionState = MutableStateFlow(BleManager.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleManager.ConnectionState> = _connectionState.asStateFlow()
    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _telemetry = MutableStateFlow<VescTelemetry?>(null)
    val telemetry: StateFlow<VescTelemetry?> = _telemetry.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val history: StateFlow<List<HistoryPoint>> = _history.asStateFlow()

    /** Eventi brevi da mostrare all'utente (es. "Connessione non riuscita"). */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // ----------------------------------------------------------------------
    // Allarme anti-allontanamento
    // ----------------------------------------------------------------------

    enum class AlarmStatus { OFF, MONITORING, ALARM }

    data class AlarmUiState(
        val status: AlarmStatus = AlarmStatus.OFF,
        val rssi: Int? = null,
        val thresholdDbm: Int = -75,
        val belowSeconds: Int = 0,      // secondi consecutivi sotto soglia
        val debounceSeconds: Int = 5,
        val marginDb: Int = 0            // margine attuale: rssi - soglia
    )

    private val _alarmState = MutableStateFlow(AlarmUiState())
    val alarmState: StateFlow<AlarmUiState> = _alarmState.asStateFlow()

    private var pollingJob: Job? = null
    private var alarmJob: Job? = null

    // snapshot aggiornati dal flow delle impostazioni
    @Volatile private var currentSettings: AppSettings = AppSettings()
    @Volatile private var vehicle: VehicleParams = VehicleParams(7, 25.4f, 1f, 10)
    @Volatile private var hasEverConnected = false

    private var lastConnectedAddress: String? = null

    init {
        // tiene aggiornati i parametri di conversione e lo stato dell'allarme
        scope.launch {
            settingsRepository.settings.collect { s ->
                currentSettings = s
                vehicle = VehicleParams(s.polePairs, s.wheelDiameterCm, s.gearRatio, s.batteryCells)
            }
        }
        // persiste l'ultimo dispositivo connesso (per riconnessione rapida)
        scope.launch {
            ble.state.collect { st ->
                if (st == BleManager.ConnectionState.CONNECTED) {
                    hasEverConnected = true
                    lastConnectedAddress?.let { addr ->
                        settingsRepository.setLastDevice(addr, ble.deviceName.value ?: addr)
                    }
                }
            }
        }
        // stato/nome/rssi: dal transport attivo
        scope.launch {
            ble.state.collect { if (_transport.value == Transport.BLE) _connectionState.value = it }
        }
        scope.launch {
            spp.state.collect { if (_transport.value == Transport.SPP) _connectionState.value = it }
        }
        scope.launch {
            ble.deviceName.collect { if (_transport.value == Transport.BLE) _deviceName.value = it }
        }
        scope.launch {
            spp.deviceName.collect { if (_transport.value == Transport.SPP) _deviceName.value = it }
        }
        scope.launch {
            ble.rssi.collect { if (_transport.value == Transport.BLE) _rssi.value = it }
        }
        scope.launch {
            spp.rssi.collect { if (_transport.value == Transport.SPP) _rssi.value = it }
        }
        // aggiorna in continuazione lo stato allarme esposto alla UI
        scope.launch {
            _rssi.collect { value ->
                val a = _alarmState.value
                _alarmState.value = a.copy(rssi = value, marginDb = (value ?: a.thresholdDbm) - a.thresholdDbm)
            }
        }
    }

    // ----------------------------------------------------------------------
    // Connessione
    // ----------------------------------------------------------------------

    fun connect(address: String, name: String? = null) {
        lastConnectedAddress = address
        _telemetry.value = null
        _history.value = emptyList()
        hasEverConnected = false
        commandPath = CommandPath.UNKNOWN
        useSelectiveForPoll = true
        consecutivePollFailures = 0
        _transport.value = Transport.BLE // si riparte sempre dal BLE (con fallback SPP)
        ble.connect(address)
        startPollingWhenConnected()
    }

    fun disconnect() {
        ble.disconnect()
        spp.disconnect()
        pollingJob?.cancel()
        _telemetry.value = null
        _connectionState.value = BleManager.ConnectionState.DISCONNECTED
    }

    private suspend fun switchToSpp(address: String) {
        _transport.value = Transport.SPP
        ble.disconnect()
        _events.tryEmit("BLE muto: tento il Bluetooth Classic (SPP)...")
        spp.connect(address)
    }

    private fun switchToBle(address: String) {
        _transport.value = Transport.BLE
        spp.disconnect()
        _events.tryEmit("SPP muto: ritento il BLE...")
        ble.connect(address)
    }

    /**
     * Modalita' di comando rilevata (al primo collegamento):
     *  - DIRECT: il modulo BLE parla direttamente col motore (comandi nudi)
     *  - CAN: il modulo e' un bridge BLE->CAN (VESC Express e simili): i comandi
     *    motore vanno incapsulati con [COMM_FORWARD_CAN, canId] o il bridge li ignora
     */
    enum class CommandPath { UNKNOWN, DIRECT, CAN }

    private var commandPath = CommandPath.UNKNOWN
    private var canId: Int? = null

    /**
     * Variante del comando telemetria che ha funzionato in rilevazione.
     * Alcuni firmware (es. Flipsky FSESC 6.02) non implementano il SELECTIVE:
     * dopo la rilevazione usiamo sempre quella confermata.
     */
    @Volatile private var useSelectiveForPoll = true

    /**
     * Sequenza di collegamento con rilevamento transport (port semplificato del
     * BoardTransportDetector di vescape) e poi polling in regime acquisito:
     *  1. COMM_FW_VERSION: gestito localmente da ogni bridge/VESC -> conferma TX/RX+CRC
     *  2. COMM_PING_CAN: scopre i canId sul bus CAN (se il modulo e' un bridge)
     *  3. prova telemetria DIRECT, poi CAN-forwarded per ogni id scoperto
     */
    private fun startPollingWhenConnected() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            var detected = false
            while (isActive) {
                if (_connectionState.value != BleManager.ConnectionState.CONNECTED) {
                    delay(300)
                    continue
                }
                if (!detected) {
                    detected = detectCommandPath()
                    continue
                }
                pollOnce()
            }
        }
    }

    private suspend fun requestPayload(packet: ByteArray, commId: Int): ByteArray? = when (_transport.value) {
        Transport.BLE -> ble.request(packet, commId, REQUEST_TIMEOUT_MS)
        Transport.SPP -> spp.request(packet, commId, REQUEST_TIMEOUT_MS)
    }

    private suspend fun detectCommandPath(): Boolean {
        // 1) ping di connettivita' (risposta sempre generata se il canale funziona)
        var fwOk = false
        repeat(3) {
            val fw = requestPayload(VescPacket.buildFwVersion(), VescPacket.COMM_FW_VERSION)
            if (fw != null) {
                fwOk = true
                Log.i("VescRepo", "FW_VERSION risposta: ${fw.joinToString(" ") { "%02X".format(it) }}")
            }
        }
        if (!fwOk) {
            _events.tryEmit(
                "Il modulo non risponde nemmeno al ping: chiudi VESC Tool/nRF Connect " +
                    "(rubano la connessione) e riprova"
            )
            return false
        }

        // 2) scoperta bus CAN (se il modulo e' un bridge BLE->CAN)
        val canIds = mutableListOf<Int>()
        repeat(2) {
            val resp = requestPayload(VescPacket.buildPingCan(), VescPacket.COMM_PING_CAN)
            if (resp != null && resp.isNotEmpty() && resp[0].toInt() == VescPacket.COMM_PING_CAN) {
                for (i in 1 until resp.size) canIds.add(resp[i].toInt() and 0xFF)
            }
        }
        if (canIds.isNotEmpty()) {
            Log.i("VescRepo", "CAN bridge rilevato, id rispondenti: $canIds")
        }

        // 3a) telemetria DIRECT
        if (tryTelemetry(null)) {
            commandPath = CommandPath.DIRECT
            canId = null
            Log.i("VescRepo", "Transport: DIRECT")
            return true
        }
        // 3b) telemetria CAN-forwarded per ogni id scoperto
        for (id in canIds) {
            if (tryTelemetry(id)) {
                commandPath = CommandPath.CAN
                canId = id
                Log.i("VescRepo", "Transport: CAN (id=$id)")
                return true
            }
        }
        // nessuna via ancora confermata: riparto dal ping
        return false
    }

    /** Un solo scambio di telemetria sulla via indicata: true se un frame valido e' arrivato. */
    private suspend fun tryTelemetry(canId: Int?): Boolean {
        for (useSelective in listOf(true, false)) {
            val commId = if (useSelective) VescPacket.COMM_GET_VALUES_SELECTIVE else VescPacket.COMM_GET_VALUES
            val inner = if (useSelective) VescPacket.buildGetValuesSelective() else VescPacket.buildGetValues()
            val packet = if (canId == null) inner else VescPacket.frameCanForward(
                inner.copyOfRange(1, inner.size), canId
            )
            val resp = requestPayload(packet, commId)
            val parsed = resp?.let { VescPacket.parseTelemetry(it) }
            if (parsed != null) {
                useSelectiveForPoll = useSelective
                _telemetry.value = parsed
                pushHistory(parsed)
                return true
            }
        }
        return false
    }

    /** Un ciclo di poll in regime acquisito. */
    private suspend fun pollOnce() {
        val started = System.nanoTime()
        val useSelective = useSelectiveForPoll
        val commId = if (useSelective) VescPacket.COMM_GET_VALUES_SELECTIVE else VescPacket.COMM_GET_VALUES
        val inner = if (useSelective) VescPacket.buildGetValuesSelective() else VescPacket.buildGetValues()
        val packet = if (commandPath == CommandPath.CAN && canId != null) {
            VescPacket.frameCanForward(inner.copyOfRange(1, inner.size), canId!!)
        } else {
            inner
        }
        val response = requestPayload(packet, commId)
        val parsed = response?.let { VescPacket.parseTelemetry(it) }
        if (parsed != null) {
            consecutivePollFailures = 0
            _telemetry.value = parsed
            pushHistory(parsed)
        } else {
            // se il poll smette di funzionare in modo persistente (es. il VESC si e'
            // riavviato con config diversa), rifai la rilevazione da capo
            consecutivePollFailures++
            if (consecutivePollFailures >= 20) {
                consecutivePollFailures = 0
                commandPath = CommandPath.UNKNOWN
                Log.w("VescRepo", "Poll muto per troppo tempo: rifaccio la rilevazione transport")
            }
        }
        // mantiene il ritmo di POLL_PERIOD_MS al netto del tempo di attesa
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val sleep = POLL_PERIOD_MS - elapsedMs
        if (sleep > 0) delay(sleep)
    }

    @Volatile private var consecutivePollFailures = 0

    private fun pushHistory(t: VescTelemetry) {
        val now = System.currentTimeMillis()
        val list = _history.value + HistoryPoint(now, t.powerW, t.erpm)
        _history.value = list.dropWhile { now - it.timestampMs > HISTORY_WINDOW_MS }
    }

    // ----------------------------------------------------------------------
    // Controllo allarme (avviato da dashboard / servizio)
    // ----------------------------------------------------------------------

    /**
     * Attiva il monitoraggio. Chi chiama questo metodo e' responsabile di aver
     * avviato prima [com.ruggerocadamuro.myapplication.service.AlarmForegroundService].
     */
    fun enableAlarm() {
        if (_alarmState.value.status != AlarmStatus.OFF) return
        _alarmState.value = _alarmState.value.copy(
            status = AlarmStatus.MONITORING,
            belowSeconds = 0
        )
        startAlarmMonitor()
    }

    fun disableAlarm() {
        alarmJob?.cancel()
        alarmJob = null
        _alarmState.value = AlarmUiState(status = AlarmStatus.OFF, rssi = _alarmState.value.rssi)
    }

    private fun startAlarmMonitor() {
        alarmJob?.cancel()
        alarmJob = scope.launch {
            // tick da 1 s: campiona RSSI e stato link, applica debounce e trigger
            while (isActive) {
                delay(ALARM_TICK_MS)
                val s = currentSettings
                val a = _alarmState.value
                if (a.status == AlarmStatus.OFF) break
                // "link perso" conta solo se c'e' mai stato un collegamento:
                // allarme armato ma mai connesso non deve far scattare la sirena
                val linkDown = hasEverConnected &&
                    ble.state.value != BleManager.ConnectionState.CONNECTED
                val sample: Int? = ble.rssi.value

                // "segnale perso": disconnessione improvvisa o RSSI sotto soglia
                val below = if (linkDown) {
                    s.alarmOnDisconnect // se disattivato, la disconnessione non fa scattare l'allarme
                } else {
                    sample != null && sample < s.alarmThresholdDbm
                }

                var belowSeconds = a.belowSeconds
                if (below) belowSeconds++ else belowSeconds = 0

                val newStatus = when {
                    a.status == AlarmStatus.MONITORING && belowSeconds >= s.alarmDebounceSeconds -> AlarmStatus.ALARM
                    a.status == AlarmStatus.ALARM &&
                        s.alarmAutoStopOnReturn && !below && !linkDown -> AlarmStatus.MONITORING
                    else -> a.status
                }
                _alarmState.value = a.copy(
                    status = newStatus,
                    belowSeconds = belowSeconds,
                    thresholdDbm = s.alarmThresholdDbm,
                    debounceSeconds = s.alarmDebounceSeconds,
                    marginDb = (sample ?: a.rssi ?: a.thresholdDbm) - s.alarmThresholdDbm
                )
            }
        }
    }

    /** Parametri correnti per le conversioni in UI. */
    fun vehicleParams(): VehicleParams = vehicle
}
