package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.TestFamily
import com.inamibrahim.kinhold.tree.TreeMetrics
import com.inamibrahim.kinhold.tree.layoutTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeLayoutTest {
    @Test
    fun generatorMakesExactlyTheRequestedNumberOfPeople() {
        for (n in listOf(2, 10, 60, 300, 1000, 3000)) {
            assertEquals(n, layoutTree(TestFamily.generate(n)).people.size)
        }
    }

    @Test
    fun everyoneHasAUniqueId() {
        val ids = layoutTree(TestFamily.generate(1000)).people.map { it.person.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun nobodyOverlapsInARow() {
        val layout = layoutTree(TestFamily.generate(1000))
        for ((_, row) in layout.people.groupBy { it.y }) {
            val sorted = row.sortedBy { it.x }
            for (i in 1 until sorted.size) {
                assertTrue(
                    "Boxes overlap in a row",
                    sorted[i].x >= sorted[i - 1].x + TreeMetrics.NODE_W - 0.01f,
                )
            }
        }
    }

    @Test
    fun rowsAreOneGenerationApart() {
        val layout = layoutTree(TestFamily.generate(300))
        for (p in layout.people) assertEquals(p.generation * TreeMetrics.ROW, p.y, 0.01f)
        assertTrue(layout.generations >= 4)
    }

    @Test
    fun everyBoxStaysInsideTheReportedSize() {
        val layout = layoutTree(TestFamily.generate(1000))
        for (p in layout.people) {
            assertTrue(p.x >= -0.01f && p.x + TreeMetrics.NODE_W <= layout.width + 0.01f)
            assertTrue(p.y + TreeMetrics.NODE_H <= layout.height + 0.01f)
        }
    }

    @Test
    fun theSameSeedGivesTheSameTree() {
        val a = layoutTree(TestFamily.generate(500, seed = 3))
        val b = layoutTree(TestFamily.generate(500, seed = 3))
        assertEquals(a.width, b.width, 0f)
        assertEquals(a.edges.size, b.edges.size)
    }

    @Test
    fun edgesComeInFours() {
        assertEquals(0, layoutTree(TestFamily.generate(300)).edges.size % 4)
    }
}
