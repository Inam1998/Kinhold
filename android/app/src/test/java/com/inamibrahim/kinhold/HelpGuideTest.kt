package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.HelpGuide
import com.inamibrahim.kinhold.model.HelpTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelpGuideTest {
    @Test
    fun everyFeatureHasExactlyOnePage() {
        for (topic in HelpTopic.entries) {
            assertEquals("Topic $topic needs exactly one page", 1, HelpGuide.pages.count { it.topic == topic })
        }
        assertEquals(HelpTopic.entries.size, HelpGuide.pages.size)
    }

    @Test
    fun everyPageHasContentAndStatesItsLimits() {
        for (page in HelpGuide.pages) {
            assertTrue("${page.title} has no title", page.title.isNotBlank())
            assertTrue("${page.title} has no summary", page.summary.isNotBlank())
            assertTrue("${page.title} has no sections", page.sections.isNotEmpty())
            assertTrue("${page.title} does not state its limits", page.limits.isNotEmpty())
            for (s in page.sections) {
                assertTrue(s.heading.isNotBlank())
                assertTrue(s.text.isNotBlank())
            }
            assertTrue(page.limits.all { it.isNotBlank() })
        }
    }

    @Test
    fun titlesAreUniqueSoAPageCanBeToldApart() {
        val titles = HelpGuide.pages.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun sentencesEndProperlyAndNoPlaceholdersAreLeft() {
        val all = HelpGuide.pages.flatMap { p -> p.sections.map { it.text } + p.limits + p.summary }
        for (text in all) {
            assertTrue("Should end with a full stop: $text", text.trimEnd().endsWith("."))
            assertFalse("Left-over placeholder: $text", Regex("""TODO|TBD|FIXME|lorem""", RegexOption.IGNORE_CASE).containsMatchIn(text))
            assertFalse("Double space in: $text", text.contains("  "))
        }
    }

    @Test
    fun theGuideNeverPromisesWhatTheAppDoesNotDo() {
        // These claims would be false today. If a feature is built, change the page and this test together.
        val text = HelpGuide.pages.joinToString(" ") { p -> p.sections.joinToString(" ") { it.text } }.lowercase()
        assertFalse(text.contains("encrypted backup"))
        assertFalse(text.contains("syncs"))
    }

    @Test
    fun theNoInternetClaimIsTrueOfTheManifest() {
        val says = HelpGuide.pages.flatMap { p -> p.sections.map { it.text } }.any { it.contains("no internet permission") }
        assertTrue("The Privacy page should say the app has no internet permission", says)
        // Adding ads or sync needs INTERNET; when that happens, change the Privacy page and the store listing too.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("The manifest asks for INTERNET, so the Privacy page is no longer true", manifest.contains("android.permission.INTERNET"))
    }

    @Test
    fun lookingUpATopicGivesItsPage() {
        assertEquals("Privacy", HelpGuide.page(HelpTopic.Privacy).title)
    }
}
