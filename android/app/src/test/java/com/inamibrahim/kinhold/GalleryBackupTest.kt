package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.TreeDetails
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class GalleryBackupTest {
    private fun jpeg(n: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), n.toByte(), 1, 2, 3)
    private val family = Family.of(listOf(Person(1, "A", ""), Person(2, "B", "")), emptyList(), emptyList())

    @Test
    fun extraPhotosSurviveABackupInOrder() {
        val gallery = mapOf(1 to listOf(jpeg(1), jpeg(2), jpeg(3)))
        val data = BackupCodec.decode(BackupCodec.encode(family, "", emptyMap(), TreeDetails.Empty, gallery))
        assertEquals(setOf(1), data.gallery.keys)
        assertEquals(3, data.gallery.getValue(1).size)
        assertArrayEquals(jpeg(2), data.gallery.getValue(1)[1])
    }

    @Test
    fun oldBackupsHaveNoGallery() {
        val v6 = """{"format":"kinhold-backup","version":6,"people":[{"id":1,"given":"A"}],"parentLinks":[],"partnerships":[]}"""
        assertTrue(BackupCodec.decode(v6).gallery.isEmpty())
    }

    private fun refused(text: String) {
        try {
            BackupCodec.decode(text)
            fail("Expected a refusal")
        } catch (e: BackupException) {
            assertEquals("This backup is damaged and cannot be read.", e.message)
        }
    }

    @Test
    fun tooManyOrNonPictureGalleryEntriesMakeABackupDamaged() {
        val ok = Base64.getEncoder().encodeToString(jpeg(1))
        fun file(items: String) = """{"format":"kinhold-backup","version":7,"people":[{"id":1,"given":"A","gallery":[$items]}],"parentLinks":[],"partnerships":[]}"""
        BackupCodec.decode(file("\"$ok\""))
        refused(file((1..13).joinToString(",") { "\"$ok\"" }))
        refused(file("\"${Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5, 6))}\""))
        refused(file("\"!!not base64!!\""))
    }
}
