package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonUid
import com.inamibrahim.kinhold.model.TreeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PersonUidTest {
    private val a = "3f2b8c1e-0a4d-4b7e-9c11-5d6e7f8a9b0c"
    private val b = "0b9a8c7d-6e5f-4a3b-8c2d-1e0f9a8b7c6d"

    @Test
    fun createdIdsAreValidAndDifferent() {
        val ids = List(1000) { PersonUid.create() }
        assertTrue(ids.all { PersonUid.isValid(it) })
        assertEquals(1000, ids.toSet().size)
    }

    @Test
    fun onlyLowerCaseCanonicalUuidsAreAccepted() {
        assertTrue(PersonUid.isValid(a))
        assertFalse(PersonUid.isValid(a.uppercase()))
        assertFalse(PersonUid.isValid(""))
        assertFalse(PersonUid.isValid("not-an-id"))
        assertFalse(PersonUid.isValid("$a "))
        assertFalse(PersonUid.isValid(a.replace("-", "")))
    }

    private val family = Family.of(listOf(Person(1, "A", "X", uid = a), Person(2, "B", "X", uid = b), Person(3, "C", "X")), emptyList(), listOf(1 to 2))

    @Test
    fun idsSurviveABackup() {
        val data = BackupCodec.decode(BackupCodec.encode(family, ""))
        assertEquals(listOf(a, b, ""), data.people.map { it.uid })
    }

    @Test
    fun backupsFromBeforeVersionFourHaveNoIds() {
        val v3 = """{"format":"kinhold-backup","version":3,"people":[{"id":1,"given":"A"},{"id":2,"given":"B"}],"parentLinks":[],"partnerships":[]}"""
        assertEquals(listOf("", ""), BackupCodec.decode(v3).people.map { it.uid })
    }

    @Test
    fun aBadOrRepeatedIdMeansTheBackupIsDamaged() {
        fun file(u1: String, u2: String) =
            """{"format":"kinhold-backup","version":4,"people":[{"id":1,"given":"A","uid":"$u1"},{"id":2,"given":"B","uid":"$u2"}],"parentLinks":[],"partnerships":[]}"""
        BackupCodec.decode(file(a, b))
        for (bad in listOf(file(a, a), file("nope", b), file(a.uppercase(), b))) {
            try {
                BackupCodec.decode(bad)
                fail("Expected a refusal")
            } catch (e: BackupException) {
                assertEquals("This backup is damaged and cannot be read.", e.message)
            }
        }
    }

    @Test
    fun idsSurviveGedcom() {
        val text = Gedcom.write(family, TreeDetails.Empty, "1.0", "7 NOV 2026")
        assertTrue(text.contains("1 _UID $a"))
        val people = Gedcom.read(text.toByteArray()).data.people
        assertEquals(listOf(a, b, ""), people.map { it.uid })
    }

    @Test
    fun gedcomIdsFromOtherProgramsAreUsedOnlyWhenTheyAreValidAndUnique() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME A /X/\n1 _UID $a\n0 @I2@ INDI\n1 NAME B /X/\n1 _UID $a\n" +
            "0 @I3@ INDI\n1 NAME C /X/\n1 _UID 6F1A2B3C4D5E6F708192A3B4C5D6E7F8\n0 @I4@ INDI\n1 NAME D /X/\n1 UID ${b.uppercase()}\n0 TRLR"
        val ids = Gedcom.read(text.toByteArray()).data.people.map { it.uid }
        assertEquals(listOf(a, "", "", b), ids) // the repeat and the non-UUID are dropped; upper case is lowered
        assertNotEquals(ids[0], ids[1])
    }
}
