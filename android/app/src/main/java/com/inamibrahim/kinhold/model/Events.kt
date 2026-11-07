package com.inamibrahim.kinhold.model

/**
 * A date where the year, the month and the day are each optional. A month needs a year and a day needs a month, so
 * "1850", "Mar 1850" and "12 Mar 1850" are all possible and nothing else. Use [normalized] to drop a month or day that
 * has nothing to hang on.
 */
data class PartialDate(val year: Int? = null, val month: Int? = null, val day: Int? = null) {
    val isEmpty: Boolean get() = year == null && month == null && day == null

    /** Whether every part that is given is in range and the parts fit together (no month without a year, and so on). */
    val isValid: Boolean
        get() = (year == null || year in 0..9999) &&
            (month == null || (year != null && month in 1..12)) &&
            (day == null || (month != null && day in 1..MAX_DAY[month - 1]))

    /** The same date with a month dropped if there is no year and a day dropped if there is no month. */
    fun normalized(): PartialDate {
        val m = if (year == null) null else month
        val d = if (m == null) null else day
        return PartialDate(year, m, d)
    }

    /** "12 Mar 1850", "Mar 1850", "1850", or an empty string. */
    fun text(): String = parts(MONTH_NAMES)

    /** The same in GEDCOM style: "12 MAR 1850". */
    fun gedcom(): String = parts(MONTH_TAGS)

    private fun parts(names: List<String>): String = listOfNotNull(
        day?.toString(),
        month?.let { names.getOrNull(it - 1) },
        year?.toString(),
    ).joinToString(" ")

    /** A number that sorts dates in time order; dates with no year sort last. */
    val sortKey: Int
        get() = if (year == null) Int.MAX_VALUE else year * 10_000 + (month ?: 0) * 100 + (day ?: 0)

    /** True when this date is certainly earlier than [other]: only the parts both dates give are compared. */
    fun isBefore(other: PartialDate): Boolean {
        val y1 = year ?: return false
        val y2 = other.year ?: return false
        if (y1 != y2) return y1 < y2
        val m1 = month ?: return false
        val m2 = other.month ?: return false
        if (m1 != m2) return m1 < m2
        val d1 = day ?: return false
        val d2 = other.day ?: return false
        return d1 < d2
    }

    companion object {
        val None = PartialDate()
        val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val MONTH_TAGS = MONTH_NAMES.map { it.uppercase() }
        private val MAX_DAY = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        /** Reads the three boxes of a form. Blank boxes are unknown parts; null means the numbers cannot be a date. */
        fun parse(day: String, month: String, year: String): PartialDate? {
            fun number(s: String): Int? = s.trim().toIntOrNull()?.takeIf { it >= 0 }
            val d = number(day)
            val m = number(month)
            val y = number(year)
            if ((day.isNotBlank() && d == null) || (month.isNotBlank() && m == null) || (year.isNotBlank() && y == null)) return null
            val date = PartialDate(y, m, d)
            return if (date.isValid) date else null
        }
    }
}

/**
 * The kinds of event a person or a couple can have. [tag] is the GEDCOM tag it is written with; kinds without one are
 * written as a general event whose type is the [label]. Couple events (marriage and the like) belong to a pair of
 * partners; the rest belong to one person.
 */
enum class EventKind(val key: String, val label: String, val tag: String?, val forCouple: Boolean = false) {
    Baptism("baptism", "Baptism", "BAPM"),
    Christening("christening", "Christening", "CHR"),
    Confirmation("confirmation", "Confirmation", "CONF"),
    Education("education", "Education", "EDUC"),
    Graduation("graduation", "Graduation", "GRAD"),
    Occupation("occupation", "Occupation", "OCCU"),
    Military("military", "Military service", null),
    Residence("residence", "Residence", "RESI"),
    Immigration("immigration", "Immigration", "IMMI"),
    Emigration("emigration", "Emigration", "EMIG"),
    Naturalization("naturalization", "Naturalization", "NATU"),
    Census("census", "Census", "CENS"),
    Adoption("adoption", "Adoption", "ADOP"),
    Religion("religion", "Religion", "RELI"),
    Nationality("nationality", "Nationality", "NATI"),
    Title("title", "Title", "TITL"),
    Property("property", "Property", "PROP"),
    Retirement("retirement", "Retirement", "RETI"),
    Will("will", "Will", "WILL"),
    Probate("probate", "Probate", "PROB"),
    Cremation("cremation", "Cremation", "CREM"),
    Burial("burial", "Burial", "BURI"),
    Engagement("engagement", "Engagement", "ENGA", forCouple = true),
    Marriage("marriage", "Marriage", "MARR", forCouple = true),
    Divorce("divorce", "Divorce", "DIV", forCouple = true),
    Annulment("annulment", "Annulment", "ANUL", forCouple = true),
    Custom("custom", "Other", null);

    /** Whether this kind ends a partnership: after a divorce or an annulment the couple is no longer together. */
    val endsPartnership: Boolean get() = this == Divorce || this == Annulment

    companion object {
        /** The kinds offered when adding an event to a person. */
        val forPerson: List<EventKind> = entries.filter { !it.forCouple }

        /** The kinds offered when adding an event to a couple. */
        val forCouple: List<EventKind> = entries.filter { it.forCouple } + Custom

        fun fromKey(key: String): EventKind? = entries.firstOrNull { it.key == key }

        /** The kind for a GEDCOM tag such as "BURI", or null if no kind uses it. */
        fun fromTag(tag: String): EventKind? = entries.firstOrNull { it.tag == tag }

        /** The kind whose label is [text] (ignoring case), used for general events typed "Military service". */
        fun fromLabel(text: String): EventKind? =
            entries.firstOrNull { it != Custom && it.label.equals(text.trim(), ignoreCase = true) }
    }
}

