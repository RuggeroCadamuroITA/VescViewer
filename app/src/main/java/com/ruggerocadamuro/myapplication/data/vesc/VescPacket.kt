package com.ruggerocadamuro.myapplication.data.vesc

import java.io.ByteArrayOutputStream

/**
 * Lunghezza massima payload supportata dal reassembler (come packet.c).
 * File-level: condivisa da VescPacket e VescPacketReassembler.
 */
private const val MAX_PL_LEN = 1024

/**
 * Implementazione del protocollo pacchetti VESC (port parziale del `packet.c`
 * del firmware Vedder) + parser dei comandi di telemetria.
 *
 * Formato pacchetto "corto" (< 256 byte di payload):
 *
 *   [0x02] [len:1] [payload: len byte] [crc_hi:1] [crc_lo:1] [0x03]
 *
 * Formato pacchetto "lungo" (>= 256 byte, gestito per completezza):
 *
 *   [0x03] [len_hi:1] [len_lo:1] [payload] [crc_hi:1] [crc_lo:1] [0x03]
 *
 * Il CRC e' un CRC16-CCITT (poly 0x1021, init 0x0000, aka CRC-16/XMODEM),
 * calcolato **solo sul payload**, identico alla funzione crc16() del firmware.
 *
 * L'app e' puramente passiva: espone solo comandi di LETTURA (COMM_GET_VALUES
 * / COMM_GET_VALUES_SELECTIVE). Nessun comando di controllo motore.
 */
object VescPacket {

    // ------------------------------------------------------------------
    // COMM_ID del firmware VESC (commands.h)
    // ------------------------------------------------------------------
    const val COMM_FW_VERSION = 0
    const val COMM_GET_VALUES = 4
    const val COMM_GET_VALUES_SELECTIVE = 50
    const val COMM_FORWARD_CAN = 34
    const val COMM_PING_CAN = 62

    // ------------------------------------------------------------------
    // CRC16
    // ------------------------------------------------------------------

