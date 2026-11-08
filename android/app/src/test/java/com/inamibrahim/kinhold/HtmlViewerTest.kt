package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.HtmlViewer
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlViewerTest {
    private val family = Family.of(
        listOf(
            Person(1, "Ali", "Khan", Sex.Male, 1920, 1990, "Lahore", "Multan", notes = "Wrote </script><b>x</b> & more"),
            Person(2, "Sana", "Khan", Sex.Female, 1925, phone = "+92 300 1", address = "12 Canal Road", email = "s@example.com"),
            Person(3, "Noor", "Khan", Sex.Female, 1950),
        ),
        listOf(3 to 1, 3 to 2),
        listOf(1 to 2),
    )

    @Test
    fun thePageHoldsEveryPersonAndTheirLinks() {
        val data = JSONArray(HtmlViewer.data(family))
        assertEquals(3, data.length())
        val noor = (0 until 3).map { data.getJSONObject(it) }.first { it.getString("n") == "Noor Khan" }
        assertEquals(2, noor.getJSONArray("p").length())
        val ali = (0 until 3).map { data.getJSONObject(it) }.first { it.getString("n") == "Ali Khan" }
        assertEquals("1920", ali.getString("b"))
        assertEquals("Lahore", ali.getString("bp"))
        assertEquals(1, ali.getJSONArray("c").length())
        assertEquals(1, ali.getJSONArray("s").length())
    }

    @Test
    fun noContactDetailsOrPhotosGoIntoThePage() {
        val html = HtmlViewer.page(family, "Family of Ali", "2026-11-08")
        for (secret in listOf("+92 300 1", "12 Canal Road", "s@example.com")) assertFalse("$secret must not be in the page", html.contains(secret))
    }

    @Test
    fun textInANoteCannotBreakOutOfTheScriptBlock() {
        val html = HtmlViewer.page(family, "A <b>title</b> & more", "2026-11-08")
        // The note holds a closing script tag; in the page it must be escaped so the data block does not end early.
        val dataStart = html.indexOf("id=\"data\"")
        val dataEnd = html.indexOf("</script>", dataStart)
        assertTrue(html.substring(dataStart, dataEnd).contains("<" + "\\" + "/script>"))
        assertTrue(html.contains("<title>A &lt;b&gt;title&lt;/b&gt; &amp; more</title>"))
        assertEquals(1, Regex("<title>").findAll(html).count())
    }

    @Test
    fun aBigTreeMakesAReasonablySizedPage() {
        val people = (1..3000).map { Person(it, "Given$it", "Family${it % 40}", Sex.Unknown, 1800 + it % 200, birthPlace = "Town${it % 100}") }
        val links = (2..3000).map { it to it / 2 }
        val html = HtmlViewer.page(Family.of(people, links, emptyList()), "Big", "2026-11-08")
        assertTrue("page is ${html.length} characters", html.length < 1_500_000)
    }

    @Test
    fun writeASamplePageForTheScriptCheck() {
        java.io.File("build").mkdirs()
        java.io.File("build/sample-family.html").writeText(HtmlViewer.page(family, "Family of Ali", "2026-11-08"))
    }
}