/**
 * A plain-text citation: where a fact came from. [title] names the source (a book, a certificate, a website),
 * [citation] says where in it (a page, an entry number), [note] is anything else. No scans or files are attached.
 */
data class Source(val title: String = "", val citation: String = "", val note: String = "") {
    val isBlank: Boolean get() = title.isBlank() && citation.isBlank() && note.isBlank()

    /** "Title, citation" for a list. */
    fun summary(): String = listOf(title, citation).filter { it.isNotBlank() }.joinToString(", ")

    fun clamped() = Source(
        title.trim().take(Limits.SOURCE_TITLE),
        citation.trim().take(Limits.SOURCE_CITATION),
        note.trim().take(Limits.TEXT),
    )
}

/**
 * Something that happened in a life, or to a couple. [detail] is the job for an occupation, the name of a custom event,
 * or any extra words ("St Mary's church"); [sources] are the citations for it. Events have no identity of their own:
 * they are stored and replaced as a list, in date order.
 */
data class Event(
    val kind: EventKind,
    val detail: String = "",
    val date: PartialDate = PartialDate.None,
    val place: String = "",
    val note: String = "",
    val sources: List<Source> = emptyList(),
) {
    /** What to call it in a list: "Occupation: Farmer", "Marriage", or the name of a custom event. */
    fun title(): String = when {
        kind == EventKind.Custom -> detail.ifBlank { "Event" }
        detail.isBlank() -> kind.label
        else -> "${kind.label}: $detail"
    }

    /** Trimmed, cut to the length limits, with an impossible date dropped and at most [Limits.SOURCES] citations. */
    fun clamped() = Event(
        kind,
        detail.trim().take(Limits.DETAIL),
        if (date.isValid) date.normalized() else PartialDate.None,
        place.trim().take(Limits.PLACE),
        note.trim().take(Limits.TEXT),
        sources.map { it.clamped() }.filter { !it.isBlank }.take(Limits.SOURCES),
    )
}

/** Bounds that keep one person's memory use small however much is typed or imported. */
object Limits {
    const val EVENTS = 500
    const val SOURCES = 50
    const val DETAIL = 200
    const val PLACE = 300
    const val SOURCE_TITLE = 300
    const val SOURCE_CITATION = 1000
    const val TEXT = 5000
    const val FIELDS = 30
    const val FIELD_LABEL = 60
    const val FIELD_VALUE = 500
}

/** Events in time order (undated last), keeping the typed order between equal dates, and cut to [Limits.EVENTS]. */
fun List<Event>.inDateOrder(): List<Event> = map { it.clamped() }.sortedBy { it.date.sortKey }.take(Limits.EVENTS)

/**
 * Everything about one person beyond the basic fields: their events, the sources for the person in general, and the
 * events of each of their couples (by the partner's id, e.g. the marriage).
 */
data class PersonDetails(
    val events: List<Event> = emptyList(),
    val sources: List<Source> = emptyList(),
    val coupleEvents: Map<Int, List<Event>> = emptyMap(),
    val fields: List<CustomField> = emptyList(),
) {
    val isEmpty: Boolean get() = events.isEmpty() && sources.isEmpty() && fields.isEmpty() && coupleEvents.values.all { it.isEmpty() }

    companion object {
        val Empty = PersonDetails()
    }
}

/**
 * The events and sources of a whole tree, for backups and GEDCOM. [couples] is keyed by (smaller id, larger id).
 */
class TreeDetails(
    val events: Map<Int, List<Event>> = emptyMap(),
    val sources: Map<Int, List<Source>> = emptyMap(),
    val couples: Map<Pair<Int, Int>, List<Event>> = emptyMap(),
    val fields: Map<Int, List<CustomField>> = emptyMap(),
) {
    companion object {
        val Empty = TreeDetails()
    }
}

/** What the layout and the relatives list need to know about a couple, kept in memory for every couple. */
class CoupleInfo(
    /** The earliest marriage date as [PartialDate.sortKey], or null if no marriage date is recorded. */
    val marriedKey: Int?,
    /** True once a divorce or annulment is recorded. */
    val ended: Boolean,
)

/** A fact the owner defines for one person, such as "Blood group: O+" or "Nickname: Bhai". */
data class CustomField(val label: String = "", val value: String = "") {
    val isBlank: Boolean get() = label.isBlank() && value.isBlank()

    fun clamped() = CustomField(label.trim().take(Limits.FIELD_LABEL), value.trim().take(Limits.FIELD_VALUE))
}

/** How a child is linked to a parent. Only birth and adoptive parents count as ancestors in the relationship finder. */
enum class LinkKind(val label: String, val gedcom: String) {
    Birth("Birth parent", "birth"),
    Adopted("Adoptive parent", "adopted"),
    Foster("Foster parent", "foster"),
    Step("Step-parent", "step");

    val isLineage: Boolean get() = this == Birth || this == Adopted

    companion object {
        fun fromOrdinal(n: Int): LinkKind = entries.getOrElse(n) { Birth }
        fun fromGedcom(text: String): LinkKind? = entries.firstOrNull { it.gedcom.equals(text.trim(), ignoreCase = true) && it != Birth }
    }
}
