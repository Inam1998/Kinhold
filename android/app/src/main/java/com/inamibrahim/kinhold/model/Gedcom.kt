package com.inamibrahim.kinhold.model

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** A GEDCOM file that cannot be used. [message] is written for the person, not for a programmer. */
class GedcomException(message: String) : Exception(message)

/**
 * What reading a GEDCOM file found and what it could not keep, so the person can be told before anything is changed.
 * Kinhold keeps names, sex, birth and death years and places, notes and the family links; everything else is counted.
 */
class GedcomReport(
    val individuals: Int,
    val families: Int,
    val datesReducedToYear: Int,
    val ignoredEvents: Int,
    val ignoredSources: Int,
    val ignoredMedia: Int,
    val ignoredOther: Int,
    val skippedLinks: Int,
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
        if (datesReducedToYear > 0) {
            lines += "$datesReducedToYear ${if (datesReducedToYear == 1) "date has" else "dates have"} more than a year " +
                "(a day, a month or \"about\"). Only the year is kept."
        }
        fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
        val dropped = ArrayList<String>()
        if (ignoredEvents > 0) dropped += count(ignoredEvents, "event", "events") + " such as marriages, jobs and residences"
        if (ignoredSources > 0) dropped += count(ignoredSources, "source citation", "source citations")
        if (ignoredMedia > 0) dropped += count(ignoredMedia, "photo or file", "photos or files")
        if (ignoredOther > 0) dropped += count(ignoredOther, "other detail", "other details")
        if (dropped.isNotEmpty()) lines += "Not kept: ${dropped.joinToString(", ")}."
        if (skippedLinks == 1) lines += "1 link to a person missing from the file was skipped."
        if (skippedLinks > 1) lines += "$skippedLinks links to people missing from the file were skipped."
        if (datesReducedToYear == 0 && dropped.isEmpty() && skippedLinks == 0) lines += "Everything in the file can be kept."
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
        val ids = HashMap<String, Int>()
        val persons = ArrayList<Person>(people.size)
        var reduced = 0
        var events = 0
        var sources = 0
        var media = 0
        var other = 0

        fun yearOf(date: String?): Int? {
            if (date.isNullOrBlank()) return null
            val text = date.trim()
            if (BC.containsMatchIn(text)) return null
            val year = YEAR.find(text)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..9999 } ?: return null
            if (text != year.toString()) reduced++
            return year
        }

        for (node in people) {
            if (ids.containsKey(node.xref)) continue
            val id = persons.size + 1
            ids[node.xref!!] = id
            val (given, surname) = nameOf(node.child("NAME"))
            val birth = node.child("BIRT")
            val death = node.child("DEAT")
            val noteText = node.all("NOTE").mapNotNull { n ->
                val v = n.value
                (if (REFERENCE.matches(v)) notes[v] else v)?.trim()?.takeIf { it.isNotEmpty() }
            }.joinToString("\n\n")
            persons += Person(
                id = id,
                given = given,
                surname = surname,
                sex = when (node.child("SEX")?.value?.trim()?.uppercase()) {
                    "M" -> Sex.Male
                    "F" -> Sex.Female
                    else -> Sex.Unknown
                },
                born = yearOf(birth?.child("DATE")?.value),
                died = yearOf(death?.child("DATE")?.value),
                birthPlace = birth?.child("PLAC")?.value?.trim().orEmpty(),
                deathPlace = death?.child("PLAC")?.value?.trim().orEmpty(),
                notes = noteText,
            )
            for (detail in node.children) {
                when (detail.tag) {
                    in KEPT_PERSON_TAGS -> Unit
                    in EVENT_TAGS -> events++
                    "SOUR" -> sources++
                    "OBJE" -> media++
                    else -> if (!detail.tag.startsWith("_") && detail.tag !in HOUSEKEEPING_TAGS) other++
                }
            }
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
            if (parents.size == 2) partnerships += minOf(parents[0], parents[1]) to maxOf(parents[0], parents[1])
            for (child in fam.all("CHIL")) {
                val childId = lookup(child.value) ?: continue
                for (parent in parents) if (parent != childId) links += childId to parent
            }
            for (detail in fam.children) {
                when (detail.tag) {
                    in KEPT_FAMILY_TAGS -> Unit
                    in EVENT_TAGS -> events++
                    "SOUR" -> sources++
                    "OBJE" -> media++
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

        val report = GedcomReport(persons.size, families.size, reduced, events, sources, media, other, skipped)
        return GedcomResult(BackupData(persons, links.toList(), partnerships.toList()), report)
    }

    private val YEAR = Regex("""(?<![0-9])([0-9]{3,4})(?![0-9])""")
    private val BC = Regex("""\bB\.?\s?C\.?(?:E\.?)?\b""", RegexOption.IGNORE_CASE)
    private val REFERENCE = Regex("""@[^@\s]+@""")
    private val LINE = Regex("""^(\d+)\s+(?:(@[^@\s]+@)\s+)?([A-Za-z0-9_]+)(?:\s(.*))?$""")

    private val KEPT_PERSON_TAGS = setOf("NAME", "SEX", "BIRT", "DEAT", "NOTE", "SNOTE", "FAMC", "FAMS")
    private val KEPT_FAMILY_TAGS = setOf("HUSB", "WIFE", "CHIL", "NOTE", "SNOTE")
    private val HOUSEKEEPING_TAGS = setOf("CHAN", "RIN", "REFN", "RFN", "AFN", "UID", "SUBM", "ALIA", "ASSO", "ANCI", "DESI", "SOURCE", "TYPE", "RESN", "CREA", "NO")
    private val EVENT_TAGS = setOf(
        "MARR", "DIV", "DIVF", "ANUL", "ENGA", "MARB", "MARC", "MARL", "MARS", "CENS", "BURI", "CREM", "CHR", "CHRA", "BAPM",
        "BARM", "BASM", "BLES", "CONF", "FCOM", "ORDN", "NATU", "EMIG", "IMMI", "PROB", "WILL", "GRAD", "RETI", "EVEN",
        "OCCU", "RESI", "TITL", "EDUC", "PROP", "RELI", "DSCR", "CAST", "NATI", "NCHI", "NMR", "SSN", "IDNO", "FACT",
        "ADOP", "BAPL", "CONL", "ENDL", "SLGC", "SLGS", "ADDR", "PHON", "EMAIL", "WWW", "FAX",
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
    fun write(family: Family, appVersion: String, exportedOn: String): String {
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
            when (p.sex) {
                Sex.Male -> put(1, "SEX", "M")
                Sex.Female -> put(1, "SEX", "F")
                Sex.Unknown -> Unit
            }
            if (p.born != null || p.birthPlace.isNotBlank()) {
                put(1, "BIRT")
                if (p.born != null) put(2, "DATE", p.born.toString())
                if (p.birthPlace.isNotBlank()) text(2, "PLAC", p.birthPlace.trim())
            }
            if (p.died != null || p.deathPlace.isNotBlank()) {
                put(1, "DEAT")
                if (p.died != null) put(2, "DATE", p.died.toString())
                if (p.deathPlace.isNotBlank()) text(2, "PLAC", p.deathPlace.trim())
            }
            if (p.notes.isNotBlank()) text(1, "NOTE", p.notes.trim())
            for (f in childIn[p.id].orEmpty()) put(1, "FAMC", "@F$f@")
            for (f in spouseIn[p.id].orEmpty()) put(1, "FAMS", "@F$f@")
        }

        for ((key, children) in families) {
            put(0, "FAM", xref = "F${familyIds.getValue(key)}")
            val (husband, wife) = roles(family, key)
            if (husband != null) put(1, "HUSB", "@I$husband@")
            if (wife != null) put(1, "WIFE", "@I$wife@")
            for (child in children) put(1, "CHIL", "@I$child@")
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
