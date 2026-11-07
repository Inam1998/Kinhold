package com.inamibrahim.kinhold.model

/** Order matters: parents are listed father first, so Male comes before Female and Unknown last. */
enum class Sex { Male, Female, Unknown }

/** One person in the tree. Years are optional because many people's dates are not known. */
class Person(
    val id: Int,
    val given: String,
    val surname: String,
    val sex: Sex = Sex.Unknown,
    val born: Int? = null,
    val died: Int? = null,
    val birthPlace: String = "",
    val deathPlace: String = "",
    val notes: String = "",
    /** When the photo was last set (0 if there is none). The photo itself is loaded only when it is drawn. */
    val photoStamp: Long = 0L,
    /** Month and day of birth and death, when known. They only exist alongside a year. */
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val deathMonth: Int? = null,
    val deathDay: Int? = null,
    /** A permanent random id (UUID text) that stays the same on every phone, so a shared copy of this person can be recognised. Empty only before it is stored. */
    val uid: String = "",
    /** Where they live, how to reach them, and an optional saved map pin (latitude and longitude). */
    val address: String = "",
    val phone: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    /** True for the person who is the owner of this tree ("this is me"); at most one person has it. */
    val isMe: Boolean = false,
    val email: String = "",
) {
    val birthDate: PartialDate get() = PartialDate(born, birthMonth, birthDay).normalized()
    val deathDate: PartialDate get() = PartialDate(died, deathMonth, deathDay).normalized()

    /** "12 Mar 1850 – 3 Jan 1920" with whatever is known, or an empty string. */
    fun lifeDates(): String {
        val b = birthDate.text()
        val d = deathDate.text()
        return when {
            b.isNotEmpty() && d.isNotEmpty() -> "$b – $d"
            b.isNotEmpty() -> "b. $b"
            d.isNotEmpty() -> "d. $d"
            else -> ""
        }
    }

    val hasPhoto: Boolean get() = photoStamp > 0

    /** The full name as written on a card; "Unnamed" if neither part is filled in. */
    val name: String
        get() = listOf(given, surname).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Unnamed" }

    /** "1950 – 2010", "b. 1990", "d. 1950" or nothing when no year is known. */
    fun years(): String = when {
        born != null && died != null -> "$born – $died"
        born != null -> "b. $born"
        died != null -> "d. $died"
        else -> ""
    }

    /** The same person without address, phone, map pin and the "this is me" mark, for files sent to other people. */
    fun withoutPrivateDetails(keepContact: Boolean) = Person(
        id, given, surname, sex, born, died, birthPlace, deathPlace, notes, photoStamp, birthMonth, birthDay, deathMonth, deathDay, uid,
        if (keepContact) address else "", if (keepContact) phone else "", if (keepContact) lat else null, if (keepContact) lng else null, isMe = false, email = if (keepContact) email else "",
    )

    fun toDraft() = PersonDraft(given, surname, sex, born, died, birthPlace, deathPlace, notes, birthMonth, birthDay, deathMonth, deathDay, uid, address, phone, lat, lng, email)
}

/** What the form fills in. It becomes a [Person] with an id when it is saved. */
class PersonDraft(
    val given: String = "",
    val surname: String = "",
    val sex: Sex = Sex.Unknown,
    val born: Int? = null,
    val died: Int? = null,
    val birthPlace: String = "",
    val deathPlace: String = "",
    val notes: String = "",
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val deathMonth: Int? = null,
    val deathDay: Int? = null,
    /** Kept when a stored person is copied or restored; empty for a new person, who gets one when saved. */
    val uid: String = "",
    val address: String = "",
    val phone: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val email: String = "",
) {
    val birthDate: PartialDate get() = PartialDate(born, birthMonth, birthDay)
    val deathDate: PartialDate get() = PartialDate(died, deathMonth, deathDay)

    /** Someone needs at least part of a name. */
    val hasName: Boolean get() = given.isNotBlank() || surname.isNotBlank()

    /** A person cannot die before they were born. */
    val datesMakeSense: Boolean get() = born == null || died == null || !deathDate.isBefore(birthDate)

    /** Months and days that are not real ones (a 13th month, 31 February) are refused. */
    val datesAreReal: Boolean get() = birthDate.isValid && deathDate.isValid

    val isValid: Boolean get() = hasName && datesMakeSense && datesAreReal
}

