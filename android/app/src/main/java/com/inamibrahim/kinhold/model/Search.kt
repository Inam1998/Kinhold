package com.inamibrahim.kinhold.model

import java.text.Normalizer

/** Where a search matched: the name first, then a place, then the notes. */
enum class MatchIn { Name, Place, Notes }

/** One person found by a search, with a short line saying why when the match was not the name. */
class SearchHit(val person: Person, val matchedIn: MatchIn, val snippet: String)

/**
 * Find by name, place or notes, ignoring case and accents. The text to search is folded once when the index is built, so
 * typing only compares; build it off the main thread. Places are the birth place, death place and address; only the first
 * [NOTE_CHARS] characters of a person's notes are searched, to keep memory small on weak phones. Places and notes are
 * searched from two typed characters, so one letter does not match half the tree.
 */
class SearchIndex internal constructor(private val rows: List<Row>) {
    internal class Row(val person: Person, val name: String, val places: List<Pair<String, String>>, val notes: String)

    val size: Int get() = rows.size

    fun find(query: String, limit: Int = 30): List<SearchHit> {
        val q = TreeSearch.fold(query)
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<SearchHit>()
        val seen = HashSet<Int>()
        // Names first, alphabetically, the way the plain name search has always listed them.
        for (r in rows.filter { it.name.contains(q) }.sortedBy { it.person.name }) {
            if (out.size >= limit) return out
            if (seen.add(r.person.id)) out += SearchHit(r.person, MatchIn.Name, "")
        }
        if (q.length < 2) return out
        for (r in rows) {
            if (out.size >= limit) return out
            if (r.person.id in seen) continue
            val hit = r.places.firstOrNull { it.second.contains(q) } ?: continue
            seen += r.person.id
            out += SearchHit(r.person, MatchIn.Place, hit.first)
        }
        for (r in rows) {
            if (out.size >= limit) return out
            if (r.person.id in seen || !r.notes.contains(q)) continue
            seen += r.person.id
            out += SearchHit(r.person, MatchIn.Notes, excerpt(r.person.notes, query))
        }
        return out
    }

    /** About 70 characters of the notes around the match, with the line breaks flattened. */
    private fun excerpt(notes: String, query: String): String {
        val flat = notes.replace(Regex("\\s+"), " ").trim()
        val at = flat.indexOf(query.trim(), ignoreCase = true).takeIf { it >= 0 } ?: 0
        val from = (at - 25).coerceAtLeast(0)
        val to = (at + 45).coerceAtMost(flat.length)
        return (if (from > 0) "…" else "") + flat.substring(from, to) + (if (to < flat.length) "…" else "")
    }
}

object TreeSearch {
    const val NOTE_CHARS = 1000

    /** Lower case with accents removed, so "Zoë" is found by "zoe". */
    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

    fun index(family: Family): SearchIndex = SearchIndex(
        family.people.map { p ->
            val places = ArrayList<Pair<String, String>>(3)
            if (p.birthPlace.isNotBlank()) places += "Born in ${p.birthPlace.trim()}" to fold(p.birthPlace)
            if (p.deathPlace.isNotBlank()) places += "Died in ${p.deathPlace.trim()}" to fold(p.deathPlace)
            if (p.address.isNotBlank()) places += "Address: ${p.address.trim().replace(Regex("\\s+"), " ")}" to fold(p.address)
            SearchIndex.Row(p, fold(p.name), places, fold(p.notes.take(NOTE_CHARS)))
        },
    )
}
