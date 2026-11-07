package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Anchor
import com.inamibrahim.kinhold.model.AttachKind
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Merge
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TreeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorTest {
    private val uidFather = "11111111-1111-4111-8111-111111111111"
    private val uidSon = "22222222-2222-4222-8222-222222222222"
    private val uidMe = "33333333-3333-4333-8333-333333333333"

    private fun p(id: Int, given: String, sex: Sex = Sex.Unknown, born: Int? = null, uid: String = "") =
        Person(id, given, "Raza", sex, born = born, uid = uid)

    // My tree: me (1), my mother (2) and my sister (3).
    private val mine = Family.of(
        listOf(p(1, "Inam", Sex.Male, 1990, uidMe), p(2, "Hina", Sex.Female, 1960), p(3, "Sara", Sex.Female, 1992)),
        listOf(1 to 2, 3 to 2),
        emptyList(),
    )

    // His file: the sender (1, "Ahmed", not in my tree under that name), his son (2) and his father (3).
    private fun file(rootUid: String = uidFather) = BackupData(
        listOf(p(1, "Ahmed", Sex.Male, 1955, uidFather), p(2, "Zain", Sex.Male, 1998, uidSon), p(3, "Nasir", Sex.Male, 1920)),
        listOf(2 to 1, 1 to 3),
        emptyList(),
        rootUid = rootUid,
    )

    private fun merge(data: BackupData, family: Family = mine) = Merge(family, TreeDetails.Empty, data)

    @Test
    fun theRootFieldSurvivesAFileAndIsDroppedWhenItNamesNoOne() {
        val text = BackupCodec.encode(Family.of(file().people, file().parentLinks, emptyList()), "now", rootUid = uidFather)
        assertEquals(uidFather, BackupCodec.decode(text).rootUid)
        val without = BackupCodec.encode(Family.of(file().people, file().parentLinks, emptyList()), "now")
        assertEquals("", BackupCodec.decode(without).rootUid)
        val stray = BackupCodec.encode(Family.of(file().people, file().parentLinks, emptyList()), "now", rootUid = "44444444-4444-4444-8444-444444444444")
        assertEquals("", BackupCodec.decode(stray).rootUid)
    }

    @Test
    fun theFileSaysWhoItsMainPersonIs() {
        assertEquals(1, merge(file()).rootIncomingId)
        assertEquals(null, merge(file(rootUid = "")).rootIncomingId)
    }

    @Test
    fun choosingWhoTheyAreOverridesAnyAutomaticMatch() {
        // Ahmed is "Hina" in my tree by the person's own say-so, even though nothing matched automatically.
        val m = merge(file())
        assertTrue(m.matchesFor(emptySet()).isEmpty())
        val r = m.resolve(emptySet(), emptySet(), true, Anchor.Same(2))
        assertEquals(2, r.matches[1])
        // Zain becomes a child of the chosen person, Nasir a parent of them.
        assertTrue(r.links.contains(-2 to 2))
        assertTrue(r.links.contains(2 to -3))
        assertEquals(listOf(2, 3), r.newPeople)
    }

    @Test
    fun aChosenMatchReplacesTheAutomaticOneForBothSides() {
        // The file's Ahmed shares an id with my "Inam"; the person says he is Hina instead.
        val data = BackupData(listOf(p(1, "Ahmed", uid = uidMe)), emptyList(), emptyList(), rootUid = uidMe)
        val m = merge(data)
        assertEquals(mapOf(1 to 1), m.matchesFor(emptySet()))
        assertEquals(mapOf(1 to 2), m.matchesFor(emptySet(), Anchor.Same(2)))
    }

    @Test
    fun aNewMainPersonCanBeAttachedAsParentChildSpouseOrSibling() {
        val m = merge(file())
        fun attach(kind: AttachKind) = m.resolve(emptySet(), emptySet(), true, Anchor.Attach(1, kind))
        // Ahmed (new, -1) is a parent of Inam (1), so Inam gets the link (child 1, parent -1).
        assertTrue(attach(AttachKind.Parent).links.contains(1 to -1))
        // ... a child of Inam: (child -1, parent 1).
        assertTrue(attach(AttachKind.Child).links.contains(-1 to 1))
        // ... Inam's spouse.
        assertTrue(attach(AttachKind.Spouse).partnerships.contains(-1 to 1))
        // ... Inam's sibling: a child of Inam's parent (2).
        assertTrue(attach(AttachKind.Sibling).links.contains(-1 to 2))
    }

    @Test
    fun attachingDoesNothingWhenTheMainPersonIsAlreadyMatched() {
        val data = BackupData(listOf(p(1, "Ahmed", uid = uidMe)), emptyList(), emptyList(), rootUid = uidMe)
        val r = merge(data).resolve(emptySet(), emptySet(), true, Anchor.Attach(2, AttachKind.Child))
        assertTrue(r.links.isEmpty())
        assertEquals(1, r.matches[1])
    }

    @Test
    fun anAttachedParentRespectsTheFourParentLimit() {
        val full = Family.of(
            listOf(p(1, "Kid"), p(2, "A"), p(3, "B"), p(4, "C"), p(5, "D")),
            listOf(1 to 2, 1 to 3, 1 to 4, 1 to 5),
            emptyList(),
        )
        val r = merge(file(), full).resolve(emptySet(), emptySet(), true, Anchor.Attach(1, AttachKind.Parent))
        assertTrue(r.links.none { it == 1 to -1 })
        assertEquals(1, r.skippedParentLinks)
    }

    @Test
    fun withoutAnAnchorNothingChangesFromBefore() {
        val r = merge(file()).resolve(emptySet(), emptySet(), true)
        assertEquals(listOf(1, 2, 3), r.newPeople)
        assertEquals(2, r.links.size)
    }
}
