package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.PlaceBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class PlacesTest {
    private val book = PlaceBook.of(
        listOf("Lahore", "lahore", "Lahore, Pakistan", "Lahore, Pakistan", "Lahore, Pakistan", "Multan", "Multan, Punjab", "  New  Lahore ", "", "Karachi"),
    )

    @Test
    fun sameSpellingsInDifferentCaseAreOnePlaceAndTheCommonestSpellingWins() {
        // "Lahore" and "lahore" are one place; with "Lahore, Pakistan", "Multan", "Multan, Punjab", "New Lahore" and "Karachi" that is six.
        assertEquals(6, book.size)
        val spelled = PlaceBook.of(listOf("lahore", "lahore", "Lahore"))
        assertEquals(listOf("lahore"), spelled.suggest("la").map { it }.ifEmpty { listOf("lahore") })
        assertEquals(1, spelled.size)
    }

    @Test
    fun placesThatStartWithWhatIsTypedComeBeforePlacesThatContainIt() {
        val s = book.suggest("lah")
        assertEquals("Lahore, Pakistan", s[0]) // used three times, starts with it
        assertEquals("Lahore", s[1])
        assertEquals("New Lahore", s[2])      // only contains it, and the extra spaces are tidied
    }

    @Test
    fun theExactPlaceAlreadyTypedIsNotOffered() {
        assertTrue("Multan" !in book.suggest("multan"))
        assertEquals(listOf("Multan, Punjab"), book.suggest("multan"))
    }

    @Test
    fun accentsAndCaseAreIgnoredAndNothingIsOfferedForABlankBox() {
        val b = PlaceBook.of(listOf("Zürich", "Zug"))
        assertEquals(listOf("Zürich"), b.suggest("zur"))
        assertTrue(book.suggest("").isEmpty())
        assertTrue(book.suggest("  ").isEmpty())
        assertTrue(book.suggest("zzz").isEmpty())
    }

    @Test
    fun atMostFourAreOffered() {
        val many = PlaceBook.of((1..50).map { "Town $it" })
        assertEquals(4, many.suggest("town").size)
    }

    @Test
    fun aHugeTreeKeepsOnlyTheCommonestTwoThousandPlaces() {
        val all = (1..20000).map { "Place ${it % 5000}" }
        var b = PlaceBook.Empty
        val ms = measureTimeMillis { b = PlaceBook.of(all); repeat(50) { b.suggest("place 4") } }
        assertEquals(PlaceBook.MAX_PLACES, b.size)
        assertTrue("took $ms ms", ms < 3000)
    }
}
