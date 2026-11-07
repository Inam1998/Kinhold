package com.inamibrahim.kinhold.model

import java.time.LocalDate

/** What kind of date it is. */
enum class UpcomingKind { Birthday, Remembrance, Anniversary }

/**
 * A date coming up in the tree: a birthday of someone who may be alive, the day someone died (remembered each year), or a
 * marriage anniversary. [on] is the next time it falls, [daysAway] how many days from today (0 = today), and [years] how
 * many years it will be since the first year, when that year is known. [other] is the spouse for an anniversary.
 */
class UpcomingDate(
    val kind: UpcomingKind,
    val person: Person,
    val other: Person?,
    val on: LocalDate,
    val daysAway: Int,
    val years: Int?,
)

/** A marriage date with both the day and the month known, for anniversaries. */
class MarriageDate(val a: Int, val b: Int, val date: PartialDate)

/** Finds the birthdays, remembrance days and anniversaries coming up. Pure Kotlin; it reads the people already in memory. */
object Upcoming {
    /** The next time the month and day fall on or after [today]. A 29 February is kept on 28 February in years without one. */
    fun nextOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
        fun inYear(y: Int): LocalDate {
            val d = if (month == 2 && day == 29 && !java.time.Year.isLeap(y.toLong())) 28 else day
            return LocalDate.of(y, month, d)
        }
        val thisYear = inYear(today.year)
        return if (thisYear >= today) thisYear else inYear(today.year + 1)
    }

    /** The dates within [withinDays] days of [today], soonest first (then by name), at most [limit]. */
    fun compute(family: Family, marriages: List<MarriageDate>, today: LocalDate, withinDays: Int = 366, limit: Int = 500): List<UpcomingDate> {
        val out = ArrayList<UpcomingDate>()
        fun add(kind: UpcomingKind, p: Person, other: Person?, month: Int, day: Int, startYear: Int?) {
            if (month !in 1..12 || day !in 1..31) return
            val on = runCatching { nextOccurrence(month, day, today) }.getOrNull() ?: return
            val away = java.time.temporal.ChronoUnit.DAYS.between(today, on).toInt()
            if (away > withinDays) return
            out += UpcomingDate(kind, p, other, on, away, startYear?.let { on.year - it }?.takeIf { it >= 0 })
        }
        for (p in family.people) {
            val bm = p.birthMonth
            val bd = p.birthDay
            if (p.born != null && bm != null && bd != null && Share.mayBeAlive(p, today.year)) {
                add(UpcomingKind.Birthday, p, null, bm, bd, p.born)
            }
            val dm = p.deathMonth
            val dd = p.deathDay
            if (dm != null && dd != null) add(UpcomingKind.Remembrance, p, null, dm, dd, p.died)
        }
        for (m in marriages) {
            val month = m.date.month ?: continue
            val day = m.date.day ?: continue
            if (m.a !in family || m.b !in family || family.partnershipEnded(m.a, m.b)) continue
            val a = family.person(m.a)
            val b = family.person(m.b)
            // A couple where one has died is still remembered, but only while both are listed as living is it an anniversary to celebrate.
            if (!a.deathDate.isEmpty || !b.deathDate.isEmpty) continue
            add(UpcomingKind.Anniversary, a, b, month, day, m.date.year)
        }
        return out.sortedWith(compareBy({ it.daysAway }, { it.person.name })).take(limit)
    }

    /** "Today", "Tomorrow", "In 5 days", "In 3 weeks", "In 4 months". */
    fun describe(daysAway: Int): String = when {
        daysAway == 0 -> "Today"
        daysAway == 1 -> "Tomorrow"
        daysAway < 14 -> "In $daysAway days"
        daysAway < 60 -> "In ${daysAway / 7} weeks"
        else -> "In ${daysAway / 30} months"
    }
}
