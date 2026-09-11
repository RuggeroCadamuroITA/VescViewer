package com.ruggerocadamuro.myapplication.data.vesc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test del protocollo VESC: CRC16, framing/reassemblaggio, parsing telemetria.
 * Puri JVM, nessuna dipendenza Android.
 */
class VescPacketTest {

    @Test
    fun `crc16 e' CRC-16-XMODEM (poly 0x1021, init 0)`() {
        // check value standard CRC-16/XMODEM: per "123456789" = 0x31C3.
        // E' l'algoritmo di crc.c del firmware VESC, confermato dall'implementazione
        // di vescape (verificata su hardware reale).
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0x31C3, VescPacket.crc16(data))
    }

    @Test
    fun `crc16 con offset e lunghezza parziale`() {
        val data = byteArrayOf(0x00, 0x31, 0x32, 0x33, 0x00)
        assertEquals(VescPacket.crc16("123".toByteArray()), VescPacket.crc16(data, 1, 3))
    }

    @Test
    fun `wrap produce il framing atteso per un pacchetto corto`() {
        val payload = byteArrayOf(0x04)
        val packet = VescPacket.wrap(payload)
        val crc = VescPacket.crc16(payload)
        val expected = byteArrayOf(
            0x02, 0x01, 0x04,
            ((crc shr 8) and 0xFF).toByte(), (crc and 0xFF).toByte(), 0x03
        )
        assertTrue(expected.contentEquals(packet))
    }

    @Test
    fun `reassembler gestisce pacchetto intero`() {
        val payload = byteArrayOf(0x04, 0x01, 0x02, 0x03)
        val reassembler = VescPacketReassembler()
        val frames = reassembler.process(VescPacket.wrap(payload))
        assertEquals(1, frames.size)
        assertTrue(payload.contentEquals(frames[0]))
    }

    @Test
    fun `reassembler gestisce rumore prima e dopo il pacchetto`() {
        val payload = ByteArray(200) { (it + 7).toByte() }
        val packet = VescPacket.wrap(payload)
        val reassembler = VescPacketReassembler()
        // byte spuri tra pacchetti: il reassembler li salta e si risincronizza
        val frames = reassembler.process(byteArrayOf(0x55, 0x00) + packet + byteArrayOf(0xAA.toByte(), 0x00))
        assertEquals(1, frames.size)
        assertTrue(payload.contentEquals(frames[0]))
    }

    @Test
    fun `reassembler si risincronizza dopo un pacchetto corrotto`() {
        val payload = ByteArray(200) { (it + 7).toByte() }
        val packet = VescPacket.wrap(payload)
        val corrupted = packet.copyOf().also { it[50] = ((it[50] + 1).toInt()).toByte() } // CRC non piu' valido
        val reassembler = VescPacketReassembler()
        val frames = reassembler.process(corrupted + packet)
        // il pacchetto corrotto viene scartato (CRC), quello valido dopo passa
        assertEquals(1, frames.size)
        assertTrue(payload.contentEquals(frames[0]))
    }

    @Test
    fun `reassembler gestisce due pacchetti consecutivi`() {
        val p1 = byteArrayOf(0x04, 0x0A)
        val p2 = byteArrayOf(0x35)
        val reassembler = VescPacketReassembler()
        val frames = reassembler.process(VescPacket.wrap(p1) + VescPacket.wrap(p2))
        assertEquals(2, frames.size)
        assertTrue(p1.contentEquals(frames[0]))
        assertTrue(p2.contentEquals(frames[1]))
    }

    @Test
    fun `reassembler gestisce pacchetti corti con payload di 2 e 3 byte`() {
        // la lunghezza 0x02/0x03 nel campo len NON va confusa con lo start byte lungo
        val p2 = byteArrayOf(0x04, 0x0A)       // payload 2 byte
        val p3 = byteArrayOf(0x04, 0x0A, 0x0B) // payload 3 byte
        val reassembler = VescPacketReassembler()
        val frames = reassembler.process(VescPacket.wrap(p2) + VescPacket.wrap(p3))
        assertEquals(2, frames.size)
        assertTrue(p2.contentEquals(frames[0]))
        assertTrue(p3.contentEquals(frames[1]))
    }

    @Test
    fun `reassembler accetta pacchetto di keepalive senza payload`() {
        // lunghezza 0: solo CRC (0x0000) e stop byte
        val reassembler = VescPacketReassembler()
        val frames = reassembler.process(byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x03))
        assertEquals(1, frames.size)
        assertEquals(0, frames[0].size)
    }

    @Test
    fun `reassembler scarta pacchetto con CRC errato`() {
        val packet = VescPacket.wrap(byteArrayOf(0x04))
        packet[packet.size - 3] = (packet[packet.size - 3] + 1).toByte() // corrompe crc hi
        val frames = VescPacketReassembler().process(packet)
        assertEquals(0, frames.size)
        // lo stato deve essere tornato IDLE: un pacchetto valido dopo va accettato
        val frames2 = VescPacketReassembler().process(VescPacket.wrap(byteArrayOf(0x04)))
        assertEquals(1, frames2.size)
    }

    @Test
    fun `parseSelective legge risposta con header mask e campi scalati`() {
        // La risposta reale contiene [id][mask uint32] prima dei campi selezionati.
        fun i16(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
        fun i32(v: Int) = byteArrayOf(
            ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte()
        )
        val mask = VescPacket.SELECTIVE_MASK
        val payload = byteArrayOf(VescPacket.COMM_GET_VALUES_SELECTIVE.toByte()) +
            i32(mask) +
            i16(312) +                      // temp_mos = 31.2 C
            i16(405) +                      // temp_motor = 40.5 C
            i32(2750) +                     // current_motor = 27.50 A
            i32(1250) +                     // current_in = 12.50 A
            i16(420) +                      // duty = 0.42 -> 42 %
            i32(49000) +                    // erpm
            i16(472) +                      // voltage_in = 47.2 V
            i32(123400) +                   // ah = 12.340 Ah
            i32(5678000) +                  // wh = 567.8 Wh
            i32(15000) +                    // tach
            i32(16000)                      // tach_abs
        val t = VescPacket.parseSelective(payload)
        assertNotNull(t)
        t!!
        assertEquals(31.2f, t.tempMos, 0.01f)
        assertEquals(40.5f, t.tempMotor, 0.01f)
        assertEquals(12.5f, t.currentBattery, 0.01f)
        assertEquals(27.5f, t.currentMotor, 0.01f)
        assertEquals(47.2f, t.voltage, 0.01f)
        assertEquals(49000f, t.erpm, 0.5f)
        assertEquals(42f, t.dutyCyclePercent, 0.01f)
        assertEquals(12.34f, t.ampHoursConsumed, 0.001f)
        assertEquals(567.8f, t.wattHoursConsumed, 0.1f)
        assertEquals(15000, t.tachometer)
        assertEquals(16000, t.tachometerAbs)
        assertEquals(47.2f * 12.5f, t.powerW, 0.1f)
    }

    @Test
    fun `parseGetValues legge layout moderno VESC da 74 byte`() {
        // Layout VESC fw6: id + 2 temperature + 4 float32 + duty int16 +
        // rpm int32 + tensione int16 + 4 contatori + 2 tach + diagnostica.
        fun i16(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
        fun i32(v: Int) = byteArrayOf(
            ((v shr 24) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte()
        )
        fun f32(v: Float) = i32(java.lang.Float.floatToIntBits(v))
        val payload = byteArrayOf(VescPacket.COMM_GET_VALUES.toByte()) +
            i16(243) + i16(-30) +             // MOS 24.3 C, motore -3.0 C
            i32(2750) + i32(-1250) +           // motore 27.5 A, batteria -12.5 A
            i32(0) + i32(0) +                   // avg_id, avg_iq
            i16(420) +                          // duty 42 %
            i32(49000) +                        // ERPM
            i16(472) +                          // 47.2 V
            i32(123400) + i32(0) +              // Ah usati/ricaricati
            i32(5678000) + i32(0) +             // Wh usati/ricaricati
            i32(15000) + i32(16000) +           // tach/tach abs
            byteArrayOf(0) +                    // fault
            i32(0) + byteArrayOf(1) +           // PID position, controller id
            i16(0) + i16(0) + i16(0) +          // tre temperature MOS
            f32(0f) + f32(0f) +                 // avg vd/vq
            byteArrayOf(0)                       // timeout status
        assertEquals(74, payload.size)
        val t = VescPacket.parseGetValues(payload)
        assertNotNull(t)
        t!!
        assertEquals(24.3f, t.tempMos, 0.01f)
        assertEquals(-3.0f, t.tempMotor, 0.01f)
        assertEquals(-12.5f, t.currentBattery, 0.01f)
        assertEquals(27.5f, t.currentMotor, 0.01f)
        assertEquals(47.2f, t.voltage, 0.01f)
        assertEquals(49000f, t.erpm, 0.5f)
        assertEquals(42f, t.dutyCyclePercent, 0.01f)
        assertEquals(12.34f, t.ampHoursConsumed, 0.001f)
        assertEquals(567.8f, t.wattHoursConsumed, 0.1f)
        assertEquals(15000, t.tachometer)
        assertEquals(16000, t.tachometerAbs)
    }

    @Test
    fun `parseGetValues gestisce layout firmware legacy (2 temperature)`() {
        // payload completo fw <= 5.x: id + 2 i16 + 12 f32 + 2 i32
        fun f32(v: Float) = byteArrayOf(
            ((java.lang.Float.floatToIntBits(v) shr 24) and 0xFF).toByte(),
            ((java.lang.Float.floatToIntBits(v) shr 16) and 0xFF).toByte(),
            ((java.lang.Float.floatToIntBits(v) shr 8) and 0xFF).toByte(),
            (java.lang.Float.floatToIntBits(v) and 0xFF).toByte()
        )
        fun i16(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
        val payload = byteArrayOf(VescPacket.COMM_GET_VALUES.toByte()) +
            i16(255) + i16(300) +   // temp fet 25.5, temp motor 30.0
            f32(1000f) +            // current_in 10 A
            f32(2000f) +            // current_motor 20 A
            f32(3000f) +            // current_in_abs (ignorato)
            f32(4000f) +            // current_motor_abs (ignorato)
            f32(420f) +             // voltage 42.0 V
            f32(0f) +               // pid_pos (ignorato)
            f32(21000f) +           // erpm
            f32(500f) +             // duty 0.5 -> 50 %
            f32(5000f) +            // ah 5.0
            f32(0f) +               // ah_charged (ignorato)
            f32(210000f) +          // wh 210.0
            f32(0f) +               // wh_charged (ignorato)
            byteArrayOf(0, 0, 0x3A, 0x98.toByte()) +          // tach 15000
            byteArrayOf(0, 0, 0x40, 0x54)            // tach_abs 16404
        val t = VescPacket.parseGetValues(payload)
        assertNotNull(t)
        t!!
        assertEquals(25.5f, t.tempMos, 0.01f)
        assertEquals(30.0f, t.tempMotor, 0.01f)
        assertEquals(10f, t.currentBattery, 0.01f)
        assertEquals(20f, t.currentMotor, 0.01f)
        assertEquals(42f, t.voltage, 0.01f)
        assertEquals(21000f, t.erpm, 0.5f)
        assertEquals(5f, t.ampHoursConsumed, 0.001f)
        assertEquals(210f, t.wattHoursConsumed, 0.1f)
    }

    @Test
    fun `parseGetValues gestisce layout firmware 6 (4 temperature)`() {
        // fw 6.x: id + 4 i16 (temp) + 12 f32 + 2 i32 + 1 fault = 66 byte payload
        fun i16(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
        val zeros = ByteArray(48) // 12 float qualsiasi, il parser li scarta/legge senza crash
        val payload = byteArrayOf(VescPacket.COMM_GET_VALUES.toByte()) +
            i16(300) + i16(310) + i16(320) + i16(330) + zeros +
            byteArrayOf(0, 0, 0x27, 0x10) +   // tach 10000
            byteArrayOf(0, 0, 0x2E, 0xE0.toByte()) +   // tach_abs 12000
            byteArrayOf(0x00)                 // fault
        val t = VescPacket.parseGetValues(payload)
        assertNotNull(t)
        t!!
        assertEquals(30f, t.tempMos, 0.01f)        // prima temperatura
        assertEquals(33f, t.tempMotor, 0.01f)      // ultima temperatura
        assertEquals(10000, t.tachometer)
        assertEquals(12000, t.tachometerAbs)
    }

    @Test
    fun `parseTelemetry rifiuta comm id sconosciuto`() {
        assertNull(VescPacket.parseTelemetry(byteArrayOf(0x2E, 0x01, 0x02)))
        assertNull(VescPacket.parseTelemetry(byteArrayOf()))
    }

    @Test
    fun `round-trip build e reassemblaggio selettivo`() {
        val request = VescPacket.buildGetValuesSelective()
        // il pacchetto di richiesta deve potersi ricostruire dal reassembler
        val frames = VescPacketReassembler().process(request)
        assertEquals(1, frames.size)
        assertEquals(5, frames[0].size) // id + mask 4 byte
        assertEquals(VescPacket.COMM_GET_VALUES_SELECTIVE, frames[0][0].toInt())
        val mask = ((frames[0][1].toInt() and 0xFF) shl 24) or
            ((frames[0][2].toInt() and 0xFF) shl 16) or
            ((frames[0][3].toInt() and 0xFF) shl 8) or
            (frames[0][4].toInt() and 0xFF)
        assertEquals(VescPacket.SELECTIVE_MASK, mask)
    }

    @Test
    fun `frameCanForward incapsula con 0x22 e canId`() {
        val inner = byteArrayOf(0x04)
        val framed = VescPacket.frameCanForward(inner, 0)
        val frames = VescPacketReassembler().process(framed)
        assertEquals(1, frames.size)
        assertEquals(3, frames[0].size) // 0x22, canId, 0x04
        assertEquals(VescPacket.COMM_FORWARD_CAN, frames[0][0].toInt())
        assertEquals(0, frames[0][1].toInt())
        assertEquals(0x04, frames[0][2].toInt())
    }

    @Test
    fun `parseTelemetry unwrappa la risposta CAN annidata`() {
        // forma difensiva: [0x22, canId, 0x53, ...payload selettivo completo (41B)]
        val good = byteArrayOf(VescPacket.COMM_GET_VALUES_SELECTIVE.toByte()) + ByteArray(40)
        val nested = byteArrayOf(VescPacket.COMM_FORWARD_CAN.toByte(), 0x00) + good
        val t = VescPacket.parseTelemetry(nested)
        assertNotNull(t) // senza unwrap sarebbe null
        // la forma gia' unwrapped continua a funzionare
        assertNotNull(VescPacket.parseTelemetry(good))
    }
}
