package com.inamibrahim.kinhold.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Plus Codes (Open Location Code), the short place codes Google Maps shows for a dropped pin, such as "H88G+PF7" or
 * "8J2VH88G+PF7". Reading one needs no internet: a full code (with the area digits) decodes straight to a point, and a short
 * code is placed at the nearest match to a [reference] point that is within about 50 km (a saved pin or a geocoded city).
 * Pure Kotlin.
 */
object PlusCode {
    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR_AT = 8
    private val TOKEN = Regex("""(?i)(?<![A-Z0-9])([23456789CFGHJMPQRVWX]{2,8}\+[23456789CFGHJMPQRVWX]{0,})""")

    /** A Plus Code found in [text] and any words after it (Maps writes the town after the code, e.g. "H88G+PF7 Lahore"). */
    class Found(val code: String, val locality: String) {
        /** Short codes lack the first four digits and need a nearby point to be placed. */
        val isShort: Boolean get() = code.indexOf('+') < SEPARATOR_AT
    }

    fun find(text: String): Found? {
        val m = TOKEN.find(text.trim()) ?: return null
        val code = m.groupValues[1].uppercase()
        val digits = code.replace("+", "")
        if (digits.length < 4 || code.indexOf('+') % 2 != 0 || code.indexOf('+') > SEPARATOR_AT) return null
        val rest = text.substring(m.range.last + 1).trim().trim(',', ' ')
        return Found(code, rest)
    }

    private fun index(c: Char) = ALPHABET.indexOf(c.uppercaseChar())

    /** The centre of a full code, or null if it is not a full, well-formed code. */
    fun decode(code: String): GeoPin? {
        val c = code.trim().uppercase()
        if (c.indexOf('+') != SEPARATOR_AT) return null
        val digits = c.replace("+", "").trimEnd('0')
        if (digits.length < 2 || digits.any { index(it) < 0 }) return null
        val pairLen = min(digits.length, 10)
        if (pairLen % 2 != 0) return null
        var lat = -90.0
        var lng = -180.0
        var res = 20.0
        var lastRes = res
        for (p in 0 until pairLen / 2) {
            lat += index(digits[2 * p]) * res
            lng += index(digits[2 * p + 1]) * res
            lastRes = res
            res /= 20.0
        }
        var latSize = lastRes
        var lngSize = lastRes
        if (digits.length > 10) {
            var latRes = 1.0 / 8000
            var lngRes = 1.0 / 8000
            for (k in 10 until digits.length) {
                val d = index(digits[k])
                latRes /= 5
                lngRes /= 4
                lat += (d / 4) * latRes
                lng += (d % 4) * lngRes
            }
            latSize = latRes
            lngSize = lngRes
        }
        val pin = GeoPin(lat + latSize / 2, lng + lngSize / 2)
        return pin.takeIf { it.isValid }
    }

    /** The first [length] digits (an even number up to 8) of the full code of [ref]: the area digits a short code is missing. */
    private fun prefixOf(ref: GeoPin, length: Int): String {
        var lat = min(max(ref.lat, -90.0), 90.0 - 1e-9) + 90
        var lng = ((ref.lng + 180) % 360 + 360) % 360
        val sb = StringBuilder()
        var res = 20.0
        repeat(length / 2) {
            val a = floor(lat / res).toInt().coerceIn(0, 19)
            val b = floor(lng / res).toInt().coerceIn(0, 19)
            sb.append(ALPHABET[a]).append(ALPHABET[b])
            lat -= a * res
            lng -= b * res
            res /= 20.0
        }
        return sb.toString()
    }

    /** Places a short code at the nearest point to [ref] that it can mean, or null if it is not a valid short code. */
    fun recover(shortCode: String, ref: GeoPin): GeoPin? {
        val c = shortCode.trim().uppercase()
        val plus = c.indexOf('+')
        if (plus < 2 || plus >= SEPARATOR_AT || plus % 2 != 0 || c.any { it != '+' && index(it) < 0 }) return null
        val padding = SEPARATOR_AT - plus
        val full = prefixOf(ref, padding) + c
        val centre = decode(full) ?: return null
        val resolution = 20.0.pow(2 - padding / 2)
        val half = resolution / 2
        var lat = centre.lat
        var lng = centre.lng
        if (ref.lat + half < lat && lat - resolution >= -90) lat -= resolution
        else if (ref.lat - half > lat && lat + resolution <= 90) lat += resolution
        if (ref.lng + half < lng) lng -= resolution else if (ref.lng - half > lng) lng += resolution
        if (lng > 180) lng -= 360 else if (lng < -180) lng += 360
        return GeoPin(lat, lng).takeIf { it.isValid }
    }
}
