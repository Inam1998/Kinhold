package com.inamibrahim.kinhold.model

/** How someone already in the tree is to be linked to a chosen person: as their parent, their child or their partner. */
enum class LinkType(val label: String) {
    Parent("A parent"),
    Child("A child"),
    Spouse("A spouse or partner"),
}

/**
 * The rules for linking two people who are already in the tree, so a link never makes something impossible: nobody is their
 * own ancestor, nobody has more than [Family.MAX_PARENTS] parents, and a link is never made twice.
 */
object LinkRules {
    /** Everyone above [id] through parent links of any kind, never including [id] itself. */
    fun ancestorsOf(family: Family, id: Int): Set<Int> = reach(id) { family.parentsOf(it).map { p -> p.id } }

    /** Everyone below [id] through parent links of any kind, never including [id] itself. */
    fun descendantsOf(family: Family, id: Int): Set<Int> = reach(id) { family.childrenOf(it).map { c -> c.id } }

    private fun reach(start: Int, next: (Int) -> List<Int>): Set<Int> {
        val seen = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        queue += start
        while (queue.isNotEmpty()) {
            for (n in next(queue.removeFirst())) if (n != start && seen.add(n)) queue += n
        }
        return seen
    }

    /**
     * People who cannot be linked to [personId] as [type], computed once so a list of candidates can hide them:
     * the person, anyone already linked that way, and anyone the link would make impossible.
     */
    fun excluded(family: Family, personId: Int, type: LinkType): Set<Int> {
        val out = HashSet<Int>()
        out += personId
        when (type) {
            LinkType.Parent -> {
                // Their descendants cannot be their parents. A person with all their parents already recorded takes no more.
                if (family.parentsOf(personId).size >= Family.MAX_PARENTS) return family.people.map { it.id }.toSet()
                out += family.parentsOf(personId).map { it.id }
                out += descendantsOf(family, personId)
            }
            LinkType.Child -> {
                out += family.childrenOf(personId).map { it.id }
                out += ancestorsOf(family, personId)
                for (p in family.people) if (family.parentsOf(p.id).size >= Family.MAX_PARENTS) out += p.id
            }
            LinkType.Spouse -> {
                out += family.spousesOf(personId).map { it.id }
                out += family.parentsOf(personId).map { it.id }
                out += family.childrenOf(personId).map { it.id }
            }
        }
        return out
    }

    /** Why [otherId] cannot be linked to [personId] as [type], in plain words, or null when the link is allowed. */
    fun problem(family: Family, personId: Int, otherId: Int, type: LinkType): String? {
        if (personId !in family || otherId !in family) return "That person is not in the tree."
        if (personId == otherId) return "A person cannot be linked to themselves."
        val a = family.person(personId).name
        val b = family.person(otherId).name
        return when (type) {
            LinkType.Parent -> when {
                otherId in family.parentsOf(personId).map { it.id } -> "$b is already a parent of $a."
                family.parentsOf(personId).size >= Family.MAX_PARENTS -> "$a already has ${Family.MAX_PARENTS} parents recorded."
                otherId in descendantsOf(family, personId) -> "$b is a descendant of $a, so cannot also be a parent."
                else -> null
            }
            LinkType.Child -> when {
                otherId in family.childrenOf(personId).map { it.id } -> "$b is already a child of $a."
                family.parentsOf(otherId).size >= Family.MAX_PARENTS -> "$b already has ${Family.MAX_PARENTS} parents recorded."
                otherId in ancestorsOf(family, personId) -> "$b is an ancestor of $a, so cannot also be a child."
                else -> null
            }
            LinkType.Spouse -> when {
                otherId in family.spousesOf(personId).map { it.id } -> "$a and $b are already partners."
                otherId in family.parentsOf(personId).map { it.id } || otherId in family.childrenOf(personId).map { it.id } -> "$a and $b are parent and child, so cannot be partners."
                else -> null
            }
        }
    }
}
