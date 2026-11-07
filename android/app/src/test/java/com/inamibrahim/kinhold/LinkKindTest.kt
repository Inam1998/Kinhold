package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupException
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.LinkKind
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Relationships
import com.inamibrahim.kinhold.model.Share
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TreeDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LinkKindTest {
    private fun p(id: Int, sex: Sex = Sex.Unknown) = Person(id, "P$id", "", sex)

    // Kid 1 has a birth mother 2, an adoptive father 3, a foster mother 4 and a step-father 5.
    private val family = Family.of(
        (1..5).map { p(it, if (it in listOf(2, 4)) Sex.Female else Sex.Male) },
        listOf(1 to 2, 1 to 3, 1 to 4, 1 to 5),
        emptyList(),
        linkKinds = mapOf((1 to 3) to LinkKind.Adopted, (1 to 4) to LinkKind.Foster, (1 to 5) to LinkKind.Step),
    )

    @Test
    fun aLinkIsABirthLinkUnlessAnotherKindWasRecorded() {
        assertEquals(LinkKind.Birth, family.kindOf(1, 2))
        assertEquals(LinkKind.Adopted, family.kindOf(1, 3))
        assertEquals(LinkKind.Foster, family.kindOf(1, 4))
        assertEquals(setOf(2, 3), family.lineageParentsOf(1).map { it.id }.toSet())
        assertEquals(4, family.parentsOf(1).size)
    }

    @Test
    fun aKindForALinkThatDoesNotExistIsIgnored() {
        val f = Family.of(listOf(p(1), p(2)), emptyList(), emptyList(), linkKinds = mapOf((1 to 2) to LinkKind.Foster))
        assertTrue(f.linkKinds().isEmpty())
    }

    @Test
    fun fosterAndStepParentsAreNotAncestorsButAdoptiveOnesAre() {
        assertEquals("Father", Relationships.between(family, 1, 3)!!.english) // adoptive
        assertEquals("Mother", Relationships.between(family, 1, 2)!!.english)
        assertEquals(null, Relationships.between(family, 1, 4)) // foster: no ancestry
        assertEquals(null, Relationships.between(family, 1, 5))
    }

    @Test
    fun kindsSurviveABackup() {
        val data = BackupCodec.decode(BackupCodec.encode(family, ""))
        assertEquals(family.linkKinds(), data.linkKinds)
        assertEquals(4, data.parentLinks.size)
    }

    @Test
    fun oldBackupsHaveOnlyBirthLinksAndABadKindIsDamage() {
        val v7 = """{"format":"kinhold-backup","version":7,"people":[{"id":1,"given":"A"},{"id":2,"given":"B"}],"parentLinks":[[1,2]],"partnerships":[]}"""
        assertTrue(BackupCodec.decode(v7).linkKinds.isEmpty())
        try {
            BackupCodec.decode(v7.replace("[[1,2]]", "[[1,2,9]]"))
            fail("Expected a refusal")
        } catch (e: BackupException) {
            assertEquals("This backup is damaged and cannot be read.", e.message)
        }
    }

    @Test
    fun kindsSurviveGedcomAsPedigreeTypes() {
        val f = Family.of(
            listOf(p(1), p(2, Sex.Male), p(3, Sex.Male), p(4, Sex.Male)),
            listOf(1 to 2, 1 to 3, 1 to 4), emptyList(),
            linkKinds = mapOf((1 to 2) to LinkKind.Adopted, (1 to 3) to LinkKind.Foster),
        )
        val text = Gedcom.write(f, TreeDetails.Empty, "1.0", "7 NOV 2026")
        assertTrue(text.contains("2 PEDI adopted"))
        assertTrue(text.contains("2 PEDI foster"))
        val data = Gedcom.read(text.toByteArray()).data
        assertEquals(LinkKind.Adopted, data.linkKinds[1 to 2])
        assertEquals(LinkKind.Foster, data.linkKinds[1 to 3])
        assertEquals(null, data.linkKinds[1 to 4])
    }

    @Test
    fun gedcomPedigreeTypesFromOtherProgramsAreRead() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME Kid /X/\n1 FAMC @F1@\n2 PEDI adopted\n0 @I2@ INDI\n1 NAME Dad /X/\n0 @I3@ INDI\n1 NAME Mum /X/\n" +
            "0 @F1@ FAM\n1 HUSB @I2@\n1 WIFE @I3@\n1 CHIL @I1@\n0 TRLR"
        val data = Gedcom.read(text.toByteArray()).data
        assertEquals(setOf(1 to 2, 1 to 3), data.linkKinds.keys)
        assertTrue(data.linkKinds.values.all { it == LinkKind.Adopted })
    }

    @Test
    fun aSharedBranchKeepsTheKindsOfLinksInsideIt() {
        val sub = Share.subFamily(family, setOf(1, 3, 4))
        assertEquals(setOf(1 to 3, 1 to 4), sub.linkKinds().keys)
    }
}
