package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.HijriDates
import com.inamibrahim.kinhold.model.PartialDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HijriDatesTest {
    @Test
    fun knownDatesConvertBothWays() {
        // 1 Muharram 1445 AH was 19 July 2023, and 1 Ramadan 1445 AH was 11 March 2024 (Umm al-Qura).
        assertEquals(Triple(1445, 1, 1), HijriDates.fromGregorian(LocalDate.of(2023, 7, 19)))
        assertEquals(Triple(1445, 9, 1), HijriDates.fromGregorian(LocalDate.of(2024, 3, 11)))
        assertEquals(LocalDate.of(2023, 7, 19), HijriDates.toGregorian(1445, 1, 1))
        assertEquals(LocalDate.of(2024, 3, 11), HijriDates.toGregorian(1445, 9, 1))
    }

    @Test
    fun everyDayRoundTripsAcrossManyYears() {
        var d = LocalDate.of(1950, 1, 1)
        while (d.isBefore(LocalDate.of(2030, 1, 1))) {
            val (y, m, day) = HijriDates.fromGregorian(d)!!
            assertEquals(d, HijriDates.toGregorian(y, m, day))
            d = d.plusDays(11)
        }
    }

    @Test
    fun aStoredFullDateIsDescribedAndPartialOnesAreNot() {
        assertEquals("1 Muharram 1445 AH", HijriDates.describe(PartialDate(2023, 7, 19)))
        assertEquals("1 Ramadan 1445 AH", HijriDates.describe(PartialDate(2024, 3, 11)))
        assertNull(HijriDates.describe(PartialDate(2024, 3, null)))
        assertNull(HijriDates.describe(PartialDate(2024, null, null)))
        assertNull(HijriDates.describe(PartialDate.None))
    }

    @Test
    fun impossibleOrOutOfRangeDatesGiveNothing() {
        assertNull(HijriDates.toGregorian(1445, 13, 1))
        assertNull(HijriDates.toGregorian(1445, 1, 31))
        assertNull(HijriDates.toGregorian(100, 1, 1))
        assertNull(HijriDates.fromGregorian(LocalDate.of(1800, 1, 1)))
        assertEquals(PartialDate(2023, 7, 19), HijriDates.partialFromHijri(1445, 1, 1))
    }
}
