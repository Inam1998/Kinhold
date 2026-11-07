package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.CoupleInfo
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventJson
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.Limits
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.Source
import com.inamibrahim.kinhold.model.TreeDetails
import com.inamibrahim.kinhold.model.inDateOrder
import com.inamibrahim.kinhold.model.relativesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EventsTest {
    // ---------------------------------------------------------------- dates

    @Test
    fun aDateKeepsOnlyWhatIsKnownAndPrintsIt() {
        assertEquals("12 Mar 1850", PartialDate(1850, 3, 12).text())
        assertEquals("Mar 1850", PartialDate(1850, 3).text())
        assertEquals("1850", PartialDate(1850).text())
        assertEquals("", PartialDate.None.text())
        assertEquals("12 MAR 1850", PartialDate(1850, 3, 12).gedcom())
    }

    @Test
    fun impossibleDatesAreRefused() {
        assertTrue(PartialDate(1850, 3, 12).isValid)
        assertTrue(PartialDate(1852, 2, 29).isValid)
        assertFalse(PartialDate(1850, 2, 30).isValid)
        assertFalse(PartialDate(1850, 13).isValid)
        assertFalse(PartialDate(1850, 0).isValid)
        assertFalse(PartialDate(null, 3).isValid)      // a month needs a year
        assertFalse(PartialDate(1850, null, 5).isValid) // a day needs a month
        assertNull(PartialDate.parse("", "13", "1850"))
        assertNull(PartialDate.parse("x", "", "1850"))
        assertEquals(PartialDate(1850, 3, null), PartialDate.parse("", "3", "1850"))
        assertEquals(PartialDate.None, PartialDate.parse("", "", ""))
    }

    @Test
    fun datesOrderInTimeAndOnlyKnownPartsAreComparedForBefore() {
        assertTrue(PartialDate(1850, 3, 12).isBefore(PartialDate(1850, 3, 13)))
        assertTrue(PartialDate(1849).isBefore(PartialDate(1850, 1, 1)))
        assertFalse(PartialDate(1850).isBefore(PartialDate(1850, 5, 5))) // cannot tell
        assertFalse(PartialDate(1851, 1).isBefore(PartialDate(1850, 12)))
    }

    @Test
    fun eventsAreSortedByDateWithUndatedLastAndAreBounded() {
        val events = listOf(
            Event(EventKind.Burial, date = PartialDate(1920)),
            Event(EventKind.Custom, "undated"),
            Event(EventKind.Baptism, date = PartialDate(1850, 3)),
            Event(EventKind.Residence, date = PartialDate(1850, 3, 2)),
        )
        assertEquals(listOf("Baptism", "Residence", "Burial", "undated"), events.inDateOrder().map { it.title() })
        assertEquals(Limits.EVENTS, List(Limits.EVENTS + 20) { Event(EventKind.Residence) }.inDateOrder().size)
    }

    @Test
    fun longTextIsCutAndBlankSourcesDropped() {
        val e = Event(
            EventKind.Custom, "x".repeat(1000), PartialDate(1850, 2, 30), "p".repeat(2000), "n".repeat(20000),
            listOf(Source("t".repeat(900)), Source(), Source("ok", "p. 3")),
        ).clamped()
        assertEquals(Limits.DETAIL, e.detail.length)
        assertEquals(Limits.PLACE, e.place.length)
        assertEquals(Limits.TEXT, e.note.length)
        assertEquals(PartialDate.None, e.date) // 30 Feb cannot be kept
        assertEquals(2, e.sources.size)
        assertEquals(Limits.SOURCE_TITLE, e.sources[0].title.length)
    }

    // ---------------------------------------------------------------- couples

    private fun person(id: Int, born: Int? = null) = Person(id, "P$id", "", if (id % 2 == 0) Sex.Female else Sex.Male, born = born)

    @Test
    fun spousesAreOrderedByMarriageDateThenBirthYearThenId() {
        val people = (1..5).map { person(it, born = if (it == 3) 1900 else if (it == 4) 1890 else null) }
        val family = Family.of(
            people, emptyList(), listOf(1 to 2, 1 to 3, 1 to 4, 1 to 5),
            mapOf(
                (1 to 2) to CoupleInfo(PartialDate(1930).sortKey, false),
                (1 to 5) to CoupleInfo(PartialDate(1920, 6).sortKey, false),
            ),
        )
        // 5 married in 1920, 2 in 1930, then 4 (born 1890) and 3 (born 1900) by birth year.
        assertEquals(listOf(5, 2, 4, 3), family.spousesInOrder(1).map { it.id })
    }

    @Test
    fun withNoMarriageDatesTheOldOrderIsKept() {
        val family = Family.of(listOf(person(1), person(2, 1900), person(3, 1890), person(4)), emptyList(), listOf(1 to 2, 1 to 3, 1 to 4))
        assertEquals(listOf(3, 2, 4), family.spousesInOrder(1).map { it.id })
    }

    @Test
    fun aDivorceStopsAFormerPartnerShowingAsAStepParent() {
        // Dad 1 and Mum 2 have Me 3. Dad's former wife 4 (divorced) and current wife 5 each have a child (6 and 7).
        val people = (1..7).map { person(it) }
        val links = listOf(3 to 1, 3 to 2, 6 to 4, 7 to 5)
        val partnerships = listOf(1 to 2, 1 to 4, 1 to 5)
        val before = Family.of(people, links, partnerships)
        assertEquals(setOf(4, 5), before.relativesOf(3).filter { it.label == "Stepmother" || it.label == "Stepfather" }.map { it.person.id }.toSet())

        val divorced = Family.of(people, links, partnerships, mapOf((1 to 4) to CoupleInfo(null, true)))
        val labels = divorced.relativesOf(3).associate { it.person.id to it.label }
        assertEquals("Stepfather", labels[5])
        assertFalse(labels.containsKey(4))
        assertFalse(labels.containsKey(6)) // the former wife's child is no longer a step-sibling
        assertEquals("Stepbrother", labels[7])
        assertTrue(divorced.partnershipEnded(1, 4))
        assertEquals(listOf(2, 5), divorced.currentSpousesOf(1).map { it.id })
    }

    // ---------------------------------------------------------------- backup

    private val sampleFamily = Family.of(
        listOf(
            Person(1, "Adam", "Khan", Sex.Male, born = 1850, died = 1920, birthMonth = 3, birthDay = 12, deathMonth = 7),
            Person(2, "Eve", "Khan", Sex.Female, born = 1855),
            Person(3, "Kid", "Khan"),
        ),
        listOf(3 to 1, 3 to 2),
        listOf(1 to 2),
    )

    private val sampleDetails = TreeDetails(
        events = mapOf(
            1 to listOf(
                Event(EventKind.Occupation, "Farmer", PartialDate(1880), "Lahore", "Own land", listOf(Source("Census 1881", "p. 4", "Name misspelt"))),
                Event(EventKind.Custom, "Pilgrimage", PartialDate(1900, 5), "Mecca"),
            ),
        ),
        sources = mapOf(2 to listOf(Source("Family Bible", "inside cover", ""))),
        couples = mapOf((1 to 2) to listOf(Event(EventKind.Marriage, "", PartialDate(1875, 6, 1), "Lahore"))),
    )

    @Test
    fun eventsSourcesAndFullDatesSurviveABackup() {
        val data = BackupCodec.decode(BackupCodec.encode(sampleFamily, "", emptyMap(), sampleDetails))
        val adam = data.people.first { it.id == 1 }
        assertEquals(PartialDate(1850, 3, 12), adam.birthDate)
        assertEquals(PartialDate(1920, 7, null), adam.deathDate)
        assertEquals(sampleDetails.events.getValue(1), data.details.events.getValue(1))
        assertEquals(sampleDetails.sources, data.details.sources)
        assertEquals(sampleDetails.couples, data.details.couples)
        assertEquals(6, BackupCodec.VERSION)
    }

    @Test
    fun backupsOfVersionOneAndTwoStillOpen() {
        val v2 = """{"format":"kinhold-backup","version":2,"people":[{"id":1,"given":"A","born":1900,"photo":null},{"id":2,"given":"B"}],"parentLinks":[],"partnerships":[[1,2]]}"""
        val data = BackupCodec.decode(v2)
        assertEquals(2, data.people.size)
        assertEquals(1900, data.people[0].born)
        assertNull(data.people[0].birthMonth)
        assertTrue(data.details.events.isEmpty() && data.details.couples.isEmpty())
        BackupCodec.decode(v2.replace("\"version\":2", "\"version\":1"))
    }

    private fun refused(text: String): BackupException {
        try {
            BackupCodec.decode(text)
        } catch (e: BackupException) {
            return e
        }
        fail("Expected the file to be refused")
        throw AssertionError()
    }

    private fun file(personExtra: String = "", couples: String = "[]", partnerships: String = "[[1,2]]") =
        """{"format":"kinhold-backup","version":3,"people":[{"id":1,"given":"A"$personExtra},{"id":2,"given":"B"}],"parentLinks":[],"partnerships":$partnerships,"coupleEvents":$couples}"""

    @Test
    fun damagedEventsAreRefusedWithAPlainMessage() {
        val message = "This backup is damaged and cannot be read."
        assertEquals(message, refused(file(""","events":[{"kind":"burial","year":1900,"month":13}]""")).message)
        assertEquals(message, refused(file(""","events":[{"kind":"burial","month":3}]""")).message)
        assertEquals(message, refused(file(""","events":[{"kind":"burial","year":1900,"month":2,"day":30}]""")).message)
        assertEquals(message, refused(file(""","events":"not a list" """)).message)
        assertEquals(message, refused(file(""","birthMonth":3""")).message)
        assertEquals(message, refused(file(""","born":1900,"birthMonth":14""")).message)
        assertEquals(message, refused(file("", """[{"a":1,"b":2,"events":[{"kind":"marriage","year":99999}]}]""")).message)
        // Events for a couple that is not in the file mean the file was altered or cut short.
        assertEquals(message, refused(file("", """[{"a":1,"b":2,"events":[{"kind":"marriage"}]}]""", partnerships = "[]")).message)
        val many = (1..Limits.EVENTS + 1).joinToString(",", prefix = "[", postfix = "]") { """{"kind":"residence"}""" }
        assertEquals(message, refused(file(""","events":$many""")).message)
    }

    @Test
    fun unknownKindsAndFieldsAreTolerated() {
        val data = BackupCodec.decode(file(""","events":[{"kind":"teleportation","year":1900,"colour":"red"}],"hair":"brown""""))
        val e = data.details.events.getValue(1).single()
        assertEquals(EventKind.Custom, e.kind)
        assertEquals("teleportation", e.detail)
        assertEquals(PartialDate(1900), e.date)
    }

    @Test
    fun aCutOffBackupWithEventsIsNeverAccepted() {
        val text = BackupCodec.encode(sampleFamily, "", emptyMap(), sampleDetails)
        for (cut in 0 until text.length step 13) {
            try {
                BackupCodec.decode(text.substring(0, cut))
                fail("A cut backup was accepted at $cut")
            } catch (e: BackupException) {
                // Refused, as it should be.
            }
        }
    }

    @Test
    fun theFormKeepsADetailsDraftAcrossARotation() {
        val details = PersonDetails(
            sampleDetails.events.getValue(1), listOf(Source("Bible"), Source()), mapOf(2 to sampleDetails.couples.getValue(1 to 2)),
        )
        val back = EventJson.decodeDetails(EventJson.encodeDetails(details), editing = true)
        assertEquals(details, back) // blank sources are kept while editing
        assertEquals(1, EventJson.decodeDetails(EventJson.encodeDetails(details)).sources.size)
        assertTrue(EventJson.decodeDetails("garbage").isEmpty)
    }

    // ---------------------------------------------------------------- GEDCOM

    @Test
    fun eventsSourcesAndFullDatesSurviveGedcom() {
        val text = Gedcom.write(sampleFamily, sampleDetails, "1.0", "7 NOV 2026")
        assertTrue(text.contains("2 DATE 12 MAR 1850"))
        assertTrue(text.contains("1 OCCU Farmer"))
        assertTrue(text.contains("1 MARR"))
        assertTrue(text.lines().all { it.length <= 256 })
        val result = Gedcom.read(text.toByteArray(Charsets.UTF_8))
        val people = result.data.people
        val adam = people.first { it.given == "Adam" }
        assertEquals(PartialDate(1850, 3, 12), adam.birthDate)
        assertEquals(PartialDate(1920, 7, null), adam.deathDate)
        val events = result.data.details.events.getValue(adam.id)
        assertEquals(sampleDetails.events.getValue(1), events)
        val eve = people.first { it.given == "Eve" }
        assertEquals(Source("Family Bible", "inside cover", ""), result.data.details.sources.getValue(eve.id).single())
        val marriage = result.data.details.couples.values.single().single()
        assertEquals(Event(EventKind.Marriage, "", PartialDate(1875, 6, 1), "Lahore"), marriage)
        val r = result.report
        assertEquals(3, r.eventsKept)
        assertEquals(2, r.sourcesKept)
        assertEquals(0, r.ignoredEvents + r.ignoredSources + r.datesSimplified)
    }

    @Test
    fun everyEventKindSurvivesGedcomWithItsDetail() {
        val kinds = EventKind.entries
        val events = kinds.filter { !it.forCouple }.map { Event(it, if (it == EventKind.Custom) "Named" else "Some detail", PartialDate(1900, 1, 2), "Here", "A note\nover two lines") }
        val family = Family.of(listOf(Person(1, "A", "B")), emptyList(), emptyList())
        val text = Gedcom.write(family, TreeDetails(events = mapOf(1 to events)), "1.0", "7 NOV 2026")
        val back = Gedcom.read(text.toByteArray()).data.details.events.getValue(1)
        assertEquals(events, back)
    }

    @Test
    fun anAncestryStyleFileGivesEventsSourcesAndKeepsWhatItCannot() {
        val text = """
            0 HEAD
            1 CHAR UTF-8
            0 @I1@ INDI
            1 NAME John /Smith/
            1 BIRT
            2 DATE 3 FEB 1801
            2 SOUR @S1@
            3 PAGE Entry 77
            1 BAPM
            2 DATE ABT MAR 1801
            2 PLAC St Mary's
            2 NOTE Baptised at home
            1 EVEN
            2 TYPE Military service
            2 DATE 1820
            1 EVEN Something odd
            2 TYPE Local honour
            1 BARM
            2 DATE 1814
            1 SSN 123
            1 OCCU Smith
            2 SOUR Inline source text
            3 PAGE p. 2
            0 @I2@ INDI
            1 NAME Jane /Doe/
            1 FAMS @F1@
            0 @F1@ FAM
            1 HUSB @I1@
            1 WIFE @I2@
            1 MARR
            2 DATE 4 JUL 1825
            2 PLAC Leeds
            1 DIV
            2 DATE 1840
            1 SOUR @S1@
            0 @S1@ SOUR
            1 TITL Parish register
            1 AUTH Rev. Jones
            0 TRLR
        """.trimIndent()
        val r = Gedcom.read(text.toByteArray())
        val john = r.data.people.first { it.given == "John" }
        assertEquals(PartialDate(1801, 2, 3), john.birthDate)
        val events = r.data.details.events.getValue(john.id)
        assertEquals(listOf("Baptism", "Military service", "Local honour", "Bar Mitzvah", "Occupation: Smith"), events.map { it.title() })
        assertEquals(PartialDate(1801, 3), events[0].date)
        assertEquals("St Mary's", events[0].place)
        assertEquals("Baptised at home", events[0].note)
        assertEquals(EventKind.Military, events[1].kind)
        assertEquals(Source("Inline source text", "p. 2", ""), events[4].sources.single())
        assertEquals(
            Source("Parish register", "Entry 77", "About the birth. Author: Rev. Jones"),
            r.data.details.sources.getValue(john.id).single(),
        )
        val couple = r.data.details.couples.values.single()
        assertEquals(listOf(EventKind.Marriage, EventKind.Divorce), couple.map { it.kind })
        assertEquals(PartialDate(1825, 7, 4), couple[0].date)
        // ABT MAR 1801 was simplified; the SSN line and the family-level citation could not be kept.
        assertEquals(1, r.report.datesSimplified)
        assertEquals(1, r.report.ignoredEvents)
        assertEquals(1, r.report.ignoredSources)
        assertEquals(7, r.report.eventsKept)
    }

    @Test
    fun eventsWithoutAPairOfParentsOrBeyondTheLimitAreCounted() {
        val many = (1..Limits.EVENTS + 5).joinToString("\n") { "1 RESI\n2 DATE ${1000 + it}" }
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME A /B/\n$many\n1 FAMS @F1@\n0 @F1@ FAM\n1 HUSB @I1@\n1 MARR\n2 DATE 1900\n0 TRLR"
        val r = Gedcom.read(text.toByteArray())
        assertEquals(Limits.EVENTS, r.data.details.events.getValue(1).size)
        assertEquals(5 + 1, r.report.ignoredEvents) // five over the limit, and a marriage with only one parent
        assertTrue(r.data.details.couples.isEmpty())
    }

    @Test
    fun dateStylesKeepTheDayAndMonthWhenTheyAreThere() {
        fun parse(s: String) = Gedcom.parseDate(s)
        assertEquals(PartialDate(1950, 1, 12) to false, parse("12 JAN 1950"))
        assertEquals(PartialDate(1950, 1, 5) to false, parse("5 jan 1950"))
        assertEquals(PartialDate(1950, 1, null) to false, parse("January 1950"))
        assertEquals(PartialDate(1950) to false, parse("1950"))
        assertEquals(PartialDate(1950) to false, parse("1950/51"))
        assertEquals(PartialDate(1950, 1, null) to true, parse("ABT JAN 1950"))
        assertEquals(PartialDate(1900) to true, parse("BET 1900 AND 1910"))
        assertEquals(PartialDate(1950, 2, null) to true, parse("30 FEB 1950")) // no such day: the month is kept
        assertEquals(PartialDate(1950) to true, parse("12 1950"))
        assertNull(parse("Unknown"))
        assertNull(parse("44 BC"))
        assertNull(parse(""))
    }
}
