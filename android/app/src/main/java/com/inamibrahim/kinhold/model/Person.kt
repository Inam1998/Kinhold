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
) {
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

    fun toDraft() = PersonDraft(given, surname, sex, born, died, birthPlace, deathPlace, notes)
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
) {
    /** Someone needs at least part of a name. */
    val hasName: Boolean get() = given.isNotBlank() || surname.isNotBlank()

    /** A person cannot die before they were born. */
    val datesMakeSense: Boolean get() = born == null || died == null || died >= born

    val isValid: Boolean get() = hasName && datesMakeSense
}

/**
 * A person (or a couple) and the children they had. [partners] holds the family member first and, when there is one,
 * the person they married second. Each child is the head of a unit of its own. Only the test family generator and
 * the layout use this; the stored model is [Family].
 */
class FamilyUnit(val partners: List<Person>) {
    val children = mutableListOf<FamilyUnit>()
}
