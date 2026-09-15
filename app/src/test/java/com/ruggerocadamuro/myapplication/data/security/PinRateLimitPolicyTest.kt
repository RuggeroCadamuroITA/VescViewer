package com.ruggerocadamuro.myapplication.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRateLimitPolicyTest {
    @Test
    fun failuresUseProgressiveBackoffAndLockOnFifthAttempt() {
        assertEquals(1_000L, PinRateLimitPolicy.afterFailure(1).retryAfterMs)
        assertEquals(2_000L, PinRateLimitPolicy.afterFailure(2).retryAfterMs)
        assertEquals(4_000L, PinRateLimitPolicy.afterFailure(3).retryAfterMs)
        assertEquals(8_000L, PinRateLimitPolicy.afterFailure(4).retryAfterMs)
        assertEquals(AuthRepository.LOCKOUT_DURATION_MS, PinRateLimitPolicy.afterFailure(5).retryAfterMs)
        assertTrue(PinRateLimitPolicy.afterFailure(5).lockedOut)
        assertFalse(PinRateLimitPolicy.afterFailure(4).lockedOut)
    }
}
