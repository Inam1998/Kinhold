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
    private val couples: Map<Pair<Int, Int>, CoupleInfo>,
    private val linkKinds: Map<Pair<Int, Int>, LinkKind>,
) {
    val size: Int get() = byId.size
    val people: Collection<Person> get() = byId.values

    operator fun contains(id: Int): Boolean = id in byId

    /** Every (child, parent) pair, for saving. */
    fun parentLinks(): List<Pair<Int, Int>> = parentIds.flatMap { (child, parents) -> parents.map { child to it } }

    /** Every couple once, with the smaller id first, for saving. */
    fun partnerships(): List<Pair<Int, Int>> =
        spouseIds.flatMap { (a, others) -> others.filter { a < it }.map { a to it } }

    fun person(id: Int): Person = byId.getValue(id)

    /** How [child] is linked to [parent]: a birth link unless another kind was recorded. */
    fun kindOf(child: Int, parent: Int): LinkKind = linkKinds[child to parent] ?: LinkKind.Birth

    /** The links that are not birth links, for saving: (child, parent) to kind. */
    fun linkKinds(): Map<Pair<Int, Int>, LinkKind> = linkKinds

    /** Parents who count as ancestors: birth and adoptive parents, not foster or step. */
    fun lineageParentsOf(id: Int): List<Person> = parentsOf(id).filter { kindOf(id, it.id).isLineage }

    /** Father first, then mother, then anyone whose sex is not recorded. */
    fun parentsOf(id: Int): List<Person> = parentIds[id].orEmpty().map(byId::getValue).sortedBy { it.sex.ordinal }

    /** Oldest first; children with no birth year come last. */
    fun childrenOf(id: Int): List<Person> = childIds[id].orEmpty().map(byId::getValue).sortedBy { it.born ?: Int.MAX_VALUE }

    fun spousesOf(id: Int): List<Person> = spouseIds[id].orEmpty().map(byId::getValue)

    private fun info(a: Int, b: Int): CoupleInfo? = couples[minOf(a, b) to maxOf(a, b)]

    /** Whether a divorce or annulment is recorded for this couple. */
    fun partnershipEnded(a: Int, b: Int): Boolean = info(a, b)?.ended == true

    /** Spouses who are still together: those with no divorce or annulment recorded. */
    fun currentSpousesOf(id: Int): List<Person> = spousesOf(id).filter { !partnershipEnded(id, it.id) }

    /**
     * The spouses in the order they are placed and listed: by marriage date where one is recorded (earliest first),
     * then the others by their own birth year, then by the order they were added.
     */
    fun spousesInOrder(id: Int): List<Person> = spousesOf(id).sortedWith(
        compareBy({ info(id, it.id)?.marriedKey ?: Int.MAX_VALUE }, { it.born ?: Int.MAX_VALUE }, { it.id }),
    )

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
        /** The most parents one person can have recorded: birth, adoptive, foster and step parents together. */
        const val MAX_PARENTS = 4

        val Empty = of(emptyList(), emptyList(), emptyList())

        /**
         * Builds a snapshot. [parentLinks] are (child, parent) pairs and [partnerships] are pairs of people who are
         * partners. Links that mention someone who is not in [people] are ignored.
         */
        fun of(
            people: List<Person>,
            parentLinks: List<Pair<Int, Int>>,
            partnerships: List<Pair<Int, Int>>,
            couples: Map<Pair<Int, Int>, CoupleInfo> = emptyMap(),
            linkKinds: Map<Pair<Int, Int>, LinkKind> = emptyMap(),
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
            return Family(byId, parents, children, spouses, couples, linkKinds.filterKeys { it.first in byId && it.second in byId && it.second in parents[it.first].orEmpty() })
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
