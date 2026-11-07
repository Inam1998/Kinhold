package com.inamibrahim.kinhold.model

/**
 * One set of people who are all linked to each other through parents, children and partners. Only its size and one person to
 * open it on are kept (not every member), so the list stays small however big the tree is.
 */
class TreeGroup(
    val size: Int,
    /** The person to open the group on: the one born earliest, or the first added when no birth year is known. */
    val start: Person,
)

/**
 * The separate groups in a tree. A tree whose links were removed, or whose people were added without being linked, is several
 * groups, and the tree screen only ever shows the group of the person in focus. Largest first.
 */
object TreeGroups {
    fun of(family: Family): List<TreeGroup> {
        val ids = family.people.map { it.id }
        val parent = HashMap<Int, Int>(ids.size * 2)
        for (id in ids) parent[id] = id

        fun find(x: Int): Int {
            var root = x
            while (parent.getValue(root) != root) root = parent.getValue(root)
            // Shorten the path so later lookups are quick.
            var cur = x
            while (parent.getValue(cur) != root) {
                val next = parent.getValue(cur)
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun join(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        for ((child, par) in family.parentLinks()) join(child, par)
        for ((a, b) in family.partnerships()) join(a, b)

        val sizes = HashMap<Int, Int>()
        val firsts = HashMap<Int, Person>()
        for (p in family.people) {
            val root = find(p.id)
            sizes.merge(root, 1, Int::plus)
            val best = firsts[root]
            if (best == null || (p.born ?: Int.MAX_VALUE) < (best.born ?: Int.MAX_VALUE)) firsts[root] = p
        }
        return sizes.entries
            .map { (root, size) -> TreeGroup(size, firsts.getValue(root)) }
            .sortedWith(compareBy({ -it.size }, { it.start.id }))
    }
}
