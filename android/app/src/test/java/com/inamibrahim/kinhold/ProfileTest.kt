package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.CustomField
import com.inamibrahim.kinhold.model.EventJson
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.Limits
import com.inamibrahim.kinhold.model.Merge
import com.inamibrahim.kinhold.model.MergeField
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.Share
import com.inamibrahim.kinhold.model.TreeDetails
import com.inamibrahim.kinhold.model.ageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class ProfileTest {
    private val today = LocalDate.of(2026, 11, 7)
    private val uid = "88888888-8888-4888-8888-888888888888"

    // ---------------------------------------------------------------- age

    @Test
    fun aLivingPersonsAgeIsWorkedOutFromTheDateOfBirth() {
        assertEquals("Age 28", Person(1, "A", "B", born = 1998, birthMonth = 11, birthDay = 7).ageText(today))
        assertEquals("Age 27", Person(1, "A", "B", born = 1998, birthMonth = 11, birthDay = 8).ageText(today)) // birthday tomorrow
        assertEquals("Age 28", Person(1, "A", "B", born = 1998, birthMonth = 3, birthDay = 1).ageText(today))
    }

    @Test
    fun withOnlyAYearTheAgeIsAnEstimate() {
        assertEquals("About 28 years old", Person(1, "A", "B", born = 1998).ageText(today))
        assertEquals("About 1 year old", Person(1, "A", "B", born = 2025).ageText(today))
    }

    @Test
    fun noAgeForTheDeadTheUnbornOrThoseWithoutABirthYear() {
        assertEquals("", Person(1, "A", "B").ageText(today))
        assertEquals("", Person(1, "A", "B", born = 1850).ageText(today)) // too old to be alive and no death date
        assertEquals("", Person(1, "A", "B", born = 2030).ageText(today))
    }

    @Test
    fun aDeadPersonShowsTheAgeTheyReached() {
        assertEquals("Aged 71 when they died", Person(1, "A", "B", born = 1948, birthMonth = 6, birthDay = 4, died = 2019, deathMonth = 7, deathDay = 1).ageText(today))
        assertEquals("About 71 when they died", Person(1, "A", "B", born = 1948, died = 2019).ageText(today))
        assertEquals("", Person(1, "A", "B", born = 1950, died = 1940).ageText(today))
    }

    // ---------------------------------------------------------------- email and own facts

    private val people = listOf(
        Person(1, "Inam", "Ibrahim", email = "inam@example.com", uid = uid),
        Person(2, "Other", "Person"),
    )
    private val family = Family.of(people, emptyList(), emptyList())
    private val details = TreeDetails(fields = mapOf(1 to listOf(CustomField("Blood group", "O+"), CustomField("Nickname", "Inu"))))

    @Test
    fun emailAndOwnFactsSurviveABackup() {
        val data = BackupCodec.decode(BackupCodec.encode(family, "", emptyMap(), details))
        assertEquals("inam@example.com", data.people[0].email)
        assertEquals("", data.people[1].email)
        assertEquals(details.fields, data.details.fields)
    }

    @Test
    fun tooManyFactsMakeABackupDamaged() {
        val many = (1..Limits.FIELDS + 1).joinToString(",", prefix = "[", postfix = "]") { """{"label":"L$it","value":"v"}""" }
        val text = """{"format":"kinhold-backup","version":6,"people":[{"id":1,"given":"A","fields":$many}],"parentLinks":[],"partnerships":[]}"""
        try {
            BackupCodec.decode(text)
            fail("Expected a refusal")
        } catch (e: BackupException) {
            assertEquals("This backup is damaged and cannot be read.", e.message)
        }
    }

    @Test
    fun longFactsAreCutAndBlankOnesDropped() {
        val text = """{"format":"kinhold-backup","version":6,"people":[{"id":1,"given":"A","fields":[{"label":"${"x".repeat(200)}","value":"${"y".repeat(2000)}"},{"label":"","value":""}]}],"parentLinks":[],"partnerships":[]}"""
        val f = BackupCodec.decode(text).details.fields.getValue(1).single()
        assertEquals(Limits.FIELD_LABEL, f.label.length)
        assertEquals(Limits.FIELD_VALUE, f.value.length)
    }

    @Test
    fun theFormKeepsFactsAcrossARotationIncludingBlankOnesBeingTyped() {
        val d = PersonDetails(fields = listOf(CustomField("Blood", "O+"), CustomField()))
        assertEquals(d.fields, EventJson.decodeDetails(EventJson.encodeDetails(d), editing = true).fields)
        assertEquals(1, EventJson.decodeDetails(EventJson.encodeDetails(d)).fields.size)
    }

    @Test
    fun emailAndOwnFactsSurviveGedcom() {
        val text = Gedcom.write(family, details, "1.0", "7 NOV 2026")
        assertTrue(text.contains("1 EMAIL inam@@example.com"))
        assertTrue(text.contains("1 _FIELD O+"))
        assertTrue(text.contains("2 TYPE Blood group"))
        val r = Gedcom.read(text.toByteArray())
        assertEquals("inam@example.com", r.data.people[0].email)
        assertEquals(details.fields, r.data.details.fields)
        assertEquals(0, r.report.ignoredOther + r.report.ignoredEvents)
    }

    @Test
    fun emailAndFactsAreLeftOutOfASharedFileUnlessAsked() {
        assertEquals("", Share.subFamily(family, setOf(1, 2)).person(1).email)
        assertEquals("inam@example.com", Share.subFamily(family, setOf(1, 2), keepContact = true).person(1).email)
        assertTrue(Share.subDetails(details, setOf(1, 2)).fields.isEmpty())
        assertEquals(2, Share.subDetails(details, setOf(1, 2), keepFields = true).fields.getValue(1).size)
    }

    @Test
    fun mergingOffersEmailAndAddsOnlyFactsYouLack() {
        val mine = Family.of(listOf(Person(1, "Inam", "Ibrahim", uid = uid)), emptyList(), emptyList())
        val mineDetails = TreeDetails(fields = mapOf(1 to listOf(CustomField("blood group", "o+"))))
        val his = BackupData(
            listOf(Person(10, "Inam", "Ibrahim", email = "inam@example.com", uid = uid)), emptyList(), emptyList(),
            details = TreeDetails(fields = mapOf(10 to listOf(CustomField("Blood group", "O+"), CustomField("Nickname", "Inu")))),
        )
        val m = Merge(mine, mineDetails, his)
        val email = m.fieldChanges(m.exact).single { it.part == MergeField.Email }
        assertTrue(email.isFill)
        val r = m.resolve(emptySet(), setOf(10 to MergeField.Email), addEvents = true)
        assertEquals("inam@example.com", r.updates.getValue(1).email)
        assertEquals(listOf(CustomField("Nickname", "Inu")), r.personFields.getValue(1))
        assertTrue(m.resolve(emptySet(), emptySet(), addEvents = false).personFields.isEmpty())
    }
}
