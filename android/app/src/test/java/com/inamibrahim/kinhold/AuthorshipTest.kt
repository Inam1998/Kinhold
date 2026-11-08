package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Authorship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorshipTest {
    @Test
    fun theStoredPhraseHashIsSetAndWellFormed() {
        assertTrue(Authorship.isSet)
        assertEquals(64, Authorship.HASH.length)
        assertEquals(32, Authorship.SALT.length)
    }

    @Test
    fun aPhraseMatchesIgnoringCapitalsAndExtraSpacesButNotOtherWords() {
        val salt = "00112233445566778899aabbccddeeff"
        val hash = Authorship.hash("Tulip walnut ember", salt, 1000)
        assertTrue(Authorship.check("  tulip   WALNUT ember ", hash, salt, 1000))
        assertFalse(Authorship.check("tulip walnut embers", hash, salt, 1000))
        assertFalse(Authorship.check("", hash, salt, 1000))
    }

    @Test
    fun theHashMatchesTheOneScriptsSetPhraseMjsMakes() {
        // Computed with Node's pbkdf2Sync, the same call scripts/set-phrase.mjs uses.
        assertEquals("2b90808c8c9944f601a97e6fec768c2fa6aa4fd0ff36b90d23988e33e35c466d", Authorship.hash("tulip walnut ember", "00112233445566778899aabbccddeeff", 1000))
    }

    @Test
    fun wrongPhraseDoesNotMatchTheStoredHash() {
        assertFalse(Authorship.check("not the phrase at all really"))
    }
}
