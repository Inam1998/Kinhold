package com.inamibrahim.kinhold

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.LinkKind
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone: adoptive, foster and step links are stored, kept by every save, and migrate in from version 7. */
@RunWith(AndroidJUnit4::class)
class LinkKindDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun safety() = File(context.cacheDir, "lk-safety-${System.nanoTime()}.json").also { files += it }

    private fun repo() = FamilyRepository(context, null, safety()).also { runBlocking { it.load() } }

    @Test
    fun aKindIsStoredChangedAndAnAdoptiveParentIsNotMadeACouple() = runBlocking {
        val r = repo()
        val kid = r.addPerson(PersonDraft("Kid"))
        val mum = r.addPerson(PersonDraft("Mum"), Relation.ParentOf(kid))
        val dad = r.addPerson(PersonDraft("Adoptive dad"), Relation.ParentOf(kid), linkKind = LinkKind.Adopted)
        assertEquals(LinkKind.Adopted, r.family.value.kindOf(kid, dad))
        assertTrue("an adoptive parent is not paired with the birth mother", r.family.value.spousesOf(mum).isEmpty())
        r.setLinkKind(kid, dad, LinkKind.Foster)
        assertEquals(LinkKind.Foster, r.family.value.kindOf(kid, dad))
        val bio = r.addPerson(PersonDraft("Birth dad"), Relation.ParentOf(kid))
        // A birth parent joining a child who already has only non-matching links is not paired either.
        assertEquals(4 - 1, r.family.value.parentsOf(kid).size)
        assertEquals(LinkKind.Birth, r.family.value.kindOf(kid, bio))
    }

    @Test
    fun kindsSurviveBackupRestoreUndoAndDeleteUndo() = runBlocking {
        val r = repo()
        val kid = r.addPerson(PersonDraft("Kid"))
        val p = r.addPerson(PersonDraft("Foster mum"), Relation.ParentOf(kid), linkKind = LinkKind.Foster)
        val data = BackupCodec.decode(r.exportBackup())
        assertEquals(LinkKind.Foster, data.linkKinds[kid to p])

        val other = repo()
        other.addPerson(PersonDraft("Other"))
        other.replaceAll(data)
        assertEquals(LinkKind.Foster, other.family.value.kindOf(kid, p))
        other.undoRestore()
        assertTrue(other.family.value.linkKinds().isEmpty())

        val deleted = r.deletePerson(p)!!
        r.restoreDeleted(deleted)
        assertEquals(LinkKind.Foster, r.family.value.kindOf(kid, p))
    }

    @Test
    fun aVersionSevenDatabaseGainsTheKindColumn() = runBlocking {
        val name = "kinhold-migrate7-${System.nanoTime()}.db"
        try {
            val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
            db.execSQL("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, given TEXT NOT NULL DEFAULT '', surname TEXT NOT NULL DEFAULT '', sex INTEGER NOT NULL DEFAULT 2, birth_year INTEGER, death_year INTEGER, birth_place TEXT NOT NULL DEFAULT '', death_place TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', birth_month INTEGER, birth_day INTEGER, death_month INTEGER, death_day INTEGER, address TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', geo_lat REAL, geo_lng REAL, is_me INTEGER NOT NULL DEFAULT 0, email TEXT NOT NULL DEFAULT '', uid TEXT)")
            db.execSQL("CREATE UNIQUE INDEX person_uid ON person(uid)")
            db.execSQL("CREATE TABLE parent_link (child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (child_id, parent_id))")
            db.execSQL("CREATE TABLE partnership (a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (a_id, b_id), CHECK (a_id < b_id))")
            db.execSQL("CREATE TABLE photo (person_id INTEGER PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE, stamp INTEGER NOT NULL, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("CREATE TABLE event (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, a_id INTEGER, b_id INTEGER, kind TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', year INTEGER, month INTEGER, day INTEGER, place TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', FOREIGN KEY (a_id, b_id) REFERENCES partnership(a_id, b_id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE source (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, event_id INTEGER REFERENCES event(id) ON DELETE CASCADE, title TEXT NOT NULL DEFAULT '', citation TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE person_field (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, label TEXT NOT NULL DEFAULT '', value TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE person_photo (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("INSERT INTO person (id, given, uid) VALUES (1, 'Kid', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), (2, 'Mum', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')")
            db.execSQL("INSERT INTO parent_link VALUES (1, 2)")
            db.version = 7
            db.close()

            val r = FamilyRepository(context, name, safety())
            r.load()
            assertEquals(listOf(2), r.family.value.parentsOf(1).map { it.id })
            assertEquals(LinkKind.Birth, r.family.value.kindOf(1, 2))
            r.setLinkKind(1, 2, LinkKind.Adopted)
            val again = FamilyRepository(context, name, safety())
            again.load()
            assertEquals(LinkKind.Adopted, again.family.value.kindOf(1, 2))
        } finally {
            context.deleteDatabase(name)
        }
    }
}
