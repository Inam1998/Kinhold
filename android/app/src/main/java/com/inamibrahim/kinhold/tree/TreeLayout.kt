package com.inamibrahim.kinhold.tree

import com.inamibrahim.kinhold.model.FamilyUnit
import com.inamibrahim.kinhold.model.Person
import java.util.IdentityHashMap

/** Sizes of the tree, in dp at 100% zoom. */
object TreeMetrics {
    const val NODE_W = 132f
    const val NODE_H = 56f
    const val PARTNER_GAP = 10f
    const val SIBLING_GAP = 24f
    const val ROW_GAP = 72f
    const val ROW = NODE_H + ROW_GAP
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

/**
 * Lays out a family from its oldest couple down. Each unit gets as much width as its descendants need and is centred
 * over them, so nothing in a row can overlap. Costs one pass over the family.
 */
fun layoutTree(root: FamilyUnit): TreeLayout = TreeBuilder().build(root)

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
    private val widths = IdentityHashMap<FamilyUnit, Float>()
    private var deepest = 0

    private fun unitWidth(u: FamilyUnit): Float =
        u.partners.size * TreeMetrics.NODE_W + (u.partners.size - 1) * TreeMetrics.PARTNER_GAP

    private fun childrenWidth(u: FamilyUnit): Float =
        if (u.children.isEmpty()) 0f else u.children.sumOf { width(it).toDouble() }.toFloat() + (u.children.size - 1) * TreeMetrics.SIBLING_GAP

    private fun width(u: FamilyUnit): Float = widths.getOrPut(u) { maxOf(unitWidth(u), childrenWidth(u)) }

    fun build(root: FamilyUnit): TreeLayout {
        place(root, 0f, 0)
        return TreeLayout(
            people = placed,
            edges = edges.toArray(),
            width = width(root),
            height = deepest * TreeMetrics.ROW + TreeMetrics.NODE_H,
            generations = deepest + 1,
        )
    }

    private fun place(u: FamilyUnit, left: Float, generation: Int) {
        deepest = maxOf(deepest, generation)
        val sub = width(u)
        val unitW = unitWidth(u)
        val unitLeft = left + (sub - unitW) / 2
        val y = generation * TreeMetrics.ROW
        u.partners.forEachIndexed { i, p ->
            placed += PlacedPerson(p, unitLeft + i * (TreeMetrics.NODE_W + TreeMetrics.PARTNER_GAP), y, generation)
        }
        if (u.partners.size == 2) {
            val mid = y + TreeMetrics.NODE_H / 2
            edges.line(unitLeft + TreeMetrics.NODE_W, mid, unitLeft + TreeMetrics.NODE_W + TreeMetrics.PARTNER_GAP, mid)
        }
        if (u.children.isEmpty()) return

        val anchorX = unitLeft + unitW / 2
        val startY = if (u.partners.size == 2) y + TreeMetrics.NODE_H / 2 else y + TreeMetrics.NODE_H
        val busY = y + TreeMetrics.NODE_H + TreeMetrics.ROW_GAP / 2
        val childY = y + TreeMetrics.ROW
        var cursor = left + (sub - childrenWidth(u)) / 2
        var minX = anchorX
        var maxX = anchorX
        val drops = FloatArray(u.children.size)
        u.children.forEachIndexed { i, c ->
            val cw = width(c)
            val childUnitLeft = cursor + (cw - unitWidth(c)) / 2
            val dropX = childUnitLeft + TreeMetrics.NODE_W / 2
            drops[i] = dropX
            minX = minOf(minX, dropX)
            maxX = maxOf(maxX, dropX)
            place(c, cursor, generation + 1)
            cursor += cw + TreeMetrics.SIBLING_GAP
        }
        edges.line(anchorX, startY, anchorX, busY)
        if (maxX > minX) edges.line(minX, busY, maxX, busY)
        for (dropX in drops) edges.line(dropX, busY, dropX, childY)
    }
}
