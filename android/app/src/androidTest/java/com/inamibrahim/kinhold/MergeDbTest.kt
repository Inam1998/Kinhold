package com.inamibrahim.kinhold

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.MergeField
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.PersonUid
import com.inamibrahim.kinhold.model.ShareDirection
import com.inamibrahim.kinhold.model.ShareScope
import com.inamibrahim.kinhold.model.Sex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs on the phone against real SQLite: a father builds his tree, shares a branch, and the child merges it into their own
 * tree without duplicates or overwrites. Both trees are throwaway in-memory databases.
 */
@RunWith(AndroidJUnit4::class)
class MergeDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun repo() = FamilyRepository(context, null, File(context.cacheDir, "merge-safety-${System.nanoTime()}.json").also { files += it })
        .also { runBlocking { it.load() } }

    private val teacher = Event(EventKind.Occupation, "Teacher", PartialDate(1975), "Multan")
    private val wedding = Event(EventKind.Marriage, "", PartialDate(1970), "Multan")

    /** The father's tree: Ahmed and Naseem (married 1970), their children Inam and Sana, and Ahmed's father Rashid. */
    private fun fathersRepo() = repo().also { r ->
        runBlocking {
            val ahmed = r.addPerson(PersonDraft("Ahmed", "Raza", Sex.Male, born = 1948, birthPlace = "Multan"), details = PersonDetails(listOf(teacher)))
            r.addPerson(PersonDraft("Rashid", "Raza", Sex.Male, born = 1920), Relation.ParentOf(ahmed))
            val naseem = r.addPerson(PersonDraft("Naseem", "Bibi", Sex.Female, born = 1952), Relation.SpouseOf(ahmed), details = PersonDetails(coupleEvents = mapOf(ahmed to listOf(wedding))))
            r.addPerson(PersonDraft("Inam", "Raza", Sex.Male, born = 1998), Relation.ChildOf(ahmed, naseem))
            r.addPerson(PersonDraft("Sana", "Raza", Sex.Female, born = 2001), Relation.ChildOf(ahmed, naseem))
        }
    }

    private fun shared(father: FamilyRepository, scope: ShareScope = ShareScope()): com.inamibrahim.kinhold.model.BackupData {
        val ahmed = father.family.value.people.first { it.given == "Ahmed" }.id
        return BackupCodec.decode(runBlocking { father.exportShare(ahmed, scope, 2026).first })
    }

    @Test
    fun aSharedBranchHoldsOnlyTheChosenPeople() = runBlocking {
        val father = fathersRepo()
        val ahmed = father.family.value.people.first { it.given == "Ahmed" }.id
        val ancestors = BackupCodec.decode(father.exportShare(ahmed, ShareScope(ShareDirection.Ancestors, 0, withSpouses = false), 2026).first)
        assertEquals(setOf("Ahmed", "Rashid"), ancestors.people.map { it.given }.toSet())
        val (text, count) = father.exportShare(ahmed, ShareScope(ShareDirection.Both, 0), 2026)
        assertEquals(5, count)
        assertEquals(5, BackupCodec.decode(text).people.size)
        assertEquals(1, BackupCodec.decode(text).details.couples.size)
    }

    @Test
    fun theChildMergesTheFathersFileWithoutDuplicatesOrOverwrites() = runBlocking {
        val father = fathersRepo()
        // The child typed themselves and their father independently, so no permanent ids are shared.
        val mine = repo()
        val inam = mine.addPerson(PersonDraft("Inam", "Raza", Sex.Male, born = 1998, birthPlace = "Lahore"))
        mine.addPerson(PersonDraft("Ahmed", "Raza", Sex.Male, born = 1948, birthPlace = ""), Relation.ParentOf(inam))
        val data = shared(father)

        val session = mine.planMerge(data)
        assertTrue(session.exact.isEmpty())
        val strong = session.candidates.filter { it.strong }.map { it.incomingId }.toSet()
        assertEquals(2, strong.size) // Ahmed and Inam, recognised by name, birth year and each other
        val changes = session.fieldChanges(session.matchesFor(strong))
        val birthPlaces = changes.filter { it.part == MergeField.BirthPlace }
        assertEquals(listOf("Multan" to true), birthPlaces.map { it.his to it.isFill }) // Ahmed's empty place is filled
        assertTrue(changes.none { it.personName == "Inam" && it.part == MergeField.BirthPlace }) // his file has no place for Inam, so nothing to ask

        val useHis = changes.filter { it.isFill }.map { it.key }.toSet()
        val result = mine.applyMerge(data, strong, useHis, addEvents = true)
        assertEquals(3, result.newPeople.size) // Rashid, Naseem and Sana
        val f = mine.family.value
        assertEquals(5, f.size)
        assertEquals(1, f.people.count { it.given == "Ahmed" })
        assertEquals(1, f.people.count { it.given == "Inam" })
        assertEquals("Lahore", f.person(inam).birthPlace) // kept: his file had nothing different to say
        val ahmed = f.people.first { it.given == "Ahmed" }
        assertEquals("Multan", ahmed.birthPlace)
        assertEquals(setOf("Naseem"), f.spousesOf(ahmed.id).map { it.given }.toSet())
        assertEquals(setOf("Inam", "Sana"), f.childrenOf(ahmed.id).map { it.given }.toSet())
        assertEquals(setOf("Rashid"), f.parentsOf(ahmed.id).map { it.given }.toSet())
        assertEquals(setOf("Ahmed", "Naseem"), f.parentsOf(inam).map { it.given }.toSet())
        assertEquals(listOf(teacher), mine.details(ahmed.id).events)
        val naseem = f.people.first { it.given == "Naseem" }.id
        assertEquals(listOf(wedding), mine.details(naseem).coupleEvents.getValue(ahmed.id))
        assertTrue(f.people.map { it.uid }.all { PersonUid.isValid(it) })
        assertEquals(5, f.people.map { it.uid }.toSet().size)
    }

    @Test
    fun mergingTheSameFileAgainChangesNothingAndTheMergeCanBeUndone() = runBlocking {
        val father = fathersRepo()
        val mine = repo()
        mine.addPerson(PersonDraft("Zed", "Other"))
        val data = shared(father)
        mine.applyMerge(data, emptySet(), emptySet(), true)
        val after = mine.family.value
        assertEquals(6, after.size)

        // Everyone now carries the father's permanent ids, so a second merge of the same file finds them all.
        val second = mine.planMerge(data)
        assertEquals(5, second.exact.size)
        val again = mine.applyMerge(data, emptySet(), emptySet(), true)
        assertTrue(again.newPeople.isEmpty())
        assertEquals(0, again.eventCount)
        assertEquals(6, mine.family.value.size)

        assertTrue(mine.hasSafetyCopy())
        mine.undoRestore() // back to the tree from just before the second merge
        assertEquals(6, mine.family.value.size)
    }

    @Test
    fun aConflictIsKeptUnlessChosenAndANewPersonKeepsTheFilesId() = runBlocking {
        val father = fathersRepo()
        val mine = repo()
        val ahmed = mine.addPerson(PersonDraft("Ahmed", "Raza", Sex.Male, born = 1949, birthPlace = "Karachi"))
        val data = shared(father, ShareScope(ShareDirection.Descendants, 1, withSpouses = false))
        val plan = mine.planMerge(data)
        val c = plan.candidates.single { it.currentId == ahmed }
        assertEquals(false, c.strong) // born 1949 against 1948: only offered
        val conflicts = plan.fieldChanges(plan.matchesFor(setOf(c.incomingId))).filter { !it.isFill }
        assertEquals(setOf(MergeField.Born, MergeField.BirthPlace), conflicts.map { it.part }.toSet())
        mine.applyMerge(data, setOf(c.incomingId), emptySet(), true)
        assertEquals("Karachi", mine.family.value.person(ahmed).birthPlace)
        assertEquals(1949, mine.family.value.person(ahmed).born)
        val inam = mine.family.value.people.first { it.given == "Inam" }
        assertEquals(data.people.first { it.given == "Inam" }.uid, inam.uid)
        assertNotEquals("", inam.uid)
    }

    @Test
    fun aMergeThatFailsHalfwayChangesNothing() = runBlocking {
        val mine = repo()
        mine.addPerson(PersonDraft("Keep"))
        val before = mine.family.value.people.map { it.uid to it.given }
        val bad = com.inamibrahim.kinhold.model.BackupData(
            listOf(com.inamibrahim.kinhold.model.Person(1, "New", "One")), emptyList(), emptyList(),
            details = com.inamibrahim.kinhold.model.TreeDetails(events = mapOf(1 to listOf(teacher)), couples = emptyMap()),
            photos = mapOf(1 to byteArrayOf(1, 2, 3)), // not a picture: storing the thumbnail fails
        )
        try {
            mine.applyMerge(bad, emptySet(), emptySet(), true)
        } catch (e: Exception) {
            // Expected: the photo cannot be read.
        }
        assertEquals(before, mine.family.value.people.map { it.uid to it.given })
    }
}
