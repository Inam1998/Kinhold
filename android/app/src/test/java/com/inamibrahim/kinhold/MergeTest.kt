package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Merge
import com.inamibrahim.kinhold.model.MergeField
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.Share
import com.inamibrahim.kinhold.model.ShareDirection
import com.inamibrahim.kinhold.model.ShareScope
import com.inamibrahim.kinhold.model.Source
import com.inamibrahim.kinhold.model.TreeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    private val uidDad = "11111111-1111-4111-8111-111111111111"
    private val uidMe = "22222222-2222-4222-8222-222222222222"
    private val uidMum = "33333333-3333-4333-8333-333333333333"

    private fun p(id: Int, given: String, surname: String = "Raza", sex: Sex = Sex.Unknown, born: Int? = null, uid: String = "", died: Int? = null) =
        Person(id, given, surname, sex, born = born, died = died, uid = uid)

    private fun data(people: List<Person>, links: List<Pair<Int, Int>> = emptyList(), couples: List<Pair<Int, Int>> = emptyList(), details: TreeDetails = TreeDetails.Empty, photos: Map<Int, ByteArray> = emptyMap()) =
        BackupData(people, links, couples, photos = photos, details = details)

    private fun merge(current: Family, incoming: BackupData, currentDetails: TreeDetails = TreeDetails.Empty) = Merge(current, currentDetails, incoming)

    // ---------------------------------------------------------------- sharing a branch

    private val tree = Family.of(
        listOf(
            p(1, "Great", sex = Sex.Male, born = 1900, died = 1970), p(2, "Grand", sex = Sex.Male, born = 1925, died = 1999),
            p(3, "Dad", sex = Sex.Male, born = 1950), p(4, "Mum", sex = Sex.Female, born = 1952), p(5, "Me", sex = Sex.Male, born = 1990),
            p(6, "Wife", sex = Sex.Female, born = 1992), p(7, "Kid", born = 2020), p(8, "Sis", born = 1988), p(9, "Unknown", born = null),
        ),
        listOf(2 to 1, 3 to 2, 5 to 3, 5 to 4, 7 to 5, 7 to 6, 8 to 3, 8 to 4),
        listOf(3 to 4, 5 to 6),
    )

    @Test
    fun aBranchGoesOutwardTheChosenWayAndNumberOfGenerations() {
        fun ids(dir: ShareDirection, gen: Int, spouses: Boolean = false) = Share.select(tree, 5, ShareScope(dir, gen, spouses), 2026)
        assertEquals(setOf(5, 3, 4, 2, 1), ids(ShareDirection.Ancestors, 0))
        assertEquals(setOf(5, 3, 4), ids(ShareDirection.Ancestors, 1))
        assertEquals(setOf(5, 7), ids(ShareDirection.Descendants, 0))
        assertEquals(setOf(5, 3, 4, 7), ids(ShareDirection.Both, 1))
        assertEquals(setOf(5, 3, 4, 7, 6), ids(ShareDirection.Both, 1, spouses = true))
        assertTrue(8 !in ids(ShareDirection.Both, 0)) // siblings are not part of the branch
        assertEquals(emptySet<Int>(), Share.select(tree, 99, ShareScope(), 2026))
    }

    @Test
    fun leavingOutPeopleWhoMayBeAliveKeepsTheRootAndPeopleWithNoDates() {
        val ids = Share.select(tree, 5, ShareScope(ShareDirection.Both, 0, withSpouses = true, excludeLiving = true), 2026)
        assertEquals(setOf(5, 2, 1), ids) // Dad, Mum, Wife and Kid may be alive; the root is always kept
        assertTrue(Share.mayBeAlive(tree.person(7), 2026))
        assertFalse(Share.mayBeAlive(tree.person(1), 2026))
        assertFalse(Share.mayBeAlive(tree.person(9), 2026)) // no dates at all: not assumed alive
    }

    @Test
    fun theSmallerTreeKeepsOnlyLinksAmongItsPeople() {
        val sub = Share.subFamily(tree, setOf(5, 3, 4))
        assertEquals(3, sub.size)
        assertEquals(setOf(3, 4), sub.parentsOf(5).map { it.id }.toSet())
        assertTrue(sub.parentsOf(3).isEmpty())
        assertEquals(listOf(3 to 4), sub.partnerships())
        val details = TreeDetails(mapOf(5 to listOf(Event(EventKind.Burial)), 9 to listOf(Event(EventKind.Burial))), emptyMap(), mapOf((3 to 4) to listOf(Event(EventKind.Marriage)), (5 to 6) to listOf(Event(EventKind.Marriage))))
        val kept = Share.subDetails(details, setOf(5, 3, 4))
        assertEquals(setOf(5), kept.events.keys)
        assertEquals(setOf(3 to 4), kept.couples.keys)
    }

    // ---------------------------------------------------------------- matching

    @Test
    fun peopleWithTheSamePermanentIdAreMatchedForSureEvenIfRenamed() {
        val mine = Family.of(listOf(p(1, "Inam", born = 1998, uid = uidMe), p(2, "Other")), emptyList(), emptyList())
        val his = data(listOf(p(10, "Inaam", born = 1999, uid = uidMe), p(11, "Stranger", "Khan")))
        val m = merge(mine, his)
        assertEquals(mapOf(10 to 1), m.exact)
        assertTrue(m.candidates.isEmpty())
        val result = m.resolve(emptySet(), emptySet(), true)
        assertEquals(listOf(11), result.newPeople)
    }

    @Test
    fun sameNameAndBirthYearIsAStrongSuggestion() {
        val mine = Family.of(listOf(p(1, "Ahmed", born = 1948)), emptyList(), emptyList())
        val his = data(listOf(p(10, "AHMED", born = 1948), p(11, "Ahmed", "Other", born = 1948)))
        val c = merge(mine, his).candidates.single()
        assertEquals(10, c.incomingId)
        assertEquals(1, c.currentId)
        assertTrue(c.strong)
    }

    @Test
    fun sameNameAloneIsOnlyOffered() {
        val mine = Family.of(listOf(p(1, "Ahmed")), emptyList(), emptyList())
        val c = merge(mine, data(listOf(p(10, "Ahmed")))).candidates.single()
        assertFalse(c.strong)
    }

    @Test
    fun aRelativeInCommonMakesAMatchStrong() {
        val mine = Family.of(listOf(p(1, "Inam", uid = uidMe), p(2, "Ahmed")), listOf(1 to 2), emptyList())
        val his = data(listOf(p(10, "Inam", uid = uidMe), p(11, "Ahmed")), listOf(10 to 11))
        val c = merge(mine, his).candidates.single()
        assertEquals(11, c.incomingId)
        assertTrue(c.strong)
        assertTrue(c.why.contains("Inam"))
    }

    @Test
    fun differentSexOrFarApartBirthYearsAreNeverSuggested() {
        val mine = Family.of(listOf(p(1, "Sam", sex = Sex.Male, born = 1950), p(2, "Alex", born = 1950)), emptyList(), emptyList())
        val his = data(listOf(p(10, "Sam", sex = Sex.Female, born = 1950), p(11, "Alex", born = 1960)))
        assertTrue(merge(mine, his).candidates.isEmpty())
    }

    @Test
    fun eachPersonIsSuggestedAtMostOnce() {
        val mine = Family.of(listOf(p(1, "Ali", born = 1950), p(2, "Ali", born = 1950)), emptyList(), emptyList())
        val his = data(listOf(p(10, "Ali", born = 1950), p(11, "Ali", born = 1950)))
        val c = merge(mine, his).candidates
        assertEquals(2, c.size)
        assertEquals(2, c.map { it.currentId }.toSet().size)
        assertEquals(2, c.map { it.incomingId }.toSet().size)
    }

    @Test
    fun accentsAndCaseDoNotHideAMatch() {
        val mine = Family.of(listOf(p(1, "José", "Müller", born = 1950)), emptyList(), emptyList())
        val c = merge(mine, data(listOf(p(10, "jose", "MULLER", born = 1950)))).candidates.single()
        assertTrue(c.strong)
    }

    // ---------------------------------------------------------------- fields

    @Test
    fun emptyFieldsAreOfferedAsFillsAndDifferentOnesAsConflicts() {
        val mine = Family.of(
            listOf(Person(1, "Ahmed", "Raza", Sex.Unknown, born = 1948, birthPlace = "", deathPlace = "Lahore", notes = "mine", uid = uidDad)),
            emptyList(), emptyList(),
        )
        val his = data(listOf(Person(10, "Ahmed", "Raza", Sex.Male, born = 1949, birthPlace = "Multan", deathPlace = "Karachi", notes = "his", uid = uidDad, birthMonth = 3, birthDay = 4)))
        val changes = merge(mine, his).fieldChanges(mapOf(10 to 1)).associateBy { it.part }
        assertTrue(changes.getValue(MergeField.Sex).isFill)
        assertTrue(changes.getValue(MergeField.BirthPlace).isFill)
        assertFalse(changes.getValue(MergeField.DeathPlace).isFill)
        assertFalse(changes.getValue(MergeField.Born).isFill) // 1948 against 1949 disagree
        assertFalse(changes.getValue(MergeField.Notes).isFill)
        assertNull(changes[MergeField.Given])
        assertNull(changes[MergeField.Surname])
    }

    @Test
    fun aMoreExactDateOfTheSameYearIsAFill() {
        val mine = Family.of(listOf(p(1, "A", born = 1948, uid = uidDad)), emptyList(), emptyList())
        val his = data(listOf(Person(10, "A", "Raza", born = 1948, birthMonth = 3, birthDay = 4, uid = uidDad)))
        val change = merge(mine, his).fieldChanges(mapOf(10 to 1)).single()
        assertEquals(MergeField.Born, change.part)
        assertTrue(change.isFill)
        assertEquals("4 Mar 1948", change.his)
    }

    @Test
    fun noChangeIsAppliedUnlessTheFieldIsChosen() {
        val mine = Family.of(listOf(Person(1, "A", "Raza", birthPlace = "", uid = uidDad)), emptyList(), emptyList())
        val his = data(listOf(Person(10, "A", "Raza", birthPlace = "Multan", died = 2000, deathMonth = 5, uid = uidDad)))
        val m = merge(mine, his)
        assertTrue(m.resolve(emptySet(), emptySet(), true).updates.isEmpty())
        val chosen = m.resolve(emptySet(), setOf(10 to MergeField.BirthPlace, 10 to MergeField.Died), true).updates.getValue(1)
        assertEquals("Multan", chosen.birthPlace)
        assertEquals(2000, chosen.died)
        assertEquals(5, chosen.deathMonth)
        assertEquals("A", chosen.given)
        assertEquals(uidDad, chosen.uid) // your person keeps their own permanent id
    }

    @Test
    fun usingTheirNotesAddsThemAfterYours() {
        val mine = Family.of(listOf(Person(1, "A", "Raza", notes = "mine", uid = uidDad)), emptyList(), emptyList())
        val his = data(listOf(Person(10, "A", "Raza", notes = "his", uid = uidDad)))
        val d = merge(mine, his).resolve(emptySet(), setOf(10 to MergeField.Notes), true).updates.getValue(1)
        assertEquals("mine\n\nhis", d.notes)
    }

    @Test
    fun aPhotoIsOfferedOnlyToPeopleWithoutOne() {
        val mine = Family.of(listOf(Person(1, "A", "Raza", uid = uidDad), Person(2, "B", "Raza", uid = uidMe, photoStamp = 5)), emptyList(), emptyList())
        val his = data(listOf(Person(10, "A", "Raza", uid = uidDad), Person(11, "B", "Raza", uid = uidMe)), photos = mapOf(10 to byteArrayOf(1), 11 to byteArrayOf(2)))
        val m = merge(mine, his)
        val changes = m.fieldChanges(m.exact)
        assertEquals(listOf(MergeField.Photo), changes.map { it.part })
        assertEquals(mapOf(1 to 10), m.resolve(emptySet(), setOf(10 to MergeField.Photo), true).photoFills)
    }

    // ---------------------------------------------------------------- what is added

    /** My tree: Me (shared id) with no parents. His tree: Dad and Mum, Me, and Me's sister, with a marriage. */
    private val fathersFile = data(
        people = listOf(p(1, "Dad", sex = Sex.Male, born = 1950, uid = uidDad), p(2, "Mum", sex = Sex.Female, uid = uidMum), p(3, "Inam", born = 1998, uid = uidMe), p(4, "Sis", born = 2000)),
        links = listOf(3 to 1, 3 to 2, 4 to 1, 4 to 2),
        couples = listOf(1 to 2),
        details = TreeDetails(
            events = mapOf(1 to listOf(Event(EventKind.Occupation, "Teacher", PartialDate(1975))), 3 to listOf(Event(EventKind.Baptism, date = PartialDate(1998)))),
            sources = mapOf(1 to listOf(Source("Service record", "p. 4"))),
            couples = mapOf((1 to 2) to listOf(Event(EventKind.Marriage, date = PartialDate(1970), place = "Multan"))),
        ),
    )

    @Test
    fun missingPeopleAreAddedAndLinkedToThoseYouHave() {
        val mine = Family.of(listOf(p(1, "Inam", born = 1998, uid = uidMe)), emptyList(), emptyList())
        val m = merge(mine, fathersFile)
        assertEquals(mapOf(3 to 1), m.exact)
        val r = m.resolve(emptySet(), emptySet(), true)
        assertEquals(listOf(1, 2, 4), r.newPeople)
        // Inam (mine, id 1) gets his parents; Sis (new) gets them too.
        assertEquals(setOf(1 to -1, 1 to -2, -4 to -1, -4 to -2), r.links.toSet())
        assertEquals(listOf(-2 to -1), r.partnerships)
        assertEquals(0, r.skippedParentLinks)
    }

    @Test
    fun peopleAlreadyLinkedAreNotLinkedAgainAndAThirdParentIsSkipped() {
        val mine = Family.of(
            listOf(p(1, "Inam", uid = uidMe), p(2, "Dad", uid = uidDad), p(3, "Stepdad"), p(4, "Aunt")),
            listOf(1 to 2, 1 to 3), emptyList(),
        )
        val his = data(listOf(p(10, "Inam", uid = uidMe), p(11, "Dad", uid = uidDad), p(12, "Mum", sex = Sex.Female)), listOf(10 to 11, 10 to 12))
        val r = merge(mine, his).resolve(emptySet(), emptySet(), true)
        assertEquals(1, r.skippedParentLinks) // Inam already has two parents; Mum is not added as a third
        assertTrue(r.links.isEmpty())
        assertEquals(listOf(12), r.newPeople)
    }

    @Test
    fun eventsAndSourcesYouLackAreAddedAndOnesYouHaveAreNot() {
        val mine = Family.of(listOf(p(1, "Dad", uid = uidDad), p(2, "Mum", uid = uidMum)), emptyList(), listOf(1 to 2))
        val currentDetails = TreeDetails(
            events = mapOf(1 to listOf(Event(EventKind.Occupation, "teacher", PartialDate(1975), note = "mine"))),
            sources = mapOf(1 to listOf(Source("SERVICE RECORD", "P. 4"))),
            couples = mapOf((1 to 2) to listOf(Event(EventKind.Marriage, date = PartialDate(1970), place = "Multan"))),
        )
        val r = merge(mine, fathersFile, currentDetails).resolve(emptySet(), emptySet(), addEvents = true)
        assertTrue(r.personEvents.filterKeys { it > 0 }.isEmpty()) // same job and marriage: already there
        assertTrue(r.personSources.isEmpty())
        assertTrue(r.coupleEvents.isEmpty())
        // The baptism belongs to Inam, who is new here, so it comes with him.
        assertEquals(listOf(Event(EventKind.Baptism, date = PartialDate(1998))), r.personEvents.getValue(-3))
    }

    @Test
    fun turningEventsOffLeavesPeopleInBothTreesAloneButNewPeopleKeepTheirs() {
        val mine = Family.of(listOf(p(1, "Dad", uid = uidDad), p(2, "Mum", uid = uidMum)), emptyList(), listOf(1 to 2))
        val r = merge(mine, fathersFile).resolve(emptySet(), emptySet(), addEvents = false)
        assertTrue(r.personEvents.filterKeys { it > 0 }.isEmpty())
        assertTrue(r.coupleEvents.isEmpty())
        assertEquals(listOf(Event(EventKind.Baptism, date = PartialDate(1998))), r.personEvents.getValue(-3))
        val withEvents = merge(mine, fathersFile).resolve(emptySet(), emptySet(), addEvents = true)
        assertEquals(listOf(Event(EventKind.Occupation, "Teacher", PartialDate(1975))), withEvents.personEvents.getValue(1))
        assertEquals(listOf(Source("Service record", "p. 4")), withEvents.personSources.getValue(1))
        assertEquals(1, withEvents.coupleEvents.getValue(1 to 2).size)
    }

    @Test
    fun acceptingASuggestionMakesThatPersonMatchedAndStopsThemBeingAddedTwice() {
        val mine = Family.of(listOf(p(1, "Inam", born = 1998, uid = uidMe), p(2, "Ahmed", born = 1948)), listOf(1 to 2), emptyList())
        val his = data(listOf(p(10, "Inam", born = 1998, uid = uidMe), p(11, "Ahmed", born = 1948), p(12, "Sana")), listOf(10 to 11, 12 to 11))
        val m = merge(mine, his)
        val suggestion = m.candidates.single()
        assertEquals(11, suggestion.incomingId)
        val taken = m.resolve(setOf(11), emptySet(), true)
        assertEquals(listOf(12), taken.newPeople)
        assertEquals(listOf(-12 to 2), taken.links) // Sana becomes a child of the Ahmed you already had
        val declined = m.resolve(emptySet(), emptySet(), true)
        assertEquals(listOf(11, 12), declined.newPeople) // declined: he is added as a second Ahmed, as chosen
    }

    @Test
    fun mergingTheSameFileTwiceAddsNothingTheSecondTime() {
        // First merge: adds Dad, Mum and Sis. Second: build the tree as it would then be, with the same permanent ids.
        val sisUid = "44444444-4444-4444-8444-444444444444"
        val his = data(fathersFile.people.map { if (it.id == 4) Person(4, "Sis", "Raza", born = 2000, uid = sisUid) else it }, fathersFile.parentLinks, fathersFile.partnerships)
        val after = Family.of(
            listOf(p(1, "Inam", born = 1998, uid = uidMe), p(2, "Dad", sex = Sex.Male, born = 1950, uid = uidDad), p(3, "Mum", sex = Sex.Female, uid = uidMum), Person(4, "Sis", "Raza", born = 2000, uid = sisUid)),
            listOf(1 to 2, 1 to 3, 4 to 2, 4 to 3), listOf(2 to 3),
        )
        val r = merge(after, his).resolve(emptySet(), emptySet(), true)
        assertTrue(r.newPeople.isEmpty())
        assertTrue(r.links.isEmpty())
        assertTrue(r.partnerships.isEmpty())
        assertEquals(0, r.fieldCount)
    }
}
