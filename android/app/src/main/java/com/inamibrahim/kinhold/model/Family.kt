package com.inamibrahim.kinhold.model

/**
 * Everyone in a tree and how they are related, as one unchanging snapshot. It answers "who are this person's parents,
 * spouse and children" for anyone, which is what the focus view needs. Changes go through the repository, which builds
 * a new snapshot.
 */
class Family private constructor(
    private val byId: Map<Int, Person>,
    private val parentIds: Map<Int, List<Int>>,
    private val childIds: Map<Int, List<Int>>,
    private val spouseIds: Map<Int, List<Int>>,
) {
    val size: Int get() = byId.size
    val people: Collection<Person> get() = byId.values

    operator fun contains(id: Int): Boolean = id in byId

    fun person(id: Int): Person = byId.getValue(id)

    /** Father first, then mother, then anyone whose sex is not recorded. */
    fun parentsOf(id: Int): List<Person> = parentIds[id].orEmpty().map(byId::getValue).sortedBy { it.sex.ordinal }

    /** Oldest first; children with no birth year come last. */
    fun childrenOf(id: Int): List<Person> = childIds[id].orEmpty().map(byId::getValue).sortedBy { it.born ?: Int.MAX_VALUE }

    fun spousesOf(id: Int): List<Person> = spouseIds[id].orEmpty().map(byId::getValue)

    /** The person born earliest, or the first one added when no birth years are known. */
    fun oldest(): Person? = people.minByOrNull { it.born ?: Int.MAX_VALUE }

    /** People whose name contains [query], alphabetically. Nothing for a blank query. */
    fun search(query: String, limit: Int = 30): List<Person> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return people.filter { it.name.contains(q, ignoreCase = true) }.sortedBy { it.name }.take(limit)
    }

    /**
     * A person in the middle of the tree with parents, grandparents, children and grandchildren, so the first view has
     * people in every direction. Falls back to the oldest person, and is null only for an empty tree.
     */
    fun defaultFocus(): Person? {
        var best: Person? = null
        var bestScore = -1
        for (p in people) {
            val parents = parentIds[p.id].orEmpty()
            val kidIds = childIds[p.id].orEmpty()
            if (parents.isEmpty() || kidIds.isEmpty()) continue
            val grandparents = parents.count { parentIds[it].orEmpty().isNotEmpty() }
            val hasGrandchildren = kidIds.any { childIds[it].orEmpty().isNotEmpty() }
            val score = grandparents * 10 + (if (hasGrandchildren) 6 else 0) + kidIds.size
            if (score > bestScore) {
                best = p
                bestScore = score
            }
        }
        return best ?: oldest()
    }

    companion object {
        val Empty = of(emptyList(), emptyList(), emptyList())

        /**
         * Builds a snapshot. [parentLinks] are (child, parent) pairs and [partnerships] are pairs of people who are
         * partners. Links that mention someone who is not in [people] are ignored.
         */
        fun of(
            people: List<Person>,
            parentLinks: List<Pair<Int, Int>>,
            partnerships: List<Pair<Int, Int>>,
        ): Family {
            val byId = LinkedHashMap<Int, Person>()
            for (p in people) byId[p.id] = p
            val parents = HashMap<Int, MutableList<Int>>()
            val children = HashMap<Int, MutableList<Int>>()
            val spouses = HashMap<Int, MutableList<Int>>()
            for ((child, parent) in parentLinks) {
                if (child !in byId || parent !in byId || child == parent) continue
                parents.getOrPut(child) { mutableListOf() } += parent
                children.getOrPut(parent) { mutableListOf() } += child
            }
            for ((a, b) in partnerships) {
                if (a !in byId || b !in byId || a == b) continue
                spouses.getOrPut(a) { mutableListOf() } += b
                spouses.getOrPut(b) { mutableListOf() } += a
            }
            return Family(byId, parents, children, spouses)
        }
    }
}

/** Turns the descendants-only test structure into a [Family]. */
fun FamilyUnit.toFamily(): Family {
    val people = ArrayList<Person>()
    val links = ArrayList<Pair<Int, Int>>()
    val partnerships = ArrayList<Pair<Int, Int>>()

    fun visit(u: FamilyUnit) {
        people += u.partners
        if (u.partners.size == 2) partnerships += u.partners[0].id to u.partners[1].id
        for (c in u.children) {
            for (parent in u.partners) links += c.partners[0].id to parent.id
            visit(c)
        }
    }
    visit(this)
    return Family.of(people, links, partnerships)
}
