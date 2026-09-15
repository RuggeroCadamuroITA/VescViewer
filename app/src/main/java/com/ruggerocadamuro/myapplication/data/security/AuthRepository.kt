package com.ruggerocadamuro.myapplication.data.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local authentication store. The PIN itself is never persisted: only a
 * salted PBKDF2 verifier and non-secret rate-limit metadata are kept locally.
 */
class AuthRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun hasPin(): Boolean = preferences.contains(KEY_HASH) && preferences.contains(KEY_SALT)

    fun setPin(pin: String) {
        require(pin.length >= MIN_PIN_LENGTH && pin.all(Char::isDigit))
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(pin, salt)
        preferences.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_NEXT_ATTEMPT_AT)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    fun verifyPin(pin: String): Boolean = verifyPinRateLimited(pin).success

    @Synchronized
    fun verifyPinRateLimited(pin: String, nowMs: Long = System.currentTimeMillis()): PinVerificationResult {
        val lockoutUntil = preferences.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil > nowMs) {
            return PinVerificationResult(
                success = false,
                retryAfterMs = lockoutUntil - nowMs,
                lockedOut = true
            )
        }
        val nextAttemptAt = preferences.getLong(KEY_NEXT_ATTEMPT_AT, 0L)
        if (nextAttemptAt > nowMs) {
            return PinVerificationResult(
                success = false,
                retryAfterMs = nextAttemptAt - nowMs,
                lockedOut = false
            )
        }

        val salt = preferences.getString(KEY_SALT, null)?.let {
            runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
        } ?: return PinVerificationResult(false)
        val expected = preferences.getString(KEY_HASH, null)?.let {
            runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
        } ?: return PinVerificationResult(false)

        if (MessageDigest.isEqual(expected, derive(pin, salt))) {
            preferences.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_NEXT_ATTEMPT_AT)
                .remove(KEY_LOCKOUT_UNTIL)
                .apply()
            return PinVerificationResult(success = true)
        }

        val failedAttempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val decision = PinRateLimitPolicy.afterFailure(failedAttempts)
        val editor = preferences.edit().putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
        if (decision.lockedOut) {
            editor.remove(KEY_NEXT_ATTEMPT_AT)
                .putLong(KEY_LOCKOUT_UNTIL, nowMs + decision.retryAfterMs)
        } else {
            editor.putLong(KEY_NEXT_ATTEMPT_AT, nowMs + decision.retryAfterMs)
        }
        editor.apply()
        return PinVerificationResult(
            success = false,
            retryAfterMs = decision.retryAfterMs,
            lockedOut = decision.lockedOut
        )
    }

    fun resetForDevelopment() {
        preferences.edit().clear().apply()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val MIN_PIN_LENGTH = 6
        const val MAX_FAILED_ATTEMPTS = PinRateLimitPolicy.MAX_FAILED_ATTEMPTS
        const val INITIAL_BACKOFF_MS = PinRateLimitPolicy.INITIAL_BACKOFF_MS
        const val MAX_BACKOFF_MS = PinRateLimitPolicy.MAX_BACKOFF_MS
        const val LOCKOUT_DURATION_MS = PinRateLimitPolicy.LOCKOUT_DURATION_MS
        private const val PREFS = "vesc_viewer_auth"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_NEXT_ATTEMPT_AT = "next_attempt_at"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val SALT_BYTES = 32
        private const val KEY_BITS = 256
        private const val ITERATIONS = 120_000
    }
}

data class PinVerificationResult(
    val success: Boolean,
    val retryAfterMs: Long = 0L,
    val lockedOut: Boolean = false
)

internal data class PinRateLimitDecision(
    val retryAfterMs: Long,
    val lockedOut: Boolean
)

internal object PinRateLimitPolicy {
    const val MAX_FAILED_ATTEMPTS = 5
    const val INITIAL_BACKOFF_MS = 1_000L
    const val MAX_BACKOFF_MS = 30_000L
    const val LOCKOUT_DURATION_MS = 5 * 60_000L

    fun afterFailure(failedAttempts: Int): PinRateLimitDecision {
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            return PinRateLimitDecision(LOCKOUT_DURATION_MS, lockedOut = true)
        }
        val delayMs = (INITIAL_BACKOFF_MS shl (failedAttempts - 1).coerceAtLeast(0).coerceAtMost(4))
            .coerceAtMost(MAX_BACKOFF_MS)
        return PinRateLimitDecision(delayMs, lockedOut = false)
    }
}
