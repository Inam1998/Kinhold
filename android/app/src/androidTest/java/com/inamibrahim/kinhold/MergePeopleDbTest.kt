package com.inamibrahim.kinhold

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.CustomField
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone: joining two entries of one person moves everything to the one that stays, and can be undone. */
@RunWith(AndroidJUnit4::class)
class MergePeopleDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun repo() = FamilyRepository(context, null, File(context.cacheDir, "mp-safety-${System.nanoTime()}.json").also { files += it })
        .also { runBlocking { it.load() } }

    @Test
    fun theKeptPersonGetsLinksDetailsEventsAndFacts() = runBlocking {
        val r = repo()
        val mum = r.addPerson(PersonDraft("Mum", sex = Sex.Female))
        val first = r.addPerson(PersonDraft("Ahmed", "Raza", Sex.Male, born = 1948), Relation.ChildOf(mum), details = PersonDetails(listOf(Event(EventKind.Burial)), fields = listOf(CustomField("A", "1"))))
        val wife = r.addPerson(PersonDraft("Naseem"), Relation.SpouseOf(first))
        val dup = r.addPerson(
            PersonDraft("Ahmed", "Raza", born = 1948, birthPlace = "Multan", phone = "123", notes = "from the second entry"),
            details = PersonDetails(listOf(Event(EventKind.Occupation, "Teacher")), fields = listOf(CustomField("B", "2"))),
        )
        val kid = r.addPerson(PersonDraft("Kid"), Relation.ChildOf(dup))
        val dupWife = r.addPerson(PersonDraft("Sara"), Relation.SpouseOf(dup), details = PersonDetails(coupleEvents = mapOf(dup to listOf(Event(EventKind.Marriage, date = PartialDate(1979))))))

        r.mergePeople(first, dup)
        val f = r.family.value
        assertFalse(dup in f)
        val kept = f.person(first)
        assertEquals("Multan", kept.birthPlace)       // filled from the other entry
        assertEquals("123", kept.phone)
        assertEquals(Sex.Male, kept.sex)               // the kept entry's own value stays
        assertTrue(kept.notes.contains("second entry"))
        assertEquals(setOf(mum), f.parentsOf(first).map { it.id }.toSet())
        assertEquals(setOf(wife, dupWife), f.spousesOf(first).map { it.id }.toSet())
        assertEquals(listOf(kid), f.childrenOf(first).map { it.id })
        val d = r.details(first)
        assertEquals(setOf(EventKind.Burial, EventKind.Occupation), d.events.map { it.kind }.toSet())
        assertEquals(setOf("A", "B"), d.fields.map { it.label }.toSet())
        assertEquals(listOf(PartialDate(1979)), d.coupleEvents.getValue(dupWife).map { it.date })
    }

    @Test
    fun theMergeCanBeUndoneAndAPersonKeepsAtMostFourParents() = runBlocking {
        val r = repo()
        val a = r.addPerson(PersonDraft("Dad"))
        val b = r.addPerson(PersonDraft("Mum"))
        val c = r.addPerson(PersonDraft("Step"))
        val one = r.addPerson(PersonDraft("Kid"), Relation.ChildOf(a, b))
        val two = r.addPerson(PersonDraft("Kid"), Relation.ChildOf(c))
        r.addPerson(PersonDraft("X"), Relation.ParentOf(two))
        r.addPerson(PersonDraft("Y"), Relation.ParentOf(two))
        r.mergePeople(one, two)
        assertEquals(4, r.family.value.parentsOf(one).size) // the fifth parent link is dropped
        assertTrue(r.hasSafetyCopy())
        r.undoRestore()
        assertEquals(7, r.family.value.size)
        assertEquals(setOf("Kid"), r.family.value.people.filter { it.given == "Kid" }.map { it.given }.toSet())
        assertEquals(2, r.family.value.people.count { it.given == "Kid" })
    }
}
