package com.inamibrahim.kinhold.model

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** A GEDCOM file that cannot be used. [message] is written for the person, not for a programmer. */
class GedcomException(message: String) : Exception(message)

/**
 * What reading a GEDCOM file found and what it could not keep, so the person can be told before anything is changed.
 * Kinhold keeps names, sex, birth and death dates and places, notes, events, source citations and the family links;
 * everything else is counted.
 */
class GedcomReport(
    val individuals: Int,
    val families: Int,
    val datesSimplified: Int,
    val ignoredEvents: Int,
    val ignoredSources: Int,
    val ignoredMedia: Int,
    val ignoredOther: Int,
    val skippedLinks: Int,
    val eventsKept: Int = 0,
    val sourcesKept: Int = 0,
) {
    /** Plain sentences for the confirmation, given how many people the tree on this phone already has. */
    fun summary(currentPeople: Int): List<String> {
        fun people(n: Int) = "$n ${if (n == 1) "person" else "people"}"
        val lines = ArrayList<String>()
        lines += "This file has ${people(individuals)} in $families ${if (families == 1) "family" else "families"}."
        if (currentPeople > 0) {
            lines += "Your tree has ${people(currentPeople)}. Replacing it keeps a copy you can undo. " +
                "Adding keeps everyone already there, and anyone in both will appear twice."
        }
        if (datesSimplified > 0) {
            lines += "$datesSimplified ${if (datesSimplified == 1) "date is" else "dates are"} given as \"about\", a range or a phrase. " +
                "Only the first date in each is kept."
        }
        fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
        if (eventsKept > 0 || sourcesKept > 0) {
            lines += "It also holds ${count(eventsKept, "event", "events")} and ${count(sourcesKept, "source citation", "source citations")}, which are kept."
        }
        val dropped = ArrayList<String>()
        if (ignoredEvents > 0) dropped += count(ignoredEvents, "event", "events") + " of kinds Kinhold has no place for"
        if (ignoredSources > 0) dropped += count(ignoredSources, "source citation", "source citations")
        if (ignoredMedia > 0) dropped += count(ignoredMedia, "photo or file", "photos or files")
        if (ignoredOther > 0) dropped += count(ignoredOther, "other detail", "other details")
        if (dropped.isNotEmpty()) lines += "Not kept: ${dropped.joinToString(", ")}."
        if (skippedLinks == 1) lines += "1 link to a person missing from the file was skipped."
        if (skippedLinks > 1) lines += "$skippedLinks links to people missing from the file were skipped."
        if (datesSimplified == 0 && dropped.isEmpty() && skippedLinks == 0) lines += "Everything in the file can be kept."
        return lines
    }
}

/** The people and links read from a GEDCOM file (numbered from 1 in file order), and the [report] about them. */
class GedcomResult(val data: BackupData, val report: GedcomReport)

/**
 * GEDCOM, the standard file for moving a family tree between programs. This reads versions 5.5, 5.5.1 and the common
 * parts of 7.0, and writes 5.5.1. Reading is forgiving about the many dialects in use (line endings, character sets,
 * date styles) and strict about nothing that would lose data silently: anything not kept is counted in the report.
 */
object Gedcom {
    private const val MAX_PEOPLE = 200_000
    private const val NOT_GEDCOM = "This file is not a GEDCOM family tree."

    // ---------------------------------------------------------------- reading

