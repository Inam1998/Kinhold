package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Kin
import com.inamibrahim.kinhold.model.KinLanguage
import com.inamibrahim.kinhold.model.KinWords
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Relationships
import com.inamibrahim.kinhold.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KinWordsTest {
    private fun p(id: Int, sex: Sex, born: Int? = null) = Person(id, "P$id", "", sex, born = born)

    /** 1 Grandfather > 2 Dad (b1950), 3 Dad's younger brother (b1955), 4 Dad's sister; 5 Mum's father > 6 Mum, 7 Mum's brother; 10 Me (child of 2 and 6); 11 is 3's son. */
    private val family = Family.of(
        listOf(p(1, Sex.Male), p(2, Sex.Male, 1950), p(3, Sex.Male, 1955), p(4, Sex.Female), p(5, Sex.Male), p(6, Sex.Female), p(7, Sex.Male), p(10, Sex.Male), p(11, Sex.Male)),
        listOf(2 to 1, 3 to 1, 4 to 1, 6 to 5, 7 to 5, 10 to 2, 10 to 6, 11 to 3),
        listOf(2 to 6),
    )

    private fun kin(to: Int) = Relationships.between(family, 10, to)!!.kin

    @Test
    fun theKindOfRelativeIsWorkedOutFromTheSideAndTheSex() {
        assertEquals(Kin.Father, kin(2))
        assertEquals(Kin.Mother, kin(6))
        assertEquals(Kin.PatGrandfather, kin(1))
        assertEquals(Kin.MatGrandfather, kin(5))
        assertEquals(Kin.FathersYoungerBrother, kin(3))
        assertEquals(Kin.FathersSister, kin(4))
        assertEquals(Kin.MothersBrother, kin(7))
        assertEquals(Kin.CousinPatUncleSon, kin(11))
    }

    @Test
    fun wordsInOtherLanguagesFollowTheKind() {
        val uncle = Relationships.between(family, 10, 3)!!
        assertEquals("Chacha", uncle.wordIn(KinLanguage.UrduPunjabi))
        assertEquals("Chacha", uncle.wordIn(KinLanguage.Hindi))
        assertEquals("Kaka", uncle.wordIn(KinLanguage.Bengali))
        assertEquals("Amm", uncle.wordIn(KinLanguage.Arabic))
        assertEquals("Amca", uncle.wordIn(KinLanguage.Turkish))
        assertEquals("Tío", uncle.wordIn(KinLanguage.Spanish))
        assertEquals("Ibn amm", Relationships.between(family, 10, 11)!!.wordIn(KinLanguage.Arabic))
    }

    @Test
    fun nothingIsShownWhenTheTreeDoesNotSayEnough() {
        // The parent the uncle runs through has no recorded sex, so the side of the family is unknown.
        val f = Family.of(listOf(p(1, Sex.Male), p(2, Sex.Unknown), p(3, Sex.Male), p(10, Sex.Male)), listOf(2 to 1, 3 to 1, 10 to 2), emptyList())
        val r = Relationships.between(f, 10, 3)!!
        assertNull(r.kin)
        assertNull(r.wordIn(KinLanguage.Hindi))
    }

    @Test
    fun everyLanguageOtherThanUrduHasAWordForEveryKind() {
        for (lang in KinLanguage.entries.filter { it != KinLanguage.UrduPunjabi }) {
            for (k in Kin.entries) assertNotNull("$lang has no word for $k", KinWords.of(lang, k))
        }
    }
}
