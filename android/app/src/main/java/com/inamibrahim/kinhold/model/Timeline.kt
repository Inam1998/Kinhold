package com.inamibrahim.kinhold.model

enum class TimelineKind { Birth, Event, Marriage, Child, Death }

/** One line of a person's life story: when, what, where, and how old they were. */
class TimelineEntry(
    val kind: TimelineKind,
    val date: PartialDate,
    val title: String,
    val place: String = "",
    val note: String = "",
    /** "Age 25" or "About 25", empty for the birth itself or when it cannot be worked out. */
    val age: String = "",
    val sources: Int = 0,
)

/**
 * A person's whole life in date order: birth, their events, their marriages and other couple events, the births of their
 * children, and death. Built from what is already recorded, nothing is stored. Things with no date are kept: undated
 * events come after the dated ones, an undated death is last, and an undated birth is first.
 */
object Timeline {
    /** Kinds that happen after a death, so they are listed after it when the dates are the same. */
    private val AFTER_DEATH = setOf(EventKind.Burial, EventKind.Cremation, EventKind.Probate)

    private class Slot(val key: Int, val rank: Int, val entry: TimelineEntry)

    fun of(family: Family, person: Person, details: PersonDetails): List<TimelineEntry> {
        val birth = person.birthDate
        val slots = ArrayList<Slot>()

        fun age(date: PartialDate): String = ageAt(birth, date)

        if (!birth.isEmpty || person.birthPlace.isNotBlank()) {
            slots += Slot(if (birth.year != null) birth.sortKey else -1, 0, TimelineEntry(TimelineKind.Birth, birth, "Born", person.birthPlace.trim()))
        }
        val death = person.deathDate
        if (!death.isEmpty || person.deathPlace.isNotBlank()) {
            slots += Slot(if (death.year != null) death.sortKey else Int.MAX_VALUE, 2, TimelineEntry(TimelineKind.Death, death, "Died", person.deathPlace.trim(), age = age(death)))
        }
        for (e in details.events) {
            slots += Slot(e.date.sortKey, if (e.kind in AFTER_DEATH) 3 else 1, TimelineEntry(TimelineKind.Event, e.date, e.title(), e.place, e.note, age(e.date), e.sources.size))
        }
        for ((partnerId, events) in details.coupleEvents) {
            if (partnerId !in family) continue
            val partner = family.person(partnerId).name
            for (e in events) {
                slots += Slot(e.date.sortKey, 1, TimelineEntry(TimelineKind.Marriage, e.date, "${e.title()} with $partner", e.place, e.note, age(e.date), e.sources.size))
            }
        }
        for (child in family.childrenOf(person.id)) {
            val d = child.birthDate
            if (d.year == null) continue
            slots += Slot(d.sortKey, 1, TimelineEntry(TimelineKind.Child, d, "${child.name} was born", child.birthPlace.trim(), age = age(d)))
        }
        // sortedWith is stable, so events with the same date and rank stay in the order they were recorded.
        return slots.sortedWith(compareBy({ it.key }, { it.rank })).map { it.entry }
    }

    /**
     * How old someone born on [birth] was on [date]: "Age 25" when both dates are complete enough to be sure, "About 25"
     * when only the years are known, and empty when either year is missing or [date] is before the birth.
     */
    fun ageAt(birth: PartialDate, date: PartialDate): String {
        val by = birth.year ?: return ""
        val dy = date.year ?: return ""
        var years = dy - by
        val exact = birth.month != null && birth.day != null && date.month != null && date.day != null
        if (exact && (date.month!! < birth.month!! || (date.month == birth.month && date.day!! < birth.day!!))) years--
        if (years < 0 || years > 130) return ""
        return if (exact) "Age $years" else "About $years"
    }
}
