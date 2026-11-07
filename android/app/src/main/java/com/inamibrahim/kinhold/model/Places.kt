package com.inamibrahim.kinhold.model

/**
 * The places already used in the tree, so a place typed once can be picked again instead of typed in a different spelling.
 * Built from the birth and death places of everyone, commonest first; "Lahore" and "lahore" count as one place and the
 * spelling used most often is the one offered. At most [MAX_PLACES] are kept, so memory stays small however big the tree is.
 */
class PlaceBook internal constructor(private val places: List<Pair<String, String>>) {
    val size: Int get() = places.size

    /** Up to [limit] places for what has been typed: those that start with it first, then those that contain it. Never the exact place already typed. */
    fun suggest(typed: String, limit: Int = 4): List<String> {
        val q = TreeSearch.fold(typed)
        if (q.isEmpty()) return emptyList()
        val starts = ArrayList<String>()
        val contains = ArrayList<String>()
        for ((shown, folded) in places) {
            if (folded == q) continue
            if (folded.startsWith(q)) starts += shown else if (folded.contains(q)) contains += shown
            if (starts.size >= limit) break
        }
        return (starts + contains).take(limit)
    }

    companion object {
        const val MAX_PLACES = 2000
        val Empty = PlaceBook(emptyList())

        fun of(family: Family): PlaceBook = of(family.people.flatMap { listOf(it.birthPlace, it.deathPlace) })

        fun of(all: List<String>): PlaceBook {
            // folded spelling -> how often each written spelling occurs
            val byKey = HashMap<String, HashMap<String, Int>>()
            for (raw in all) {
                val place = raw.trim().replace(Regex("\\s+"), " ")
                if (place.isEmpty()) continue
                byKey.getOrPut(TreeSearch.fold(place)) { HashMap() }.merge(place, 1, Int::plus)
            }
            return PlaceBook(
                byKey.entries
                    .map { (key, spellings) -> Triple(key, spellings.maxWithOrNull(compareBy({ it.value }, { it.key.firstOrNull()?.isUpperCase() == true }, { it.key }))!!.key, spellings.values.sum()) }
                    .sortedWith(compareBy({ -it.third }, { it.first }))
                    .take(MAX_PLACES)
                    .map { it.second to it.first },
            )
        }
    }
}
