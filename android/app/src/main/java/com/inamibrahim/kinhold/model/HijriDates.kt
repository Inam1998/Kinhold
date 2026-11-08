package com.inamibrahim.kinhold.model

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate

/**
 * Hijri (Islamic) dates beside the Gregorian ones, using the Umm al-Qura calendar that Android provides. Kinhold stores
 * and sorts every date as Gregorian; a Hijri date entered in the picker is converted to the Gregorian day it falls on, and
 * a stored full date can be shown with its Hijri equivalent. Only years 1300 to 1600 AH can be converted. Pure Kotlin.
 */
object HijriDates {
    val MONTH_NAMES = listOf(
        "Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani", "Jumada al-awwal", "Jumada al-thani",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah",
    )

    /** The Hijri date of a Gregorian day as (year, month, day), or null outside the calendar's range. */
    fun fromGregorian(date: LocalDate): Triple<Int, Int, Int>? = runCatching {
        val h = HijrahDate.from(date)
        Triple(h.get(java.time.temporal.ChronoField.YEAR), h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), h.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
    }.getOrNull()

    /** The Gregorian day of a Hijri date, or null if that Hijri date does not exist. */
    fun toGregorian(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.from(HijrahChronology.INSTANCE.date(year, month, day)) }.getOrNull()

    /** "28 Sha'ban 1367 AH" for a stored date that has a year, a month and a day, or null if it is only partly known. */
    fun describe(date: PartialDate): String? {
        val y = date.year ?: return null
        val m = date.month ?: return null
        val d = date.day ?: return null
        val local = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return null
        val (hy, hm, hd) = fromGregorian(local) ?: return null
        return "$hd ${MONTH_NAMES[hm - 1]} $hy AH"
    }

    /** The Hijri dates of someone's life for a card: "b. 3 Rajab 1357 AH – d. ...", only for the parts that are full dates; null when neither is. */
    fun lifeText(birth: PartialDate, death: PartialDate): String? {
        val b = describe(birth)
        val d = describe(death)
        return when {
            b != null && d != null -> "$b – $d"
            b != null -> "b. $b"
            d != null -> "d. $d"
            else -> null
        }
    }

    /** A life as one line with each full date followed by its Hijri date: "b. 24 Feb 1998 (27 Shawwal 1418 AH)". Dates that cannot be converted stay as they are. */
    fun lifeLine(birth: PartialDate, death: PartialDate): String {
        fun part(d: PartialDate): String {
            val g = d.text()
            val h = describe(d)
            return if (g.isNotEmpty() && h != null) "$g ($h)" else g
        }
        val b = part(birth)
        val d = part(death)
        return when {
            b.isNotEmpty() && d.isNotEmpty() -> "$b – $d"
            b.isNotEmpty() -> "b. $b"
            d.isNotEmpty() -> "d. $d"
            else -> ""
        }
    }

    /** A Hijri date as the [PartialDate] it falls on in the Gregorian calendar, or null. */
    fun partialFromHijri(year: Int, month: Int, day: Int): PartialDate? =
        toGregorian(year, month, day)?.let { PartialDate(it.year, it.monthValue, it.dayOfMonth) }
}
