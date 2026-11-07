package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.MatchIn
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.Timeline
import com.inamibrahim.kinhold.model.TimelineKind
import com.inamibrahim.kinhold.model.TreeSearch
import com.inamibrahim.kinhold.model.TreeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTimelineStatsTest {
    private fun p(id: Int, given: String, surname: String = "", sex: Sex = Sex.Unknown, born: Int? = null, died: Int? = null, birthPlace: String = "", deathPlace: String = "", notes: String = "", address: String = "") =
        Person(id, given, surname, sex, born, died, birthPlace, deathPlace, notes, address = address)

    // ---------------------------------------------------------------- search

    private val people = listOf(
        p(1, "Zoë", "Khan", birthPlace = "Lahore", notes = "Worked at the railway station for forty years"),
        p(2, "Ahmed", "Lahori"),
        p(3, "Bilal", "Raza", deathPlace = "Karachi", address = "12 Canal Road, Lahore"),
        p(4, "Adam", "Ahmed", notes = "Moved to Multan"),
    )
    private val index = TreeSearch.index(Family.of(people, emptyList(), emptyList()))

    @Test
    fun namesComeFirstThenPlacesThenNotes() {
        val hits = index.find("lahor")
        assertEquals(listOf(2, 1, 3), hits.map { it.person.id })
        assertEquals(listOf(MatchIn.Name, MatchIn.Place, MatchIn.Place), hits.map { it.matchedIn })
        assertEquals("Born in Lahore", hits[1].snippet)
        assertEquals("Address: 12 Canal Road, Lahore", hits[2].snippet)
    }

    @Test
    fun caseAndAccentsAreIgnored() {
        assertEquals(listOf(1), index.find("ZOE").map { it.person.id })
        assertEquals(listOf(1), index.find("zoë").map { it.person.id })
    }

    @Test
    fun aNoteMatchShowsTheWordsAroundIt() {
        val hit = index.find("railway").single()
        assertEquals(MatchIn.Notes, hit.matchedIn)
        assertTrue(hit.snippet.contains("railway station"))
    }

    @Test
    fun onePersonAppearsOnceEvenIfSeveralThingsMatch() {
        // "ahmed" is Ahmed Lahori's name and Adam Ahmed's surname; both are name matches, each listed once, alphabetically.
        assertEquals(listOf(4, 2), index.find("ahmed").map { it.person.id })
        // A person whose name and place both match is still listed once.
        val both = TreeSearch.index(Family.of(listOf(p(1, "Lahore", "Smith", birthPlace = "Lahore")), emptyList(), emptyList()))
        assertEquals(1, both.find("lahore").size)
    }

    @Test
    fun oneLetterMatchesNamesOnlyAndABlankQueryFindsNothing() {
        assertTrue(index.find("l").all { it.matchedIn == MatchIn.Name })
        assertTrue(index.find("  ").isEmpty())
        assertTrue(index.find("zzzz").isEmpty())
    }

    @Test
    fun theLimitIsRespected() {
        val many = (1..100).map { p(it, "Same", "Name$it") }
        assertEquals(30, TreeSearch.index(Family.of(many, emptyList(), emptyList())).find("same").size)
    }

    @Test
    fun searchingThreeThousandPeopleIsFast() {
        val big = (1..3000).map { p(it, "Given$it", "Family${it % 50}", birthPlace = "Town${it % 200}", notes = "note number $it about something") }
        val f = Family.of(big, emptyList(), emptyList())
        var idx = TreeSearch.index(f)
        val build = kotlin.system.measureTimeMillis { idx = TreeSearch.index(f) }
        val search = kotlin.system.measureTimeMillis { repeat(20) { idx.find("town1"); idx.find("something"); idx.find("given29") } }
        assertTrue("index took $build ms", build < 3000)
        assertTrue("20 searches took $search ms", search < 1500)
    }

    // ---------------------------------------------------------------- timeline

    @Test
    fun theTimelineIsInDateOrderWithAgesAndTheDeathLast() {
        val me = Person(1, "Ali", "Khan", Sex.Male, 1950, 2010, "Lahore", "Multan", birthMonth = 3, birthDay = 10, deathMonth = 5, deathDay = 1)
        val spouse = p(2, "Sana")
        val kid = Person(3, "Noor", "Khan", Sex.Female, 1980, birthPlace = "Karachi")
        val family = Family.of(listOf(me, spouse, kid), listOf(3 to 1), listOf(1 to 2))
        val details = PersonDetails(
            events = listOf(
                Event(EventKind.Burial, date = PartialDate(2010, 5, 3), place = "Multan"),
                Event(EventKind.Occupation, "Teacher", PartialDate(1975)),
                Event(EventKind.Baptism, date = PartialDate(1950, 4, 1)),
                Event(EventKind.Residence),
            ),
            coupleEvents = mapOf(2 to listOf(Event(EventKind.Marriage, date = PartialDate(1976, 6, 6), place = "Lahore"))),
        )
        val t = Timeline.of(family, me, details)
        // The undated Residence comes after everything dated, even the burial.
        assertEquals(listOf("Born", "Baptism", "Occupation: Teacher", "Marriage with Sana", "Noor Khan was born", "Died", "Burial", "Residence"), t.map { it.title })
        assertEquals(TimelineKind.Birth, t.first().kind)
        assertEquals("Lahore", t.first().place)
        assertEquals("About 25", t[2].age)
        assertEquals("Age 60", t[5].age)
        assertEquals("Multan", t[5].place)
        assertEquals("Multan", t[6].place)
    }

    @Test
    fun anUndatedBirthIsFirstAnUndatedDeathIsLastAndAChildWithNoYearIsLeftOut() {
        val me = Person(1, "Ali", "Khan", Sex.Male, null, null, "Lahore", "Multan")
        val kid = p(2, "Noor")
        val family = Family.of(listOf(me, kid), listOf(2 to 1), emptyList())
        val t = Timeline.of(family, me, PersonDetails(events = listOf(Event(EventKind.Occupation, "Farmer", PartialDate(1980)), Event(EventKind.Residence))))
        // The dated job is first after the birth; the undated residence follows it; an undated death is last of all.
        assertEquals(listOf("Born", "Occupation: Farmer", "Residence", "Died"), t.map { it.title })
        assertEquals(TimelineKind.Death, t.last().kind)
    }

    @Test
    fun anEmptyLifeHasAnEmptyTimeline() {
        val me = p(1, "Ali")
        assertTrue(Timeline.of(Family.of(listOf(me), emptyList(), emptyList()), me, PersonDetails.Empty).isEmpty())
    }

    @Test
    fun ageIsExactOnlyWithFullDatesAndNeverNegative() {
        assertEquals("Age 24", Timeline.ageAt(PartialDate(1950, 6, 15), PartialDate(1975, 3, 1)))
        assertEquals("Age 25", Timeline.ageAt(PartialDate(1950, 3, 1), PartialDate(1975, 3, 1)))
        assertEquals("About 25", Timeline.ageAt(PartialDate(1950), PartialDate(1975, 3, 1)))
        assertEquals("", Timeline.ageAt(PartialDate(1950), PartialDate(1940)))
        assertEquals("", Timeline.ageAt(PartialDate(), PartialDate(1940)))
        assertEquals("", Timeline.ageAt(PartialDate(1950), PartialDate()))
    }

    // ---------------------------------------------------------------- statistics

    @Test
    fun statisticsCountWhatIsRecorded() {
        val list = listOf(
            p(1, "Ali", "Khan", Sex.Male, 1920, 1990, birthPlace = "Lahore"),
            p(2, "Sana", "Khan", Sex.Female, 1925, 1995, birthPlace = "Lahore"),
            p(3, "Noor", "Khan", Sex.Female, 1950, birthPlace = "Karachi"),
            p(4, "Omar", "Shah", Sex.Male, 1952),
            p(5, "Mina", "Shah", Sex.Female, 2000),
            p(6, "Nobody"),
        )
        val f = Family.of(list, listOf(3 to 1, 3 to 2, 5 to 3, 5 to 4), listOf(1 to 2, 3 to 4))
        val s = TreeStats.of(f, 2026)
        assertEquals(6, s.people)
        assertEquals(2, s.men)
        assertEquals(3, s.women)
        assertEquals(1, s.sexNotStated)
        assertEquals(2, s.deceased)
        assertEquals(3, s.mayBeAlive) // Noor (1950), Omar (1952) and Mina (2000)
        assertEquals(1, s.withNoDates)
        assertEquals(2, s.couples)
        assertEquals(3, s.generations)
        assertEquals(1, s.oldest!!.id)
        assertEquals(5, s.youngest!!.id)
        assertEquals(70, s.longestLife!!.value)
        assertEquals(1, s.longestLife!!.person.id)
        assertEquals(70.0, s.averageLifespan!!, 0.001)
        assertEquals(1, s.mostChildren!!.value)
        assertEquals(listOf("Khan" to 3, "Shah" to 2), s.topSurnames)
        assertEquals("Lahore" to 2, s.topPlaces.first())
        assertEquals(listOf(1920, 1950, 2000), s.birthsByDecade.map { it.first })
        assertEquals(listOf(2, 2, 1), s.birthsByDecade.map { it.second })
    }

    @Test
    fun anEmptyTreeHasEmptyStatistics() {
        val s = TreeStats.of(Family.Empty, 2026)
        assertEquals(0, s.people)
        assertNull(s.oldest)
        assertNull(s.averageLifespan)
        assertNull(s.mostChildren)
        assertTrue(s.birthsByDecade.isEmpty())
        assertEquals(0, s.generations)
    }

    @Test
    fun aLoopInBadDataDoesNotHangTheGenerationCount() {
        val a = p(1, "A")
        val b = p(2, "B")
        val f = Family.of(listOf(a, b), listOf(1 to 2, 2 to 1), emptyList())
        assertTrue(TreeStats.generations(f) >= 1)
    }

    @Test
    fun anImpossibleLifespanIsIgnored() {
        val f = Family.of(listOf(p(1, "A", born = 1700, died = 1900), p(2, "B", born = 1900, died = 1850)), emptyList(), emptyList())
        assertNull(TreeStats.of(f, 2026).averageLifespan)
    }
}
