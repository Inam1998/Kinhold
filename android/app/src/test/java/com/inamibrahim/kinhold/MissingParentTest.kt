package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TreeChecks
import com.inamibrahim.kinhold.model.missingParents
import com.inamibrahim.kinhold.ui.siblingRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingParentTest {
    private fun p(id: Int, sex: Sex = Sex.Unknown) = Person(id, "P$id", "", sex, null)

    // 1 father, 2 mother (his partner), 3 and 4 have only the father recorded, 5 has both.
    private fun family() = Family.of(
        (1..5).map { p(it, if (it == 2) Sex.Female else Sex.Male) },
        listOf(3 to 1, 4 to 1, 5 to 1, 5 to 2),
        listOf(1 to 2),
    )

    @Test
    fun childrenWithOnlyOneParentAreFoundWhenThePartnerParentsAnotherChild() {
        val found = TreeChecks.missingParents(family())
        assertEquals(1, found.size)
        assertEquals(2, found[0].partner.id)
        assertEquals(setOf(3, 4), found[0].children.map { it.id }.toSet())
    }

    @Test
    fun noFindingWhenThePartnerParentsNoChild() {
        val f = Family.of((1..3).map { p(it) }, listOf(3 to 1), listOf(1 to 2))
        assertTrue(TreeChecks.missingParents(f).isEmpty())
    }

    @Test
    fun aSiblingGetsBothParentsWhenTheOnlyRecordedParentHasOnePartner() {
        val rel = siblingRelation(family(), 3)
        assertEquals(Relation.ChildOf(1, 2), rel)
    }

    @Test
    fun withSeveralPartnersTheChosenOneIsUsed() {
        val f = Family.of((1..4).map { p(it) }, listOf(4 to 1), listOf(1 to 2, 1 to 3))
        assertEquals(Relation.ChildOf(1, null), siblingRelation(f, 4))
        assertEquals(Relation.ChildOf(1, 3), siblingRelation(f, 4, 3))
    }
}
