package com.inamibrahim.kinhold.model

/** One relative of the person in focus and how they are related, such as "Half-sister". */
class RelativeLabel(val person: Person, val label: String)

/**
 * Siblings, half-siblings and step relatives of [id], worked out from the parent and spouse links already stored. Nothing
 * extra is saved, so every tree, backup and GEDCOM file gets them.
 *
 * - A sibling has exactly the same recorded parents; a half-sibling shares some but not all of them.
 * - A step-parent is a parent's spouse who is not a parent. A step-sibling is a step-parent's child who is not a
 *   sibling or half-sibling. A stepchild is a spouse's child who is not the person's own.
 *
 * At most [limit] relatives are returned, siblings first, so the cost stays small however large the tree is.
 */
fun Family.relativesOf(id: Int, limit: Int = 30): List<RelativeLabel> {
    if (id !in this) return emptyList()
    val out = ArrayList<RelativeLabel>()
    val parents = parentsOf(id)
    val parentIds = parents.map { it.id }.toSet()

    val shared = LinkedHashMap<Int, Person>()
    for (p in parents) for (c in childrenOf(p.id)) if (c.id != id) shared.putIfAbsent(c.id, c)
    val full = ArrayList<Person>()
    val half = ArrayList<Person>()
    for (c in shared.values) {
        if (parentsOf(c.id).map { it.id }.toSet() == parentIds) full += c else half += c
    }
    for (c in full) out += RelativeLabel(c, by(c.sex, "Brother", "Sister", "Sibling"))
    for (c in half) out += RelativeLabel(c, by(c.sex, "Half-brother", "Half-sister", "Half-sibling"))

    val stepParents = LinkedHashMap<Int, Person>()
    for (p in parents) for (s in currentSpousesOf(p.id)) if (s.id !in parentIds) stepParents.putIfAbsent(s.id, s)
    for (s in stepParents.values) out += RelativeLabel(s, by(s.sex, "Stepfather", "Stepmother", "Step-parent"))

    val stepSiblings = LinkedHashMap<Int, Person>()
    for (s in stepParents.values) for (c in childrenOf(s.id)) {
        if (c.id != id && c.id !in shared && c.id !in stepSiblings) stepSiblings[c.id] = c
    }
    for (c in stepSiblings.values) out += RelativeLabel(c, by(c.sex, "Stepbrother", "Stepsister", "Step-sibling"))

    val stepChildren = LinkedHashMap<Int, Person>()
    for (s in currentSpousesOf(id)) for (c in childrenOf(s.id)) {
        if (parentsOf(c.id).none { it.id == id }) stepChildren.putIfAbsent(c.id, c)
    }
    for (c in stepChildren.values) out += RelativeLabel(c, by(c.sex, "Stepson", "Stepdaughter", "Stepchild"))

    return if (out.size > limit) out.subList(0, limit) else out
}

private fun by(sex: Sex, male: String, female: String, neutral: String): String = when (sex) {
    Sex.Male -> male
    Sex.Female -> female
    Sex.Unknown -> neutral
}
