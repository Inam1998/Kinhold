package com.inamibrahim.kinhold.model

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A PIN as it is stored: never the PIN itself, only a salted hash that cannot be turned back into it. */
class StoredPin(val salt: String, val hash: String)

/**
 * The optional app lock's PIN rules. A PIN is 4 to 8 digits. It is stored as a salted PBKDF2 hash and checked in constant
 * time; wrong tries earn a growing wait. Pure Kotlin apart from the standard cryptography classes.
 */
object PinLock {
    const val MIN_DIGITS = 4
    const val MAX_DIGITS = 8
    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256

    fun isValid(pin: String): Boolean = pin.length in MIN_DIGITS..MAX_DIGITS && pin.all { it in '0'..'9' }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)).encoded

    fun create(pin: String): StoredPin {
        require(isValid(pin)) { "A PIN is 4 to 8 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val b64 = Base64.getEncoder()
        return StoredPin(b64.encodeToString(salt), b64.encodeToString(hash(pin, salt)))
    }

    fun verify(pin: String, stored: StoredPin): Boolean {
        if (!isValid(pin)) return false
        val salt = runCatching { Base64.getDecoder().decode(stored.salt) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return false
        val expected = runCatching { Base64.getDecoder().decode(stored.hash) }.getOrNull() ?: return false
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    /** Seconds to wait before the next try after [failures] wrong tries in a row: none for the first four, then 30 seconds growing to an hour. */
    fun waitSeconds(failures: Int): Int = when {
        failures < 5 -> 0
        else -> minOf(3600, 30 * (1 shl minOf(failures - 5, 7)))
    }
}

/** How long the app may be in the background before it asks for the PIN again. */
enum class LockDelay(val label: String, val millis: Long) {
    Immediately("Immediately", 0L),
    OneMinute("After 1 minute", 60_000L),
    FiveMinutes("After 5 minutes", 300_000L),
}