/**
 * A person (or a couple) and the children they had. [partners] holds the family member first and, when there is one,
 * the person they married second. Each child is the head of a unit of its own. Only the test family generator and
 * the layout use this; the stored model is [Family].
 */
class FamilyUnit(val partners: List<Person>) {
    val children = mutableListOf<FamilyUnit>()
}

/** Permanent person ids: random UUID text, created once when a person is stored. */
object PersonUid {
    private val FORMAT = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun create(): String = java.util.UUID.randomUUID().toString()

    /** Whether [text] is a UUID in lower-case canonical form, the only kind that is accepted from files. */
    fun isValid(text: String): Boolean = FORMAT.matches(text)
}

/** A saved place on the map: latitude and longitude in degrees. */
data class GeoPin(val lat: Double, val lng: Double) {
    val isValid: Boolean get() = lat in -90.0..90.0 && lng in -180.0..180.0

    /** "31.52040, 74.35870": what is shown and what can be pasted back. */
    fun text(): String = "%.5f, %.5f".format(java.util.Locale.ROOT, lat, lng)

    companion object {
        private const val N = """(-?\d{1,3}(?:\.\d+)?)"""
        private val PATTERNS = listOf(
            Regex("""@$N,\s*$N"""), // a long Google Maps link: .../maps/@31.52,74.35,15z or .../place/Name/@31.52,74.35,...
            Regex("""!3d$N!4d$N"""), // place links carry !3dLAT!4dLNG
            Regex("""[?&](?:q|ll|query|destination)=$N(?:,|%2C)\s*$N"""),
            Regex("""geo:$N,\s*$N"""),
            Regex("""^\s*$N\s*[,; ]\s*$N\s*$"""), // plain "31.52, 74.35"
        )

        /**
         * Reads a pin from what a person pastes: plain coordinates, a geo: link or a long Google Maps link that holds them.
         * Short links (maps.app.goo.gl and the like) hold no coordinates and give null: the full link has to be opened first.
         */
        fun parse(text: String): GeoPin? {
            val t = text.trim()
            for (p in PATTERNS) {
                val m = p.find(t) ?: continue
                val pin = GeoPin(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
                if (pin.isValid) return pin
            }
            return null
        }
    }
}

/** Phone numbers for the call, text and WhatsApp buttons. These only build links; the phone's own apps do the rest. */
object PhoneNumber {
    /** Digits and a leading plus, e.g. "+92 300 1234567" gives "+923001234567". Empty if there are no digits. */
    fun dialable(phone: String): String {
        val t = phone.trim()
        val digits = t.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return if (t.startsWith("+")) "+$digits" else digits
    }

    /** The digits WhatsApp needs, with the country code and no plus, or null if the number is not international. */
    fun whatsApp(phone: String): String? {
        val d = dialable(phone)
        return when {
            d.startsWith("+") && d.length >= 8 -> d.drop(1)
            d.startsWith("00") && d.length >= 9 -> d.drop(2)
            else -> null
        }
    }
}

/**
 * "Age 45" for someone who may still be alive (no death date, born within 110 years), or "Aged 71 when they died" when both
 * years are known. With only years known the age can be a year out, so it says "About". Empty when there is nothing to
 * work out.
 */
fun Person.ageText(today: java.time.LocalDate): String {
    val b = birthDate
    val by = b.year ?: return ""
    val d = deathDate
    if (d.year == null) {
        if (by <= today.year - 110 || by > today.year) return ""
        return agePhrase(by, b.month, b.day, today.year, today.monthValue, today.dayOfMonth, prefix = "Age")
    }
    if (d.year < by) return ""
    return agePhrase(by, b.month, b.day, d.year, d.month, d.day, prefix = "Aged", suffix = " when they died")
}

private fun agePhrase(by: Int, bm: Int?, bd: Int?, ey: Int, em: Int?, ed: Int?, prefix: String, suffix: String = ""): String {
    val exact = bm != null && bd != null && em != null && ed != null
    var age = ey - by
    if (exact && (em!! < bm!! || (em == bm && ed!! < bd!!))) age--
    if (age < 0) return ""
    val unit = if (age == 1) "year" else "years"
    return if (exact) "$prefix $age$suffix" else if (suffix.isEmpty()) "About $age $unit old" else "About $age$suffix"
}
