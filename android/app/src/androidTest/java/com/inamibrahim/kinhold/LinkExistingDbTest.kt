package com.inamibrahim.kinhold

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inamibrahim.kinhold.data.FamilyRepository
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.LinkKind
import com.inamibrahim.kinhold.model.LinkType
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on the phone: linking and unlinking people already in the tree, with the rules enforced inside the change. */
@RunWith(AndroidJUnit4::class)
class LinkExistingDbTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = ArrayList<File>()

    @After
    fun cleanUp() {
        files.forEach { it.delete() }
    }

    private fun repo() = FamilyRepository(context, null, File(context.cacheDir, "link-safety-${System.nanoTime()}.json").also { files += it }).also { runBlocking { it.load() } }

    @Test
    fun aParentAChildAndAPartnerCanBeLinkedAfterTheFact() = runBlocking {
        val r = repo()
        val kid = r.addPerson(PersonDraft("Kid"))
        val mum = r.addPerson(PersonDraft("Mum"))
        val dad = r.addPerson(PersonDraft("Dad"))
        r.linkExisting(kid, mum, LinkType.Parent)
        r.linkExisting(kid, dad, LinkType.Parent, LinkKind.Adopted)
        val f = r.family.value
        assertEquals(setOf(mum, dad), f.parentsOf(kid).map { it.id }.toSet())
        assertEquals(LinkKind.Adopted, f.kindOf(kid, dad))
        // A second birth parent would have made a couple; an adoptive one does not.
        assertTrue(f.spousesOf(mum).isEmpty())
        val other = r.addPerson(PersonDraft("Grandkid"))
        r.linkExisting(kid, other, LinkType.Child)
        r.linkExisting(mum, dad, LinkType.Spouse)
        assertEquals(listOf(other), r.family.value.childrenOf(kid).map { it.id })
        assertEquals(listOf(dad), r.family.value.spousesOf(mum).map { it.id })
    }

    @Test
    fun twoBirthParentsOfOneChildBecomeACouple() = runBlocking {
        val r = repo()
        val kid = r.addPerson(PersonDraft("Kid"))
        val a = r.addPerson(PersonDraft("A"))
        val b = r.addPerson(PersonDraft("B"))
        r.linkExisting(kid, a, LinkType.Parent)
        r.linkExisting(kid, b, LinkType.Parent)
        assertEquals(listOf(b), r.family.value.spousesOf(a).map { it.id })
    }

    @Test
    fun aLinkThatBreaksTheRulesIsRefusedAndChangesNothing() = runBlocking {
        val r = repo()
        val gran = r.addPerson(PersonDraft("Gran"))
        val mum = r.addPerson(PersonDraft("Mum"))
        r.linkExisting(mum, gran, LinkType.Parent)
        val before = r.family.value.parentLinks()
        try {
            r.linkExisting(gran, mum, LinkType.Parent)
            fail("a daughter cannot be her mother's parent")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotBlank())
        }
        assertEquals(before, r.family.value.parentLinks())
    }

    @Test
    fun unlinkingRemovesOnlyTheLink() = runBlocking {
        val r = repo()
        val kid = r.addPerson(PersonDraft("Kid"))
        val mum = r.addPerson(PersonDraft("Mum"))
        r.linkExisting(kid, mum, LinkType.Parent)
        r.unlinkParent(kid, mum)
        val f = r.family.value
        assertEquals(2, f.size)
        assertTrue(f.parentsOf(kid).isEmpty())
    }

    @Test
    fun aPartnershipWithAMarriageEventCannotBeUnlinkedUntilTheEventIsGone() = runBlocking {
        val r = repo()
        val a = r.addPerson(PersonDraft("A"))
        val b = r.addPerson(PersonDraft("B"))
        r.linkExisting(a, b, LinkType.Spouse)
        r.updatePerson(a, PersonDraft("A"), details = PersonDetails(coupleEvents = mapOf(b to listOf(Event(EventKind.Marriage, date = PartialDate(1975))))))
        assertFalse(r.unlinkPartners(a, b))
        assertEquals(1, r.family.value.spousesOf(a).size)
        assertEquals(1, r.details(a).coupleEvents.getValue(b).size)
        r.updatePerson(a, PersonDraft("A"), details = PersonDetails(coupleEvents = mapOf(b to emptyList())))
        assertTrue(r.unlinkPartners(a, b))
        assertTrue(r.family.value.spousesOf(a).isEmpty())
    }
}
