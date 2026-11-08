package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.HijriDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahChronology

/** The facts the Hijri calendar grid relies on: where a month starts, how long it is, and the whole 1300 to 1600 range. */
class HijriCalendarTest {
    @Test
    fun ramadan1446StartedOnASaturdayInMarch2025() {
        val first = HijrahChronology.INSTANCE.date(1446, 9, 1)
        assertEquals(LocalDate.of(2025, 3, 1), LocalDate.from(first))
        assertEquals(DayOfWeek.SATURDAY, LocalDate.from(first).dayOfWeek)
    }

    @Test
    fun everyMonthInTheRangeHas29Or30DaysAndConvertsBothWays() {
        for (y in 1300..1600) for (m in 1..12) {
            val first = HijrahChronology.INSTANCE.date(y, m, 1)
            val len = first.lengthOfMonth()
            assertTrue("$y-$m has $len days", len == 29 || len == 30)
            val g = LocalDate.from(first)
            assertEquals(Triple(y, m, 1), HijriDates.fromGregorian(g))
            assertNotNull(HijriDates.toGregorian(y, m, len))
        }
    }

    @Test
    fun theGridHasAtMostSixRows() {
        for (y in 1300..1600) for (m in 1..12) {
            val first = HijrahChronology.INSTANCE.date(y, m, 1)
            val offset = LocalDate.from(first).dayOfWeek.value % 7
            val rows = (offset + first.lengthOfMonth() + 6) / 7
            assertTrue(rows in 4..6)
        }
    }
}
