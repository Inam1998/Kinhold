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

    fun toDraft() = PersonDraft(given, surname, sex, born, died, birthPlace, deathPlace, notes, birthMonth, birthDay, deathMonth, deathDay)
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
