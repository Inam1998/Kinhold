package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.GeoPin
import com.inamibrahim.kinhold.model.Merge
import com.inamibrahim.kinhold.model.MergeField
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.PersonUid
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PhoneNumber
import com.inamibrahim.kinhold.model.Share
import com.inamibrahim.kinhold.model.TreeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ContactTest {
    // ---------------------------------------------------------------- map pins

    @Test
    fun pinsAreReadFromCoordinatesAndFromMapLinks() {
        val lahore = GeoPin(31.5204, 74.3587)
        assertEquals(lahore, GeoPin.parse("31.5204, 74.3587"))
        assertEquals(lahore, GeoPin.parse("  31.5204 74.3587 "))
        assertEquals(lahore, GeoPin.parse("31.5204;74.3587"))
        assertEquals(lahore, GeoPin.parse("https://www.google.com/maps/@31.5204,74.3587,15z"))
        assertEquals(lahore, GeoPin.parse("https://www.google.com/maps/place/Lahore/@31.5204,74.3587,12z/data=!3m1"))
        assertEquals(lahore, GeoPin.parse("https://www.google.com/maps/place/X/data=!3d31.5204!4d74.3587"))
        assertEquals(lahore, GeoPin.parse("https://maps.google.com/?q=31.5204,74.3587"))
        assertEquals(lahore, GeoPin.parse("geo:31.5204,74.3587?z=10"))
        assertEquals(GeoPin(-33.8688, 151.2093), GeoPin.parse("-33.8688, 151.2093"))
    }

    @Test
    fun thingsThatAreNotPinsAreRefused() {
        assertNull(GeoPin.parse(""))
        assertNull(GeoPin.parse("Lahore"))
        assertNull(GeoPin.parse("https://maps.app.goo.gl/AbCdEf123")) // a short link holds no coordinates
        assertNull(GeoPin.parse("95.0, 10.0")) // latitude out of range
        assertNull(GeoPin.parse("10.0, 200.0")) // longitude out of range
        assertNull(GeoPin.parse("31.5204"))
    }

    @Test
    fun aPinPrintsInAFormThatParsesBack() {
        val pin = GeoPin(-1.28333, 36.81667)
        assertEquals("-1.28333, 36.81667", pin.text())
        assertEquals(pin, GeoPin.parse(pin.text()))
    }

    // ---------------------------------------------------------------- phone numbers

    @Test
    fun phoneNumbersBecomeDialableAndWhatsAppReady() {
        assertEquals("+923001234567", PhoneNumber.dialable("+92 300 123-4567"))
        assertEquals("03001234567", PhoneNumber.dialable("(0300) 123 4567"))
        assertEquals("", PhoneNumber.dialable("none"))
        assertEquals("923001234567", PhoneNumber.whatsApp("+92 300 1234567"))
        assertEquals("923001234567", PhoneNumber.whatsApp("0092 300 1234567"))
        assertNull(PhoneNumber.whatsApp("0300 1234567")) // no country code: WhatsApp cannot know the country
        assertNull(PhoneNumber.whatsApp(""))
        assertNull(PhoneNumber.whatsApp("+12"))
    }

    // ---------------------------------------------------------------- saving

    private val uid = "55555555-5555-4555-8555-555555555555"
    private val me = Person(1, "Inam", "Ibrahim", address = "12 Canal Road, Lahore", phone = "+92 300 1234567", lat = 31.5204, lng = 74.3587, isMe = true, uid = uid)
    private val other = Person(2, "Ahmed", "Raza", phone = "0300 1")
    private val family = Family.of(listOf(me, other), emptyList(), emptyList())

    @Test
    fun addressPhonePinAndTheMeMarkSurviveABackup() {
        val back = BackupCodec.decode(BackupCodec.encode(family, "")).people
        assertEquals("12 Canal Road, Lahore", back[0].address)
        assertEquals("+92 300 1234567", back[0].phone)
        assertEquals(31.5204, back[0].lat!!, 1e-9)
        assertEquals(74.3587, back[0].lng!!, 1e-9)
        assertTrue(back[0].isMe)
        assertFalse(back[1].isMe)
        assertNull(back[1].lat)
        assertEquals("0300 1", back[1].phone)
    }

    @Test
    fun oldBackupsHaveNoContactDetails() {
        val v4 = """{"format":"kinhold-backup","version":4,"people":[{"id":1,"given":"A"}],"parentLinks":[],"partnerships":[]}"""
        val p = BackupCodec.decode(v4).people.single()
        assertEquals("", p.address)
        assertEquals("", p.phone)
        assertNull(p.lat)
        assertFalse(p.isMe)
    }

    @Test
    fun badPinsOrTwoOwnersMakeABackupDamaged() {
        fun file(person1: String, person2: String = "") =
            """{"format":"kinhold-backup","version":5,"people":[{"id":1,"given":"A"$person1},{"id":2,"given":"B"$person2}],"parentLinks":[],"partnerships":[]}"""
        BackupCodec.decode(file(""","lat":10.5,"lng":20.5""", ""","me":true"""))
        for (bad in listOf(file(""","lat":10.5"""), file(""","lat":95.0,"lng":1.0"""), file(""","lng":181.0,"lat":1.0"""), file(""","me":true""", ""","me":true"""))) {
            try {
                BackupCodec.decode(bad)
                fail("Expected a refusal")
            } catch (e: BackupException) {
                assertEquals("This backup is damaged and cannot be read.", e.message)
            }
        }
    }

    @Test
    fun addressPhoneAndPinSurviveGedcom() {
        val text = Gedcom.write(family, TreeDetails.Empty, "1.0", "7 NOV 2026")
        assertTrue(text.contains("1 ADDR 12 Canal Road, Lahore"))
        assertTrue(text.contains("1 PHON +92 300 1234567"))
        assertTrue(text.contains("1 _PIN 31.52040, 74.35870"))
        assertFalse(text.contains("_ME"))
        val r = Gedcom.read(text.toByteArray())
        val p = r.data.people[0]
        assertEquals("12 Canal Road, Lahore", p.address)
        assertEquals("+92 300 1234567", p.phone)
        assertEquals(31.5204, p.lat!!, 1e-4)
        assertFalse(p.isMe)
        assertEquals(0, r.report.ignoredOther + r.report.ignoredEvents)
    }

    @Test
    fun structuredAndMultiLineAddressesAreRead() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME A /B/\n1 ADDR\n2 ADR1 5 High Street\n2 CITY Leeds\n2 POST LS1\n2 CTRY UK\n1 PHON 0113 000\n" +
            "0 @I2@ INDI\n1 NAME C /D/\n1 ADDR 7 Low Road\n2 CONT Multan\n0 TRLR"
        val people = Gedcom.read(text.toByteArray()).data.people
        assertEquals("5 High Street, Leeds, LS1, UK", people[0].address)
        assertEquals("0113 000", people[0].phone)
        assertEquals("7 Low Road, Multan", people[1].address)
    }

    // ---------------------------------------------------------------- sharing and merging

    @Test
    fun contactDetailsAndTheMeMarkAreLeftOutOfAShareUnlessAsked() {
        val ids = setOf(1, 2)
        val without = Share.subFamily(family, ids).person(1)
        assertEquals("", without.address)
        assertEquals("", without.phone)
        assertNull(without.lat)
        assertFalse(without.isMe)
        assertEquals("Inam", without.given)
        assertEquals(uid, without.uid)
        val with = Share.subFamily(family, ids, keepContact = true).person(1)
        assertEquals("+92 300 1234567", with.phone)
        assertEquals("12 Canal Road, Lahore", with.address)
        assertNull(null)
        assertFalse(with.isMe) // never sent, whatever the choice
    }

    @Test
    fun mergingOffersAddressPhoneAndPinLikeAnyField() {
        val mine = Family.of(listOf(Person(1, "A", "B", uid = uid, phone = "111")), emptyList(), emptyList())
        val his = BackupData(listOf(Person(10, "A", "B", uid = uid, phone = "222", address = "Here", lat = 1.0, lng = 2.0)), emptyList(), emptyList())
        val m = Merge(mine, TreeDetails.Empty, his)
        val changes = m.fieldChanges(m.exact).associateBy { it.part }
        assertFalse(changes.getValue(MergeField.Phone).isFill)
        assertTrue(changes.getValue(MergeField.Address).isFill)
        assertTrue(changes.getValue(MergeField.Pin).isFill)
        val d = m.resolve(emptySet(), setOf(10 to MergeField.Address, 10 to MergeField.Pin), true).updates.getValue(1)
        assertEquals("Here", d.address)
        assertEquals("111", d.phone)
        assertEquals(1.0, d.lat!!, 0.0)
        assertEquals(2.0, d.lng!!, 0.0)
    }

    @Test
    fun aDraftFromAPersonKeepsTheirContactDetails() {
        val d: PersonDraft = me.toDraft()
        assertEquals("+92 300 1234567", d.phone)
        assertEquals("12 Canal Road, Lahore", d.address)
        assertEquals(31.5204, d.lat!!, 0.0)
        assertTrue(PersonUid.isValid(d.uid))
    }
}
