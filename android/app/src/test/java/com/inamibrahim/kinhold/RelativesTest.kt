package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.relativesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativesTest {
    private fun p(id: Int, sex: Sex) = Person(id, "P$id", "", sex)

    /** Dad(1) and Mum(2) have Me(3) and Sis(4). Dad also has Half(5) with Ann(6). Mum's later husband Stan(7) has Stepbro(8). */
    private val family = Family.of(
        people = listOf(
            p(1, Sex.Male), p(2, Sex.Female), p(3, Sex.Male), p(4, Sex.Female), p(5, Sex.Male),
            p(6, Sex.Female), p(7, Sex.Male), p(8, Sex.Male), p(9, Sex.Female),
        ),
        parentLinks = listOf(3 to 1, 3 to 2, 4 to 1, 4 to 2, 5 to 1, 5 to 6, 8 to 7, 9 to 3, 9 to 10),
        partnerships = listOf(1 to 2, 1 to 6, 2 to 7),
    )

    private fun labels(id: Int) = family.relativesOf(id).associate { it.person.id to it.label }

    @Test
    fun siblingsHalfSiblingsAndStepRelativesAreFound() {
        val l = labels(3)
        assertEquals("Sister", l[4])
        assertEquals("Half-brother", l[5])
        assertEquals("Stepfather", l[7])
        assertEquals("Stepbrother", l[8])
        assertEquals("Stepmother", l[6])
        assertEquals(5, l.size)
    }

    @Test
    fun theOrderIsSiblingsThenHalfThenStepParentsThenStepSiblings() {
        assertEquals(listOf(4, 5, 6, 7, 8), family.relativesOf(3).map { it.person.id })
    }

    @Test
    fun theRelationRunsBothWays() {
        val l = labels(5)
        assertEquals("Brother", labels(4)[3])
        assertEquals("Half-brother", l[3])
        assertEquals("Half-sister", l[4])
        // Ann is Half's mother, Dad is his father; Mum is Dad's spouse but not Half's parent.
        assertEquals("Stepmother", l[2])
    }

    @Test
    fun stepchildrenAreASpousesChildrenWhoAreNotYours() {
        // Mum (2) is married to Stan (7), who has Stepbro (8) from elsewhere.
        assertEquals("Stepson", labels(2)[8])
        // Dad is not a parent of 8 and not married to Stan.
        assertTrue(8 !in labels(1))
        // A person's own children are not step-children.
        assertTrue(3 !in labels(2))
    }

    @Test
    fun aMissingPersonOrLoneChildHasNoRelatives() {
        assertTrue(family.relativesOf(99).isEmpty())
        assertTrue(family.relativesOf(9).isEmpty())
    }

    @Test
    fun theListIsCapped() {
        val kids = (10..80).map { p(it, Sex.Unknown) }
        val big = Family.of(listOf(p(1, Sex.Male), p(2, Sex.Female)) + kids, kids.flatMap { listOf(it.id to 1, it.id to 2) }, listOf(1 to 2))
        assertEquals(30, big.relativesOf(10).size)
        assertEquals(5, big.relativesOf(10, limit = 5).size)
    }
}
