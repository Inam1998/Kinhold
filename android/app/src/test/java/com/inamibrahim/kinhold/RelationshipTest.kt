package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Relationships
import com.inamibrahim.kinhold.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipTest {
    private fun p(id: Int, sex: Sex, born: Int? = null) = Person(id, "P$id", "", sex, born = born)

    /**
     * 1 Great-grandfather > 2 Grandfather > 3 Dad (b1950), 4 Chacha (b1955), 5 Phupho; 7 Nana > 6 Mum, 8 Mamu, 9 Khala.
     * Me 10 (child of 3 and 6), Sis 13, half-brother 14 (child of 3 and 15). 11 is Chacha's son, 12 Mamu's daughter.
     * Wife 16 of Me, with her father 18 and brother 17. 19 is Chacha's wife. Son 20 of Me and Wife, grandson 21.
     */
    private val family = Family.of(
        listOf(
            p(1, Sex.Male), p(2, Sex.Male), p(3, Sex.Male, 1950), p(4, Sex.Male, 1955), p(5, Sex.Female),
            p(6, Sex.Female), p(7, Sex.Male), p(8, Sex.Male), p(9, Sex.Female),
            p(10, Sex.Male), p(11, Sex.Male), p(12, Sex.Female), p(13, Sex.Female), p(14, Sex.Male), p(15, Sex.Female),
            p(16, Sex.Female), p(17, Sex.Male), p(18, Sex.Male), p(19, Sex.Female), p(20, Sex.Male), p(21, Sex.Male),
            p(22, Sex.Male), p(23, Sex.Unknown), p(24, Sex.Male),
        ),
        listOf(
            2 to 1, 3 to 2, 4 to 2, 5 to 2, 6 to 7, 8 to 7, 9 to 7, 10 to 3, 10 to 6, 13 to 3, 13 to 6, 14 to 3, 14 to 15,
            11 to 4, 12 to 8, 16 to 18, 17 to 18, 20 to 10, 20 to 16, 21 to 20, 24 to 23,
        ),
        listOf(3 to 6, 3 to 15, 10 to 16, 4 to 19),
    )

    private fun rel(from: Int, to: Int) = Relationships.between(family, from, to)

    @Test
    fun parentsAndGrandparentsOnBothSides() {
        assertEquals("Father" to "Abbu (Walid)", rel(10, 3)!!.let { it.english to it.local })
        assertEquals("Mother" to "Ammi (Walida)", rel(10, 6)!!.let { it.english to it.local })
        assertEquals("Grandfather" to "Dada", rel(10, 2)!!.let { it.english to it.local })
        assertEquals("Grandfather" to "Nana", rel(10, 7)!!.let { it.english to it.local })
        assertEquals("Great-grandfather" to "Pardada", rel(10, 1)!!.let { it.english to it.local })
    }

    @Test
    fun uncleAndAuntTermsFollowTheSideAndTheAge() {
        assertEquals("Uncle" to "Chacha", rel(10, 4)!!.let { it.english to it.local }) // younger than Dad
        assertEquals("Aunt" to "Phupho (Phuppi)", rel(10, 5)!!.let { it.english to it.local })
        assertEquals("Uncle" to "Mamu", rel(10, 8)!!.let { it.english to it.local })
        assertEquals("Aunt" to "Khala", rel(10, 9)!!.let { it.english to it.local })
    }

    @Test
    fun anUnclesAgeIsNeededForChachaOrTau() {
        val f = Family.of(listOf(p(1, Sex.Male), p(2, Sex.Male), p(3, Sex.Male), p(4, Sex.Male, 1940), p(5, Sex.Male, 1950)), listOf(2 to 1, 3 to 1, 4 to 2, 5 to 2), emptyList())
        // Person 4 (older) is the uncle of 3's child? Build: 6 is a child of 3 and so on is not needed: ask 5 about their father's brother.
        val g = Family.of(
            listOf(p(1, Sex.Male), p(2, Sex.Male, 1950), p(3, Sex.Male, 1940), p(4, Sex.Male)),
            listOf(2 to 1, 3 to 1, 4 to 2), emptyList(),
        )
        assertEquals("Tau", Relationships.between(g, 4, 3)!!.local) // father's older brother
        assertEquals(5, f.size)
    }

    @Test
    fun siblingsHalfSiblingsAndCousins() {
        assertEquals("Sister" to "Behan", rel(10, 13)!!.let { it.english to it.local })
        assertEquals("Half-brother", rel(10, 14)!!.english)
        assertEquals("First cousin" to "Chachazad bhai", rel(10, 11)!!.let { it.english to it.local })
        assertEquals("First cousin" to "Mamuzad behan", rel(10, 12)!!.let { it.english to it.local })
    }

    @Test
    fun childrenGrandchildrenNiecesAndNephews() {
        assertEquals("Son" to "Beta", rel(3, 10)!!.let { it.english to it.local })
        assertEquals("Grandson" to "Pota", rel(2, 10)!!.let { it.english to it.local })    // through a son
        assertEquals("Grandson" to "Nawasa", rel(7, 10)!!.let { it.english to it.local })  // through a daughter
        assertEquals("Grandson" to "Pota", rel(10, 21)!!.let { it.english to it.local })
        assertEquals("Nephew" to "Bhatija", rel(4, 10)!!.let { it.english to it.local })    // brother's son
    }

    @Test
    fun cousinsOnceRemovedAndSecondCousins() {
        val f = Family.of(
            (1..8).map { p(it, Sex.Male) },
            // 1 is the common ancestor; 2 and 3 are his children; 4 is 2's child, 5 is 3's child, 6 is 4's child.
            listOf(2 to 1, 3 to 1, 4 to 2, 5 to 3, 6 to 4, 7 to 5), emptyList(),
        )
        assertEquals("First cousin", Relationships.between(f, 4, 5)!!.english)
        assertEquals("First cousin, once removed", Relationships.between(f, 6, 5)!!.english)
        assertEquals("Second cousin", Relationships.between(f, 6, 7)!!.english)
    }

    @Test
    fun marriageRelationships() {
        assertEquals("Wife" to "Biwi", rel(10, 16)!!.let { it.english to it.local })
        assertEquals("Husband" to "Shauhar", rel(16, 10)!!.let { it.english to it.local })
        assertEquals("Spouse's father (in-law)", rel(10, 18)!!.english)
        assertEquals("Susar (father-in-law)", rel(10, 18)!!.local)
        assertEquals("Spouse's brother (in-law)" to "Saala (wife's brother)", rel(10, 17)!!.let { it.english to it.local })
        assertEquals("Chachi (or Tai)", rel(10, 19)!!.local)
        assertTrue(!rel(10, 19)!!.blood)
    }

    @Test
    fun thePathNamesThePeopleBetween() {
        val path = rel(10, 11)!!.path.map { it.id }
        assertEquals(listOf(10, 3, 2, 4, 11), path)
    }

    @Test
    fun unrelatedPeopleAndMissingPeopleGiveNothing() {
        assertNull(rel(10, 22))
        assertNull(rel(10, 999))
        assertEquals("The same person", rel(10, 10)!!.english)
    }

    @Test
    fun unknownSexGivesNeutralWordsAndNoLocalTerm() {
        val r = Relationships.between(family, 24, 23)!!
        assertEquals("Parent", r.english)
        assertNull(r.local)
    }
}
