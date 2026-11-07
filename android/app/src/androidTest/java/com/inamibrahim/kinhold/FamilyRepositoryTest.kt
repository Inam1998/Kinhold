package com.inamibrahim.kinhold

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs on the phone against real SQLite. Most tests use a throwaway in-memory database, so they never touch the tree
 * stored in the installed app.
 */
@RunWith(AndroidJUnit4::class)
class FamilyRepositoryTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val safetyFiles = ArrayList<File>()

    private fun safetyFile() = File(context.cacheDir, "safety-test-${System.nanoTime()}.json").also { safetyFiles += it }

    private fun memoryRepo() = FamilyRepository(context, dbName = null, safetyFile = safetyFile()).also { runBlocking { it.load() } }

    @After
    fun cleanUp() {
        safetyFiles.forEach { it.delete() }
    }

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
    fun aDeletedPersonCanBeBroughtBackWithTheirLinks() = runBlocking {
        val repo = memoryRepo()
        val grandparent = repo.addPerson(PersonDraft("Grandparent"))
        val parent = repo.addPerson(PersonDraft("Parent", "Khan", Sex.Male, 1960), Relation.ChildOf(grandparent))
        val spouse = repo.addPerson(PersonDraft("Spouse"), Relation.SpouseOf(parent))
        val child = repo.addPerson(PersonDraft("Child"), Relation.ChildOf(parent))
        val deleted = repo.deletePerson(parent)!!
        assertTrue(parent !in repo.family.value)
        repo.restoreDeleted(deleted)
        val f = repo.family.value
        assertEquals(parent, f.person(parent).id)
        assertEquals("Khan", f.person(parent).surname)
        assertEquals(1960, f.person(parent).born)
        assertEquals(listOf(grandparent), f.parentsOf(parent).map { it.id })
        assertEquals(listOf(spouse), f.spousesOf(parent).map { it.id })
        assertEquals(setOf(parent, spouse), f.parentsOf(child).map { it.id }.toSet())
    }

    @Test
    fun undoingADeleteSkipsRelativesWhoHaveSinceBeenDeleted() = runBlocking {
        val repo = memoryRepo()
        val parent = repo.addPerson(PersonDraft("Parent"))
        val child = repo.addPerson(PersonDraft("Child"), Relation.ChildOf(parent))
        val deleted = repo.deletePerson(parent)!!
        repo.deletePerson(child)
        repo.restoreDeleted(deleted)
        assertEquals(listOf("Parent"), repo.family.value.people.map { it.given })
    }

    @Test
    fun deletingSomeoneWhoIsNotThereChangesNothing() = runBlocking {
        val repo = memoryRepo()
        repo.addPerson(PersonDraft("Only"))
        assertNull(repo.deletePerson(12345))
        assertEquals(1, repo.family.value.size)
    }

    @Test
    fun restoringABackupReplacesTheTreeAndKeepsTheOldOneForUndo() = runBlocking {
        val source = memoryRepo()
        val me = source.addPerson(PersonDraft("Backup", "Person", born = 1980))
        source.addPerson(PersonDraft("Mum"), Relation.ParentOf(me))
        val backup = BackupCodec.decode(source.exportBackup())

        val repo = memoryRepo()
        repo.addPerson(PersonDraft("Current"))
        repo.addPerson(PersonDraft("Tree"))
        assertTrue(!repo.hasSafetyCopy())

        repo.replaceAll(backup)
        val f = repo.family.value
        assertEquals(2, f.size)
        assertEquals(setOf("Backup", "Mum"), f.people.map { it.given }.toSet())
        assertEquals("Mum", f.parentsOf(me).single().given)
        assertTrue(repo.hasSafetyCopy())

        repo.undoRestore()
        assertEquals(setOf("Current", "Tree"), repo.family.value.people.map { it.given }.toSet())
        // Undoing swaps the two trees, so the restored one is now the safety copy.
        repo.undoRestore()
        assertEquals(setOf("Backup", "Mum"), repo.family.value.people.map { it.given }.toSet())
    }

    @Test
    fun aRestoreThatFailsHalfwayLeavesTheTreeExactlyAsItWas() = runBlocking {
        val repo = memoryRepo()
        repo.addPerson(PersonDraft("Keep", "Me"))
        // Two people with the same id make the database refuse the second one after the first was stored.
        val broken = BackupData(
            people = listOf(Person(1, "A", ""), Person(1, "B", "")),
            parentLinks = emptyList(),
            partnerships = emptyList(),
        )
        try {
            repo.replaceAll(broken)
            fail("Expected the restore to fail")
        } catch (expected: Exception) {
            // Everything it did must have been undone, including removing the existing tree.
        }
        assertEquals(listOf("Keep"), repo.family.value.people.map { it.given })
        // The tree on disk, not just the one on screen.
        repo.load()
        assertEquals(listOf("Keep"), repo.family.value.people.map { it.given })
    }

    @Test
    fun aBackupOfALargeTreeRestoresExactly() = runBlocking {
        val source = memoryRepo()
        source.addSampleFamily(1000)
        val backup = BackupCodec.decode(source.exportBackup())
        val repo = memoryRepo()
        repo.replaceAll(backup)
        val a = source.family.value
        val b = repo.family.value
        assertEquals(1000, b.size)
        for (p in a.people) {
            assertEquals(p.name, b.person(p.id).name)
            assertEquals(a.parentsOf(p.id).map { it.id }, b.parentsOf(p.id).map { it.id })
            assertEquals(a.spousesOf(p.id).map { it.id }.sorted(), b.spousesOf(p.id).map { it.id }.sorted())
        }
    }

    @Test
    fun addingAnImportKeepsEveryoneAlreadyThereAndCanBeUndone() = runBlocking {
        val repo = memoryRepo()
        val mine = repo.addPerson(PersonDraft("Mine", "Own"))
        repo.addPerson(PersonDraft("Mum"), Relation.ParentOf(mine))
        // Ids in the file overlap the ids already in the tree, so they must be renumbered, not reused.
        val data = BackupData(
            people = listOf(Person(1, "Imported", "Parent", Sex.Male), Person(2, "Imported", "Child"), Person(3, "Imported", "Spouse", Sex.Female)),
            parentLinks = listOf(2 to 1, 2 to 3),
            partnerships = listOf(1 to 3),
        )
        repo.addAll(data)
        val f = repo.family.value
        assertEquals(5, f.size)
        assertEquals("Mum", f.parentsOf(mine).single().given)
        val child = f.people.single { it.given == "Imported" && it.surname == "Child" }
        assertEquals(setOf("Parent", "Spouse"), f.parentsOf(child.id).map { it.surname }.toSet())
        val parent = f.people.single { it.surname == "Parent" }
        assertEquals(listOf("Spouse"), f.spousesOf(parent.id).map { it.surname })
        assertTrue(repo.hasSafetyCopy())

        repo.undoRestore()
        assertEquals(setOf("Mine", "Mum"), repo.family.value.people.map { it.given }.toSet())
    }

    @Test
    fun aGedcomExportReadBackAndRestoredMakesTheSameTree() = runBlocking {
        val source = memoryRepo()
        source.addSampleFamily(300)
        val gedcom = source.exportGedcom("test", "1 JAN 2026")
        val parsed = com.inamibrahim.kinhold.model.Gedcom.read(gedcom.toByteArray(Charsets.UTF_8))
        val repo = memoryRepo()
        repo.replaceAll(parsed.data)
        val a = source.family.value
        val b = repo.family.value
        assertEquals(300, b.size)
        assertEquals(a.people.map { it.name }.sorted(), b.people.map { it.name }.sorted())
        assertEquals(a.parentLinks().size, b.parentLinks().size)
        assertEquals(a.partnerships().size, b.partnerships().size)
    }

    @Test
    fun newPeopleAddedAfterARestoreDoNotReuseRestoredIds() = runBlocking {
        val repo = memoryRepo()
        repo.replaceAll(BackupData(listOf(Person(500, "High", "Id")), emptyList(), emptyList()))
        val id = repo.addPerson(PersonDraft("New"))
        assertTrue("New id $id must differ from 500", id != 500)
        assertEquals(2, repo.family.value.size)
    }

    @Test
    fun theTreeSurvivesClosingAndReopeningTheApp() = runBlocking {
        val file = "kinhold-test-${System.nanoTime()}.db"
        try {
            val first = FamilyRepository(context, file, safetyFile())
            val me = first.addPerson(PersonDraft("Me", "Khan", born = 1990))
            first.addPerson(PersonDraft("Mum", "Khan"), Relation.ParentOf(me))

            val reopened = FamilyRepository(context, file, safetyFile())
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
