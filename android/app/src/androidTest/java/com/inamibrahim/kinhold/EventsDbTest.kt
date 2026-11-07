package com.inamibrahim.kinhold

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.Source
import com.inamibrahim.kinhold.model.TreeDetails
import com.inamibrahim.kinhold.model.relativesOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone against real SQLite: events, sources, full dates, and the move from database version 2 to 3. */
@RunWith(AndroidJUnit4::class)
class EventsDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun safety() = File(context.cacheDir, "events-safety-${System.nanoTime()}.json").also { files += it }

    private fun repo() = FamilyRepository(context, dbName = null, safetyFile = safety()).also { runBlocking { it.load() } }

    private val marriage = Event(EventKind.Marriage, "", PartialDate(1975, 6, 1), "Lahore", "Big wedding", listOf(Source("Certificate", "No. 12")))
    private val job = Event(EventKind.Occupation, "Farmer", PartialDate(1980), "Multan", "", listOf(Source("Census 1981", "p. 3", "faint")))
    private val moved = Event(EventKind.Residence, "", PartialDate(1970, 2), "Karachi")
    private val undated = Event(EventKind.Custom, "Pilgrimage")

    @Test
    fun eventsAndSourcesAreStoredReadBackAndKeptInDateOrder() = runBlocking {
        val repo = repo()
        val id = repo.addPerson(
            PersonDraft("A", "B", born = 1950, birthMonth = 3, birthDay = 12, died = 2020, deathMonth = 7),
            details = PersonDetails(listOf(undated, job, moved), listOf(Source("Family Bible", "front page"))),
        )
        val person = repo.family.value.person(id)
        assertEquals(PartialDate(1950, 3, 12), person.birthDate)
        assertEquals(PartialDate(2020, 7, null), person.deathDate)
        val d = repo.details(id)
        assertEquals(listOf(moved, job, undated), d.events)
        assertEquals(listOf(Source("Family Bible", "front page")), d.sources)
    }

    @Test
    fun aMonthOrDayWithoutItsYearIsNotStored() = runBlocking {
        val repo = repo()
        val id = repo.addPerson(PersonDraft("A", born = null, birthMonth = 5, birthDay = 6))
        assertEquals(PartialDate.None, repo.family.value.person(id).birthDate)
        assertEquals(null, repo.family.value.person(id).birthMonth)
    }

    @Test
    fun savingWithoutDetailsLeavesThemAndSavingWithDetailsReplacesThem() = runBlocking {
        val repo = repo()
        val id = repo.addPerson(PersonDraft("A"), details = PersonDetails(listOf(job)))
        repo.updatePerson(id, PersonDraft("A renamed"))
        assertEquals(listOf(job), repo.details(id).events)
        repo.updatePerson(id, PersonDraft("A renamed"), details = PersonDetails(listOf(moved)))
        assertEquals(listOf(moved), repo.details(id).events)
        repo.updatePerson(id, PersonDraft("A renamed"), details = PersonDetails.Empty)
        assertTrue(repo.details(id).isEmpty)
    }

    @Test
    fun aMarriageOrdersSpousesAndADivorceEndsThePartnership() = runBlocking {
        val repo = repo()
        val me = repo.addPerson(PersonDraft("Me", "X", Sex.Male, born = 1950))
        val first = repo.addPerson(PersonDraft("First", "Y", Sex.Female, born = 1960), Relation.SpouseOf(me))
        val second = repo.addPerson(
            PersonDraft("Second", "Z", Sex.Female, born = 1940), Relation.SpouseOf(me),
            details = PersonDetails(coupleEvents = mapOf(me to listOf(marriage))),
        )
        // Second was born earlier, but First was added first; with no marriage dates it would be Second (birth year).
        assertEquals(listOf(second, first), repo.family.value.spousesInOrder(me).map { it.id })
        // Now give First an earlier marriage.
        repo.updatePerson(
            me, PersonDraft("Me", "X", Sex.Male, born = 1950),
            details = PersonDetails(coupleEvents = mapOf(first to listOf(Event(EventKind.Marriage, date = PartialDate(1970))), second to listOf(marriage))),
        )
        assertEquals(listOf(first, second), repo.family.value.spousesInOrder(me).map { it.id })
        assertEquals(listOf(first, second), repo.family.value.currentSpousesOf(me).map { it.id })

        // A divorce from First ends that partnership, and the layout and relatives lists can tell.
        val child = repo.addPerson(PersonDraft("Kid"), Relation.ChildOf(me, second))
        assertEquals("Stepmother", repo.family.value.relativesOf(child).first { it.person.id == first }.label)
        repo.updatePerson(
            me, PersonDraft("Me", "X", Sex.Male, born = 1950),
            details = PersonDetails(coupleEvents = mapOf(first to listOf(Event(EventKind.Marriage, date = PartialDate(1970)), Event(EventKind.Divorce, date = PartialDate(1980))), second to listOf(marriage))),
        )
        assertTrue(repo.family.value.partnershipEnded(me, first))
        assertEquals(listOf(second), repo.family.value.currentSpousesOf(me).map { it.id })
        assertTrue("the former wife is no longer a step-parent", repo.family.value.relativesOf(child).none { it.person.id == first })
        assertEquals(listOf(marriage), repo.details(second).coupleEvents.getValue(me))
    }

    @Test
    fun aCoupleEventForSomeoneWhoIsNotAPartnerIsIgnored() = runBlocking {
        val repo = repo()
        val a = repo.addPerson(PersonDraft("A"))
        val b = repo.addPerson(PersonDraft("B"))
        repo.updatePerson(a, PersonDraft("A"), details = PersonDetails(coupleEvents = mapOf(b to listOf(marriage))))
        assertTrue(repo.details(a).isEmpty)
        assertTrue(repo.family.value.currentSpousesOf(a).isEmpty())
    }

    @Test
    fun deletingAPersonDeletesTheirEventsAndUndoBringsEverythingBack() = runBlocking {
        val repo = repo()
        val me = repo.addPerson(PersonDraft("Me"), details = PersonDetails(listOf(job, moved), listOf(Source("Bible"))))
        val spouse = repo.addPerson(
            PersonDraft("Spouse"), Relation.SpouseOf(me),
            details = PersonDetails(listOf(undated), coupleEvents = mapOf(me to listOf(marriage))),
        )
        val deleted = repo.deletePerson(me)!!
        assertEquals(1, repo.family.value.size)
        assertTrue("the couple's marriage goes with the person", repo.details(spouse).coupleEvents.isEmpty())
        assertEquals(listOf(undated), repo.details(spouse).events)

        repo.restoreDeleted(deleted)
        val back = repo.details(me)
        assertEquals(listOf(moved, job), back.events)
        assertEquals(listOf(Source("Bible")), back.sources)
        assertEquals(listOf(marriage), back.coupleEvents.getValue(spouse))
        assertEquals(listOf(marriage), repo.details(spouse).coupleEvents.getValue(me))
        assertEquals(listOf(undated), repo.details(spouse).events)
    }

    @Test
    fun deletingTheOnlyPartnerKeepsTheOtherPersonsOwnEventsOnly() = runBlocking {
        val repo = repo()
        val me = repo.addPerson(PersonDraft("Me"), details = PersonDetails(listOf(job)))
        val spouse = repo.addPerson(PersonDraft("S"), Relation.SpouseOf(me), details = PersonDetails(coupleEvents = mapOf(me to listOf(marriage))))
        repo.deletePerson(spouse)
        assertEquals(listOf(job), repo.details(me).events)
        assertTrue(repo.details(me).coupleEvents.isEmpty())
    }

    private fun populated(): FamilyRepository = repo().also { r ->
        runBlocking {
            val me = r.addPerson(PersonDraft("Me", born = 1950, birthMonth = 3), details = PersonDetails(listOf(job, moved), listOf(Source("Bible"))))
            r.addPerson(PersonDraft("Wife"), Relation.SpouseOf(me), details = PersonDetails(coupleEvents = mapOf(me to listOf(marriage))))
        }
    }

    @Test
    fun aBackupCarriesEventsAndARestoreBringsThemBack() = runBlocking {
        val source = populated()
        val data = BackupCodec.decode(source.exportBackup())
        assertEquals(listOf(moved, job), data.details.events.getValue(1))
        assertEquals(listOf(marriage), data.details.couples.getValue(1 to 2))

        val target = repo()
        target.addPerson(PersonDraft("Other"), details = PersonDetails(listOf(undated)))
        target.replaceAll(data)
        assertEquals(2, target.family.value.size)
        assertEquals(PartialDate(1950, 3, null), target.family.value.person(1).birthDate)
        assertEquals(listOf(moved, job), target.details(1).events)
        assertEquals(listOf(Source("Bible")), target.details(1).sources)
        assertEquals(listOf(marriage), target.details(2).coupleEvents.getValue(1))
    }

    @Test
    fun theSafetyCopyAndUndoRestoreCarryEvents() = runBlocking {
        val repo = populated()
        repo.replaceAll(BackupData(listOf(Person(7, "Replacement", "")), emptyList(), emptyList()))
        assertEquals(1, repo.family.value.size)
        assertTrue(repo.hasSafetyCopy())
        repo.undoRestore()
        assertEquals(2, repo.family.value.size)
        val me = repo.family.value.people.first { it.given == "Me" }.id
        assertEquals(listOf(moved, job), repo.details(me).events)
        assertEquals(listOf(Source("Bible")), repo.details(me).sources)
        val wife = repo.family.value.people.first { it.given == "Wife" }.id
        assertEquals(listOf(marriage), repo.details(wife).coupleEvents.getValue(me))
        // And undoing the undo brings the replacement back.
        repo.undoRestore()
        assertEquals("Replacement", repo.family.value.people.single().given)
    }

    @Test
    fun addingAFileMapsEventsToTheNewIds() = runBlocking {
        val data = BackupCodec.decode(populated().exportBackup())
        val target = repo()
        target.addPerson(PersonDraft("Mine"), details = PersonDetails(listOf(undated)))
        target.addAll(data)
        assertEquals(3, target.family.value.size)
        val me = target.family.value.people.first { it.given == "Me" }.id
        val wife = target.family.value.people.first { it.given == "Wife" }.id
        assertEquals(listOf(moved, job), target.details(me).events)
        assertEquals(listOf(marriage), target.details(wife).coupleEvents.getValue(me))
        assertEquals(listOf(undated), target.details(target.family.value.people.first { it.given == "Mine" }.id).events)
    }

    @Test
    fun aRestoreWhoseEventsCannotBeStoredChangesNothing() = runBlocking {
        val repo = populated()
        val before = repo.family.value.size
        // Events for a person who is not in the file: the database refuses them, so the whole restore is undone.
        val broken = BackupData(
            listOf(Person(1, "New", "")), emptyList(), emptyList(),
            details = TreeDetails(events = mapOf(99 to listOf(job))),
        )
        try {
            repo.replaceAll(broken)
            fail("The restore should have failed")
        } catch (e: Exception) {
            // Expected.
        }
        assertEquals(before, repo.family.value.size)
        assertEquals(listOf(moved, job), repo.details(1).events)
        assertEquals("Me", repo.family.value.person(1).given)
    }

    @Test
    fun aGedcomExportOfStoredEventsReadsBackTheSame() = runBlocking {
        val repo = populated()
        val text = repo.exportGedcom("1.0", "7 NOV 2026")
        assertTrue(text.contains("1 MARR"))
        val result = com.inamibrahim.kinhold.model.Gedcom.read(text.toByteArray())
        assertEquals(listOf(moved, job), result.data.details.events.getValue(1))
        assertEquals(listOf(marriage), result.data.details.couples.getValue(1 to 2))
        assertEquals(3, result.report.eventsKept)
        assertEquals(3, result.report.sourcesKept)
    }

    @Test
    fun eventsSurviveClosingAndReopeningTheDatabaseFile() = runBlocking {
        val name = "kinhold-events-${System.nanoTime()}.db"
        try {
            val first = FamilyRepository(context, name, safety())
            first.load()
            val id = first.addPerson(PersonDraft("Kept"), details = PersonDetails(listOf(job), listOf(Source("S"))))
            val second = FamilyRepository(context, name, safety())
            second.load()
            assertEquals(listOf(job), second.details(id).events)
            assertEquals(listOf(Source("S")), second.details(id).sources)
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun aVersionTwoDatabaseIsUpgradedWithoutLosingAnything() = runBlocking {
        val name = "kinhold-migrate2-${System.nanoTime()}.db"
        try {
            // A database exactly as version 0.8.0 made it: photo table, no event tables, no month or day columns.
            val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
            db.execSQL("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, given TEXT NOT NULL DEFAULT '', surname TEXT NOT NULL DEFAULT '', sex INTEGER NOT NULL DEFAULT 2, birth_year INTEGER, death_year INTEGER, birth_place TEXT NOT NULL DEFAULT '', death_place TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE parent_link (child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (child_id, parent_id))")
            db.execSQL("CREATE INDEX parent_link_parent ON parent_link(parent_id)")
            db.execSQL("CREATE TABLE partnership (a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (a_id, b_id), CHECK (a_id < b_id))")
            db.execSQL("CREATE INDEX partnership_b ON partnership(b_id)")
            db.execSQL("CREATE TABLE photo (person_id INTEGER PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE, stamp INTEGER NOT NULL, data BLOB NOT NULL, thumb BLOB NOT NULL)")
            db.execSQL("INSERT INTO person (id, given, surname, sex, birth_year, birth_place, notes) VALUES (1, 'Old', 'Parent', 0, 1950, 'Lahore', 'kept note'), (2, 'Old', 'Spouse', 1, 1952, '', ''), (3, 'Old', 'Child', 1, 1980, '', '')")
            db.execSQL("INSERT INTO parent_link (child_id, parent_id) VALUES (3, 1), (3, 2)")
            db.execSQL("INSERT INTO partnership (a_id, b_id) VALUES (1, 2)")
            db.execSQL("INSERT INTO photo (person_id, stamp, data, thumb) VALUES (1, 12345, x'FFD8FFE000', x'FFD8FFE001')")
            db.version = 2
            db.close()

            val repo = FamilyRepository(context, name, safety())
            repo.load()
            val f = repo.family.value
            assertEquals(3, f.size)
            assertEquals(1950, f.person(1).born)
            assertEquals("Lahore", f.person(1).birthPlace)
            assertEquals("kept note", f.person(1).notes)
            assertEquals(null, f.person(1).birthMonth)
            assertEquals(listOf(1, 2), f.parentsOf(3).map { it.id })
            assertEquals(listOf(2), f.spousesOf(1).map { it.id })
            assertEquals(12345L, f.person(1).photoStamp)
            assertNotNull(repo.thumb(1))
            assertTrue(repo.details(1).isEmpty)

            // The new columns and tables work on the upgraded database, and it still opens after being closed.
            repo.updatePerson(
                1, PersonDraft("Old", "Parent", born = 1950, birthMonth = 4, birthDay = 9, birthPlace = "Lahore", notes = "kept note"),
                details = PersonDetails(listOf(job), listOf(Source("S")), mapOf(2 to listOf(marriage))),
            )
            val reopened = FamilyRepository(context, name, safety())
            reopened.load()
            assertEquals(PartialDate(1950, 4, 9), reopened.family.value.person(1).birthDate)
            assertEquals(listOf(job), reopened.details(1).events)
            assertEquals(listOf(marriage), reopened.details(1).coupleEvents.getValue(2))
            assertEquals(12345L, reopened.family.value.person(1).photoStamp)
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun aLargeTreeWithManyEventsStoresAndReadsQuickly() = runBlocking {
        val repo = repo()
        val start = System.nanoTime()
        var last = 0
        repeat(300) { i ->
            val events = List(20) { Event(EventKind.Residence, "", PartialDate(1900 + it), "Place $it", sources = listOf(Source("S$it"))) }
            last = repo.addPerson(PersonDraft("P$i"), details = PersonDetails(events))
        }
        assertEquals(20, repo.details(last).events.size)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("300 people with 20 events each took $ms ms", ms < 60_000)
        val backup = repo.exportBackup()
        assertEquals(300, BackupCodec.decode(backup).details.events.size)
    }

}
