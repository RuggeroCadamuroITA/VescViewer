package com.ruggerocadamuro.myapplication.data.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(private val repository: AuthRepository) {
    private val _unlocked = MutableStateFlow(!repository.hasPin())
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var lastUnlockedElapsedMs: Long = if (_unlocked.value) SystemClock.elapsedRealtime() else 0L

    fun hasPin(): Boolean = repository.hasPin()

    fun createPin(pin: String, confirmation: String): Boolean {
        if (pin.length < AuthRepository.MIN_PIN_LENGTH || !pin.all(Char::isDigit) || pin != confirmation) return false
        repository.setPin(pin)
        unlock()
        return true
    }

    fun verify(pin: String): Boolean = repository.verifyPin(pin).also { if (it) unlock() }

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