    /**
     * CRC16 del firmware VESC: CRC-16/XMODEM bitwise (poly 0x1021, init 0,
     * byte XOR-ato nella posizione alta, MSB-first, big-endian sul filo).
     *
     * Implementazione identica a crc.c del firmware VESC e a VescProtocol.kt
     * del progetto vescape (verificato su hardware reale). Il check value
     * standard XMODEM per "123456789" e' 0x31C3 (coperto da unit test).
     */
    fun crc16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }

    // ------------------------------------------------------------------
    // Builder comandi di lettura
    // ------------------------------------------------------------------

    /**
     * Incapsula un payload nel formato pacchetto VESC.
     * I payload generati dai builder qui sotto sono sempre < 256 byte,
     * ma gestiamo comunque il framing "lungo" per correttezza.
     */
    fun wrap(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 8)
        if (payload.size < 256) {
            out.write(0x02)
            out.write(payload.size)
        } else {
            out.write(0x03)
            out.write((payload.size shr 8) and 0xFF)
            out.write(payload.size and 0xFF)
        }
        out.write(payload)
        val crc = crc16(payload)
        out.write((crc shr 8) and 0xFF)   // CRC big-endian, come il firmware
        out.write(crc and 0xFF)
        out.write(0x03)
        return out.toByteArray()
    }

    /**
     * COMM_GET_VALUES (id 4): richiede lo stato completo del controller.
     * Risposta piu' pesante sul link; usato come fallback se il firmware
     * non supporta la versione selettiva (fw < 6.0).
     */
    fun buildGetValues(): ByteArray = wrap(byteArrayOf(COMM_GET_VALUES.toByte()))

    /**
     * Bit-mask dei campi richiesti con COMM_GET_VALUES_SELECTIVE (fw >= 6.0).
     * La risposta contiene SOLO i campi richiesti, in ordine di bit:
     * cosi' riduciamo drasticamente i byte trasmessi via BLE.
     *
     * Mappatura esatta di commands.h (fw 6.x):
     *   bit 0  temp_mos        bit 8  voltage_in
     *   bit 1  temp_motor      bit 9  amp_hours
     *   bit 2  current_motor   bit 10 amp_hours_charged
     *   bit 3  current_in      bit 11 watt_hours
     *   bit 4  avg_id          bit 12 watt_hours_charged
     *   bit 5  avg_iq          bit 13 tachometer
     *   bit 6  duty            bit 14 tachometer_abs
     *   bit 7  rpm             bit 15 fault
     */
    const val SELECTIVE_MASK: Int =
        (1 shl 0) or   // temp_mos        (temperatura MOSFET, int16 x10)
        (1 shl 1) or   // temp_motor      (temperatura motore, int16 x10)
        (1 shl 2) or   // current_motor   (corrente motore, f32 x100)
        (1 shl 3) or   // current_in      (corrente batteria, f32 x100)
        (1 shl 6) or   // duty             (duty cycle, int16 x1000)
        (1 shl 7) or   // rpm              (ERPM, f32 x1)
        (1 shl 8) or   // voltage_in       (tensione batteria, int16 x10)
        (1 shl 9) or   // amp_hours        (Ah consumati, f32 x10000)
        (1 shl 11) or  // watt_hours       (Wh consumati, f32 x10000)
        (1 shl 13) or  // tachometer       (int32)
        (1 shl 14)     // tachometer_abs   (int32)

    /** COMM_GET_VALUES_SELECTIVE (id 50): [id][mask uint32 big-endian]. */
    fun buildGetValuesSelective(): ByteArray {
        val payload = byteArrayOf(
            COMM_GET_VALUES_SELECTIVE.toByte(),
            ((SELECTIVE_MASK ushr 24) and 0xFF).toByte(),
            ((SELECTIVE_MASK ushr 16) and 0xFF).toByte(),
            ((SELECTIVE_MASK ushr 8) and 0xFF).toByte(),
            (SELECTIVE_MASK and 0xFF).toByte()
        )
        return wrap(payload)
    }

    /**
     * COMM_FW_VERSION (id 0): ping di connettivita'. Viene gestito LOCALMENTE dai
     * bridge ESP32 (VESC Express e simili) e dal VESC stesso: genera sempre una
     * risposta se il canale TX/RX funziona. Utile per verificare che CRC e
     * notifiche funzionino prima di altri comandi.
     */
    fun buildFwVersion(): ByteArray = wrap(byteArrayOf(COMM_FW_VERSION.toByte()))

    /**
     * COMM_PING_CAN (id 62): chiede al bridge di scoprire i dispositivi sul bus CAN.
     * Risposta: [0x3E, id0, id1, ...]. Se rispondono dispositivi, il modulo e' un
     * bridge BLE->CAN e tutti i comandi motore vanno incapsulati con [FORWARD_CAN].
     */
    fun buildPingCan(): ByteArray = wrap(byteArrayOf(COMM_PING_CAN.toByte()))

    /**
     * Incapsula un comando per l'invio via bridge CAN:
     * [COMM_FORWARD_CAN (0x22), canId, <payload>].
     * Senza questo prefisso il bridge non sa a chi inoltrare e ignora il comando
     * (sintomo: TX confermato ma zero risposte).
     */
    fun frameCanForward(payload: ByteArray, canId: Int): ByteArray {
        require(canId in 0..255) { "canId fuori range" }
        return wrap(byteArrayOf(COMM_FORWARD_CAN.toByte(), canId.toByte()) + payload)
    }

    /**
     * Tolge il wrapper di risposta CAN annidato, se presente.
     * Il bridge ESP32 normalmente STRIPPA il wrapper sulle risposte (arrivano
     * al top-level come [cmd, ...]), ma alcune versioni ritengono la forma
     * annidata [0x22, canId, cmd, ...]: gestita difensivamente qui.
     */
    fun unwrapCanResponse(payload: ByteArray): ByteArray {
        if (payload.size >= 4 && (payload[0].toInt() and 0xFF) == COMM_FORWARD_CAN) {
            return payload.copyOfRange(2, payload.size)
        }
        return payload
    }

    // ------------------------------------------------------------------
    // Lettori big-endian (equivalenti di buffer_get_* del firmware)
    // ------------------------------------------------------------------

    fun readInt16(b: ByteArray, off: Int): Int {
        require(off + 2 <= b.size) { "buffer corto: offset $off, size ${b.size}" }
        val unsigned = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)
        return if (unsigned and 0x8000 != 0) unsigned - 0x10000 else unsigned
    }

    fun readInt32(b: ByteArray, off: Int): Int {
        require(off + 4 <= b.size) { "buffer corto: offset $off, size ${b.size}" }
        return ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
    }

    /** Float IEEE754 big-endian (come buffer_append_float32 del firmware). */
    fun readFloat32(b: ByteArray, off: Int): Float {
        val bits = readInt32(b, off)
        return Float.fromBits(bits)
    }

    // ------------------------------------------------------------------
    // Parser telemetria
    // ------------------------------------------------------------------

    /**
     * Parsa la risposta a COMM_GET_VALUES_SELECTIVE.
     * Il firmware include nella risposta [id][mask uint32] e poi serializza
     * soltanto i campi selezionati, nello stesso ordine dei bit della mask.
     */
    fun parseSelective(payload: ByteArray): VescTelemetry? {
        if (payload.size < 5 || (payload[0].toInt() and 0xFF) != COMM_GET_VALUES_SELECTIVE) return null
        return try {
            val mask = readInt32(payload, 1).toLong() and 0xFFFF_FFFFL
            var i = 5
            var tempMos = 0f
            var tempMotor = 0f
            var currentBattery = 0f
            var currentMotor = 0f
            var voltage = 0f
            var erpm = 0f
            var duty = 0f
            var ahUsed = 0f
            var whUsed = 0f
            var tach = 0
            var tachAbs = 0

            fun has(bit: Int): Boolean = mask and (1L shl bit) != 0L
            for (bit in 0..21) {
                if (!has(bit)) continue
                when (bit) {
                    0 -> {
                        tempMos = readInt16(payload, i) / 10f
                        i += 2
                    }
                    1 -> {
                        tempMotor = readInt16(payload, i) / 10f
                        i += 2
                    }
                    2 -> {
                        currentMotor = readInt32(payload, i) / 100f
                        i += 4
                    }
                    3 -> {
                        currentBattery = readInt32(payload, i) / 100f
                        i += 4
                    }
                    4, 5 -> i += 4 // avg_id / avg_iq
                    6 -> {
                        duty = readInt16(payload, i) / 1000f
                        i += 2
                    }
                    7 -> {
                        erpm = readInt32(payload, i).toFloat()
                        i += 4
                    }
                    8 -> {
                        voltage = readInt16(payload, i) / 10f
                        i += 2
                    }
                    9 -> {
                        ahUsed = readInt32(payload, i) / 10000f
                        i += 4
                    }
                    10 -> i += 4 // amp_hours_charged
                    11 -> {
                        whUsed = readInt32(payload, i) / 10000f
                        i += 4
                    }
                    12 -> i += 4 // watt_hours_charged
                    13 -> {
                        tach = readInt32(payload, i)
                        i += 4
                    }
                    14 -> {
                        tachAbs = readInt32(payload, i)
                        i += 4
                    }
                    15 -> i += 1 // fault code
                    16 -> i += 4 // pid position, scaled int32
                    17 -> i += 1 // controller id
                    18 -> i += 6 // three MOS temperature sensors
                    19, 20 -> i += 4 // avg_vd / avg_vq
                    21 -> i += 1 // timeout/kill-switch status
                }
            }
            if (i > payload.size) throw IllegalArgumentException("risposta selective incompleta")
            VescTelemetry(
                tempMos = tempMos,
                tempMotor = tempMotor,
                currentBattery = currentBattery,
                currentMotor = currentMotor,
                voltage = voltage,
                erpm = erpm,
                dutyCyclePercent = duty * 100f,
                ampHoursConsumed = ahUsed,
                wattHoursConsumed = whUsed,
                tachometer = tach,
                tachometerAbs = tachAbs
            ).takeIf(VescTelemetry::isSane)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Layout completo VESC moderno: 74 byte di payload inclusa la COMM_ID.
     * Le nuove versioni aggiungono fault, PID, controller id e diagnostica dopo
     * i campi storici; la parte usata dall'app resta quella comune e scalata.
     */
    private fun parseModernGetValues(payload: ByteArray): VescTelemetry {
        var i = 1
        val tempMos = readInt16(payload, i) / 10f; i += 2
        val tempMotor = readInt16(payload, i) / 10f; i += 2
        val currentMotor = readInt32(payload, i) / 100f; i += 4
        val currentIn = readInt32(payload, i) / 100f; i += 4
        i += 4 // avg_id
        i += 4 // avg_iq
        val duty = readInt16(payload, i) / 1000f; i += 2
        val erpm = readInt32(payload, i).toFloat(); i += 4
        val voltage = readInt16(payload, i) / 10f; i += 2
        val ahUsed = readInt32(payload, i) / 10000f; i += 4
        i += 4 // amp_hours_charged
        val whUsed = readInt32(payload, i) / 10000f; i += 4
        i += 4 // watt_hours_charged
        val tach = readInt32(payload, i); i += 4
        val tachAbs = readInt32(payload, i)
        return VescTelemetry(
            tempMos = tempMos,
            tempMotor = tempMotor,
            currentBattery = currentIn,
            currentMotor = currentMotor,
            voltage = voltage,
            erpm = erpm,
            dutyCyclePercent = duty * 100f,
            ampHoursConsumed = ahUsed,
            wattHoursConsumed = whUsed,
            tachometer = tach,
            tachometerAbs = tachAbs
        )
    }

    /**
     * Parsa la risposta completa a COMM_GET_VALUES.
     * Il layout da 74 byte e' quello VESC moderno usato dal firmware FSESC 6.x;
     * restano supportate le fixture storiche piu' corte per compatibilita'.
     */
    fun parseGetValues(payload: ByteArray): VescTelemetry? {
        if (payload.isEmpty() || payload[0].toInt() != COMM_GET_VALUES) return null
        val dataLen = payload.size - 1
        return try {
            val telemetry = if (payload.size == 74) {
                parseModernGetValues(payload)
            } else {
                parseLegacyGetValues(payload, dataLen)
            }
            telemetry.takeIf(VescTelemetry::isSane)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Parser compatibile con le fixture/layout VESC precedenti gia' supportati. */
    private fun parseLegacyGetValues(payload: ByteArray, dataLen: Int): VescTelemetry {
        var i = 1
        val tempFields = if (dataLen >= 65) 4 else 2
        var tempMos = 0f
        var tempMotor = 0f
        repeat(tempFields) { idx ->
            val t = readInt16(payload, i) / 10f; i += 2
            if (idx == 0) tempMos = t
            if (idx == tempFields - 1) tempMotor = t
        }
        val currentIn = readFloat32(payload, i) / 100f; i += 4
        val currentMotor = readFloat32(payload, i) / 100f; i += 4
        i += 4 // current_in_abs: ignorato
        i += 4 // current_motor_abs: ignorato
        val voltage = readFloat32(payload, i) / 10f; i += 4
        i += 4 // pid_pos: ignorato
        val erpm = readFloat32(payload, i); i += 4
        val duty = readFloat32(payload, i) / 1000f; i += 4
        val ahUsed = readFloat32(payload, i) / 1000f; i += 4
        i += 4 // amp_hours_charged: ignorato
        val whUsed = readFloat32(payload, i) / 1000f; i += 4
        i += 4 // watt_hours_charged: ignorato
        val tach = readInt32(payload, i); i += 4
        val tachAbs = readInt32(payload, i)
        return VescTelemetry(
            tempMos = tempMos,
            tempMotor = tempMotor,
            currentBattery = currentIn,
            currentMotor = currentMotor,
            voltage = voltage,
            erpm = erpm,
            dutyCyclePercent = duty * 100f,
            ampHoursConsumed = ahUsed,
            wattHoursConsumed = whUsed,
            tachometer = tach,
            tachometerAbs = tachAbs
        )
    }

    /** entry-point unico del parser: smista in base al COMM_ID (con unwrap CAN difensivo). */
    fun parseTelemetry(payload: ByteArray): VescTelemetry? {
        if (payload.isEmpty()) return null
        val unwrapped = unwrapCanResponse(payload)
        if (unwrapped.isEmpty()) return null
        return when (unwrapped[0].toInt()) {
            COMM_GET_VALUES -> parseGetValues(unwrapped)
            COMM_GET_VALUES_SELECTIVE -> parseSelective(unwrapped)
            else -> null
        }
    }
}

/**
 * Snapshot di telemetria proveniente dal VESC.
 * Tutti i valori sono gia' convertiti in unita' fisiche.
 */
data class VescTelemetry(
    val tempMos: Float,            // gradi C, temperatura MOSFET
    val tempMotor: Float,          // gradi C, temperatura motore
    val currentBattery: Float,     // A, corrente in ingresso (batteria)
    val currentMotor: Float,       // A, corrente motore
    val voltage: Float,            // V, tensione pack batteria
    val erpm: Float,               // ERPM (rivoluzioni elettriche/min)
    val dutyCyclePercent: Float,   // % (puo' essere negativa in retromarcia)
    val ampHoursConsumed: Float,   // Ah consumati dal controller
    val wattHoursConsumed: Float,  // Wh consumati dal controller
    val tachometer: Int,           // conteggio tachimetro (motori * 3, convenzione VESC)
    val tachometerAbs: Int,        // idem, valore assoluto
    val timestampMs: Long = System.currentTimeMillis()
) {
    /** Potenza istantanea stimata: P = V * I batteria. */
    val powerW: Float get() = voltage * currentBattery

    /** Reject impossible decoded values before they reach UI, alarms, or recording. */
    fun isSane(): Boolean = listOf(
        tempMos, tempMotor, currentBattery, currentMotor, voltage, erpm,
        dutyCyclePercent, ampHoursConsumed, wattHoursConsumed
    ).all(Float::isFinite) &&
        voltage in 0f..200f &&
        tempMos in -50f..250f &&
        tempMotor in -50f..250f &&
        currentBattery in -2_000f..2_000f &&
        currentMotor in -2_000f..2_000f &&
        dutyCyclePercent in -110f..110f
}

/**
 * Reassembler dei flussi byte BLE -> pacchetti VESC.
 *
 * I pacchetti VESC possono arrivare frammentati in un numero arbitrario di
 * scritture BLE (e teoricamente due pacchetti possono arrivare spezzati in
 * modo sfalsato): questa macchina a stati e' il port della `packet.c` del
 * firmware e gestisce tutti i casi. Un CRC errato scarta il pacchetto.
 */
class VescPacketReassembler {

    private enum class State { IDLE, SHORT_LEN, LONG_LEN_HI, LONG_LEN_LOW, DATA, CRC_HI, CRC_LO, STOP }

    private var state = State.IDLE
    private var expectedLen = 0
    private var counter = 0
    private val payload = ByteArray(MAX_PL_LEN + 1)

    /** Consuma i byte ricevuti e restituisce i payload completi e validi (CRC ok). */
    fun process(bytes: ByteArray): List<ByteArray> {
        val frames = ArrayList<ByteArray>(1)
        for (b in bytes) processByte(b.toInt() and 0xFF, frames)
        return frames
    }

    /** Resetta la macchina a stati (usato all'inizio di ogni nuova sessione BLE). */
    fun clearSession() {
        reset()
    }

    private fun processByte(b: Int, out: MutableList<ByteArray>) {
        when (state) {
            State.IDLE -> when (b) {
                0x02 -> { state = State.SHORT_LEN }   // corto: lunghezza su 1 byte
                0x03 -> { state = State.LONG_LEN_HI } // lungo: lunghezza su 2 byte
                // altri byte: rumore/flush, ignorati
            }
            // NB: lo start byte distingue corto/lungo, NON la lunghezza stessa:
            // un pacchetto corto con payload di 2 o 3 byte e' perfettamente valido.
            State.SHORT_LEN -> {
                expectedLen = b
                if (expectedLen > MAX_PL_LEN) { reset(); return }
                counter = 0
                state = if (expectedLen == 0) State.CRC_HI else State.DATA
            }
            State.LONG_LEN_HI -> {
                expectedLen = b shl 8
                state = State.LONG_LEN_LOW
            }
            State.LONG_LEN_LOW -> {
                expectedLen = (expectedLen and 0xFF00) or b
                if (expectedLen > MAX_PL_LEN) { reset(); return }
                counter = 0
                state = if (expectedLen == 0) State.CRC_HI else State.DATA
            }
            State.DATA -> {
                payload[counter++] = b.toByte()
                if (counter == expectedLen) state = State.CRC_HI
            }
            State.CRC_HI -> {
                counter = b shl 8
                state = State.CRC_LO
            }
            State.CRC_LO -> {
                counter = counter or b
                state = State.STOP
            }
            State.STOP -> {
                if (b == 0x03) {
                    // `counter` contiene qui il CRC ricevuto (hi<<8 | lo):
                    // confrontiamolo con quello calcolato sul payload ricevuto.
                    val pl = payload.copyOf(expectedLen)
                    val receivedCrc = counter
                    if (receivedCrc == VescPacket.crc16(pl)) out.add(pl)
                }
                reset()
            }
        }
    }

    private fun reset() {
        state = State.IDLE
        counter = 0
        expectedLen = 0
    }
}
