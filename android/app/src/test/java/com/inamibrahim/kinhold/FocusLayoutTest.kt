package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.TestFamily
import com.inamibrahim.kinhold.tree.TreeMetrics
import com.inamibrahim.kinhold.tree.layoutFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLayoutTest {
    private val family = TestFamily.generateFamily(1000)

    @Test
    fun familyKnowsParentsSpousesAndChildren() {
        val focus = family.defaultFocus()!!
        assertTrue(family.parentsOf(focus.id).isNotEmpty())
        assertTrue(family.childrenOf(focus.id).isNotEmpty())
        // Every child lists its parent, and every parent lists the child.
        for (child in family.childrenOf(focus.id)) {
            assertTrue(family.parentsOf(child.id).any { it.id == focus.id })
        }
    }

    @Test
    fun theFocusedPersonIsDrawnOnceInTheMiddleRow() {
        val focus = family.defaultFocus()!!
        val layout = layoutFocus(family, focus.id)
        val boxes = layout.people.filter { it.person.id == focus.id }
        assertEquals(1, boxes.size)
        assertEquals(layout.focusY, boxes[0].y + TreeMetrics.NODE_H / 2, 0.01f)
        assertEquals(layout.focusX, boxes[0].x + TreeMetrics.NODE_W / 2, 0.01f)
    }

    @Test
    fun ancestorsAreAboveAndDescendantsBelow() {
        val focus = family.defaultFocus()!!
        val layout = layoutFocus(family, focus.id)
        val focusBox = layout.people.first { it.person.id == focus.id }
        for (p in family.parentsOf(focus.id)) {
            val box = layout.people.first { it.person.id == p.id }
            assertTrue("A parent should sit above", box.y < focusBox.y)
        }
        for (c in family.childrenOf(focus.id)) {
            val box = layout.people.first { it.person.id == c.id }
            assertTrue("A child should sit below", box.y > focusBox.y)
        }
    }

    @Test
    fun nobodyOverlapsInARow() {
        for (id in family.people.take(200).map { it.id }) {
            val layout = layoutFocus(family, id)
            for ((_, row) in layout.people.groupBy { it.y }) {
                val sorted = row.sortedBy { it.x }
                for (i in 1 until sorted.size) {
                    assertTrue("Boxes overlap for focus $id", sorted[i].x >= sorted[i - 1].x + TreeMetrics.NODE_W - 0.01f)
                }
            }
        }
    }

    @Test
    fun everyBoxStaysInsideTheReportedSize() {
        for (id in family.people.take(200).map { it.id }) {
            val layout = layoutFocus(family, id)
            for (p in layout.people) {
                assertTrue(p.x >= -0.01f && p.x + TreeMetrics.NODE_W <= layout.width + 0.01f)
                assertTrue(p.y >= -0.01f && p.y + TreeMetrics.NODE_H <= layout.height + 0.01f)
            }
        }
    }

    @Test
    fun theViewStaysSmallEvenForAHugeTree() {
        val big = TestFamily.generateFamily(3000)
        val layout = layoutFocus(big, big.defaultFocus()!!.id)
        // Three rows up and two down: a few dozen people, not thousands.
        assertTrue("Drew ${layout.people.size} people", layout.people.size < 120)
        assertTrue(layout.generations <= 6)
    }

    @Test
    fun peopleBeyondTheEdgeAreFlagged() {
        val focus = family.defaultFocus()!!
        val layout = layoutFocus(family, focus.id, ancestorDepth = 1, descendantDepth = 1)
        // With one row each way, grandchildren exist but are not drawn.
        assertTrue(layout.people.any { it.moreBelow > 0 })
        assertTrue(layout.people.any { it.moreAbove })
    }

    @Test
    fun noOneIsMarkedMoreBelowWhenAllChildrenAreShown() {
        val focus = family.defaultFocus()!!
        val layout = layoutFocus(family, focus.id, descendantDepth = 2)
        val box = layout.people.first { it.person.id == focus.id }
        assertEquals(0, box.moreBelow)
    }

    @Test
    fun searchFindsPeopleByPartOfTheName() {
        val sample = family.people.first()
        val hits = family.search(sample.name.substring(1, 4))
        assertTrue(hits.any { it.id == sample.id })
        assertTrue(family.search("   ").isEmpty())
        assertFalse(family.search("zzzz-not-a-name").isNotEmpty())
    }

    @Test
    fun layoutIsFastForALargeFamily() {
        val big = TestFamily.generateFamily(3000)
        val start = System.nanoTime()
        repeat(50) { layoutFocus(big, big.defaultFocus()!!.id) }
        val ms = (System.nanoTime() - start) / 1_000_000.0 / 50
        assertTrue("Took $ms ms per layout", ms < 25.0)
    }
}
