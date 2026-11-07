package com.inamibrahim.kinhold

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone: address, phone, map pin and "this is me" are stored, kept by every save and restore, and migrate in. */
@RunWith(AndroidJUnit4::class)
class ContactDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun safety() = File(context.cacheDir, "contact-safety-${System.nanoTime()}.json").also { files += it }

    private fun repo() = FamilyRepository(context, null, safety()).also { runBlocking { it.load() } }

    @Test
    fun contactDetailsAreStoredAndEditing_keepsThem() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("A", address = " 12 Canal Road ", phone = " +92 300 1 ", lat = 31.5, lng = 74.3))
        var p = r.family.value.person(id)
        assertEquals("12 Canal Road", p.address)
        assertEquals("+92 300 1", p.phone)
        assertEquals(31.5, p.lat!!, 1e-9)
        r.updatePerson(id, PersonDraft("A", address = "New road", phone = "", lat = null, lng = null))
        p = r.family.value.person(id)
        assertEquals("New road", p.address)
        assertEquals("", p.phone)
        assertNull(p.lat)
        assertNull(p.lng)
    }

    @Test
    fun aPinOutsideTheEarthIsNotStored() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("A", lat = 120.0, lng = 10.0))
        assertNull(r.family.value.person(id).lat)
    }

    @Test
    fun onlyOnePersonCanBeMeAndEditingKeepsTheMark() = runBlocking {
        val r = repo()
        val a = r.addPerson(PersonDraft("A"))
        val b = r.addPerson(PersonDraft("B"))
        r.setMe(a)
        assertTrue(r.family.value.person(a).isMe)
        r.setMe(b)
        assertFalse(r.family.value.person(a).isMe)
        assertTrue(r.family.value.person(b).isMe)
        r.updatePerson(b, PersonDraft("B renamed"))
        assertTrue(r.family.value.person(b).isMe)
        r.setMe(null)
        assertTrue(r.family.value.people.none { it.isMe })
    }

    @Test
    fun theMeMarkAndContactSurviveBackupRestoreUndoAndDeleteUndo() = runBlocking {
        val r = repo()
        val a = r.addPerson(PersonDraft("A", phone = "111", address = "Here", lat = 1.0, lng = 2.0))
        r.setMe(a)
        val data = BackupCodec.decode(r.exportBackup())
        val other = repo()
        other.addPerson(PersonDraft("Other"))
        other.replaceAll(data)
        val restored = other.family.value.people.single()
        assertTrue(restored.isMe)
        assertEquals("111", restored.phone)
        assertEquals(2.0, restored.lng!!, 0.0)
        other.undoRestore()
        assertEquals("Other", other.family.value.people.single().given)

        val deleted = r.deletePerson(a)!!
        r.restoreDeleted(deleted)
        val back = r.family.value.person(a)
        assertTrue(back.isMe)
        assertEquals("Here", back.address)
        assertEquals(1.0, back.lat!!, 0.0)
    }

    @Test
    fun aVersionFourDatabaseGainsTheNewColumnsWithoutLosingAnything() = runBlocking {
        val name = "kinhold-migrate4-${System.nanoTime()}.db"
        try {
            // A database as the sharing build made it (version 4): person ids, but no contact columns.
            val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
            db.execSQL("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, given TEXT NOT NULL DEFAULT '', surname TEXT NOT NULL DEFAULT '', sex INTEGER NOT NULL DEFAULT 2, birth_year INTEGER, death_year INTEGER, birth_place TEXT NOT NULL DEFAULT '', death_place TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', birth_month INTEGER, birth_day INTEGER, death_month INTEGER, death_day INTEGER, uid TEXT)")
            db.execSQL("CREATE UNIQUE INDEX person_uid ON person(uid)")
            db.execSQL("CREATE TABLE parent_link (child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (child_id, parent_id))")
            db.execSQL("CREATE TABLE partnership (a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (a_id, b_id), CHECK (a_id < b_id))")
            db.execSQL("CREATE TABLE photo (person_id INTEGER PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE, stamp INTEGER NOT NULL, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("CREATE TABLE event (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, a_id INTEGER, b_id INTEGER, kind TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', year INTEGER, month INTEGER, day INTEGER, place TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', FOREIGN KEY (a_id, b_id) REFERENCES partnership(a_id, b_id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE source (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, event_id INTEGER REFERENCES event(id) ON DELETE CASCADE, title TEXT NOT NULL DEFAULT '', citation TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')")
            db.execSQL("INSERT INTO person (id, given, surname, birth_year, uid) VALUES (1, 'Old', 'One', 1950, '66666666-6666-4666-8666-666666666666'), (2, 'Old', 'Two', 1952, '77777777-7777-4777-8777-777777777777')")
            db.execSQL("INSERT INTO partnership VALUES (1, 2)")
            db.version = 4
            db.close()

            val r = FamilyRepository(context, name, safety())
            r.load()
            val f = r.family.value
            assertEquals(2, f.size)
            assertEquals(1950, f.person(1).born)
            assertEquals("66666666-6666-4666-8666-666666666666", f.person(1).uid)
            assertEquals("", f.person(1).phone)
            assertNull(f.person(1).lat)
            assertTrue(f.people.none { it.isMe })
            assertEquals(listOf(2), f.spousesOf(1).map { it.id })

            r.updatePerson(1, PersonDraft("Old", "One", born = 1950, phone = "123", address = "X", lat = 1.0, lng = 2.0))
            r.setMe(1)
            val again = FamilyRepository(context, name, safety())
            again.load()
            assertEquals("123", again.family.value.person(1).phone)
            assertTrue(again.family.value.person(1).isMe)
        } finally {
            context.deleteDatabase(name)
        }
    }
}
