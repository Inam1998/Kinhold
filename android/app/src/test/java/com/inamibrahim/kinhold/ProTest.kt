package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.AdPlacement
import com.inamibrahim.kinhold.model.AdPolicy
import com.inamibrahim.kinhold.model.ProFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProTest {
    @Test
    fun adsNeverAppearOverTheTreeWhileEditingOrRecording() {
        for (p in listOf(AdPlacement.Tree, AdPlacement.PersonForm, AdPlacement.EventEditor, AdPlacement.Recording)) {
            assertFalse("$p must never show an ad", AdPolicy.shows(p, proUnlocked = false, adsOn = true))
        }
    }

    @Test
    fun allowedPlacesShowAnAdOnlyForFreeUsersWhenAdsAreOn() {
        for (p in AdPlacement.entries.filter { it.allowed }) {
            assertTrue(AdPolicy.shows(p, proUnlocked = false, adsOn = true))
            assertFalse("Pro removes ads", AdPolicy.shows(p, proUnlocked = true, adsOn = true))
            assertFalse("No ad service, no ad", AdPolicy.shows(p, proUnlocked = false, adsOn = false))
        }
    }

    @Test
    fun theTreeAndFormsAreTheOnlyForbiddenPlaces() {
        assertEquals(
            setOf(AdPlacement.Tree, AdPlacement.PersonForm, AdPlacement.EventEditor, AdPlacement.Recording),
            AdPlacement.entries.filter { !it.allowed }.toSet(),
        )
    }

    @Test
    fun dataSafetyFeaturesStayFree() {
        val free = ProFeatures.alwaysFree.joinToString(" ").lowercase()
        for (word in listOf("backup", "gedcom", "sharing")) assertTrue("$word must stay free", free.contains(word))
        assertTrue(ProFeatures.included.none { it.lowercase().contains("backup") })
    }
}
