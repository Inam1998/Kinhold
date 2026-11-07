package com.inamibrahim.kinhold.tree

import com.inamibrahim.kinhold.model.FamilyUnit
import com.inamibrahim.kinhold.model.Person
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Sizes of the tree, in dp at 100% zoom. */
object TreeMetrics {
    const val NODE_W = 176f
    const val NODE_H = 64f
    const val PARTNER_GAP = 12f
    const val SIBLING_GAP = 28f

    /** Extra space between the children of different marriages. */
    const val GROUP_GAP = 40f
    const val ROW_GAP = 80f
    const val ROW = NODE_H + ROW_GAP

    /** How far apart the connector lines of different marriages are drawn, so they can be told apart. */
    const val BUS_STEP = 6f
    const val LINK_DROP = 8f
}

/**
 * A person's box. [x] and [y] are its top-left corner and [generation] is the row counted from the top. [moreAbove]
 * means parents exist that are not drawn; [moreBelow] is how many children are not drawn.
 */
class PlacedPerson(
    val person: Person,
    val x: Float,
    val y: Float,
    val generation: Int,
    val moreAbove: Boolean = false,
    val moreBelow: Int = 0,
)

/**
 * Where everyone sits. [edges] holds the connecting lines four numbers at a time (x1, y1, x2, y2), which keeps a
 * large tree in one flat array instead of thousands of small objects. [focusX] and [focusY] are the centre of the
 * focused person's box.
 */
class TreeLayout(
    val people: List<PlacedPerson>,
    val edges: FloatArray,
    val width: Float,
    val height: Float,
    val generations: Int,
    val focusId: Int = -1,
    val focusX: Float = 0f,
    val focusY: Float = 0f,
)

/** The children one couple had. [spouse] is null for children whose other parent is not drawn (or not known). */
class ChildGroup(val spouse: Person?, val children: List<UnitNode>)

/**
 * A person, their spouses in the order they are placed, and their children grouped by the other parent. The person sits
 * in the middle of their spouses: the first spouse to the right, the second to the left, the third further right, and
 * so on, so every spouse is as close as possible and no marriage line crosses another box.
 */
class UnitNode(val head: Person, val spouses: List<Person> = emptyList(), val groups: List<ChildGroup> = emptyList())

fun FamilyUnit.toUnitNode(): UnitNode = UnitNode(
    head = partners[0],
    spouses = partners.drop(1),
    groups = if (children.isEmpty()) emptyList() else listOf(ChildGroup(partners.getOrNull(1), children.map { it.toUnitNode() })),
)

/** Lays out the test structure of one couple and their descendants. */
fun layoutTree(root: FamilyUnit): TreeLayout = layoutTree(root.toUnitNode())

/**
 * Lays out a family from its oldest person down. Each unit gets as much width as its descendants need and is centred
 * over them, so nothing in a row can overlap. Costs one pass over the family.
 */
fun layoutTree(root: UnitNode): TreeLayout = TreeBuilder().build(root)

internal class FloatBuffer {
    var data = FloatArray(1024)
    var size = 0

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (size + 4 > data.size) data = data.copyOf(data.size * 2)
        data[size++] = x1
        data[size++] = y1
        data[size++] = x2
        data[size++] = y2
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

private class TreeBuilder {
    private val placed = ArrayList<PlacedPerson>()
    private val edges = FloatBuffer()
    private val widths = IdentityHashMap<UnitNode, Float>()
    private var deepest = 0

    /** The boxes of a unit from left to right, and where the head is among them. */
    private class Row(val members: List<Person>, val headIndex: Int) {
        val width: Float get() = members.size * TreeMetrics.NODE_W + (members.size - 1) * TreeMetrics.PARTNER_GAP
        fun boxLeft(rowLeft: Float, index: Int) = rowLeft + index * (TreeMetrics.NODE_W + TreeMetrics.PARTNER_GAP)
    }

    private fun row(u: UnitNode): Row {
        val right = u.spouses.filterIndexed { i, _ -> i % 2 == 0 }
        val left = u.spouses.filterIndexed { i, _ -> i % 2 == 1 }
        return Row(left.asReversed() + u.head + right, left.size)
    }

    /** Child groups with children, left to right in the same order as their couples. */
    private fun orderedGroups(u: UnitNode, row: Row): List<ChildGroup> {
        fun position(g: ChildGroup): Int {
            val spouse = g.spouse ?: return row.headIndex
            val i = row.members.indexOfFirst { it.id == spouse.id }
            return if (i < 0) row.headIndex else i
        }
        return u.groups.filter { it.children.isNotEmpty() }.sortedBy(::position)
    }

