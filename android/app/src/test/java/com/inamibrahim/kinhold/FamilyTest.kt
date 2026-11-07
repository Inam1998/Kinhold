package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyTest {
    private fun p(id: Int, given: String, sex: Sex = Sex.Unknown, born: Int? = null, died: Int? = null) =
        Person(id, given, "Khan", sex, born, died)

    @Test
    fun anEmptyTreeHasNoFocusAndNoOldest() {
        assertEquals(0, Family.Empty.size)
        assertNull(Family.Empty.defaultFocus())
        assertNull(Family.Empty.oldest())
    }

    @Test
    fun parentsAreListedFatherFirstThenMotherThenUnknown() {
        val child = p(1, "Child")
        val mum = p(2, "Mum", Sex.Female)
        val dad = p(3, "Dad", Sex.Male)
        val other = p(4, "Other")
        val family = Family.of(listOf(child, mum, dad, other), listOf(1 to 2, 1 to 4, 1 to 3), emptyList())
        assertEquals(listOf("Dad", "Mum", "Other"), family.parentsOf(1).map { it.given })
    }

    @Test
    fun childrenAreOldestFirstAndThoseWithoutABirthYearComeLast() {
        val family = Family.of(
            listOf(p(1, "Parent"), p(2, "Young", born = 2000), p(3, "Unknown"), p(4, "Old", born = 1990)),
            listOf(2 to 1, 3 to 1, 4 to 1),
            emptyList(),
        )
        assertEquals(listOf("Old", "Young", "Unknown"), family.childrenOf(1).map { it.given })
    }

    @Test
    fun partnershipsWorkInBothDirections() {
        val family = Family.of(listOf(p(1, "A"), p(2, "B")), emptyList(), listOf(1 to 2))
        assertEquals(listOf(2), family.spousesOf(1).map { it.id })
        assertEquals(listOf(1), family.spousesOf(2).map { it.id })
    }

    @Test
    fun linksToPeopleWhoAreNotInTheTreeAreIgnored() {
        val family = Family.of(listOf(p(1, "A"), p(2, "B")), listOf(1 to 99, 99 to 2, 1 to 1), listOf(1 to 99, 2 to 2))
        assertTrue(family.parentsOf(1).isEmpty())
        assertTrue(family.childrenOf(2).isEmpty())
        assertTrue(family.spousesOf(1).isEmpty())
        assertTrue(family.spousesOf(2).isEmpty())
    }

    @Test
    fun oldestIsTheEarliestBirthYear() {
        val family = Family.of(listOf(p(1, "B", born = 1950), p(2, "A", born = 1900), p(3, "C")), emptyList(), emptyList())
        assertEquals("A", family.oldest()!!.given)
    }

    @Test
    fun searchIgnoresCaseAndBlankQueries() {
        val family = Family.of(listOf(p(1, "Ayesha"), p(2, "Omar")), emptyList(), emptyList())
        assertEquals(listOf("Ayesha"), family.search("aYE").map { it.given })
        assertTrue(family.search("  ").isEmpty())
    }

    @Test
    fun aSinglePersonIsTheDefaultFocus() {
        val family = Family.of(listOf(p(7, "Only")), emptyList(), emptyList())
        assertEquals(7, family.defaultFocus()!!.id)
    }

    @Test
    fun yearsReadWellWhateverIsKnown() {
        assertEquals("1950 – 2010", p(1, "A", born = 1950, died = 2010).years())
        assertEquals("b. 1990", p(1, "A", born = 1990).years())
        assertEquals("d. 1950", p(1, "A", died = 1950).years())
        assertEquals("", p(1, "A").years())
    }

    @Test
    fun aNamelessPersonIsCalledUnnamed() {
        assertEquals("Unnamed", Person(1, "", "").name)
        assertEquals("Ayesha Khan", Person(1, "Ayesha", "Khan").name)
        assertEquals("Khan", Person(1, "", "Khan").name)
    }

    @Test
    fun aDraftNeedsANameAndSensibleDates() {
        assertFalse(PersonDraft().isValid)
        assertFalse(PersonDraft(given = "  ").isValid)
        assertTrue(PersonDraft(given = "Ayesha").isValid)
        assertTrue(PersonDraft(surname = "Khan").isValid)
        assertFalse(PersonDraft(given = "A", born = 2000, died = 1990).isValid)
        assertTrue(PersonDraft(given = "A", born = 1990, died = 1990).isValid)
        assertTrue(PersonDraft(given = "A", born = 1990).isValid)
    }

    @Test
    fun aPersonTurnsIntoADraftAndBack() {
        val person = Person(5, "Ayesha", "Khan", Sex.Female, 1950, 2010, "Lahore", "Karachi", "A note")
        val draft = person.toDraft()
        assertEquals("Ayesha", draft.given)
        assertEquals(Sex.Female, draft.sex)
        assertEquals(1950, draft.born)
        assertEquals("Karachi", draft.deathPlace)
        assertEquals("A note", draft.notes)
    }
}
