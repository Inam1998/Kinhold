package com.inamibrahim.kinhold.model

/**
 * Everyone in a tree and how they are related. Unlike [FamilyUnit] (which only goes down from one couple) this answers
 * "who are this person's parents, spouse and children" for anyone, which is what the focus view needs.
 */
class Family(
    private val byId: Map<Int, Person>,
    private val parentIds: Map<Int, List<Int>>,
    private val childIds: Map<Int, List<Int>>,
    private val spouseIds: Map<Int, List<Int>>,
) {
    val size: Int get() = byId.size
    val people: Collection<Person> get() = byId.values

    fun person(id: Int): Person = byId.getValue(id)

    /** Father first, then mother. */
    fun parentsOf(id: Int): List<Person> = parentIds[id].orEmpty().map(byId::getValue).sortedBy { it.female }

    /** Oldest first. */
    fun childrenOf(id: Int): List<Person> = childIds[id].orEmpty().map(byId::getValue).sortedBy { it.born }

    fun spousesOf(id: Int): List<Person> = spouseIds[id].orEmpty().map(byId::getValue)

    fun oldest(): Person = people.minBy { it.born }

    /** People whose name contains [query], alphabetically. Nothing for a blank query. */
    fun search(query: String, limit: Int = 30): List<Person> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return people.filter { it.name.contains(q, ignoreCase = true) }.sortedBy { it.name }.take(limit)
    }

    /**
     * A person in the middle of the tree with parents, grandparents, children and grandchildren, so the first view has
     * people in every direction.
     */
    fun defaultFocus(): Person {
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
}

/** Turns the descendants-only test structure into a [Family]. */
fun FamilyUnit.toFamily(): Family {
    val byId = LinkedHashMap<Int, Person>()
    val parents = HashMap<Int, MutableList<Int>>()
    val children = HashMap<Int, MutableList<Int>>()
    val spouses = HashMap<Int, MutableList<Int>>()

    fun visit(u: FamilyUnit) {
        u.partners.forEach { byId[it.id] = it }
        if (u.partners.size == 2) {
            val a = u.partners[0].id
            val b = u.partners[1].id
            spouses.getOrPut(a) { mutableListOf() } += b
            spouses.getOrPut(b) { mutableListOf() } += a
        }
        for (c in u.children) {
            val head = c.partners[0].id
            for (parent in u.partners) {
                parents.getOrPut(head) { mutableListOf() } += parent.id
                children.getOrPut(parent.id) { mutableListOf() } += head
            }
            visit(c)
        }
    }
    visit(this)
    return Family(byId, parents, children, spouses)
}