    private fun groupWidth(g: ChildGroup): Float =
        g.children.sumOf { width(it).toDouble() }.toFloat() + (g.children.size - 1) * TreeMetrics.SIBLING_GAP

    private fun childrenWidth(u: UnitNode): Float {
        val groups = orderedGroups(u, row(u))
        if (groups.isEmpty()) return 0f
        return groups.sumOf { groupWidth(it).toDouble() }.toFloat() + (groups.size - 1) * TreeMetrics.GROUP_GAP
    }

    private fun width(u: UnitNode): Float = widths.getOrPut(u) { max(row(u).width, childrenWidth(u)) }

    fun build(root: UnitNode): TreeLayout {
        place(root, 0f, 0)
        return TreeLayout(
            people = placed,
            edges = edges.toArray(),
            width = width(root),
            height = deepest * TreeMetrics.ROW + TreeMetrics.NODE_H,
            generations = deepest + 1,
        )
    }

    private fun place(u: UnitNode, left: Float, generation: Int) {
        deepest = max(deepest, generation)
        val sub = width(u)
        val row = row(u)
        val rowLeft = left + (sub - row.width) / 2
        val y = generation * TreeMetrics.ROW
        row.members.forEachIndexed { i, p -> placed += PlacedPerson(p, row.boxLeft(rowLeft, i), y, generation) }

        // Where each marriage's children hang from. The head alone is key -1.
        val anchors = HashMap<Int, Pair<Float, Float>>()
        val headCenter = row.boxLeft(rowLeft, row.headIndex) + TreeMetrics.NODE_W / 2
        anchors[HEAD] = headCenter to y + TreeMetrics.NODE_H
        row.members.forEachIndexed { i, spouse ->
            if (i == row.headIndex) return@forEachIndexed
            val distance = abs(i - row.headIndex)
            if (distance == 1) {
                // Next to the head: a short line between the boxes.
                val a = min(i, row.headIndex)
                val b = max(i, row.headIndex)
                val x1 = row.boxLeft(rowLeft, a) + TreeMetrics.NODE_W
                val x2 = row.boxLeft(rowLeft, b)
                val mid = y + TreeMetrics.NODE_H / 2
                edges.line(x1, mid, x2, mid)
                anchors[spouse.id] = (x1 + x2) / 2 to mid
            } else {
                // Further away: a line under the boxes between them, so it never runs through another person.
                val spouseCenter = row.boxLeft(rowLeft, i) + TreeMetrics.NODE_W / 2
                val bottom = y + TreeMetrics.NODE_H
                val depth = bottom + TreeMetrics.LINK_DROP + (distance - 2) * TreeMetrics.BUS_STEP
                edges.line(headCenter, bottom, headCenter, depth)
                edges.line(headCenter, depth, spouseCenter, depth)
                edges.line(spouseCenter, depth, spouseCenter, bottom)
                anchors[spouse.id] = (headCenter + spouseCenter) / 2 to depth
            }
        }

        val groups = orderedGroups(u, row)
        if (groups.isEmpty()) return
        var cursor = left + (sub - childrenWidth(u)) / 2
        val busBase = y + TreeMetrics.NODE_H + TreeMetrics.ROW_GAP / 2
        val childY = y + TreeMetrics.ROW
        groups.forEachIndexed { index, group ->
            val busY = busBase + index * TreeMetrics.BUS_STEP
            val (ax, ay) = anchors[group.spouse?.id ?: HEAD] ?: anchors.getValue(HEAD)
            var minX = ax
            var maxX = ax
            val drops = ArrayList<Float>(group.children.size)
            for (child in group.children) {
                val childWidth = width(child)
                val childRow = row(child)
                val childRowLeft = cursor + (childWidth - childRow.width) / 2
                val dropX = childRow.boxLeft(childRowLeft, childRow.headIndex) + TreeMetrics.NODE_W / 2
                drops += dropX
                minX = min(minX, dropX)
                maxX = max(maxX, dropX)
                place(child, cursor, generation + 1)
                cursor += childWidth + TreeMetrics.SIBLING_GAP
            }
            // The gap after the last child of a group is the wider one between marriages.
            cursor += TreeMetrics.GROUP_GAP - TreeMetrics.SIBLING_GAP
            edges.line(ax, ay, ax, busY)
            if (maxX > minX) edges.line(minX, busY, maxX, busY)
            for (d in drops) edges.line(d, busY, d, childY)
        }
    }

    private companion object {
        const val HEAD = -1
    }
}
