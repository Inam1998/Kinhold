package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.LockDelay
import com.inamibrahim.kinhold.model.PinLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockTest {
    @Test
    fun aPinIsFourToEightDigits() {
        for (ok in listOf("1234", "00000", "12345678")) assertTrue(ok, PinLock.isValid(ok))
        for (bad in listOf("", "123", "123456789", "12a4", "12 34", "١٢٣٤")) assertFalse(bad, PinLock.isValid(bad))
    }

    @Test
    fun theRightPinVerifiesAndAWrongOneDoesNot() {
        val stored = PinLock.create("4821")
        assertTrue(PinLock.verify("4821", stored))
        assertFalse(PinLock.verify("4822", stored))
        assertFalse(PinLock.verify("482", stored))
        assertFalse(PinLock.verify("", stored))
    }

    @Test
    fun theStoredFormNeverHoldsThePinAndDiffersEachTime() {
        val a = PinLock.create("123456")
        val b = PinLock.create("123456")
        assertFalse(a.hash.contains("123456") || a.salt.contains("123456"))
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertTrue(PinLock.verify("123456", a) && PinLock.verify("123456", b))
    }

    @Test
    fun damagedStoredValuesNeverVerify() {
        assertFalse(PinLock.verify("1234", com.inamibrahim.kinhold.model.StoredPin("not base64 !!", "also not")))
        assertFalse(PinLock.verify("1234", com.inamibrahim.kinhold.model.StoredPin("", "")))
    }

    @Test
    fun anInvalidPinCannotBeStored() {
        try {
            PinLock.create("12")
            org.junit.Assert.fail("Expected a refusal")
        } catch (e: IllegalArgumentException) {
            // Refused.
        }
    }

    @Test
    fun theWaitGrowsAfterFiveWrongTriesAndStopsAtAnHour() {
        assertEquals(listOf(0, 0, 0, 0, 0, 30, 60, 120), (0..7).map { PinLock.waitSeconds(it) })
        assertEquals(3600, PinLock.waitSeconds(40))
    }

    @Test
    fun theDelaysAreOrdered() {
        assertEquals(listOf(0L, 60_000L, 300_000L), LockDelay.entries.map { it.millis })
    }
}
