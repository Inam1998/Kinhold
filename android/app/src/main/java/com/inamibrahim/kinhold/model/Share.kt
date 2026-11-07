package com.inamibrahim.kinhold.model

/** Which way from the chosen person a shared branch reaches. */
enum class ShareDirection { Ancestors, Descendants, Both }

/**
 * What to put in a share file made from one person outward. [generations] is how many generations to go (0 means all of
 * them). [withSpouses] adds the husband or wife of everyone chosen, without their own families. [excludeLiving] leaves out
 * people who may still be alive, except the person the branch starts from. [withPhotos] includes their photos.
 */
class ShareScope(
    val direction: ShareDirection = ShareDirection.Both,
    val generations: Int = 0,
    val withSpouses: Boolean = true,
    val excludeLiving: Boolean = false,
    val withPhotos: Boolean = true,
    /** Address, phone number and map pin. Off unless asked: these belong to living people who did not agree to be sent. */
    val withContact: Boolean = false,
)

/** Works out which people a share file holds, and builds the smaller tree for it. Pure Kotlin. */
object Share {
    /** Someone with no death date who was born within the last 110 years. People with no dates at all are not assumed alive. */
    fun mayBeAlive(p: Person, thisYear: Int): Boolean = p.deathDate.isEmpty && (p.born?.let { it > thisYear - 110 } ?: false)

    /** The ids of everyone in the branch that starts at [rootId]. Empty if there is no such person. */
    fun select(family: Family, rootId: Int, scope: ShareScope, thisYear: Int): Set<Int> {
        if (rootId !in family) return emptySet()
        val chosen = LinkedHashSet<Int>()
        chosen += rootId
        val limit = if (scope.generations <= 0) Int.MAX_VALUE else scope.generations
        if (scope.direction != ShareDirection.Descendants) walk(rootId, limit, chosen) { family.parentsOf(it).map(Person::id) }
        if (scope.direction != ShareDirection.Ancestors) walk(rootId, limit, chosen) { family.childrenOf(it).map(Person::id) }
        if (scope.withSpouses) for (id in chosen.toList()) for (s in family.spousesOf(id)) chosen += s.id
        if (scope.excludeLiving) chosen.removeAll { it != rootId && mayBeAlive(family.person(it), thisYear) }
        return chosen
    }

    private fun walk(start: Int, limit: Int, into: MutableSet<Int>, next: (Int) -> List<Int>) {
        var level = listOf(start)
        var depth = 0
        val seen = HashSet<Int>().apply { add(start) }
        while (level.isNotEmpty() && depth < limit) {
            val following = ArrayList<Int>()
            for (id in level) for (n in next(id)) if (seen.add(n)) following += n
            into += following
            level = following
            depth++
        }
    }

    /** The people in [ids] with only the links among them. */
    fun subFamily(family: Family, ids: Set<Int>, keepContact: Boolean = false): Family = Family.of(
        family.people.filter { it.id in ids }.map { it.withoutPrivateDetails(keepContact) },
        family.parentLinks().filter { (c, p) -> c in ids && p in ids },
        family.partnerships().filter { (a, b) -> a in ids && b in ids },
        linkKinds = family.linkKinds().filterKeys { (c, p) -> c in ids && p in ids },
    )

    /** The events and sources of the people in [ids] (and of couples where both are in it). */
    fun subDetails(details: TreeDetails, ids: Set<Int>, keepFields: Boolean = false): TreeDetails = TreeDetails(
        details.events.filterKeys { it in ids },
        details.sources.filterKeys { it in ids },
        details.couples.filterKeys { (a, b) -> a in ids && b in ids },
        if (keepFields) details.fields.filterKeys { it in ids } else emptyMap(),
    )
}
