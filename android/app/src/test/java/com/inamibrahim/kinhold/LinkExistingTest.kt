package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.LinkRules
import com.inamibrahim.kinhold.model.LinkType
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class LinkExistingTest {
    private fun p(id: Int) = Person(id, "P$id", "", Sex.Unknown)

    // 1 is the grandparent of 3 (through 2); 4 is 2's partner; 5 and 6 are unrelated.
    private val family = Family.of(
        (1..6).map(::p),
        listOf(2 to 1, 3 to 2, 3 to 4),
        listOf(2 to 4),
    )

    @Test
    fun ancestorsAndDescendantsFollowEveryParentLink() {
        assertEquals(setOf(1, 2, 4), LinkRules.ancestorsOf(family, 3))
        assertEquals(setOf(2, 3), LinkRules.descendantsOf(family, 1))
        assertTrue(LinkRules.ancestorsOf(family, 1).isEmpty())
    }

    @Test
    fun aDescendantCannotBecomeAParentAndAnAncestorCannotBecomeAChild() {
        assertNotNull(LinkRules.problem(family, 1, 3, LinkType.Parent))
        assertNotNull(LinkRules.problem(family, 3, 1, LinkType.Child))
        assertTrue(3 in LinkRules.excluded(family, 1, LinkType.Parent))
        assertTrue(1 in LinkRules.excluded(family, 3, LinkType.Child))
    }

    @Test
    fun aLinkIsNotMadeTwiceAndNobodyLinksToThemselves() {
        assertNotNull(LinkRules.problem(family, 3, 2, LinkType.Parent))
        assertNotNull(LinkRules.problem(family, 2, 3, LinkType.Child))
        assertNotNull(LinkRules.problem(family, 2, 4, LinkType.Spouse))
        assertNotNull(LinkRules.problem(family, 5, 5, LinkType.Parent))
        assertTrue(5 in LinkRules.excluded(family, 5, LinkType.Spouse))
    }

    @Test
    fun parentAndChildCannotBePartners() {
        assertNotNull(LinkRules.problem(family, 3, 2, LinkType.Spouse))
        assertNotNull(LinkRules.problem(family, 2, 3, LinkType.Spouse))
        assertNull(LinkRules.problem(family, 5, 6, LinkType.Spouse))
    }

    @Test
    fun unrelatedPeopleCanBeLinkedEveryWay() {
        assertNull(LinkRules.problem(family, 5, 6, LinkType.Parent))
        assertNull(LinkRules.problem(family, 5, 6, LinkType.Child))
        assertNull(LinkRules.problem(family, 5, 6, LinkType.Spouse))
    }

    @Test
    fun noOneTakesAFifthParentOrBecomesAFifthParentsChild() {
        val people = (1..7).map(::p)
        val f = Family.of(people, listOf(1 to 2, 1 to 3, 1 to 4, 1 to 5), emptyList())
        assertNotNull(LinkRules.problem(f, 1, 6, LinkType.Parent))
        assertEquals(people.map { it.id }.toSet(), LinkRules.excluded(f, 1, LinkType.Parent))
        assertNotNull(LinkRules.problem(f, 6, 1, LinkType.Child))
        assertTrue(1 in LinkRules.excluded(f, 6, LinkType.Child))
        assertNull(LinkRules.problem(f, 6, 7, LinkType.Child))
    }

    @Test
    fun aLinkToSomeoneNotInTheTreeIsRefused() {
        assertNotNull(LinkRules.problem(family, 1, 99, LinkType.Spouse))
    }

    @Test
    fun excludedIsFastOnALargeTree() {
        // 3,000 people in a chain of 1,000 three-person families, so the walk is as deep as it gets.
        val people = (1..3000).map(::p)
        val links = (4..3000).map { it to it - 3 }
        val f = Family.of(people, links, emptyList())
        val ms = measureTimeMillis { repeat(20) { LinkRules.excluded(f, 3000, LinkType.Child); LinkRules.excluded(f, 1, LinkType.Parent) } }
        assertFalse("took $ms ms", ms > 1500)
    }
}
