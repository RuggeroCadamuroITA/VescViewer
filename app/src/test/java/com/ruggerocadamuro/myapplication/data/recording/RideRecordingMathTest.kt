package com.ruggerocadamuro.myapplication.data.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RideRecordingMathTest {
    @Test
    fun counterDeltaUsesSessionBaseline() {
        assertEquals(12.5f, RideRecordingMath.counterDelta(42.5f, 30f), 0.001f)
    }

    @Test
    fun counterDeltaRestartsAfterControllerReset() {
        assertEquals(3f, RideRecordingMath.counterDelta(3f, 30f), 0.001f)
    }

    @Test
    fun counterDeltaRejectsMissingOrInvalidSamples() {
        assertEquals(0f, RideRecordingMath.counterDelta(null, 30f), 0.001f)
        assertEquals(0f, RideRecordingMath.counterDelta(Float.NaN, 30f), 0.001f)
        assertEquals(4f, RideRecordingMath.counterDelta(4f, null), 0.001f)
    }
}
