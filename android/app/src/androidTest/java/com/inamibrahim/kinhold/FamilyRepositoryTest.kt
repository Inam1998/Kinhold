package com.inamibrahim.kinhold

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the phone against real SQLite. Most tests use a throwaway in-memory database, so they never touch the tree
 * stored in the installed app.
 */
@RunWith(AndroidJUnit4::class)
class FamilyRepositoryTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun memoryRepo() = FamilyRepository(context, dbName = null).also { runBlocking { it.load() } }

    @Test
    fun aNewTreeIsEmpty() {
        assertEquals(0, memoryRepo().family.value.size)
    }

    @Test
    fun aSavedPersonComesBackWithEveryField() = runBlocking {
        val repo = memoryRepo()
        val id = repo.addPerson(PersonDraft(" Ayesha ", "Khan", Sex.Female, 1950, null, "Lahore", "", "Likes mangoes"))
        val p = repo.family.value.person(id)
        assertEquals("Ayesha", p.given)
        assertEquals("Khan", p.surname)
        assertEquals(Sex.Female, p.sex)
        assertEquals(1950, p.born)
        assertNull(p.died)
        assertEquals("Lahore", p.birthPlace)
        assertEquals("Likes mangoes", p.notes)
    }

    @Test
    fun addingAParentChildAndSpouseConnectsThem() = runBlocking {
        val repo = memoryRepo()
        val me = repo.addPerson(PersonDraft("Me", "Khan", Sex.Male))
        val mum = repo.addPerson(PersonDraft("Mum", "Khan", Sex.Female), Relation.ParentOf(me))
        val wife = repo.addPerson(PersonDraft("Wife", "Ali", Sex.Female), Relation.SpouseOf(me))
        val kid = repo.addPerson(PersonDraft("Kid", "Khan"), Relation.ChildOf(me))
        val f = repo.family.value
        assertEquals(listOf(mum), f.parentsOf(me).map { it.id })
        assertEquals(listOf(wife), f.spousesOf(me).map { it.id })
        // A child of someone with a spouse is a child of both.
        assertEquals(setOf(me, wife), f.parentsOf(kid).map { it.id }.toSet())
        assertEquals(listOf(kid), f.childrenOf(wife).map { it.id })
    }

    @Test
    fun twoParentsOfTheSameChildBecomeACouple() = runBlocking {
        val repo = memoryRepo()
        val me = repo.addPerson(PersonDraft("Me"))
        val dad = repo.addPerson(PersonDraft("Dad", sex = Sex.Male), Relation.ParentOf(me))
        val mum = repo.addPerson(PersonDraft("Mum", sex = Sex.Female), Relation.ParentOf(me))
        val f = repo.family.value
        assertEquals(listOf(mum), f.spousesOf(dad).map { it.id })
        assertEquals(listOf(dad), f.spousesOf(mum).map { it.id })
    }

    @Test
    fun editingChangesFieldsAndCanClearYears() = runBlocking {
        val repo = memoryRepo()
        val id = repo.addPerson(PersonDraft("Old", "Name", born = 1900, died = 1980))
        repo.updatePerson(id, PersonDraft("New", "Name", Sex.Male, born = 1901, died = null))
        val p = repo.family.value.person(id)
        assertEquals("New", p.given)
        assertEquals(1901, p.born)
        assertNull(p.died)
        assertEquals(Sex.Male, p.sex)
    }

    @Test
    fun deletingAPersonRemovesTheirLinksButKeepsRelatives() = runBlocking {
        val repo = memoryRepo()
        val parent = repo.addPerson(PersonDraft("Parent"))
        val spouse = repo.addPerson(PersonDraft("Spouse"), Relation.SpouseOf(parent))
        val child = repo.addPerson(PersonDraft("Child"), Relation.ChildOf(parent))
        repo.deletePerson(parent)
        val f = repo.family.value
        assertTrue(parent !in f)
        assertEquals(2, f.size)
        assertTrue(f.parentsOf(child).map { it.id } == listOf(spouse))
        assertTrue(f.spousesOf(spouse).isEmpty())
    }

    @Test
    fun aFailedSaveLeavesTheTreeExactlyAsItWas() = runBlocking {
        val repo = memoryRepo()
        repo.addPerson(PersonDraft("Existing"))
        try {
            // The relative does not exist, so linking fails after the new person was inserted.
            repo.addPerson(PersonDraft("Ghost"), Relation.ChildOf(9999))
            fail("Expected the save to fail")
        } catch (expected: Exception) {
            // The whole change must have been rolled back, including the person who was inserted first.
        }
        val names = repo.family.value.people.map { it.given }
        assertEquals(listOf("Existing"), names)
    }

    @Test
    fun theSampleFamilyFillsAnEmptyTreeOnce() = runBlocking {
        val repo = memoryRepo()
        repo.addSampleFamily(300)
        assertEquals(300, repo.family.value.size)
        repo.addSampleFamily(60)
        assertEquals(300, repo.family.value.size)
        assertTrue(repo.family.value.defaultFocus() != null)
    }

    @Test
    fun theTreeSurvivesClosingAndReopeningTheApp() = runBlocking {
        val file = "kinhold-test-${System.nanoTime()}.db"
        try {
            val first = FamilyRepository(context, file)
            val me = first.addPerson(PersonDraft("Me", "Khan", born = 1990))
            first.addPerson(PersonDraft("Mum", "Khan"), Relation.ParentOf(me))

            val reopened = FamilyRepository(context, file)
            reopened.load()
            val f = reopened.family.value
            assertEquals(2, f.size)
            assertEquals("Mum", f.parentsOf(me).single().given)
            assertEquals(1990, f.person(me).born)
        } finally {
            context.deleteDatabase(file)
        }
    }
}
