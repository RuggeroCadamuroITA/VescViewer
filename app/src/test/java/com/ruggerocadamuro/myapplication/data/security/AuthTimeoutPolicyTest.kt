package com.ruggerocadamuro.myapplication.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTimeoutPolicyTest {
    @Test
    fun relocksOnlyAfterConfiguredBackgroundInterval() {
        assertFalse(AuthTimeoutPolicy.shouldRelock(true, 1_000L, 5_999L, 5_000L))
        assertTrue(AuthTimeoutPolicy.shouldRelock(true, 1_000L, 6_000L, 5_000L))
    }

    @Test
    fun disabledTimeoutOrLockedStateNeverRelocks() {
        assertFalse(AuthTimeoutPolicy.shouldRelock(false, 1_000L, 100_000L, 5_000L))
        assertFalse(AuthTimeoutPolicy.shouldRelock(true, 1_000L, 100_000L, 0L))
        assertFalse(AuthTimeoutPolicy.shouldRelock(true, 0L, 100_000L, 5_000L))
    }
}
