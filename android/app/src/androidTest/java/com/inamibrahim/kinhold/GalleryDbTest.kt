package com.inamibrahim.kinhold

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.data.PhotoCodec
import com.inamibrahim.kinhold.data.PreparedPhoto
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Runs on the phone: extra photos are stored, limited, kept by backups, restores and delete-undo, and removed with the person. */
@RunWith(AndroidJUnit4::class)
class GalleryDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun repo() = FamilyRepository(context, null, File(context.cacheDir, "gal-safety-${System.nanoTime()}.json").also { files += it })
        .also { runBlocking { it.load() } }

    private fun photo(color: Int): PreparedPhoto {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        val main = out.toByteArray()
        return PreparedPhoto(main, PhotoCodec.thumbOf(main))
    }

    @Test
    fun photosAreAddedListedOpenedAndRemoved() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("A"))
        r.addGalleryPhoto(id, photo(Color.RED))
        r.addGalleryPhoto(id, photo(Color.BLUE))
        val list = r.gallery(id)
        assertEquals(2, list.size)
        assertNotNull(r.galleryPhoto(list[0].id))
        r.removeGalleryPhoto(list[0].id)
        assertEquals(1, r.gallery(id).size)
        assertNull(r.galleryPhoto(list[0].id))
    }

    @Test
    fun aPersonCanHaveAtMostTwelveExtraPhotos() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("A"))
        repeat(12) { r.addGalleryPhoto(id, photo(Color.GREEN)) }
        try {
            r.addGalleryPhoto(id, photo(Color.GREEN))
            fail("The thirteenth photo should be refused")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("12"))
        }
        assertEquals(12, r.gallery(id).size)
    }

    @Test
    fun photosSurviveBackupRestoreUndoAndDeleteUndo() = runBlocking {
        val r = repo()
        val id = r.addPerson(PersonDraft("A"))
        r.addGalleryPhoto(id, photo(Color.RED))
        r.addGalleryPhoto(id, photo(Color.BLUE))
        val data = BackupCodec.decode(r.exportBackup())
        assertEquals(2, data.gallery.getValue(id).size)

        val other = repo()
        val own = other.addPerson(PersonDraft("Other"))
        other.addGalleryPhoto(own, photo(Color.YELLOW))
        other.replaceAll(data)
        assertEquals(2, other.gallery(id).size)
        other.undoRestore()
        assertEquals(1, other.gallery(other.family.value.people.single().id).size)

        val deleted = r.deletePerson(id)!!
        r.restoreDeleted(deleted)
        assertEquals(2, r.gallery(id).size)
    }
}
