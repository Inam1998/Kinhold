package com.inamibrahim.kinhold

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.DateQualifier
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone: about, before, after and between dates are stored, kept by every save, restore and undo, and migrate in from version 8. */
@RunWith(AndroidJUnit4::class)
class DateQualifierDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun safety() = File(context.cacheDir, "dq-safety-${System.nanoTime()}.json").also { files += it }

    private fun repo() = FamilyRepository(context, null, safety()).also { runBlocking { it.load() } }

    private val draft = PersonDraft(
        given = "Ali", surname = "Khan", born = 1850, died = 1920,
        birthQualifier = DateQualifier.About, deathQualifier = DateQualifier.Between, deathEndYear = 1925,
    )

    @Test
    fun qualifiedDatesAreStoredForPeopleEventsAndCouples() = runBlocking {
        val r = repo()
        val id = r.addPerson(draft)
        val partner = r.addPerson(PersonDraft(given = "Sana"), com.inamibrahim.kinhold.data.Relation.SpouseOf(id))
        val baptism = Event(EventKind.Baptism, date = PartialDate(1851, null, null, DateQualifier.After))
        val marriage = Event(EventKind.Marriage, date = PartialDate(1875, 6, null, DateQualifier.Between, 1877))
        r.updatePerson(id, draft, details = PersonDetails(events = listOf(baptism), coupleEvents = mapOf(partner to listOf(marriage))))
        val p = r.family.value.person(id)
        assertEquals(DateQualifier.About, p.birthQualifier)
        assertEquals(DateQualifier.Between, p.deathQualifier)
        assertEquals(1925, p.deathEndYear)
        assertNull(p.birthEndYear)
        val d = r.details(id)
        assertEquals(listOf(baptism), d.events)
        assertEquals(marriage, d.coupleEvents.getValue(partner).single())
    }

    @Test
    fun aQualifierWithNoYearIsNotStoredAndAnEndYearGoesOnlyWithBetween() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft(given = "A", born = null, birthQualifier = DateQualifier.About, died = 1900, deathQualifier = DateQualifier.Before, deathEndYear = 1950))
        val p = r.family.value.person(id)
        assertEquals(DateQualifier.Exact, p.birthQualifier)
        assertEquals(DateQualifier.Before, p.deathQualifier)
        assertNull(p.deathEndYear)
    }

    @Test
    fun qualifiedDatesSurviveARestoreAndADeleteUndo() = runBlocking {
        val r = repo()
        val id = r.addPerson(draft)
        r.updatePerson(id, draft, details = PersonDetails(events = listOf(Event(EventKind.Baptism, date = PartialDate(1851, null, null, DateQualifier.Before)))))
        // A backup file and back again.
        val text = r.exportBackup()
        val other = repo()
        other.replaceAll(BackupCodec.decode(text))
        val back = other.family.value.people.single()
        assertEquals(DateQualifier.About, back.birthQualifier)
        assertEquals(1925, back.deathEndYear)
        assertEquals(DateQualifier.Before, other.details(back.id).events.single().date.qualifier)
        // Delete and Undo.
        val deleted = other.deletePerson(back.id)!!
        other.restoreDeleted(deleted)
        val restored = other.family.value.person(back.id)
        assertEquals(DateQualifier.Between, restored.deathQualifier)
        assertEquals(DateQualifier.Before, other.details(restored.id).events.single().date.qualifier)
    }

    @Test
    fun joiningTwoPeopleKeepsTheQualifierOfTheDateItTakes() = runBlocking {
        val r = repo()
        val keep = r.addPerson(PersonDraft(given = "Ali", surname = "Khan"))
        val gone = r.addPerson(draft)
        r.mergePeople(keep, gone)
        val p = r.family.value.person(keep)
        assertEquals(1850, p.born)
        assertEquals(DateQualifier.About, p.birthQualifier)
        assertEquals(DateQualifier.Between, p.deathQualifier)
        assertEquals(1925, p.deathEndYear)
    }

    @Test
    fun aVersionEightDatabaseGainsTheQualifierColumnsAsExactDates() = runBlocking {
        val name = "kinhold-migrate8-${System.nanoTime()}.db"
        try {
            val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
            db.execSQL("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, given TEXT NOT NULL DEFAULT '', surname TEXT NOT NULL DEFAULT '', sex INTEGER NOT NULL DEFAULT 2, birth_year INTEGER, death_year INTEGER, birth_place TEXT NOT NULL DEFAULT '', death_place TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', birth_month INTEGER, birth_day INTEGER, death_month INTEGER, death_day INTEGER, address TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', geo_lat REAL, geo_lng REAL, is_me INTEGER NOT NULL DEFAULT 0, email TEXT NOT NULL DEFAULT '', uid TEXT)")
            db.execSQL("CREATE UNIQUE INDEX person_uid ON person(uid)")
            db.execSQL("CREATE TABLE parent_link (child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, kind INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (child_id, parent_id))")
            db.execSQL("CREATE TABLE partnership (a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (a_id, b_id), CHECK (a_id < b_id))")
            db.execSQL("CREATE TABLE photo (person_id INTEGER PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE, stamp INTEGER NOT NULL, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("CREATE TABLE event (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, a_id INTEGER, b_id INTEGER, kind TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '', year INTEGER, month INTEGER, day INTEGER, place TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', FOREIGN KEY (a_id, b_id) REFERENCES partnership(a_id, b_id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE source (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER REFERENCES person(id) ON DELETE CASCADE, event_id INTEGER REFERENCES event(id) ON DELETE CASCADE, title TEXT NOT NULL DEFAULT '', citation TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE person_field (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, label TEXT NOT NULL DEFAULT '', value TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE person_photo (id INTEGER PRIMARY KEY AUTOINCREMENT, person_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("INSERT INTO person (id, given, birth_year, birth_month, birth_day, death_year, uid) VALUES (1, 'Old', 1850, 3, 4, 1920, 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')")
            db.execSQL("INSERT INTO event (person_id, kind, year, place) VALUES (1, 'baptism', 1851, 'Lahore')")
            db.version = 8
            db.close()

            val r = FamilyRepository(context, name, safety())
            r.load()
            val p = r.family.value.person(1)
            assertEquals(Triple(1850, 3, 4), Triple(p.born, p.birthMonth, p.birthDay))
            assertEquals(DateQualifier.Exact, p.birthQualifier)
            assertEquals(DateQualifier.Exact, p.deathQualifier)
            assertEquals(PartialDate(1851), r.details(1).events.single().date)
            // The new columns work on the upgraded file, and survive closing and opening it again.
            r.updatePerson(1, p.toDraft().let { PersonDraft(it.given, it.surname, it.sex, 1850, 1920, birthQualifier = DateQualifier.About) })
            val again = FamilyRepository(context, name, safety())
            again.load()
            assertEquals(DateQualifier.About, again.family.value.person(1).birthQualifier)
        } finally {
            context.deleteDatabase(name)
        }
    }
}
