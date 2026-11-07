package com.inamibrahim.kinhold

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.PhotoChange
import com.inamibrahim.kinhold.data.PhotoCodec
import com.inamibrahim.kinhold.data.PreparedPhoto
import com.inamibrahim.kinhold.data.Relation
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Runs on the phone: real pictures, real image decoding and real SQLite. Pictures are invented, never from the gallery. */
@RunWith(AndroidJUnit4::class)
class PhotoTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun tempFile(suffix: String) = File(context.cacheDir, "phototest-${System.nanoTime()}$suffix").also { files += it }

    private fun repo() = FamilyRepository(context, dbName = null, safetyFile = tempFile(".json")).also { runBlocking { it.load() } }

    /** A made-up picture: a blue field with a white disc, optionally tagged as taken sideways. */
    private fun picture(width: Int, height: Int, orientation: Int? = null): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(40, 120, 200))
            drawCircle(width / 2f, height / 3f, height / 6f, Paint().apply { color = Color.WHITE })
        }
        val file = tempFile(".jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (orientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    private fun prepared(width: Int = 1200, height: Int = 900): PreparedPhoto = PhotoCodec.prepare(context.contentResolver, picture(width, height))

    private fun dimensions(jpeg: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun aLargePictureIsShrunkAndGetsASquareThumbnail() {
        val photo = PhotoCodec.prepare(context.contentResolver, picture(3000, 2000))
        assertEquals(720 to 480, dimensions(photo.main))
        assertEquals(256 to 256, dimensions(photo.thumb))
        assertTrue("Stored picture is ${photo.main.size} bytes", photo.main.size < 300_000)
        assertTrue("Thumbnail is ${photo.thumb.size} bytes", photo.thumb.size < 40_000)
    }

    @Test
    fun aSmallPictureIsNotEnlarged() {
        val photo = PhotoCodec.prepare(context.contentResolver, picture(400, 300))
        assertEquals(400 to 300, dimensions(photo.main))
    }

    @Test
    fun aPictureTakenSidewaysIsTurnedUpright() {
        // Stored as 1000 wide by 500 high but tagged "rotate 90": it should come out tall.
        val photo = PhotoCodec.prepare(context.contentResolver, picture(1000, 500, ExifInterface.ORIENTATION_ROTATE_90))
        val (w, h) = dimensions(photo.main)
        assertTrue("Expected a tall picture, got $w by $h", h > w)
        assertEquals(720, h)
    }

    @Test
    fun aFileThatIsNotAPictureIsRefused() {
        val file = tempFile(".txt").also { it.writeText("just some words") }
        try {
            PhotoCodec.prepare(context.contentResolver, Uri.fromFile(file))
            fail("Expected an IOException")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.isNotBlank())
        }
    }

    @Test
    fun aPhotoIsStoredReplacedAndRemoved() = runBlocking {
        val repo = repo()
        val id = repo.addPerson(PersonDraft("With", "Photo"), photo = prepared())
        val first = repo.family.value.person(id)
        assertTrue(first.hasPhoto)
        val thumb = repo.thumb(id)
        assertNotNull(thumb)
        assertEquals(256 to 256, dimensions(thumb!!))

        Thread.sleep(5)
        repo.updatePerson(id, PersonDraft("With", "Photo"), PhotoChange.Set(prepared(800, 800)))
        assertTrue("A new photo must get a new stamp so pictures are reloaded", repo.family.value.person(id).photoStamp > first.photoStamp)

        // Editing the name alone keeps the photo.
        repo.updatePerson(id, PersonDraft("Renamed", "Photo"))
        assertTrue(repo.family.value.person(id).hasPhoto)

        repo.updatePerson(id, PersonDraft("Renamed", "Photo"), PhotoChange.Remove)
        assertTrue(!repo.family.value.person(id).hasPhoto)
        assertNull(repo.thumb(id))
    }

    @Test
    fun aPersonWithoutAPhotoHasNoThumbnail() = runBlocking {
        val repo = repo()
        val id = repo.addPerson(PersonDraft("No", "Photo"))
        assertTrue(!repo.family.value.person(id).hasPhoto)
        assertNull(repo.thumb(id))
    }

    @Test
    fun deletingAPersonDeletesTheirPhotoAndUndoBringsItBack() = runBlocking {
        val repo = repo()
        val parent = repo.addPerson(PersonDraft("Parent"))
        val id = repo.addPerson(PersonDraft("Photo", "Child"), Relation.ChildOf(parent), prepared())
        val deleted = repo.deletePerson(id)!!
        assertNull("The photo goes with the person", repo.thumb(id))
        repo.restoreDeleted(deleted)
        assertTrue(repo.family.value.person(id).hasPhoto)
        assertNotNull(repo.thumb(id))
        assertEquals(listOf(parent), repo.family.value.parentsOf(id).map { it.id })
    }

    @Test
    fun aBackupCarriesPhotosAndARestoreBringsThemBack() = runBlocking {
        val source = repo()
        val a = source.addPerson(PersonDraft("Has", "Photo"), photo = prepared())
        source.addPerson(PersonDraft("No", "Photo"))
        val data = BackupCodec.decode(source.exportBackup())
        assertEquals(setOf(a), data.photos.keys)

        val target = repo()
        target.addPerson(PersonDraft("Existing"))
        target.replaceAll(data)
        assertTrue(target.family.value.person(a).hasPhoto)
        assertEquals(256 to 256, dimensions(target.thumb(a)!!))
        assertEquals(1, target.family.value.people.count { it.hasPhoto })
    }

    @Test
    fun undoingARestoreBringsBackThePhotosOfTheOldTree() = runBlocking {
        val repo = repo()
        val mine = repo.addPerson(PersonDraft("Mine"), photo = prepared())
        val other = repo()
        other.addPerson(PersonDraft("Other"))
        repo.replaceAll(BackupCodec.decode(other.exportBackup()))
        assertEquals(listOf("Other"), repo.family.value.people.map { it.given })
        repo.undoRestore()
        assertEquals(listOf("Mine"), repo.family.value.people.map { it.given })
        assertTrue(repo.family.value.person(mine).hasPhoto)
        assertNotNull(repo.thumb(mine))
    }

    @Test
    fun addingAnImportKeepsPhotosOnBothSides() = runBlocking {
        val source = repo()
        source.addPerson(PersonDraft("Imported"), photo = prepared())
        val repo = repo()
        val mine = repo.addPerson(PersonDraft("Mine"), photo = prepared())
        repo.addAll(BackupCodec.decode(source.exportBackup()))
        assertEquals(2, repo.family.value.people.count { it.hasPhoto })
        assertNotNull(repo.thumb(mine))
        val imported = repo.family.value.people.single { it.given == "Imported" }
        assertNotNull(repo.thumb(imported.id))
    }

    @Test
    fun aVersionOneDatabaseIsUpgradedWithoutLosingAnything() = runBlocking {
        val name = "kinhold-migrate-${System.nanoTime()}.db"
        try {
            // A database exactly as the first version of the app made it: no photo table, user_version 1.
            val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
            db.execSQL("CREATE TABLE person (id INTEGER PRIMARY KEY AUTOINCREMENT, given TEXT NOT NULL DEFAULT '', surname TEXT NOT NULL DEFAULT '', sex INTEGER NOT NULL DEFAULT 2, birth_year INTEGER, death_year INTEGER, birth_place TEXT NOT NULL DEFAULT '', death_place TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE parent_link (child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (child_id, parent_id))")
            db.execSQL("CREATE TABLE partnership (a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE, PRIMARY KEY (a_id, b_id), CHECK (a_id < b_id))")
            db.execSQL("INSERT INTO person (id, given, surname, sex, birth_year) VALUES (1, 'Old', 'Parent', 0, 1950), (2, 'Old', 'Child', 1, 1980)")
            db.execSQL("INSERT INTO parent_link (child_id, parent_id) VALUES (2, 1)")
            db.version = 1
            db.close()

            val repo = FamilyRepository(context, name, tempFile(".json"))
            repo.load()
            val f = repo.family.value
            assertEquals(2, f.size)
            assertEquals("Old", f.person(1).given)
            assertEquals(1950, f.person(1).born)
            assertEquals(listOf(1), f.parentsOf(2).map { it.id })
            assertTrue(!f.person(1).hasPhoto)

            // The new table works on the upgraded database.
            repo.updatePerson(1, PersonDraft("Old", "Parent", born = 1950), PhotoChange.Set(prepared()))
            assertTrue(repo.family.value.person(1).hasPhoto)
            assertNotNull(repo.thumb(1))
        } finally {
            context.deleteDatabase(name)
        }
    }
}
