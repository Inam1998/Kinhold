package com.inamibrahim.kinhold.tree

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import kotlin.math.max
import kotlin.math.min

/**
 * The view around one person: their ancestors in rows above, themselves (with their first spouse) in the middle, and
 * their descendants below. How much is drawn depends on [ancestorDepth] and [descendantDepth], not on the size of the
 * tree, so it stays fast and readable for any family. People beyond the edge are flagged with `moreAbove` and
 * `moreBelow` so the screen can show there is more to reach by tapping.
 */
fun layoutFocus(
    family: Family,
    focusId: Int,
    ancestorDepth: Int = 3,
    descendantDepth: Int = 2,
): TreeLayout {
    val hidden = HashMap<Int, Int>()
    val below = layoutTree(descendantUnit(family, focusId, descendantDepth, hidden))
    val focusBox = below.people.first { it.person.id == focusId }
    val focusCenter = focusBox.x + TreeMetrics.NODE_W / 2

    // Ancestors, in the layout's own coordinates: the focus row is y = 0 and rows above have negative y.
    class Ancestor(val person: Person, val depth: Int, val x: Float, val y: Float)

    val ancestors = ArrayList<Ancestor>()
    val edges = FloatBuffer()

    fun spanOf(p: Person, depth: Int): Float {
        val parents = if (depth < ancestorDepth) family.parentsOf(p.id) else emptyList()
        if (parents.isEmpty()) return TreeMetrics.NODE_W
        val total = parents.sumOf { spanOf(it, depth + 1).toDouble() }.toFloat() + (parents.size - 1) * TreeMetrics.SIBLING_GAP
        return max(TreeMetrics.NODE_W, total)
    }

    fun placeParents(child: Person, childDepth: Int, childCenter: Float) {
        if (childDepth >= ancestorDepth) return
        val parents = family.parentsOf(child.id)
        if (parents.isEmpty()) return
        val spans = parents.map { spanOf(it, childDepth + 1) }
        val total = spans.sum() + (parents.size - 1) * TreeMetrics.SIBLING_GAP
        val childTop = -childDepth * TreeMetrics.ROW
        val parentTop = -(childDepth + 1) * TreeMetrics.ROW
        val busY = childTop - TreeMetrics.ROW_GAP / 2
        var cursor = childCenter - total / 2
        var minX = childCenter
        var maxX = childCenter
        val centers = FloatArray(parents.size)
        parents.forEachIndexed { i, p ->
            val center = cursor + spans[i] / 2
            centers[i] = center
            minX = min(minX, center)
            maxX = max(maxX, center)
            ancestors += Ancestor(p, childDepth + 1, center - TreeMetrics.NODE_W / 2, parentTop)
            placeParents(p, childDepth + 1, center)
            cursor += spans[i] + TreeMetrics.SIBLING_GAP
        }
        edges.line(childCenter, childTop, childCenter, busY)
        if (maxX > minX) edges.line(minX, busY, maxX, busY)
        for (c in centers) edges.line(c, busY, c, parentTop + TreeMetrics.NODE_H)
    }
    placeParents(family.person(focusId), 0, focusCenter)

    val rowsAbove = ancestors.maxOfOrNull { it.depth } ?: 0
    var left = 0f
    var right = below.width
    for (a in ancestors) {
        left = min(left, a.x)
        right = max(right, a.x + TreeMetrics.NODE_W)
    }
    val dx = -left
    val dy = rowsAbove * TreeMetrics.ROW

    val placed = ArrayList<PlacedPerson>(ancestors.size + below.people.size)
    for (a in ancestors) {
        placed += PlacedPerson(
            person = a.person,
            x = a.x + dx,
            y = a.y + dy,
            generation = rowsAbove - a.depth,
            moreAbove = a.depth == ancestorDepth && family.parentsOf(a.person.id).isNotEmpty(),
        )
    }
    for (p in below.people) {
        placed += PlacedPerson(p.person, p.x + dx, p.y + dy, p.generation + rowsAbove, moreBelow = hidden[p.person.id] ?: 0)
    }

    var i = 0
    while (i < below.edges.size) {
        edges.line(below.edges[i], below.edges[i + 1], below.edges[i + 2], below.edges[i + 3])
        i += 4
    }
    val all = edges.toArray()
    var k = 0
    while (k < all.size) {
        all[k] += dx
        all[k + 1] += dy
        all[k + 2] += dx
        all[k + 3] += dy
        k += 4
    }

    return TreeLayout(
        people = placed,
        edges = all,
        width = right - left,
        height = dy + below.height,
        generations = rowsAbove + below.generations,
        focusId = focusId,
        focusX = focusCenter + dx,
        focusY = dy + TreeMetrics.NODE_H / 2,
    )
}

/**
 * The person with all their spouses and, while [depthLeft] allows, a unit for each child, grouped by the other parent.
 * Spouses are placed by marriage date where one is recorded, then by birth year, then by the order they were added. A child whose
 * other parent is not one of the spouses hangs from the person alone. Where the depth runs out the number of children
 * left out is recorded in [hidden].
 */
private fun descendantUnit(family: Family, id: Int, depthLeft: Int, hidden: MutableMap<Int, Int>): UnitNode {
    val head = family.person(id)
    val spouses = family.spousesInOrder(id)
    val kids = family.childrenOf(id)
    if (depthLeft <= 0) {
        if (kids.isNotEmpty()) hidden[id] = kids.size
        return UnitNode(head, spouses)
    }
    val bySpouse = LinkedHashMap<Int?, MutableList<UnitNode>>()
    for (kid in kids) {
        val otherParents = family.parentsOf(kid.id).map { it.id }.toSet()
        val spouse = spouses.firstOrNull { it.id in otherParents }
        bySpouse.getOrPut(spouse?.id) { mutableListOf() } += descendantUnit(family, kid.id, depthLeft - 1, hidden)
    }
    val groups = bySpouse.map { (spouseId, units) -> ChildGroup(spouses.firstOrNull { it.id == spouseId }, units) }
    return UnitNode(head, spouses, groups)
}
