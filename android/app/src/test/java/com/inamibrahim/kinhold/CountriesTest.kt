package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Countries
import com.inamibrahim.kinhold.model.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountriesTest {
    private val pk = Countries.forIso("PK")

    @Test
    fun joiningDropsTheLeadingZeroAndAddsTheCode() {
        assertEquals("+92 3244691657", Countries.join(pk, "0324 4691657"))
        assertEquals("+92 3244691657", Countries.join(pk, "3244691657"))
        assertEquals("", Countries.join(pk, "  "))
        assertEquals("+39 0212345678", Countries.join(Countries.forIso("IT"), "02 1234 5678"))
    }

    @Test
    fun aStoredNumberSplitsBackIntoCountryAndNumber() {
        val (c, n) = Countries.split("+92 3244691657", Countries.forIso("GB"))
        assertEquals("PK", c!!.iso)
        assertEquals("3244691657", n)
        val (c2, n2) = Countries.split("+923244691657", Countries.forIso("GB"))
        assertEquals("PK", c2!!.iso)
        assertEquals("3244691657", n2)
        val (c3, n3) = Countries.split("00971501234567", Countries.forIso("PK"))
        assertEquals("AE", c3!!.iso)
        assertEquals("501234567", n3)
    }

    @Test
    fun aNumberWithNoCodeKeepsTheDefaultCountryAndTheTextAsTyped() {
        val (c, n) = Countries.split("0324 4691657", pk)
        assertEquals("PK", c!!.iso)
        assertEquals("0324 4691657", n)
    }

    @Test
    fun anUnknownCodeIsKeptWholeWithNoCountry() {
        val (c, n) = Countries.split("+9999 123456", pk)
        assertNull(c)
        assertEquals("+9999 123456", n)
    }

    @Test
    fun theJoinedFormWorksForWhatsAppAndTheDialler() {
        val stored = Countries.join(pk, "0324 4691657")
        assertEquals("923244691657", PhoneNumber.whatsApp(stored))
        assertEquals("+923244691657", PhoneNumber.dialable(stored))
    }

    @Test
    fun everyCountryHasAFlagAndACode() {
        for (c in Countries.all) {
            assertTrue(c.dial.startsWith("+"))
            assertEquals(2, c.flag.codePointCount(0, c.flag.length))
        }
    }
}
