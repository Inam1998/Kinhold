package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.CheckKind
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.MarriageDate
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TreeChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksTest {
    private fun p(id: Int, given: String, sex: Sex, born: Int? = null, died: Int? = null, surname: String = "Khan", place: String = "") =
        Person(id, given, surname, sex, born = born, died = died, birthPlace = place)

    private fun kinds(f: Family, marriages: List<MarriageDate> = emptyList()) = TreeChecks.run(f, marriages, 2026).map { it.kind }

    @Test
    fun aCleanTreeHasNoFindings() {
        val f = Family.of(listOf(p(1, "Dad", Sex.Male, 1950, 2010), p(2, "Mum", Sex.Female, 1955), p(3, "Kid", Sex.Male, 1980)), listOf(3 to 1, 3 to 2), listOf(1 to 2))
        assertTrue(TreeChecks.run(f, listOf(MarriageDate(1, 2, PartialDate(1975))), 2026).isEmpty())
    }

    @Test
    fun parentAgesAreChecked() {
        val f = Family.of(
            listOf(p(1, "Dad", Sex.Male, 1980), p(2, "Kid", Sex.Male, 1975), p(3, "Boy", Sex.Male, 1960), p(4, "Child", Sex.Male, 1968), p(5, "Mum", Sex.Female, 1900), p(6, "Late", Sex.Male, 1960)),
            listOf(2 to 1, 4 to 3, 6 to 5), emptyList(),
        )
        val k = kinds(f)
        assertTrue(CheckKind.ChildBeforeParent in k)  // born 1975 to a father born 1980
        assertTrue(CheckKind.ParentTooYoung in k)     // a father of 8
        assertTrue(CheckKind.ParentTooOld in k)       // a mother of 60
    }

    @Test
    fun aParentWhoDiedBeforeTheBirthIsFlaggedExceptForAFathersLastMonths() {
        val f = Family.of(
            listOf(p(1, "Dad", Sex.Male, 1950, 1979), p(2, "Mum", Sex.Female, 1952, 1979), p(3, "Kid", Sex.Male, 1980), p(4, "Dad2", Sex.Male, 1950, 1979), p(5, "Kid2", Sex.Male, 1980)),
            listOf(3 to 1, 3 to 2, 5 to 4), emptyList(),
        )
        val found = TreeChecks.run(f, emptyList(), 2026).filter { it.kind == CheckKind.ParentDiedBeforeBirth }
        // The father died in 1979 and the child was born in 1980: possible. The mother died in 1979: not.
        assertEquals(listOf("Mum"), found.map { it.people[1].given })
    }

    @Test
    fun marriagesBeforeBirthOrAtATenderAgeAreFlagged() {
        val f = Family.of(listOf(p(1, "A", Sex.Male, 1950), p(2, "B", Sex.Female, 1960)), emptyList(), listOf(1 to 2))
        assertEquals(setOf(CheckKind.MarriageBeforeBirth, CheckKind.MarriageTooYoung), kinds(f, listOf(MarriageDate(1, 2, PartialDate(1955)))).toSet()) // B was not yet born, and A was only 5
        assertEquals(listOf(CheckKind.MarriageTooYoung), kinds(f, listOf(MarriageDate(1, 2, PartialDate(1970)))))
    }

    @Test
    fun aLoopOfAncestorsIsFoundAndAnUnnamedOrVeryOldPersonIsMentioned() {
        val loop = Family.of(listOf(p(1, "A", Sex.Male), p(2, "B", Sex.Male)), listOf(1 to 2, 2 to 1), emptyList())
        assertTrue(CheckKind.LoopInTree in kinds(loop))
        val odd = Family.of(listOf(Person(1, "", "", born = 1890), p(2, "Old", Sex.Male, 1900, 1990)), emptyList(), emptyList())
        val k = kinds(odd)
        assertTrue(CheckKind.NoName in k)
        assertTrue(CheckKind.OldWithoutDeath in k)
        assertEquals(2, k.size)
    }

    @Test
    fun duplicatesAreSameNameAndBirthYearButNeverRelativesOrSpouses() {
        val f = Family.of(
            listOf(
                p(1, "Ahmed", Sex.Male, 1948, place = "Multan"), p(2, "AHMED", Sex.Male, 1948, place = "multan"), // the same man entered twice
                p(3, "Ahmed", Sex.Male, 1980), p(4, "Ahmed", Sex.Male, 1980, surname = "Raza"),                     // different surnames: not compared
                p(5, "Sara", Sex.Female, 1950), p(6, "Sara", Sex.Female, 1950),                                   // married to each other: not offered
                p(7, "Ali", Sex.Male, 1970), p(8, "Ali", Sex.Female, 1970),                                       // different sex: not offered
            ),
            emptyList(), listOf(5 to 6),
        )
        val d = TreeChecks.duplicates(f)
        assertEquals(1, d.size)
        assertEquals(setOf(1, 2), setOf(d[0].a.id, d[0].b.id))
        assertTrue(d[0].why.contains("same name"))
    }

    @Test
    fun aParentAndChildWithTheSameNameAreNotDuplicates() {
        val f = Family.of(listOf(p(1, "Ali", Sex.Male, 1950), p(2, "Ali", Sex.Male, 1950)), listOf(2 to 1), emptyList())
        assertTrue(TreeChecks.duplicates(f).isEmpty())
    }
}
