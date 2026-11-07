package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TestFamily
import com.inamibrahim.kinhold.model.describeAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private fun rejects(text: String): BackupException {
        try {
            BackupCodec.decode(text)
        } catch (e: BackupException) {
            return e
        }
        fail("Expected the file to be refused: ${text.take(80)}")
        throw AssertionError()
    }

    private fun valid(extra: String = "", people: String = """[{"id":1,"given":"A"},{"id":2,"given":"B"}]""") =
        """{"format":"kinhold-backup","version":1,"people":$people,"parentLinks":[],"partnerships":[]$extra}"""

    @Test
    fun aTreeSurvivesBeingWrittenAndReadBack() {
        val family = TestFamily.generateFamily(300)
        val data = BackupCodec.decode(BackupCodec.encode(family, "2026-11-07T10:00:00Z"))
        assertEquals(300, data.people.size)
        assertEquals("2026-11-07T10:00:00Z", data.created)
        val again = Family.of(data.people, data.parentLinks, data.partnerships)
        for (p in family.people) {
            val q = again.person(p.id)
            assertEquals(p.name, q.name)
            assertEquals(p.sex, q.sex)
            assertEquals(p.born, q.born)
            assertEquals(p.died, q.died)
            assertEquals(family.parentsOf(p.id).map { it.id }, again.parentsOf(p.id).map { it.id })
            assertEquals(family.spousesOf(p.id).map { it.id }.sorted(), again.spousesOf(p.id).map { it.id }.sorted())
        }
    }

    @Test
    fun everyFieldSurvivesIncludingUnicodeAndQuotes() {
        val person = Person(7, "Ayesha \"Aisha\"", "خان", Sex.Female, 1950, null, "Lahore, \\Punjab\\", "", "Line one\nLine two — ünïcode")
        val data = BackupCodec.decode(BackupCodec.encode(Family.of(listOf(person), emptyList(), emptyList()), ""))
        val p = data.people.single()
        assertEquals(person.given, p.given)
        assertEquals(person.surname, p.surname)
        assertEquals(Sex.Female, p.sex)
        assertEquals(1950, p.born)
        assertNull(p.died)
        assertEquals(person.birthPlace, p.birthPlace)
        assertEquals(person.notes, p.notes)
    }

    private fun jpeg(size: Int) = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(size) { (it % 251).toByte() }

    @Test
    fun photosSurviveBeingWrittenAndReadBackByteForByte() {
        val family = Family.of(listOf(Person(1, "A", "X"), Person(2, "B", "X"), Person(3, "C", "X")), emptyList(), emptyList())
        val photos = mapOf(1 to jpeg(5000), 3 to jpeg(120_000))
        val data = BackupCodec.decode(BackupCodec.encode(family, "", photos))
        assertEquals(setOf(1, 3), data.photos.keys)
        assertTrue(photos.getValue(1).contentEquals(data.photos.getValue(1)))
        assertTrue(photos.getValue(3).contentEquals(data.photos.getValue(3)))
    }

    @Test
    fun aBackupWithoutPhotosHasNoPhotosAndVersionOneFilesStillOpen() {
        assertTrue(BackupCodec.decode(BackupCodec.encode(Family.Empty, "")).photos.isEmpty())
        val v1 = """{"format":"kinhold-backup","version":1,"people":[{"id":1,"given":"Old"}],"parentLinks":[],"partnerships":[]}"""
        val data = BackupCodec.decode(v1)
        assertEquals("Old", data.people.single().given)
        assertTrue(data.photos.isEmpty())
    }

    @Test
    fun badPhotosMakeTheWholeBackupDamaged() {
        fun withPhoto(value: String) =
            """{"format":"kinhold-backup","version":2,"people":[{"id":1,"photo":$value}],"parentLinks":[],"partnerships":[]}"""
        val notBase64 = "\"%%%not base64%%%\""
        val notAJpeg = "\"" + java.util.Base64.getEncoder().encodeToString("just some text".toByteArray()) + "\""
        val tooBig = "\"" + java.util.Base64.getEncoder().encodeToString(jpeg(3_100_000)) + "\""
        for (value in listOf(notBase64, notAJpeg, tooBig, "5", "[1,2]")) {
            assertEquals("Wrong message for $value", "This backup is damaged and cannot be read.", rejects(withPhoto(value)).message)
        }
    }

    @Test
    fun anEmptyTreeIsAValidBackup() {
        assertEquals(0, BackupCodec.decode(BackupCodec.encode(Family.Empty, "")).people.size)
    }

    @Test
    fun filesThatAreNotBackupsAreRefusedWithAPlainMessage() {
        assertEquals("This file is not a Kinhold backup.", rejects("hello").message)
        assertEquals("This file is not a Kinhold backup.", rejects("").message)
        assertEquals("This file is not a Kinhold backup.", rejects("[1,2,3]").message)
        assertEquals("This file is not a Kinhold backup.", rejects("""{"format":"something-else","version":1}""").message)
        assertEquals("This file is not a Kinhold backup.", rejects("""{"format":"kinhold-backup"}""").message)
    }

    @Test
    fun aBackupFromANewerVersionIsRefusedWithAnUpdateHint() {
        val e = rejects("""{"format":"kinhold-backup","version":9,"people":[]}""")
        assertTrue(e.message!!.contains("newer version"))
    }

    @Test
    fun damagedBackupsAreRefusedAndNeverCrash() {
        val damaged = listOf(
            valid(people = """[{"id":1},{"id":1}]"""),                                   // two people with one id
            valid(people = """[{"given":"no id"}]"""),                                   // missing id
            valid(people = """[{"id":"x"}]"""),                                          // id is not a number
            valid(people = """[{"id":1,"born":99999}]"""),                               // impossible year
            valid(people = """[{"id":1,"born":-5}]"""),
            valid(people = """{"id":1}"""),                                              // people is not a list
            valid(people = """[5]"""),                                                   // a person that is not an object
            valid().replace(""""parentLinks":[]""", """"parentLinks":[[1,99]]"""),       // link to someone missing
            valid().replace(""""parentLinks":[]""", """"parentLinks":[[1,1]]"""),        // someone their own parent
            valid().replace(""""partnerships":[]""", """"partnerships":[[1,2,3],[5]]"""), // malformed pairs
            valid().replace(""""partnerships":[]""", """"partnerships":[[1,"b"]]"""),
        )
        for (text in damaged) assertEquals("Wrong message for $text", "This backup is damaged and cannot be read.", rejects(text).message)
    }

    @Test
    fun aFileCutShortIsRefusedAtEveryLength() {
        val text = BackupCodec.encode(TestFamily.generateFamily(60), "")
        for (cut in listOf(1, 10, text.length / 4, text.length / 2, text.length - 20, text.length - 1)) {
            try {
                BackupCodec.decode(text.take(cut))
                fail("A file cut to $cut characters should not be accepted")
            } catch (e: BackupException) {
                // Refused, as it should be.
            }
        }
    }

    @Test
    fun fieldsThisVersionDoesNotKnowAreIgnored() {
        val text = valid(extra = ""","futureThing":{"a":1}""", people = """[{"id":1,"given":"A","mood":"happy"},{"id":2}]""")
        val data = BackupCodec.decode(text)
        assertEquals(2, data.people.size)
        assertEquals("", data.people[1].given)
    }

    @Test
    fun missingOptionalFieldsGetSensibleDefaults() {
        val p = BackupCodec.decode(valid(people = """[{"id":3,"given":"Only"}]""")).people.single()
        assertEquals(Sex.Unknown, p.sex)
        assertNull(p.born)
        assertEquals("", p.notes)
    }

    @Test
    fun repeatedLinksAreCollapsedAndCouplesAreOrdered() {
        val text = valid(people = """[{"id":1},{"id":2},{"id":3}]""")
            .replace(""""parentLinks":[]""", """"parentLinks":[[1,2],[1,2]]""")
            .replace(""""partnerships":[]""", """"partnerships":[[3,2],[2,3]]""")
        val data = BackupCodec.decode(text)
        assertEquals(listOf(1 to 2), data.parentLinks)
        assertEquals(listOf(2 to 3), data.partnerships)
    }

    @Test
    fun ageIsDescribedInPlainWords() {
        val day = 86_400_000L
        val now = 100 * day
        assertEquals("today", describeAge(now - 1000, now))
        assertEquals("yesterday", describeAge(now - day - 1000, now))
        assertEquals("5 days ago", describeAge(now - 5 * day, now))
        assertEquals("3 months ago", describeAge(now - 95 * day, now))
        assertEquals("today", describeAge(now + day, now))
    }
}
