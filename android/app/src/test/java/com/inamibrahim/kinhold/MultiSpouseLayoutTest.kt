package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.tree.ChildGroup
import com.inamibrahim.kinhold.tree.TreeLayout
import com.inamibrahim.kinhold.tree.TreeMetrics
import com.inamibrahim.kinhold.tree.UnitNode
import com.inamibrahim.kinhold.tree.layoutFocus
import com.inamibrahim.kinhold.tree.layoutTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MultiSpouseLayoutTest {
    private fun p(id: Int, sex: Sex = Sex.Unknown, born: Int? = null) = Person(id, "P$id", "X", sex, born)

    /** Person 1 with three spouses (2, 3, 4 by birth year) and children of each, plus one child with no spouse. */
    private val big: Family = Family.of(
        people = listOf(
            p(1, Sex.Male, 1900), p(2, Sex.Female, 1902), p(3, Sex.Female, 1905), p(4, Sex.Female, 1910),
            p(5, born = 1925), p(6, born = 1927), p(7, born = 1930), p(8, born = 1935), p(9, born = 1940),
        ),
        parentLinks = listOf(5 to 1, 5 to 2, 6 to 1, 6 to 2, 7 to 1, 7 to 3, 8 to 1, 8 to 4, 9 to 1),
        partnerships = listOf(1 to 2, 1 to 3, 1 to 4),
    )

    private fun TreeLayout.box(id: Int) = people.first { it.person.id == id }

    private fun assertNoOverlaps(layout: TreeLayout, label: String = "") {
        for ((_, row) in layout.people.groupBy { it.y }) {
            val sorted = row.sortedBy { it.x }
            for (i in 1 until sorted.size) {
                assertTrue("Boxes overlap $label", sorted[i].x >= sorted[i - 1].x + TreeMetrics.NODE_W - 0.01f)
            }
        }
        for (b in layout.people) {
            assertTrue(b.x >= -0.01f && b.x + TreeMetrics.NODE_W <= layout.width + 0.01f)
            assertTrue(b.y >= -0.01f && b.y + TreeMetrics.NODE_H <= layout.height + 0.01f)
        }
        assertEquals(0, layout.edges.size % 4)
    }

    @Test
    fun everyoneIsPlacedExactlyOnceAndNothingOverlaps() {
        val layout = layoutFocus(big, 1)
        assertEquals(9, layout.people.size)
        assertEquals(9, layout.people.map { it.person.id }.toSet().size)
        assertNoOverlaps(layout)
    }

    @Test
    fun spousesStandOnBothSidesOfThePersonNearestFirst() {
        val layout = layoutFocus(big, 1)
        // By birth year the spouses are 2, 3, 4. The first goes right, the second left, the third further right.
        val order = listOf(3, 1, 2, 4).map { layout.box(it).x }
        assertEquals(order.sorted(), order)
        assertEquals(layout.box(1).y, layout.box(2).y, 0.01f)
        assertEquals(layout.box(1).y, layout.box(4).y, 0.01f)
        // The ones next to the person touch it with only the small gap between them.
        assertEquals(TreeMetrics.NODE_W + TreeMetrics.PARTNER_GAP, layout.box(2).x - layout.box(1).x, 0.01f)
        assertEquals(TreeMetrics.NODE_W + TreeMetrics.PARTNER_GAP, layout.box(1).x - layout.box(3).x, 0.01f)
    }

    @Test
    fun eachMarriagesChildrenStayTogetherInTheOrderOfTheirParents() {
        val layout = layoutFocus(big, 1)
        // Groups follow the boxes above: spouse 3's, then the person alone, then spouse 2's, then spouse 4's.
        val xs = listOf(7, 9, 5, 6, 8).map { layout.box(it).x }
        assertEquals(xs.sorted(), xs)
        for (child in listOf(5, 6, 7, 8, 9)) assertEquals(layout.box(1).y + TreeMetrics.ROW, layout.box(child).y, 0.01f)
    }

    @Test
    fun aSpouseNotNextToThePersonIsJoinedByALineUnderTheBoxes() {
        val layout = layoutFocus(big, 1)
        val headCenter = layout.box(1).x + TreeMetrics.NODE_W / 2
        val farCenter = layout.box(4).x + TreeMetrics.NODE_W / 2
        val depth = layout.box(1).y + TreeMetrics.NODE_H + TreeMetrics.LINK_DROP
        var found = false
        var i = 0
        while (i < layout.edges.size) {
            val (x1, y1, x2, y2) = layout.edges.slice(i until i + 4)
            if (y1 == depth && y2 == depth && minOf(x1, x2) == minOf(headCenter, farCenter) && maxOf(x1, x2) == maxOf(headCenter, farCenter)) found = true
            i += 4
        }
        assertTrue("Expected a line from the person to the third spouse under the boxes", found)
    }

    @Test
    fun theSecondMarriageIsDrawnWithTheFirstMarriageAndNotMixedUp() {
        val family = Family.of(
            listOf(p(1, Sex.Male), p(2, Sex.Female, 1900), p(3, Sex.Female, 1910), p(4), p(5)),
            listOf(4 to 1, 4 to 2, 5 to 1, 5 to 3),
            listOf(1 to 2, 1 to 3),
        )
        val layout = layoutFocus(family, 1)
        // Spouse 2 (older) is on the right, so the children of 2 are right of the children of 3.
        assertTrue(layout.box(2).x > layout.box(3).x)
        assertTrue(layout.box(4).x > layout.box(5).x)
    }

    @Test
    fun aMarriageWithoutChildrenAndAChildWithOnlyOneParentAreBothFine() {
        val family = Family.of(
            listOf(p(1, Sex.Male), p(2, Sex.Female), p(3, Sex.Female), p(4), p(5)),
            listOf(4 to 1, 5 to 1, 5 to 3),
            listOf(1 to 2, 1 to 3),
        )
        val layout = layoutFocus(family, 1)
        assertEquals(5, layout.people.size)
        assertNoOverlaps(layout)
    }

    @Test
    fun aPersonWithOneSpouseIsLaidOutAsBefore() {
        val family = Family.of(
            listOf(p(1, Sex.Male), p(2, Sex.Female), p(3)),
            listOf(3 to 1, 3 to 2),
            listOf(1 to 2),
        )
        val layout = layoutFocus(family, 1)
        assertEquals(TreeMetrics.NODE_W + TreeMetrics.PARTNER_GAP, layout.box(2).x - layout.box(1).x, 0.01f)
        // The child is centred under the couple.
        val coupleCentre = (layout.box(1).x + layout.box(2).x + TreeMetrics.NODE_W) / 2
        assertEquals(coupleCentre, layout.box(3).x + TreeMetrics.NODE_W / 2, 0.01f)
    }

    @Test
    fun childrenWhoAlsoHaveSeveralMarriagesStillNeverOverlap() {
        fun spouses(prefix: Int, n: Int) = (1..n).map { p(prefix + it, Sex.Female, 1900 + it) }
        val grandchildren = (1..3).map { UnitNode(p(100 + it)) }
        val a = UnitNode(p(10), spouses(20, 3), listOf(ChildGroup(null, grandchildren)))
        val b = UnitNode(p(11), spouses(30, 2), listOf(ChildGroup(p(31), grandchildren.map { UnitNode(p(it.head.id + 100)) })))
        val c = UnitNode(p(12))
        val root = UnitNode(p(1), listOf(p(2), p(3)), listOf(ChildGroup(p(2), listOf(a, b)), ChildGroup(p(3), listOf(c))))
        val layout = layoutTree(root)
        assertNoOverlaps(layout)
        assertEquals(layout.people.size, layout.people.map { it.person.id }.toSet().size)
    }

    @Test
    fun randomFamiliesWithManyMarriagesNeverOverlapForAnyFocus() {
        val rnd = Random(2026)
        repeat(12) { round ->
            val n = 40 + rnd.nextInt(40)
            val people = (1..n).map { p(it, if (rnd.nextBoolean()) Sex.Male else Sex.Female, 1800 + rnd.nextInt(200)) }
            val links = HashSet<Pair<Int, Int>>()
            val couples = HashSet<Pair<Int, Int>>()
            repeat(n) {
                val a = 1 + rnd.nextInt(n)
                val b = 1 + rnd.nextInt(n)
                if (a != b) couples += minOf(a, b) to maxOf(a, b)
            }
            // Children have one or two parents, who are older people, so the family has no loops.
            for (child in 1..n) {
                val older = people.filter { it.born!! + 18 < people[child - 1].born!! }
                if (older.isEmpty()) continue
                val first = older[rnd.nextInt(older.size)].id
                links += child to first
                if (rnd.nextInt(3) > 0) links += child to older[rnd.nextInt(older.size)].id
            }
            val family = Family.of(people, links.toList(), couples.toList())
            for (focus in 1..n) {
                val layout = layoutFocus(family, focus)
                assertNoOverlaps(layout, "(round $round, focus $focus)")
                assertTrue(layout.people.any { it.person.id == focus })
            }
        }
    }

    @Test
    fun layoutStaysFastWithManyMarriages() {
        val start = System.nanoTime()
        repeat(200) { layoutFocus(big, 1) }
        val ms = (System.nanoTime() - start) / 1_000_000.0 / 200
        assertTrue("Took $ms ms per layout", ms < 10.0)
    }
}
