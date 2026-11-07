package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.DateQualifier
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.LinkKind
import com.inamibrahim.kinhold.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reads docs/test-family-ancestry-style.ged, an invented file written to look like what Ancestry and MyHeritage produce
 * (mixed-case months, ranges, "about" dates, a second married name, a suffix, a note record, an adoption, a divorce,
 * a link to a missing person, an Urdu name). It is not a real export.
 */
class GedcomAncestryStyleTest {
    private val result = Gedcom.read(
        (File("../docs/test-family-ancestry-style.ged").takeIf { it.exists() } ?: File("../../docs/test-family-ancestry-style.ged")).readBytes(),
    )
    private val people = result.data.people
    private fun named(given: String) = people.first { it.given == given }

    @Test
    fun everyoneInTheFileIsKeptWithTheirDates() {
        assertEquals(10, people.size)
        val abdul = named("Abdul Rehman")
        assertEquals(Triple(1921, 3, 14), Triple(abdul.born, abdul.birthMonth, abdul.birthDay)) // "14 mar 1921", lower case
        assertEquals(1994, abdul.died)
        assertEquals("Multan, Punjab, Pakistan", abdul.birthPlace)
        assertEquals(1925, named("Zainab").born)  // ABT 1925
        assertEquals(1928, named("Hajra").born)   // BET 1928 AND 1931
        assertEquals(1950, named("Nasreen").born) // EST 1950
    }

    @Test
    fun aboutBeforeAndBetweenDatesKeepTheirQualifier() {
        assertEquals(DateQualifier.About, named("Zainab").birthQualifier)
        assertEquals(DateQualifier.Before, named("Zainab").deathQualifier)
        assertEquals(1990, named("Zainab").died)
        val hajra = named("Hajra")
        assertEquals(DateQualifier.Between, hajra.birthQualifier)
        assertEquals(1931, hajra.birthEndYear)
        assertEquals(DateQualifier.About, named("Nasreen").birthQualifier)
        assertEquals("between 1928 and 1931", hajra.birthDate.text())
        assertEquals("before 1990", named("Zainab").deathDate.text())
    }

    @Test
    fun aSuffixIsKeptWithTheSurname() {
        assertEquals("Qureshi Jr.", named("Mohammad Yusuf").surname)
    }

    @Test
    fun aSecondNameIsKeptAsAFactAndNotLost() {
        val zainab = named("Zainab")
        val fields = result.data.details.fields.getValue(zainab.id)
        assertTrue(fields.any { it.label == "Married name" && it.value == "Zainab Qureshi" })
    }

    @Test
    fun aPersonMarkedDeadWithNoDateIsNotTakenForLiving() {
        val p = people.first { it.given.isEmpty() && it.surname == "Qureshi" }
        assertTrue(result.data.details.fields.getValue(p.id).any { it.label == "Deceased" })
    }

    @Test
    fun noteRecordsAndContinuationLinesAreJoined() {
        assertEquals("Moved to Lahore after partition. He kept a diary of the journey, which the family still has.\nSecond line of the note.", named("Abdul Rehman").notes)
        assertEquals("A note kept as its own record, joined onto the person.\nAnd a second line.", named("محمد").notes)
    }

    @Test
    fun adoptionsMarriagesDivorcesAndEventsAreKept() {
        val sana = named("Sana").id
        val abdul = named("Abdul Rehman").id
        assertEquals(LinkKind.Adopted, result.data.linkKinds[sana to abdul])
        val kinds = result.data.details.events.getValue(abdul).map { it.kind }
        assertTrue(EventKind.Burial in kinds && EventKind.Occupation in kinds && EventKind.Residence in kinds)
        val hajra = named("Hajra").id
        val couple = result.data.details.couples.getValue(minOf(abdul, hajra) to maxOf(abdul, hajra)).map { it.kind }
        assertEquals(listOf(EventKind.Marriage, EventKind.Divorce), couple)
    }

    @Test
    fun theReportSaysWhatWasSimplifiedOrLeftOut() {
        val r = result.report
        assertEquals(10, r.individuals)
        assertEquals(4, r.families)
        assertEquals(1, r.datesSimplified) // only the phrase after INT is lost; ABT, BEF, BET, EST and FROM-TO are kept as qualified dates
        assertEquals(1, r.ignoredMedia)
        assertEquals(2, r.skippedLinks) // @I999@ and @I998@ are not in the file
        assertTrue(result.data.parentLinks.none { it.first == named("Unknown").id })
    }

    @Test
    fun theSexOfAnUnknownPersonIsNotMadeUp() {
        assertEquals(Sex.Unknown, named("Unknown").sex)
    }
}
