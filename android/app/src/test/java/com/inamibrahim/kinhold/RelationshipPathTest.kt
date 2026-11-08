package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Relationships
import com.inamibrahim.kinhold.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipPathTest {
    private fun p(id: Int, sex: Sex) = Person(id, "P$id", "", sex)

    /** 1 and 2 are a couple with children 3 and 4 (full siblings); 5 is a child of 3 and 6, 7 a child of 4 and 8; 9 is a half-sibling of 3 through 1 only. */
    private val family = Family.of(
        listOf(p(1, Sex.Male), p(2, Sex.Female), p(3, Sex.Male), p(4, Sex.Female), p(5, Sex.Male), p(6, Sex.Female), p(7, Sex.Female), p(8, Sex.Male), p(9, Sex.Male), p(10, Sex.Female)),
        listOf(3 to 1, 3 to 2, 4 to 1, 4 to 2, 5 to 3, 5 to 6, 7 to 4, 7 to 8, 9 to 1, 9 to 10),
        listOf(1 to 2, 3 to 6, 4 to 8, 1 to 10),
    )

    private fun names(from: Int, to: Int) = Relationships.between(family, from, to)!!.pathNames()

    @Test
    fun fullSiblingsAreRelatedThroughBothParents() {
        val n = names(3, 4)
        assertEquals(3, n.size)
        assertEquals(setOf("P1", "P2"), n[1].split(" / ").toSet())
    }

    @Test
    fun cousinsAreRelatedThroughBothGrandparents() {
        val n = names(5, 7)
        assertEquals(setOf("P1", "P2"), n[2].split(" / ").toSet())
    }

    @Test
    fun aHalfSiblingIsRelatedThroughTheSharedParentOnly() {
        assertEquals(listOf("P3", "P1", "P9"), names(3, 9))
    }
}
