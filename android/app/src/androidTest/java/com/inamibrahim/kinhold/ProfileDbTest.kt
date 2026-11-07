package com.inamibrahim.kinhold

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.CustomField
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone: email and the owner's own facts are stored, survive every kind of save, and migrate in from version 5. */
@RunWith(AndroidJUnit4::class)
class ProfileDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun safety() = File(context.cacheDir, "profile-safety-${System.nanoTime()}.json").also { files += it }

    private fun repo() = FamilyRepository(context, null, safety()).also { runBlocking { it.load() } }

    private val facts = listOf(CustomField("Blood group", "O+"), CustomField("Nickname", "Inu"))

    @Test
    fun emailAndFactsAreStoredEditedAndRemoved() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("A", email = " a@b.com "), details = PersonDetails(fields = facts + CustomField()))
        assertEquals("a@b.com", r.family.value.person(id).email)
        assertEquals(facts, r.details(id).fields) // the blank one is not stored
        r.updatePerson(id, PersonDraft("A", email = ""), details = PersonDetails(fields = listOf(CustomField("Only", "one"))))
        assertEquals("", r.family.value.person(id).email)
        assertEquals(listOf(CustomField("Only", "one")), r.details(id).fields)
        r.updatePerson(id, PersonDraft("A"))
        assertEquals(1, r.details(id).fields.size) // saving without details leaves them
    }

    @Test
    fun factsSurviveBackupRestoreUndoAndDeleteUndo() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("A", email = "a@b.com"), details = PersonDetails(listOf(Event(EventKind.Burial)), fields = facts))
        val data = BackupCodec.decode(r.exportBackup())
        assertEquals(facts, data.details.fields.getValue(1))

        val other = repo()
        other.addPerson(PersonDraft("Other"), details = PersonDetails(fields = listOf(CustomField("x", "y"))))
        other.replaceAll(data)
        assertEquals(facts, other.details(1).fields)
        assertEquals("a@b.com", other.family.value.person(1).email)
        other.undoRestore()
        assertEquals(listOf(CustomField("x", "y")), other.details(other.family.value.people.single().id).fields)

        val deleted = r.deletePerson(id)!!
        r.restoreDeleted(deleted)
        assertEquals(facts, r.details(id).fields)
    }

    @Test
    fun mergingAnEventDoesNotWipeTheFactsYouAlreadyHave() = runBlocking {
        val mine = repo()
        val id = mine.addPerson(PersonDraft("Inam"), details = PersonDetails(fields = facts))
        val uid = mine.family.value.person(id).uid
        val his = repo()
        val hid = his.addPerson(PersonDraft("Inam"), details = PersonDetails(listOf(Event(EventKind.Baptism)), fields = listOf(CustomField("Village", "Multan"))))
        // Give his copy the same permanent id so the two are the same person.
        val data = BackupCodec.decode(his.exportBackup().replace(his.family.value.person(hid).uid, uid))
        mine.applyMerge(data, emptySet(), emptySet(), addEvents = true)
        val d = mine.details(id)
        assertEquals(facts + CustomField("Village", "Multan"), d.fields)
        assertEquals(1, d.events.size)
    }

    @Test
    fun aVersionFiveDatabaseGainsEmailAndTheFactsTable() = runBlocking {
        val name = "kinhold-migrate5-${System.nanoTime()}.db"
        try {
            val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
            db.execSQL("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, given TEXT NOT NULL DEFAULT '', surname TEXT NOT NULL DEFAULT '', sex INTEGER NOT NULL DEFAULT 2, birth_year INTEGER, death_year INTEGER, birth_place TEXT NOT NULL DEFAULT '', death_place TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', birth_month INTEGER, birth_day INTEGER, death_month INTEGER, death_day INTEGER, address TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', geo_lat REAL, geo_lng REAL, is_me INTEGER NOT NULL DEFAULT 0, uid TEXT)")
            db.execSQL("CREATE UNIQUE INDEX person_uid ON person(uid)")
            db.execSQL("CREATE TABLE parent_link (child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (child_id, parent_id))")
            db.execSQL("CREATE TABLE partnership (a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (a_id, b_id), CHECK (a_id < b_id))")
            db.execSQL("CREATE TABLE photo (person_id INTEGER PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE, stamp INTEGER NOT NULL, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("CREATE TABLE event (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, a_id INTEGER, b_id INTEGER, kind TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', year INTEGER, month INTEGER, day INTEGER, place TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', FOREIGN KEY (a_id, b_id) REFERENCES partnership(a_id, b_id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE source (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, event_id INTEGER REFERENCES event(id) ON DELETE CASCADE, title TEXT NOT NULL DEFAULT '', citation TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')")
            db.execSQL("INSERT INTO person (id, given, surname, phone, is_me, uid) VALUES (1, 'Old', 'One', '123', 1, '99999999-9999-4999-8999-999999999999')")
            db.version = 5
            db.close()

            val r = FamilyRepository(context, name, safety())
            r.load()
            val p = r.family.value.person(1)
            assertEquals("123", p.phone)
            assertTrue(p.isMe)
            assertEquals("", p.email)
            assertTrue(r.details(1).fields.isEmpty())
            r.updatePerson(1, PersonDraft("Old", "One", phone = "123", email = "x@y.z"), details = PersonDetails(fields = facts))
            val again = FamilyRepository(context, name, safety())
            again.load()
            assertEquals("x@y.z", again.family.value.person(1).email)
            assertEquals(facts, again.details(1).fields)
        } finally {
            context.deleteDatabase(name)
        }
    }
}
