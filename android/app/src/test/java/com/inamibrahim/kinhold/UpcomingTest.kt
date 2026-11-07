package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.CoupleInfo
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.MarriageDate
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Upcoming
import com.inamibrahim.kinhold.model.UpcomingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpcomingTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test
    fun theNextOccurrenceIsThisYearOrNext() {
        assertEquals(LocalDate.of(2026, 11, 7), Upcoming.nextOccurrence(11, 7, today)) // today counts
        assertEquals(LocalDate.of(2026, 12, 1), Upcoming.nextOccurrence(12, 1, today))
        assertEquals(LocalDate.of(2027, 3, 4), Upcoming.nextOccurrence(3, 4, today))
        assertEquals(LocalDate.of(2027, 1, 1), Upcoming.nextOccurrence(1, 1, LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun aLeapDayFallsOnTheTwentyEighthInOrdinaryYears() {
        assertEquals(LocalDate.of(2027, 2, 28), Upcoming.nextOccurrence(2, 29, today))
        assertEquals(LocalDate.of(2028, 2, 29), Upcoming.nextOccurrence(2, 29, LocalDate.of(2027, 3, 1)))
    }

    private val family = Family.of(
        listOf(
            Person(1, "Ayesha", "Khan", born = 1990, birthMonth = 11, birthDay = 9),            // birthday in 2 days, turning 36
            Person(2, "Old", "Man", born = 1900, birthMonth = 11, birthDay = 8),                // too old to be assumed alive: no birthday
            Person(3, "Passed", "Away", born = 1940, birthMonth = 11, birthDay = 10, died = 2010, deathMonth = 11, deathDay = 12),
            Person(4, "No", "Day", born = 1990, birthMonth = 11),                               // no day: nothing to list
            Person(5, "Bilal", "Khan", born = 2000, birthMonth = 11, birthDay = 7),             // today
            Person(6, "Hina", "Khan", born = 1995),
        ),
        emptyList(), listOf(1 to 5, 5 to 6),
        mapOf((5 to 6) to CoupleInfo(null, true)),
    )

    @Test
    fun birthdaysAreForPeopleWhoMayBeAliveAndHaveADayAndAMonth() {
        val list = Upcoming.compute(family, emptyList(), today)
        val birthdays = list.filter { it.kind == UpcomingKind.Birthday }
        assertEquals(listOf("Bilal Khan", "Ayesha Khan"), birthdays.map { it.person.name })
        assertEquals(listOf(0, 2), birthdays.map { it.daysAway })
        assertEquals(listOf(26, 36), birthdays.map { it.years })
    }

    @Test
    fun adayOfDeathIsRememberedEachYear() {
        val r = Upcoming.compute(family, emptyList(), today).single { it.kind == UpcomingKind.Remembrance }
        assertEquals("Passed Away", r.person.name)
        assertEquals(5, r.daysAway)
        assertEquals(16, r.years) // 2026 - 2010
    }

    @Test
    fun anniversariesNeedADayAMonthAndACoupleStillTogether() {
        val marriages = listOf(
            MarriageDate(1, 5, PartialDate(2015, 11, 8)),  // together: tomorrow, 11 years
            MarriageDate(5, 6, PartialDate(2020, 11, 9)),  // ended by divorce: skipped
            MarriageDate(1, 5, PartialDate(2015, 11, null)), // no day: cannot be listed (never built, but guard)
        ).filter { it.date.day != null }
        val list = Upcoming.compute(family, marriages, today).filter { it.kind == UpcomingKind.Anniversary }
        assertEquals(1, list.size)
        assertEquals(1, list[0].daysAway)
        assertEquals(11, list[0].years)
        assertEquals("Bilal Khan", list[0].other!!.name)
    }

    @Test
    fun theListIsSoonestFirstAndBounded() {
        val list = Upcoming.compute(family, emptyList(), today, withinDays = 3)
        assertTrue(list.all { it.daysAway <= 3 })
        assertEquals(listOf(0, 2), list.map { it.daysAway })
        assertEquals(1, Upcoming.compute(family, emptyList(), today, limit = 1).size)
    }

    @Test
    fun describingTheDistance() {
        assertEquals("Today", Upcoming.describe(0))
        assertEquals("Tomorrow", Upcoming.describe(1))
        assertEquals("In 6 days", Upcoming.describe(6))
        assertEquals("In 3 weeks", Upcoming.describe(21))
        assertEquals("In 4 months", Upcoming.describe(125))
    }
}
