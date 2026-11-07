package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TreeGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class TreeGroupsTest {
    private fun p(id: Int, born: Int? = null) = Person(id, "P$id", "", Sex.Unknown, born)

    @Test
    fun aConnectedTreeIsOneGroup() {
        val f = Family.of((1..5).map { p(it) }, listOf(3 to 1, 3 to 2, 4 to 3), listOf(1 to 2, 4 to 5))
        val groups = TreeGroups.of(f)
        assertEquals(1, groups.size)
        assertEquals(5, groups.single().size)
    }

    @Test
    fun removingTheLinkBetweenTwoBranchesMakesTwoGroupsLargestFirst() {
        // 1+2 are parents of 3; 3 and 4 are partners; 5 is a child of 4. Without the (3, 4) partnership there are two groups.
        val f = Family.of((1..5).map { p(it, 1900 + it) }, listOf(3 to 1, 3 to 2, 5 to 4), listOf(1 to 2))
        val groups = TreeGroups.of(f)
        assertEquals(listOf(3, 2), groups.map { it.size })
        assertEquals(1, groups[0].start.id) // the earliest born of the larger group
        assertEquals(4, groups[1].start.id)
    }

    @Test
    fun peopleWithNoLinksAreGroupsOfOne() {
        val f = Family.of(listOf(p(1), p(2), p(3)), listOf(2 to 1), emptyList())
        assertEquals(listOf(2, 1), TreeGroups.of(f).map { it.size })
    }

    @Test
    fun aBranchIsOneGroupWhetherLinkedThroughParentsOrPartners() {
        // 1 and 2 are partners with no children; 3 is a child of 2 only: all three are one group.
        val f = Family.of(listOf(p(1), p(2), p(3)), listOf(3 to 2), listOf(1 to 2))
        assertEquals(listOf(3), TreeGroups.of(f).map { it.size })
    }

    @Test
    fun theGroupStartsOnTheEarliestBornOrTheFirstAddedWhenNoYearIsKnown() {
        val dated = Family.of(listOf(p(1, 1950), p(2, 1920), p(3, null)), listOf(1 to 2, 3 to 2), emptyList())
        assertEquals(2, TreeGroups.of(dated).single().start.id)
        val undated = Family.of(listOf(p(5), p(6)), listOf(6 to 5), emptyList())
        assertEquals(5, TreeGroups.of(undated).single().start.id)
    }

    @Test
    fun aLoopInBadDataDoesNotHangAndAnEmptyTreeHasNoGroups() {
        assertEquals(listOf(2), TreeGroups.of(Family.of(listOf(p(1), p(2)), listOf(1 to 2, 2 to 1), emptyList())).map { it.size })
        assertTrue(TreeGroups.of(Family.Empty).isEmpty())
    }

    @Test
    fun threeThousandPeopleInHundredsOfGroupsAreCountedQuickly() {
        val people = (1..3000).map { p(it, 1800 + it % 200) }
        // Chains of ten people each: 300 groups.
        val links = (1..3000).filter { it % 10 != 1 }.map { it to it - 1 }
        val f = Family.of(people, links, emptyList())
        var groups = TreeGroups.of(f)
        val ms = measureTimeMillis { repeat(10) { groups = TreeGroups.of(f) } }
        assertEquals(300, groups.size)
        assertTrue(groups.all { it.size == 10 })
        assertTrue("took $ms ms", ms < 3000)
    }

    @Test
    fun theRelationshipFinderStartsOnTwoDifferentPeople() {
        val f = Family.of((1..4).map { p(it) }, listOf(3 to 1), listOf(1 to 2))
        // The focus is the same person as "you": the second becomes their spouse, then child, then parent, then anyone.
        assertEquals(1 to 2, com.inamibrahim.kinhold.ui.relatedStart(f, 1, 1).let { it.first to it.second })
        assertEquals(3 to 1, com.inamibrahim.kinhold.ui.relatedStart(Family.of((1..3).map { p(it) }, listOf(3 to 1), emptyList()), 3, 3).let { it.first to it.second })
        assertEquals(4 to 1, com.inamibrahim.kinhold.ui.relatedStart(f, 4, 4).let { it.first to it.second })
        // A different person in focus is used as it is.
        assertEquals(1 to 3, com.inamibrahim.kinhold.ui.relatedStart(f, 1, 3).let { it.first to it.second })
        // A missing "you" falls back to the focus.
        assertEquals(3 to 1, com.inamibrahim.kinhold.ui.relatedStart(f, -1, 3).let { it.first to it.second })
    }
}