    /** Reads a GEDCOM file, or throws [GedcomException] saying what is wrong. */
    fun read(bytes: ByteArray): GedcomResult {
        val roots = parseRecords(decode(bytes))
        val people = roots.filter { it.tag == "INDI" && it.xref != null }
        if (roots.none { it.tag == "HEAD" } && people.isEmpty()) throw GedcomException(NOT_GEDCOM)
        if (people.isEmpty()) throw GedcomException("This GEDCOM file has no people in it.")
        if (people.size > MAX_PEOPLE) throw GedcomException("This GEDCOM file has too many people to open.")

        val notes = roots.filter { (it.tag == "NOTE" || it.tag == "SNOTE") && it.xref != null }.associate { it.xref!! to it.value }
        val seenUids = HashSet<String>()
        val sourceRecords = roots.filter { it.tag == "SOUR" && it.xref != null }.associateBy { it.xref!! }
        val ids = HashMap<String, Int>()
        val persons = ArrayList<Person>(people.size)
        val personEvents = LinkedHashMap<Int, List<Event>>()
        val personSources = LinkedHashMap<Int, List<Source>>()
        val personFields = LinkedHashMap<Int, List<CustomField>>()
        val coupleEvents = LinkedHashMap<Pair<Int, Int>, MutableList<Event>>()
        var reduced = 0
        var eventsDropped = 0
        var eventsKept = 0
        var sourcesDropped = 0
        var sourcesKept = 0
        var media = 0
        var other = 0

        fun dateOf(node: Node?): PartialDate {
            val parsed = parseDate(node?.child("DATE")?.value) ?: return PartialDate.None
            if (parsed.second) reduced++
            return parsed.first
        }

        fun notesOf(node: Node): List<String> = node.all("NOTE").mapNotNull { n ->
            val v = n.value
            (if (REFERENCE.matches(v)) notes[v] else v)?.trim()?.takeIf { it.isNotEmpty() }
        }

        /** A citation: either an inline text or a pointer to a source record that has the title, author and so on. */
        fun sourceOf(cit: Node): Source? {
            val v = cit.value.trim()
            val pointer = REFERENCE.matches(v)
            val record = if (pointer) sourceRecords[v] else null
            if (pointer && record == null) return null
            val title = if (record != null) record.child("TITL")?.value ?: record.child("ABBR")?.value ?: "" else v
            val extra = ArrayList<String>()
            extra += notesOf(cit)
            cit.child("DATA")?.all("TEXT")?.forEach { extra += it.value }
            if (record != null) {
                record.child("AUTH")?.value?.let { extra += "Author: $it" }
                record.child("PUBL")?.value?.let { extra += "Published: $it" }
                record.child("TEXT")?.value?.let { extra += it }
                extra += notesOf(record)
            }
            val source = Source(title, cit.child("PAGE")?.value.orEmpty(), extra.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")).clamped()
            return source.takeIf { !it.isBlank }
        }

        /** The citations under [parent]; [prefix] is put in front of each note. */
        fun sourcesOf(parent: Node, prefix: String = ""): List<Source> = parent.all("SOUR").mapNotNull { cit ->
            val s = sourceOf(cit)
            if (s == null) sourcesDropped++
            s?.let { if (prefix.isEmpty()) it else it.copy(note = (prefix + it.note).trim()) }
        }

        /** An event line (BURI, OCCU, EVEN and so on) as an [Event], or null if it holds nothing Kinhold can keep. */
        fun eventOf(node: Node): Event? {
            val value = node.value.trim().takeIf { !it.equals("Y", ignoreCase = true) }.orEmpty()
            val type = node.child("TYPE")?.value?.trim().orEmpty()
            val kind: EventKind
            val detail: String
            var extraNote = ""
            when {
                node.tag == "EVEN" || node.tag == "FACT" -> {
                    val byLabel = EventKind.fromLabel(type)
                    if (byLabel != null) {
                        kind = byLabel
                        detail = value
                    } else if (type.isNotEmpty()) {
                        kind = EventKind.Custom
                        detail = type
                        extraNote = value
                    } else {
                        kind = EventKind.Custom
                        detail = value
                    }
                }
                node.tag in GENERAL_EVENT_LABELS -> {
                    kind = EventKind.Custom
                    detail = GENERAL_EVENT_LABELS.getValue(node.tag)
                    extraNote = listOf(value, type).filter { it.isNotEmpty() }.joinToString(". ")
                }
                else -> {
                    kind = EventKind.fromTag(node.tag) ?: return null
                    detail = value.ifEmpty { type }
                }
            }
            val place = node.child("PLAC")?.value?.trim().orEmpty()
                .ifEmpty { node.child("ADDR")?.let { a -> a.value.lines().joinToString(", ") { it.trim() }.trim() }.orEmpty() }
            val note = (listOf(extraNote) + notesOf(node)).filter { it.isNotEmpty() }.joinToString("\n\n")
            return Event(kind, detail, dateOf(node), place, note, sourcesOf(node)).clamped()
        }

        for (node in people) {
            if (ids.containsKey(node.xref)) continue
            val id = persons.size + 1
            ids[node.xref!!] = id
            val (given, surname) = nameOf(node.child("NAME"))
            val birth = node.child("BIRT")
            val death = node.child("DEAT")
            val birthDate = dateOf(birth)
            val pin = node.child("_PIN")?.value?.let { GeoPin.parse(it) }
            val deathDate = dateOf(death)
            val noteText = notesOf(node).joinToString("\n\n")
            persons += Person(
                id = id,
                given = given,
                surname = surname,
                sex = when (node.child("SEX")?.value?.trim()?.uppercase()) {
                    "M" -> Sex.Male
                    "F" -> Sex.Female
                    else -> Sex.Unknown
                },
                born = birthDate.year,
                died = deathDate.year,
                birthPlace = birth?.child("PLAC")?.value?.trim().orEmpty(),
                deathPlace = death?.child("PLAC")?.value?.trim().orEmpty(),
                notes = noteText,
                birthMonth = birthDate.month,
                birthDay = birthDate.day,
                deathMonth = deathDate.month,
                deathDay = deathDate.day,
                uid = (node.child("_UID") ?: node.child("UID"))?.value?.trim()?.lowercase()?.takeIf { PersonUid.isValid(it) && seenUids.add(it) }.orEmpty(),
                address = addressOf(node.child("ADDR")),
                phone = node.child("PHON")?.value?.trim().orEmpty().take(40),
                email = node.child("EMAIL")?.value?.trim().orEmpty().take(200),
                lat = pin?.lat,
                lng = pin?.lng,
            )
            val events = ArrayList<Event>()
            for (detail in node.children) {
                when {
                    detail.tag in KEPT_PERSON_TAGS || detail.tag == "SOUR" -> Unit
                    detail.tag == "OBJE" -> media++
                    detail.tag in UNSUPPORTED_EVENT_TAGS -> eventsDropped++
                    detail.tag in EVENT_TAGS -> {
                        val e = eventOf(detail)
                        if (e != null && events.size < Limits.EVENTS) events += e else eventsDropped++
                    }
                    else -> if (!detail.tag.startsWith("_") && detail.tag !in HOUSEKEEPING_TAGS) other++
                }
            }
            if (events.isNotEmpty()) personEvents[id] = events
            val customFields = node.all("_FIELD").map { CustomField(it.child("TYPE")?.value.orEmpty(), it.value).clamped() }.filter { !it.isBlank }.take(Limits.FIELDS)
            if (customFields.isNotEmpty()) personFields[id] = customFields
            eventsKept += events.size
            // Citations for the person in general, and for the birth and the death (which are fields, not events).
            val cited = sourcesOf(node) + (birth?.let { sourcesOf(it, "About the birth. ") } ?: emptyList()) +
                (death?.let { sourcesOf(it, "About the death. ") } ?: emptyList())
            if (cited.size > Limits.SOURCES) sourcesDropped += cited.size - Limits.SOURCES
            val keptSources = cited.take(Limits.SOURCES)
            if (keptSources.isNotEmpty()) personSources[id] = keptSources
            sourcesKept += keptSources.size + events.sumOf { it.sources.size }
        }

        val links = LinkedHashSet<Pair<Int, Int>>()
        val partnerships = LinkedHashSet<Pair<Int, Int>>()
        val parentsOfFamily = HashMap<String, List<Int>>()
        var skipped = 0
        val families = roots.filter { it.tag == "FAM" && it.xref != null }

        fun lookup(pointer: String?): Int? {
            if (pointer.isNullOrBlank() || pointer.equals("@VOID@", ignoreCase = true)) return null
            val id = ids[pointer.trim()]
            if (id == null) skipped++
            return id
        }

        for (fam in families) {
            val parents = listOfNotNull(lookup(fam.child("HUSB")?.value), lookup(fam.child("WIFE")?.value)).distinct()
            parentsOfFamily[fam.xref!!] = parents
            val coupleKey = if (parents.size == 2) minOf(parents[0], parents[1]) to maxOf(parents[0], parents[1]) else null
            if (coupleKey != null) partnerships += coupleKey
            for (child in fam.all("CHIL")) {
                val childId = lookup(child.value) ?: continue
                for (parent in parents) if (parent != childId) links += childId to parent
            }
            for (detail in fam.children) {
                when {
                    detail.tag in KEPT_FAMILY_TAGS -> Unit
                    detail.tag == "SOUR" -> sourcesDropped++
                    detail.tag == "OBJE" -> media++
                    detail.tag in UNSUPPORTED_EVENT_TAGS -> eventsDropped++
                    detail.tag in EVENT_TAGS -> {
                        val e = eventOf(detail)
                        val list = if (coupleKey != null) coupleEvents.getOrPut(coupleKey) { mutableListOf() } else null
                        if (e != null && list != null && list.size < Limits.EVENTS) {
                            list += e
                            eventsKept++
                            sourcesKept += e.sources.size
                        } else {
                            eventsDropped++
                        }
                    }
                    else -> if (!detail.tag.startsWith("_") && detail.tag !in HOUSEKEEPING_TAGS) other++
                }
            }
        }
        // Some programs list a child under FAMC without the family listing them back; trust either.
        for (node in people) {
            val childId = ids[node.xref] ?: continue
            for (famc in node.all("FAMC")) {
                for (parent in parentsOfFamily[famc.value.trim()].orEmpty()) if (parent != childId) links += childId to parent
            }
        }

        val report = GedcomReport(
            persons.size, families.size, reduced, eventsDropped, sourcesDropped, media, other, skipped, eventsKept, sourcesKept,
        )
        val details = TreeDetails(personEvents, personSources, coupleEvents, personFields)
        return GedcomResult(BackupData(persons, links.toList(), partnerships.toList(), details = details), report)
    }

    private val MONTH_PATTERN = PartialDate.MONTH_TAGS.joinToString("|")
    private val EXACT_DATE = Regex("""^(?:(\d{1,2})\s+)?(?:($MONTH_PATTERN)[A-Z]*\.?\s+)?(\d{3,4})(?:/\d{1,4})?$""", RegexOption.IGNORE_CASE)
    private val LOOSE_DATE = Regex("""(?<![0-9])(?:(\d{1,2})\s+)?(?:($MONTH_PATTERN)[A-Z]*\.?\s+)?(\d{3,4})(?![0-9])""", RegexOption.IGNORE_CASE)

    /**
     * A GEDCOM date as a [PartialDate], and whether something was lost on the way: "12 MAR 1850", "MAR 1850" and "1850"
     * are kept whole; "ABT 1850", "BET 1800 AND 1810" and phrases keep their first date and count as simplified. BC dates
     * and text with no year give null.
     */
    internal fun parseDate(raw: String?): Pair<PartialDate, Boolean>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || BC.containsMatchIn(text)) return null
        val exact = EXACT_DATE.matchEntire(text)
        val m = exact ?: LOOSE_DATE.find(text) ?: return null
        val year = m.groupValues[3].toInt().takeIf { it in 1..9999 } ?: return null
        var simplified = exact == null
        var day = m.groupValues[1].toIntOrNull()
        val month = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { PartialDate.MONTH_TAGS.indexOf(it.take(3).uppercase()) + 1 }
        if (day != null && month == null) {
            day = null
            simplified = true
        }
        var date = PartialDate(year, month, day)
        if (!date.isValid) {
            date = PartialDate(year, month, null)
            simplified = true
        }
        return date to simplified
    }

    private val BC = Regex("""\bB\.?\s?C\.?(?:E\.?)?\b""", RegexOption.IGNORE_CASE)
    private val REFERENCE = Regex("""@[^@\s]+@""")
    private val LINE = Regex("""^(\d+)\s+(?:(@[^@\s]+@)\s+)?([A-Za-z0-9_]+)(?:\s(.*))?$""")

    private val KEPT_PERSON_TAGS = setOf("NAME", "SEX", "BIRT", "DEAT", "NOTE", "SNOTE", "FAMC", "FAMS", "ADDR", "PHON", "EMAIL", "_FIELD", "_PIN", "UID", "_UID")
    private val KEPT_FAMILY_TAGS = setOf("HUSB", "WIFE", "CHIL", "NOTE", "SNOTE")
    private val HOUSEKEEPING_TAGS = setOf("CHAN", "RIN", "REFN", "RFN", "AFN", "UID", "SUBM", "ALIA", "ASSO", "ANCI", "DESI", "SOURCE", "TYPE", "RESN", "CREA", "NO")
    /** General events that have a tag of their own but no kind in Kinhold; they are kept as events named by the label. */
    private val GENERAL_EVENT_LABELS = mapOf(
        "BARM" to "Bar Mitzvah", "BASM" to "Bat Mitzvah", "BLES" to "Blessing", "FCOM" to "First communion", "ORDN" to "Ordination",
    )

    /** Event lines that become an [Event]. */
    private val EVENT_TAGS: Set<String> =
        EventKind.entries.mapNotNull { it.tag }.toSet() + GENERAL_EVENT_LABELS.keys + setOf("EVEN", "FACT")

    /** Event and fact lines with no place in Kinhold: ordinances, identity numbers, contact details and the like. */
    private val UNSUPPORTED_EVENT_TAGS = setOf(
        "DIVF", "MARB", "MARC", "MARL", "MARS", "CHRA", "DSCR", "CAST", "NCHI", "NMR", "SSN", "IDNO",
        "BAPL", "CONL", "ENDL", "SLGC", "SLGS", "WWW", "FAX",
    )

    /** One line of a GEDCOM file with the lines indented under it. */
    private class Node(val level: Int, val xref: String?, val tag: String, var value: String) {
        val children = ArrayList<Node>()
        fun child(tag: String): Node? = children.firstOrNull { it.tag == tag }
        fun all(tag: String): List<Node> = children.filter { it.tag == tag }
    }

    /** The level-0 records. CONC and CONT lines are folded into the value of the line they continue. */
    private fun parseRecords(text: String): List<Node> {
        val roots = ArrayList<Node>()
        val stack = ArrayList<Node>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('﻿')
            if (line.isEmpty()) continue
            val m = LINE.matchEntire(line) ?: continue
            val level = m.groupValues[1].toIntOrNull() ?: continue
            val tag = m.groupValues[3].uppercase()
            val value = m.groups[4]?.value.orEmpty().replace("@@", "@")
            while (stack.size > level) stack.removeAt(stack.size - 1)
            if (tag == "CONC" || tag == "CONT") {
                val parent = stack.lastOrNull() ?: continue
                parent.value += (if (tag == "CONT") "\n" else "") + value
                continue
            }
            val node = Node(level, m.groups[2]?.value, tag, value)
            if (level == 0 || stack.isEmpty()) {
                roots += node
                stack.clear()
            } else {
                stack.last().children += node
            }
            stack += node
        }
        return roots
    }

    /** "Given names /Surname/ Suffix" into given names and surname; GIVN and SURN lines win when present. */
    private fun nameOf(name: Node?): Pair<String, String> {
        if (name == null) return "" to ""
        val v = name.value
        val open = v.indexOf('/')
        val close = if (open >= 0) v.indexOf('/', open + 1) else -1
        var given: String
        var surname = ""
        if (open >= 0 && close > open) {
            given = v.substring(0, open)
            surname = v.substring(open + 1, close) + " " + v.substring(close + 1)
        } else {
            given = v
        }
        name.child("GIVN")?.value?.takeIf { it.isNotBlank() }?.let { given = it }
        name.child("SURN")?.value?.takeIf { it.isNotBlank() }?.let { surname = it }
        return tidy(given) to tidy(surname)
    }

    /** An ADDR line: its own text, or if it has none the structured parts (ADR1, CITY, STAE, POST, CTRY) joined. */
    private fun addressOf(addr: Node?): String {
        if (addr == null) return ""
        val own = addr.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val parts = if (own.isNotEmpty()) own else listOf("ADR1", "ADR2", "ADR3", "CITY", "STAE", "POST", "CTRY").mapNotNull { addr.child(it)?.value?.trim()?.takeIf { v -> v.isNotEmpty() } }
        return parts.joinToString(", ").take(500)
    }

    private fun tidy(s: String) = s.replace('/', ' ').trim().replace(Regex("""\s+"""), " ")

    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    /**
     * Text from the file's bytes: byte-order marks first, then UTF-16 without one, then the character set the file
     * declares. Unmarked files that are not valid UTF-8 are read as Windows-1252, which is what "ANSI" files are and
     * the closest common match for ANSEL (accents in very old ANSEL files can come out wrong).
     */
    private fun decode(bytes: ByteArray): String {
        fun b(i: Int) = bytes[i].toInt() and 0xFF
        if (bytes.size >= 3 && b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        if (bytes.size >= 4 && b(1) == 0 && b(3) == 0) return String(bytes, Charsets.UTF_16LE)
        if (bytes.size >= 4 && b(0) == 0 && b(2) == 0) return String(bytes, Charsets.UTF_16BE)
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val declared = Regex("""(?m)^\s*1\s+CHAR\s+(\S+)""").find(head)?.groupValues?.get(1)?.uppercase()
        return if (declared == null || declared == "UTF-8" || declared == "UTF8") strictOrWindows(bytes) else String(bytes, WINDOWS_1252)
    }

    private fun strictOrWindows(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        String(bytes, WINDOWS_1252)
    }

    // ---------------------------------------------------------------- writing

    private const val CHUNK = 200

    /**
     * The whole tree as GEDCOM 5.5.1 text (UTF-8, lines ended with CR LF). Each couple, and each group of children
     * with the same parents, becomes one family record, so couples with no children and single parents are kept.
     */
    fun write(family: Family, details: TreeDetails, appVersion: String, exportedOn: String): String {
        val out = StringBuilder()

        fun put(level: Int, tag: String, value: String = "", xref: String? = null) {
            out.append(level)
            if (xref != null) out.append(" @").append(xref).append('@')
            out.append(' ').append(tag)
            if (value.isNotEmpty()) out.append(' ').append(value)
            out.append("\r\n")
        }

        /** A value that may hold line breaks or be long: continuation lines keep every line under the length limit. */
        fun text(level: Int, tag: String, value: String) {
            val lines = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            lines.forEachIndexed { i, line ->
                val escaped = line.replace("@", "@@")
                val chunks = escaped.chunkedAtSafeBoundaries(CHUNK)
                chunks.forEachIndexed { j, chunk ->
                    when {
                        i == 0 && j == 0 -> put(level, tag, chunk)
                        j == 0 -> put(level + 1, "CONT", chunk)
                        else -> put(level + 1, "CONC", chunk)
                    }
                }
            }
        }

        // Families: every couple, and every distinct set of parents that has children.
        val families = LinkedHashMap<List<Int>, MutableList<Int>>()
        for ((a, b) in family.partnerships()) families.getOrPut(listOf(minOf(a, b), maxOf(a, b))) { mutableListOf() }
        for (p in family.people) {
            val parents = family.parentsOf(p.id).map { it.id }.sorted()
            if (parents.isEmpty()) continue
            val groups = if (parents.size <= 2) listOf(parents) else parents.map { listOf(it) }
            for (g in groups) families.getOrPut(g) { mutableListOf() } += p.id
        }
        val familyIds = HashMap<List<Int>, Int>()
        families.keys.forEachIndexed { i, key -> familyIds[key] = i + 1 }

        val spouseIn = HashMap<Int, MutableList<Int>>()
        val childIn = HashMap<Int, MutableList<Int>>()
        for ((key, children) in families) {
            val number = familyIds.getValue(key)
            for (parent in key) spouseIn.getOrPut(parent) { mutableListOf() } += number
            for (child in children) childIn.getOrPut(child) { mutableListOf() } += number
        }

        // Each distinct source title is written once as a record, and every citation points at it.
        val sourceNumbers = LinkedHashMap<String, Int>()

        fun citation(level: Int, s: Source) {
            val number = sourceNumbers.getOrPut(s.title) { sourceNumbers.size + 1 }
            put(level, "SOUR", "@S$number@")
            if (s.citation.isNotBlank()) text(level + 1, "PAGE", s.citation)
            if (s.note.isNotBlank()) text(level + 1, "NOTE", s.note)
        }

        /** An event as its own tag where one exists, otherwise as a general event typed with its name. */
        fun event(level: Int, e: Event) {
            val tag = e.kind.tag
            val valueTags = setOf("OCCU", "EDUC", "RELI", "TITL", "NATI", "PROP")
            when {
                tag != null && tag in valueTags -> put(level, tag, e.detail.replace("@", "@@"))
                tag != null -> {
                    put(level, tag)
                    if (e.detail.isNotBlank()) text(level + 1, "TYPE", e.detail)
                }
                e.kind == EventKind.Custom -> {
                    put(level, "EVEN")
                    if (e.detail.isNotBlank()) text(level + 1, "TYPE", e.detail)
                }
                else -> {
                    put(level, "EVEN", e.detail.replace("@", "@@"))
                    put(level + 1, "TYPE", e.kind.label)
                }
            }
            if (!e.date.isEmpty) put(level + 1, "DATE", e.date.gedcom())
            if (e.place.isNotBlank()) text(level + 1, "PLAC", e.place)
            if (e.note.isNotBlank()) text(level + 1, "NOTE", e.note)
            for (s in e.sources) citation(level + 1, s)
        }

        put(0, "HEAD")
        put(1, "SOUR", "Kinhold")
        put(2, "NAME", "Kinhold")
        put(2, "VERS", appVersion)
        put(1, "GEDC")
        put(2, "VERS", "5.5.1")
        put(2, "FORM", "LINEAGE-LINKED")
        put(1, "CHAR", "UTF-8")
        put(1, "DATE", exportedOn)
        put(1, "SUBM", "@SUBM1@")
        put(0, "SUBM", xref = "SUBM1")
        put(1, "NAME", "Kinhold user")

        for (p in family.people) {
            put(0, "INDI", xref = "I${p.id}")
            val given = p.given.replace('/', ' ').trim()
            val surname = p.surname.replace('/', ' ').trim()
            put(1, "NAME", "${given.replace("@", "@@")} /${surname.replace("@", "@@")}/")
            if (given.isNotEmpty()) put(2, "GIVN", given.replace("@", "@@"))
            if (surname.isNotEmpty()) put(2, "SURN", surname.replace("@", "@@"))
            if (p.uid.isNotEmpty()) put(1, "_UID", p.uid)
            if (p.address.isNotBlank()) text(1, "ADDR", p.address.trim())
            if (p.phone.isNotBlank()) text(1, "PHON", p.phone.trim())
            if (p.email.isNotBlank()) text(1, "EMAIL", p.email.trim())
            for (fld in details.fields[p.id].orEmpty()) {
                text(1, "_FIELD", fld.value)
                if (fld.label.isNotBlank()) text(2, "TYPE", fld.label)
            }
            if (p.lat != null && p.lng != null) put(1, "_PIN", GeoPin(p.lat, p.lng).text())
            when (p.sex) {
                Sex.Male -> put(1, "SEX", "M")
                Sex.Female -> put(1, "SEX", "F")
                Sex.Unknown -> Unit
            }
            if (p.born != null || p.birthPlace.isNotBlank()) {
                put(1, "BIRT")
                if (p.born != null) put(2, "DATE", p.birthDate.gedcom())
                if (p.birthPlace.isNotBlank()) text(2, "PLAC", p.birthPlace.trim())
            }
            if (p.died != null || p.deathPlace.isNotBlank()) {
                put(1, "DEAT")
                if (p.died != null) put(2, "DATE", p.deathDate.gedcom())
                if (p.deathPlace.isNotBlank()) text(2, "PLAC", p.deathPlace.trim())
            }
            if (p.notes.isNotBlank()) text(1, "NOTE", p.notes.trim())
            for (e in details.events[p.id].orEmpty()) event(1, e)
            for (s in details.sources[p.id].orEmpty()) citation(1, s)
            for (f in childIn[p.id].orEmpty()) put(1, "FAMC", "@F$f@")
            for (f in spouseIn[p.id].orEmpty()) put(1, "FAMS", "@F$f@")
        }

        for ((key, children) in families) {
            put(0, "FAM", xref = "F${familyIds.getValue(key)}")
            val (husband, wife) = roles(family, key)
            if (husband != null) put(1, "HUSB", "@I$husband@")
            if (wife != null) put(1, "WIFE", "@I$wife@")
            for (child in children) put(1, "CHIL", "@I$child@")
            if (key.size == 2) for (e in details.couples[key[0] to key[1]].orEmpty()) event(1, e)
        }
        for ((title, number) in sourceNumbers) {
            put(0, "SOUR", xref = "S$number")
            text(1, "TITL", title)
        }
        put(0, "TRLR")
        return out.toString()
    }

    /** Which parent is HUSB and which WIFE: by sex where it is known, otherwise in the order given. */
    private fun roles(family: Family, parents: List<Int>): Pair<Int?, Int?> {
        if (parents.size == 1) {
            val only = parents[0]
            return if (family.person(only).sex == Sex.Female) null to only else only to null
        }
        val (x, y) = parents
        val sx = family.person(x).sex
        val sy = family.person(y).sex
        return if (sx == Sex.Male || sy == Sex.Female || (sx != Sex.Female && sy != Sex.Male)) x to y else y to x
    }

    /** Pieces of at most [size] characters that never split a surrogate pair or start or end on a space. */
    private fun String.chunkedAtSafeBoundaries(size: Int): List<String> {
        if (length <= size) return listOf(this)
        val parts = ArrayList<String>()
        var start = 0
        while (start < length) {
            var end = minOf(start + size, length)
            if (end < length) {
                while (end > start + 1 && (this[end - 1].isHighSurrogate() || this[end - 1] == ' ' || this[end] == ' ')) end--
            }
            parts += substring(start, end)
            start = end
        }
        return parts
    }
}
