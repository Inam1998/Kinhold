package com.inamibrahim.kinhold.model

/** A person with a number: the most children, the longest life. */
class Counted(val person: Person, val value: Int)

/** Counts of things in the tree, worked out in one pass over everyone. Nothing is stored. */
class TreeStats(
    val people: Int,
    val men: Int,
    val women: Int,
    val sexNotStated: Int,
    /** No death recorded and born within the last 110 years: the same test the birthday list and the age line use. */
    val mayBeAlive: Int,
    val deceased: Int,
    val withPhoto: Int,
    val withNoDates: Int,
    val couples: Int,
    /** The longest chain of parents in the tree, counting a person with no recorded parent as one generation. */
    val generations: Int,
    val oldest: Person?,
    val youngest: Person?,
    val longestLife: Counted?,
    /** Average age at death over people with both years that make sense, or null when there are none. */
    val averageLifespan: Double?,
    val mostChildren: Counted?,
    val topSurnames: List<Pair<String, Int>>,
    val topPlaces: List<Pair<String, Int>>,
    /** Births per decade, in time order, e.g. 1950 to 12 for the 1950s. */
    val birthsByDecade: List<Pair<Int, Int>>,
) {
    companion object {
        fun of(family: Family, thisYear: Int): TreeStats {
            val all = family.people
            var men = 0; var women = 0; var unknown = 0; var alive = 0; var dead = 0; var photos = 0; var noDates = 0
            val surnames = HashMap<String, Int>()
            val places = HashMap<String, Int>()
            val decades = java.util.TreeMap<Int, Int>()
            var oldest: Person? = null
            var youngest: Person? = null
            var longest: Counted? = null
            var lifeSum = 0L
            var lifeCount = 0
            var most: Counted? = null
            for (p in all) {
                when (p.sex) { Sex.Male -> men++; Sex.Female -> women++; Sex.Unknown -> unknown++ }
                if (p.died != null) dead++
                if (p.died == null && p.born != null && p.born > thisYear - 110 && p.born <= thisYear) alive++
                if (p.hasPhoto) photos++
                if (p.born == null && p.died == null) noDates++
                if (p.surname.isNotBlank()) surnames.merge(p.surname.trim(), 1, Int::plus)
                for (place in listOf(p.birthPlace, p.deathPlace)) if (place.isNotBlank()) places.merge(place.trim(), 1, Int::plus)
                p.born?.let { b ->
                    decades.merge(b / 10 * 10, 1, Int::plus)
                    if (oldest == null || b < oldest.born!!) oldest = p
                    if (youngest == null || b > youngest.born!!) youngest = p
                }
                if (p.born != null && p.died != null && p.died >= p.born && p.died - p.born <= 120) {
                    val span = p.died - p.born
                    lifeSum += span
                    lifeCount++
                    if (longest == null || span > longest.value) longest = Counted(p, span)
                }
                val kids = family.childrenOf(p.id).size
                if (kids > 0 && (most == null || kids > most.value)) most = Counted(p, kids)
            }
            return TreeStats(
                people = all.size, men = men, women = women, sexNotStated = unknown, mayBeAlive = alive, deceased = dead,
                withPhoto = photos, withNoDates = noDates, couples = family.partnerships().size, generations = generations(family),
                oldest = oldest, youngest = youngest, longestLife = longest,
                averageLifespan = if (lifeCount == 0) null else lifeSum.toDouble() / lifeCount,
                mostChildren = most,
                topSurnames = top(surnames), topPlaces = top(places),
                birthsByDecade = decades.entries.map { it.key to it.value },
            )
        }

        private fun top(counts: Map<String, Int>, n: Int = 5): List<Pair<String, Int>> =
            counts.entries.sortedWith(compareBy({ -it.value }, { it.key })).take(n).map { it.key to it.value }

        /** The longest chain of parents above anyone. A loop in bad data is cut where it closes, so this always ends. */
        fun generations(family: Family): Int {
            val depth = HashMap<Int, Int>()
            val inProgress = HashSet<Int>()
            fun visit(id: Int): Int {
                depth[id]?.let { return it }
                if (!inProgress.add(id)) return 0
                var best = 0
                for (parent in family.parentsOf(id)) best = maxOf(best, visit(parent.id))
                inProgress.remove(id)
                return (best + 1).also { depth[id] = it }
            }
            var most = 0
            for (p in family.people) most = maxOf(most, visit(p.id))
            return most
        }
    }
}
