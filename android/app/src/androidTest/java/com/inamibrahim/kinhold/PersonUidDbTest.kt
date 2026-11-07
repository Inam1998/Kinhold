package com.inamibrahim.kinhold

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.PersonUid
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone: every person has a permanent id, kept by every way of saving, restoring and copying a tree. */
@RunWith(AndroidJUnit4::class)
class PersonUidDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun safety() = File(context.cacheDir, "uid-safety-${System.nanoTime()}.json").also { files += it }

    private fun repo() = FamilyRepository(context, dbName = null, safetyFile = safety()).also { runBlocking { it.load() } }

    private fun uids(r: FamilyRepository) = r.family.value.people.associate { it.given to it.uid }

    @Test
    fun everyNewPersonGetsAUniqueValidId() = runBlocking {
        val r = repo()
        val a = r.addPerson(PersonDraft("A"))
        r.addPerson(PersonDraft("B"), Relation.ParentOf(a))
        r.addPerson(PersonDraft("C"), Relation.SpouseOf(a))
        val ids = r.family.value.people.map { it.uid }
        assertEquals(3, ids.size)
        assertTrue(ids.all { PersonUid.isValid(it) })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun theSampleFamilyGetsUniqueIds() = runBlocking {
        val r = repo()
        r.addSampleFamily(60)
        val ids = r.family.value.people.map { it.uid }
        assertTrue(ids.size >= 40)
        assertTrue(ids.all { PersonUid.isValid(it) })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun editingAPersonKeepsTheirId() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("Old"))
        val before = r.family.value.person(id).uid
        r.updatePerson(id, PersonDraft("New", "Name", born = 1900))
        assertEquals(before, r.family.value.person(id).uid)
        assertEquals("New", r.family.value.person(id).given)
    }

    @Test
    fun deleteAndUndoKeepsTheId() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("Me"))
        val before = r.family.value.person(id).uid
        val deleted = r.deletePerson(id)!!
        r.restoreDeleted(deleted)
        assertEquals(before, r.family.value.person(id).uid)
    }

    @Test
    fun aBackupRestoreAndUndoKeepEveryId() = runBlocking {
        val source = repo()
        source.addPerson(PersonDraft("A"))
        source.addPerson(PersonDraft("B"))
        val ids = uids(source)
        val data = BackupCodec.decode(source.exportBackup())

        val target = repo()
        target.addPerson(PersonDraft("Other"))
        val otherIds = uids(target)
        target.replaceAll(data)
        assertEquals(ids, uids(target))
        target.undoRestore()
        assertEquals(otherIds, uids(target))
    }

    @Test
    fun anOldBackupWithoutIdsGivesEveryoneAnId() = runBlocking {
        val v3 = """{"format":"kinhold-backup","version":3,"people":[{"id":1,"given":"A"},{"id":2,"given":"B"}],"parentLinks":[],"partnerships":[]}"""
        val r = repo()
        r.replaceAll(BackupCodec.decode(v3))
        val ids = r.family.value.people.map { it.uid }
        assertTrue(ids.all { PersonUid.isValid(it) })
        assertEquals(2, ids.toSet().size)
    }

    @Test
    fun addingAFileKeepsNewIdsAndRenewsOnesAlreadyInTheTree() = runBlocking {
        val r = repo()
        val mine = r.addPerson(PersonDraft("Mine"))
        val mineUid = r.family.value.person(mine).uid
        val fresh = PersonUid.create()
        r.addAll(BackupData(listOf(Person(1, "Copy", "", uid = mineUid), Person(2, "Fresh", "", uid = fresh)), emptyList(), emptyList()))
        val byName = uids(r)
        assertEquals(mineUid, byName.getValue("Mine"))
        assertEquals(fresh, byName.getValue("Fresh"))
        assertNotEquals(mineUid, byName.getValue("Copy"))
        assertTrue(PersonUid.isValid(byName.getValue("Copy")))
    }

    @Test
    fun aVersionThreeDatabaseGetsIdsOnUpgradeAndKeepsThemAfterReopening() = runBlocking {
        val name = "kinhold-migrate3-${System.nanoTime()}.db"
        try {
            // A database as the first build of 0.1.0 made it (version 3): event tables, but no uid column.
            val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
            db.execSQL("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, given TEXT NOT NULL DEFAULT '', surname TEXT NOT NULL DEFAULT '', sex INTEGER NOT NULL DEFAULT 2, birth_year INTEGER, death_year INTEGER, birth_place TEXT NOT NULL DEFAULT '', death_place TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', birth_month INTEGER, birth_day INTEGER, death_month INTEGER, death_day INTEGER)")
            db.execSQL("CREATE TABLE parent_link (child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (child_id, parent_id))")
            db.execSQL("CREATE TABLE partnership (a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (a_id, b_id), CHECK (a_id < b_id))")
            db.execSQL("CREATE TABLE photo (person_id INTEGER PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE, stamp INTEGER NOT NULL, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("CREATE TABLE event (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, a_id INTEGER, b_id INTEGER, kind TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', year INTEGER, month INTEGER, day INTEGER, place TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', FOREIGN KEY (a_id, b_id) REFERENCES partnership(a_id, b_id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE source (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, event_id INTEGER REFERENCES event(id) ON DELETE CASCADE, title TEXT NOT NULL DEFAULT '', citation TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')")
            db.execSQL("INSERT INTO person (id, given, surname, birth_year, birth_month) VALUES (1, 'Old', 'One', 1950, 3), (2, 'Old', 'Two', 1952, NULL), (3, 'Old', 'Three', NULL, NULL)")
            db.execSQL("INSERT INTO parent_link VALUES (3, 1), (3, 2)")
            db.execSQL("INSERT INTO partnership VALUES (1, 2)")
            db.execSQL("INSERT INTO event (a_id, b_id, kind, year) VALUES (1, 2, 'marriage', 1975)")
            db.version = 3
            db.close()

            val first = FamilyRepository(context, name, safety())
            first.load()
            val f = first.family.value
            assertEquals(3, f.size)
            assertEquals(1950, f.person(1).born)
            assertEquals(3, f.person(1).birthMonth)
            assertEquals(listOf(1, 2), f.parentsOf(3).map { it.id })
            val ids = f.people.map { it.uid }
            assertTrue(ids.all { PersonUid.isValid(it) })
            assertEquals(3, ids.toSet().size)
            assertEquals(1975, first.details(1).coupleEvents.getValue(2).single().date.year)

            val second = FamilyRepository(context, name, safety())
            second.load()
            assertEquals(ids, second.family.value.people.map { it.uid })
            // People added after the upgrade get ids too, and the unique index holds.
            val fresh = second.addPerson(PersonDraft("New"))
            assertTrue(PersonUid.isValid(second.family.value.person(fresh).uid))
            assertEquals(4, second.family.value.people.map { it.uid }.toSet().size)
        } finally {
            context.deleteDatabase(name)
        }
    }
}
