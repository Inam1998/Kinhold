package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.DateExtra
import com.inamibrahim.kinhold.model.DateQualifier
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.Merge
import com.inamibrahim.kinhold.model.MergeField
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.Timeline
import com.inamibrahim.kinhold.model.TreeDetails
import com.inamibrahim.kinhold.model.ageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class DateQualifierTest {
    private val about = DateQualifier.About
    private val before = DateQualifier.Before
    private val after = DateQualifier.After
    private val between = DateQualifier.Between

    // ---------------------------------------------------------------- the date itself

    @Test
    fun aQualifiedDateIsWrittenInWordsAndInGedcomStyle() {
        assertEquals("1850", PartialDate(1850).text())
        assertEquals("about 1850", PartialDate(1850, null, null, about).text())
        assertEquals("before Mar 1850", PartialDate(1850, 3, null, before).text())
        assertEquals("after 4 Mar 1850", PartialDate(1850, 3, 4, after).text())
        assertEquals("between 1850 and 1855", PartialDate(1850, null, null, between, 1855).text())
        assertEquals("ABT 1850", PartialDate(1850, null, null, about).gedcom())
        assertEquals("BEF MAR 1850", PartialDate(1850, 3, null, before).gedcom())
        assertEquals("AFT 4 MAR 1850", PartialDate(1850, 3, 4, after).gedcom())
        assertEquals("BET 1850 AND 1855", PartialDate(1850, null, null, between, 1855).gedcom())
        assertEquals("", PartialDate(null, null, null, about).text())
    }

    @Test
    fun theShortFormForACardUsesCircaAndRanges() {
        assertEquals("1850", PartialDate(1850, 3, 4).yearText())
        assertEquals("c. 1850", PartialDate(1850, null, null, about).yearText())
        assertEquals("bef. 1850", PartialDate(1850, null, null, before).yearText())
        assertEquals("aft. 1850", PartialDate(1850, null, null, after).yearText())
        assertEquals("1850–1855", PartialDate(1850, null, null, between, 1855).yearText())
        val p = Person(1, "A", "B", Sex.Male, 1850, 1920, birthQualifier = about, deathQualifier = before)
        assertEquals("c. 1850 – bef. 1920", p.years())
        assertEquals("1850 – 1920", Person(2, "A", "B", Sex.Male, 1850, 1920).years())
    }

    @Test
    fun anEndYearGoesOnlyWithBetweenAndNotBeforeTheStart() {
        assertTrue(PartialDate(1850, null, null, between, 1850).isValid)
        assertFalse(PartialDate(1850, null, null, between, 1849).isValid)
        assertFalse(PartialDate(1850, null, null, about, 1855).isValid)
        assertFalse(PartialDate(null, null, null, between, 1855).isValid)
        assertEquals(PartialDate(null, null, null), PartialDate(null, null, null, about).normalized())
        assertEquals(PartialDate(1850, null, null, about), PartialDate(1850, null, 5, about).normalized())
    }

    @Test
    fun theFormTurnsItsBoxesIntoAQualifiedDate() {
        assertEquals(PartialDate(1850, null, null, about), PartialDate.parse("", "", "1850", about))
        assertEquals(PartialDate(1850, null, null, between, 1855), PartialDate.parse("", "", "1850", between, "1855"))
        assertNull(PartialDate.parse("", "", "1850", between, ""))      // "between" needs both years
        assertNull(PartialDate.parse("", "", "1850", between, "1840"))  // and an end that is not earlier
        assertNull(PartialDate.parse("", "", "1850", between, "x"))
        assertEquals(PartialDate.None, PartialDate.parse("", "", "", about)) // nothing to qualify
        assertEquals("", DateExtra.encode(DateQualifier.Exact, null))
        assertEquals("about", DateExtra.encode(about, null))
        assertEquals("between:1855", DateExtra.encode(between, 1855))
        assertEquals(between, DateExtra.qualifier("between:1855"))
        assertEquals("1855", DateExtra.endYear("between:1855"))
        assertEquals(DateQualifier.Exact, DateExtra.qualifier(""))
        assertEquals("", DateExtra.endYear("about"))
    }

    @Test
    fun anApproximateDateNeverBlocksSavingAndNeverGivesAnExactAge() {
        val saveable = PersonDraft(given = "A", born = 1850, died = 1849, birthQualifier = about)
        assertTrue(saveable.datesMakeSense) // about 1850 and died 1849 may simply be inexact
        assertFalse(PersonDraft(given = "A", born = 1850, died = 1849).datesMakeSense)
        val p = Person(1, "A", "B", Sex.Male, 1950, 2010, birthMonth = 3, birthDay = 10, deathMonth = 5, deathDay = 1, birthQualifier = about)
        assertEquals("About 60 when they died", p.ageText(LocalDate.of(2026, 1, 1)))
        val exact = Person(2, "A", "B", Sex.Male, 1950, 2010, birthMonth = 3, birthDay = 10, deathMonth = 5, deathDay = 1)
        assertEquals("Aged 60 when they died", exact.ageText(LocalDate.of(2026, 1, 1)))
        assertEquals("About 60", Timeline.ageAt(PartialDate(1950, 3, 10, about), PartialDate(2010, 5, 1)))
        assertEquals("Age 60", Timeline.ageAt(PartialDate(1950, 3, 10), PartialDate(2010, 5, 1)))
    }

    // ---------------------------------------------------------------- backup file

    private fun qualifiedTree(): Triple<Family, TreeDetails, Event> {
        val a = Person(1, "Ali", "Khan", Sex.Male, 1850, 1920, birthQualifier = about, deathQualifier = between, deathEndYear = 1925, uid = "11111111-1111-4111-8111-111111111111")
        val b = Person(2, "Sana", "Khan", Sex.Female, 1855, birthQualifier = before, uid = "22222222-2222-4222-8222-222222222222")
        val baptism = Event(EventKind.Baptism, date = PartialDate(1851, null, null, after), place = "Lahore")
        val marriage = Event(EventKind.Marriage, date = PartialDate(1875, null, null, between, 1877))
        val family = Family.of(listOf(a, b), emptyList(), listOf(1 to 2))
        val details = TreeDetails(events = mapOf(1 to listOf(baptism)), couples = mapOf((1 to 2) to listOf(marriage)))
        return Triple(family, details, baptism)
    }

    @Test
    fun qualifiedDatesSurviveABackup() {
        val (family, details, baptism) = qualifiedTree()
        val back = BackupCodec.decode(BackupCodec.encode(family, "2026-11-08", details = details))
        val ali = back.people.first { it.id == 1 }
        assertEquals(about, ali.birthQualifier)
        assertEquals(between, ali.deathQualifier)
        assertEquals(1925, ali.deathEndYear)
        assertEquals(before, back.people.first { it.id == 2 }.birthQualifier)
        assertEquals(listOf(baptism), back.details.events.getValue(1))
        assertEquals(PartialDate(1875, null, null, between, 1877), back.details.couples.getValue(1 to 2).single().date)
    }

    @Test
    fun anOlderBackupWithNoQualifiersReadsAsExactDates() {
        val old = """{"format":"kinhold-backup","version":8,"people":[{"id":1,"given":"A","surname":"B","sex":0,"born":1850,"died":1920,"birthPlace":"","deathPlace":"","notes":"","events":[{"kind":"baptism","year":1851}]}],"parentLinks":[],"partnerships":[]}"""
        val back = BackupCodec.decode(old)
        val p = back.people.single()
        assertEquals(DateQualifier.Exact, p.birthQualifier)
        assertEquals(DateQualifier.Exact, p.deathQualifier)
        assertNull(p.birthEndYear)
        assertEquals(PartialDate(1851), back.details.events.getValue(1).single().date)
    }

    private fun refused(json: String) {
        try {
            BackupCodec.decode(json)
            fail("Expected the backup to be refused: $json")
        } catch (e: BackupException) {
            assertEquals("This backup is damaged and cannot be read.", e.message)
        }
    }

    @Test
    fun aBackupWithAnImpossibleQualifierIsRefusedAsDamaged() {
        fun person(extra: String) = """{"format":"kinhold-backup","version":9,"people":[{"id":1,"given":"A","born":1850,$extra"birthPlace":"","deathPlace":"","notes":""}],"parentLinks":[],"partnerships":[]}"""
        refused(person(""""birthQual":"sometime","""))
        refused(person(""""birthQual":"about","birthEnd":1855,"""))   // an end year only goes with "between"
        refused(person(""""birthQual":"between","birthEnd":1840,""")) // and cannot be before the start
        val event = """{"format":"kinhold-backup","version":9,"people":[{"id":1,"given":"A","events":[{"kind":"baptism","year":1851,"qual":"between","endYear":1850}]}],"parentLinks":[],"partnerships":[]}"""
        refused(event)
    }

    // ---------------------------------------------------------------- GEDCOM

    @Test
    fun qualifiedDatesGoOutAndComeBackThroughGedcom() {
        val (family, details, baptism) = qualifiedTree()
        val text = Gedcom.write(family, details, "1.0", "8 NOV 2026")
        assertTrue(text.contains("ABT 1850") || text.contains("DATE ABT"))
        assertTrue(text.contains("BEF 1855"))
        assertTrue(text.contains("BET 1920 AND 1925") || text.contains("BET 1875 AND 1877"))
        val back = Gedcom.read(text.toByteArray())
        val ali = back.data.people.first { it.given == "Ali" }
        assertEquals(about, ali.birthQualifier)
        assertEquals(between, ali.deathQualifier)
        assertEquals(1925, ali.deathEndYear)
        assertEquals(baptism.date, back.data.details.events.getValue(ali.id).single { it.kind == EventKind.Baptism }.date)
        assertEquals(0, back.report.datesSimplified)
    }

    // ---------------------------------------------------------------- merging

    private val uid = "11111111-1111-4111-8111-111111111111"
    private fun mergeOf(mine: Person, his: Person) =
        Merge(Family.of(listOf(mine), emptyList(), emptyList()), TreeDetails.Empty, BackupData(listOf(his), emptyList(), emptyList()))
            .fieldChanges(mapOf(his.id to mine.id)).single { it.part == MergeField.Born }

    @Test
    fun anExactDateFillsAnApproximateOneButNotTheOtherWayRound() {
        val approx = Person(1, "A", "B", born = 1900, birthQualifier = about, uid = uid)
        val exact = Person(10, "A", "B", born = 1900, birthMonth = 3, birthDay = 4, uid = uid)
        assertTrue(mergeOf(approx, exact).isFill)  // more exact, same year: offered as a fill
        val mineExact = Person(1, "A", "B", born = 1900, birthMonth = 3, birthDay = 4, uid = uid)
        val hisApprox = Person(10, "A", "B", born = 1900, birthQualifier = about, uid = uid)
        assertFalse(mergeOf(mineExact, hisApprox).isFill) // an "about" never quietly replaces an exact date
        val a1 = Person(1, "A", "B", born = 1900, birthQualifier = about, uid = uid)
        val a2 = Person(10, "A", "B", born = 1900, birthQualifier = before, uid = uid)
        assertFalse(mergeOf(a1, a2).isFill)          // two different doubts are a conflict
    }
}
