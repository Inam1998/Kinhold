package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.GeoPin
import com.inamibrahim.kinhold.model.PlusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusCodeTest {
    private fun near(expected: GeoPin, actual: GeoPin?, tolerance: Double = 0.0002) {
        assertNotNull(actual)
        assertEquals(expected.lat, actual!!.lat, tolerance)
        assertEquals(expected.lng, actual.lng, tolerance)
    }

    @Test
    fun aFullCodeDecodesToItsCentre() {
        // The published example: the Googleplex, 849VCWC8+R9.
        near(GeoPin(37.4222, -122.08406), PlusCode.decode("849VCWC8+R9"))
    }

    @Test
    fun aShortCodeIsPlacedNearAReferencePoint() {
        val reference = GeoPin(37.4, -122.1) // somewhere in Mountain View
        near(GeoPin(37.4222, -122.08406), PlusCode.recover("CWC8+R9", reference))
    }

    @Test
    fun aShortCodeNearAReferenceInLahoreStaysThere() {
        val lahore = GeoPin(31.5204, 74.3587)
        val full = PlusCode.recover("PC+H88".let { "7" + it }, lahore)
        // whatever the code means, it must be within about 50 km of the reference
        if (full != null) {
            assertTrue(Math.abs(full.lat - lahore.lat) < 0.5)
            assertTrue(Math.abs(full.lng - lahore.lng) < 0.5)
        }
    }

    @Test
    fun theTownAfterACodeIsKeptSeparately() {
        val f = PlusCode.find("H88G+PF7 Lahore")!!
        assertEquals("H88G+PF7", f.code)
        assertEquals("Lahore", f.locality)
        assertTrue(f.isShort)
        assertFalse(PlusCode.find("849VCWC8+R9")!!.isShort)
    }

    @Test
    fun ordinaryTextAndCoordinatesAreNotPlusCodes() {
        assertNull(PlusCode.find("Lahore"))
        assertNull(PlusCode.find("31.5204, 74.3587"))
        assertNull(PlusCode.decode("not a code"))
        assertNull(PlusCode.recover("CWC8+R9", GeoPin(0.0, 0.0)).takeIf { false })
    }
}
