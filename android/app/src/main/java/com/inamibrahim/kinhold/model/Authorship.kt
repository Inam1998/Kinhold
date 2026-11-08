package com.inamibrahim.kinhold.model

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Who made Kinhold, and a private phrase only the author knows. The app holds a salted, slow hash of the phrase (never the phrase),
 * so the author can type it into the hidden Developer page to show it matches, which nobody else could. Set it with `node scripts/set-phrase.mjs`.
 */
object Authorship {
    const val NAME = "Inam Ibrahim"
    const val EMAIL = "inamibrahim752@gmail.com"
    const val YEAR = 2026
    const val STARTED = "November 2026"

    // BEGIN PHRASE (written by scripts/set-phrase.mjs; do not edit by hand)
    const val SALT = "d1ccc3fc851c27a573b465c7919fb1a5"
    const val HASH = "d51ed042ccfefe286d59b5a4d6ecac07c53af5e355f391f0f02fc746437193ed"
    const val ITERATIONS = 200000
    const val SET_ON = "2026-11-08"
    // END PHRASE

    /** The phrase as it is compared: Unicode-normalised, lower case, single spaces, none at the ends. */
    fun normalize(phrase: String): String =
        java.text.Normalizer.normalize(phrase, java.text.Normalizer.Form.NFC).lowercase().split(" ").filter { it.isNotEmpty() }.joinToString(" ")

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun toHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** The slow hash: PBKDF2-SHA-256 of the normalised phrase, as lower-case hex. */
    fun hash(phrase: String, saltHex: String, iterations: Int): String =
        toHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(normalize(phrase).toCharArray(), hex(saltHex), iterations, 256)).encoded)

    val isSet: Boolean get() = HASH.isNotEmpty()

    /** True when [phrase] is the one the author set; false when none was set. Takes a fraction of a second, so call it off the main thread. */
    fun check(phrase: String, hashHex: String = HASH, saltHex: String = SALT, iterations: Int = ITERATIONS): Boolean {
        if (hashHex.isEmpty() || phrase.isBlank()) return false
        val got = hash(phrase, saltHex, iterations)
        var diff = got.length xor hashHex.length
        for (i in 0 until minOf(got.length, hashHex.length)) diff = diff or (got[i].code xor hashHex[i].code)
        return diff == 0
    }
}
