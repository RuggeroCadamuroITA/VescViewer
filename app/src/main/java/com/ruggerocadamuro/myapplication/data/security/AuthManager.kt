package com.ruggerocadamuro.myapplication.data.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(private val repository: AuthRepository) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 5 * 60_000L
    }

    private val _unlocked = MutableStateFlow(!repository.hasPin())
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var lastUnlockedElapsedMs: Long = if (_unlocked.value) SystemClock.elapsedRealtime() else 0L
    private var backgroundSinceElapsedMs: Long = 0L
    private var timeoutMs: Long = DEFAULT_TIMEOUT_MS

    fun configureTimeout(timeoutMs: Long) {
        this.timeoutMs = timeoutMs.coerceAtLeast(0L)
    }

    fun markBackground() {
        if (_unlocked.value) backgroundSinceElapsedMs = SystemClock.elapsedRealtime()
    }

    fun markForeground() {
        if (AuthTimeoutPolicy.shouldRelock(
                unlocked = _unlocked.value,
                backgroundSinceElapsedMs = backgroundSinceElapsedMs,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                timeoutMs = timeoutMs
            )
        ) {
            lock()
        }
        backgroundSinceElapsedMs = 0L
    }

    fun hasPin(): Boolean = repository.hasPin()

    fun createPin(pin: String, confirmation: String): Boolean {
        if (pin.length < AuthRepository.MIN_PIN_LENGTH || !pin.all(Char::isDigit) || pin != confirmation) return false
        repository.setPin(pin)
        unlock()
        return true
    }

    fun verify(pin: String): PinVerificationResult {
        val result = repository.verifyPinRateLimited(pin)
        if (result.success) unlock()
        return result
    }

    fun unlock() {
        lastUnlockedElapsedMs = SystemClock.elapsedRealtime()
        _unlocked.value = true
    }

    fun lock() {
        if (repository.hasPin()) _unlocked.value = false
    }

    fun lockIfExpired(timeoutMs: Long): Boolean {
        if (!_unlocked.value || timeoutMs <= 0L) return false
        if (SystemClock.elapsedRealtime() - lastUnlockedElapsedMs >= timeoutMs) {
            lock()
            return true
        }
        return false
    }
}

internal object AuthTimeoutPolicy {
    fun shouldRelock(
        unlocked: Boolean,
        backgroundSinceElapsedMs: Long,
        nowElapsedMs: Long,
        timeoutMs: Long
    ): Boolean = unlocked && backgroundSinceElapsedMs > 0L && timeoutMs > 0L &&
        nowElapsedMs - backgroundSinceElapsedMs >= timeoutMs
}
