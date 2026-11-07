package com.inamibrahim.kinhold.model

/** One person in the tree. This is the development model; the stored model comes later. */
class Person(
    val id: Int,
    val name: String,
    val born: Int,
    val died: Int?,
    val female: Boolean,
) {
    /** "1950 – 2010" for someone who has died, "b. 1990" for someone living. */
    fun years(): String = if (died != null) "$born – $died" else "b. $born"
}

/**
 * A person (or a couple) and the children they had. [partners] holds the family member first and, when there is one,
 * the person they married second. Each child is the head of a unit of its own.
 */
class FamilyUnit(val partners: List<Person>) {
    val children = mutableListOf<FamilyUnit>()
}
