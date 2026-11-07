package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.GedcomException
import com.inamibrahim.kinhold.model.GedcomResult
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

class GedcomTest {
    private val sample = """
        0 HEAD
        1 SOUR TEST
        1 GEDC
        2 VERS 5.5.1
        1 CHAR UTF-8
        0 @I1@ INDI
        1 NAME John /Smith/ Jr
        2 GIVN John
        1 SEX M
        1 BIRT
        2 DATE 12 JAN 1950
        2 PLAC Lahore, Punjab, Pakistan
        1 DEAT
        2 DATE BET 2000 AND 2005
        1 OCCU Farmer
        1 RESI
        2 ADDR somewhere
        1 SOUR @S1@
        1 OBJE
        1 FAMS @F1@
        1 FAMS @F2@
        0 @I2@ INDI
        1 NAME Mary /Jones/
        1 SEX F
        1 BIRT
        2 DATE ABT 1952
        1 NOTE First line
        2 CONT Second line
        2 CONC  continued
        1 FAMS @F1@
        0 @I3@ INDI
        1 NAME Child /Smith/
        1 FAMC @F1@
        0 @I4@ INDI
        1 NAME Anna /Brown/
        1 SEX F
        1 FAMS @F2@
        0 @I5@ INDI
        1 NAME Late /Smith/
        1 SEX U
        1 FAMC @F2@
        0 @F1@ FAM
        1 HUSB @I1@
        1 WIFE @I2@
        1 CHIL @I3@
        1 MARR
        2 DATE 1975
        0 @F2@ FAM
        1 HUSB @I1@
        1 WIFE @I4@
        1 CHIL @I5@
        1 CHIL @I99@
        0 TRLR
    """.trimIndent()

    private fun read(text: String) = Gedcom.read(text.toByteArray(Charsets.UTF_8))

    private fun refused(bytes: ByteArray): GedcomException {
        try {
            Gedcom.read(bytes)
        } catch (e: GedcomException) {
            return e
        }
        fail("Expected the file to be refused")
        throw AssertionError()
    }

    private fun family(result: GedcomResult) = Family.of(result.data.people, result.data.parentLinks, result.data.partnerships)

    // ---------------------------------------------------------------- reading

    @Test
    fun readsPeopleNamesSexYearsPlacesAndNotes() {
        val r = read(sample)
        val byName = r.data.people.associateBy { it.given + "|" + it.surname }
        assertEquals(5, r.data.people.size)
        val john = byName.getValue("John|Smith Jr")
        assertEquals(Sex.Male, john.sex)
        assertEquals(1950, john.born)
        assertEquals(2000, john.died)
        assertEquals("Lahore, Punjab, Pakistan", john.birthPlace)
        val mary = byName.getValue("Mary|Jones")
        assertEquals(Sex.Female, mary.sex)
        assertEquals(1952, mary.born)
        assertEquals("First line\nSecond line continued", mary.notes)
        assertEquals(Sex.Unknown, byName.getValue("Late|Smith").sex)
        assertNull(byName.getValue("Child|Smith").born)
    }

    @Test
    fun readsParentsAndSeveralMarriages() {
        val f = family(read(sample))
        val john = f.people.first { it.given == "John" }
        assertEquals(setOf("Mary", "Anna"), f.spousesOf(john.id).map { it.given }.toSet())
        assertEquals(setOf("John", "Mary"), f.parentsOf(f.people.first { it.given == "Child" }.id).map { it.given }.toSet())
        assertEquals(setOf("John", "Anna"), f.parentsOf(f.people.first { it.given == "Late" }.id).map { it.given }.toSet())
        assertEquals(listOf("Child", "Late"), f.childrenOf(john.id).map { it.given }.sorted())
    }

    @Test
    fun theReportCountsWhatWasKeptAndWhatWasNot() {
        val report = read(sample).report
        assertEquals(5, report.individuals)
        assertEquals(2, report.families)
        assertEquals(3, report.datesReducedToYear) // 12 JAN 1950, BET 2000 AND 2005, ABT 1952
        assertEquals(3, report.ignoredEvents)      // OCCU, RESI and the marriage
        assertEquals(1, report.ignoredSources)
        assertEquals(1, report.ignoredMedia)
        assertEquals(1, report.skippedLinks)       // the child @I99@ is not in the file
    }

    @Test
    fun theSummaryIsWrittenInPlainSentences() {
        val text = read(sample).report.summary(currentPeople = 12).joinToString("\n")
        assertTrue(text.contains("5 people in 2 families"))
        assertTrue(text.contains("Your tree has 12 people"))
        assertTrue(text.contains("Only the year is kept"))
        assertTrue(text.contains("Not kept: 3 events"))
        assertTrue(text.contains("1 link to a person missing from the file was skipped."))
        assertTrue(text.contains("1 source citation,"))
        assertTrue(text.contains("1 photo or file."))
        assertFalse(text.contains("1 source citations"))
        val clean = read("0 HEAD\n0 @I1@ INDI\n1 NAME A /B/\n0 TRLR").report.summary(0).joinToString("\n")
        assertTrue(clean.contains("Everything in the file can be kept."))
        assertFalse(clean.contains("Your tree has"))
    }

    @Test
    fun datesInEveryCommonStyleBecomeYears() {
        fun yearOf(date: String): Int? =
            read("0 HEAD\n0 @I1@ INDI\n1 NAME A /B/\n1 BIRT\n2 DATE $date\n0 TRLR").data.people.single().born
        assertEquals(1950, yearOf("1950"))
        assertEquals(1950, yearOf("JAN 1950"))
        assertEquals(1950, yearOf("5 jan 1950"))
        assertEquals(1950, yearOf("ABT 1950"))
        assertEquals(1950, yearOf("EST 1950"))
        assertEquals(1950, yearOf("BEF 1950"))
        assertEquals(1900, yearOf("BET 1900 AND 1910"))
        assertEquals(1900, yearOf("FROM 1900 TO 1910"))
        assertEquals(1950, yearOf("1950/51"))
        assertEquals(1950, yearOf("(about 1950)"))
        assertEquals(950, yearOf("950"))
        assertNull(yearOf("Unknown"))
        assertNull(yearOf("44 BC"))
        assertNull(yearOf(""))
    }

    @Test
    fun namesInEveryCommonStyleAreSplit() {
        fun nameOf(line: String): Pair<String, String> {
            val p = read("0 HEAD\n0 @I1@ INDI\n$line\n0 TRLR").data.people.single()
            return p.given to p.surname
        }
        assertEquals("Anna Maria" to "Khan", nameOf("1 NAME Anna Maria /Khan/"))
        assertEquals("" to "Khan", nameOf("1 NAME /Khan/"))
        assertEquals("Anna" to "", nameOf("1 NAME Anna"))
        assertEquals("Anna" to "Khan Jr", nameOf("1 NAME Anna /Khan/ Jr"))
        assertEquals("Anna" to "Khan", nameOf("1 NAME Wrong /Name/\n2 GIVN Anna\n2 SURN Khan"))
        assertEquals("" to "", nameOf("1 SEX F"))
        assertEquals("Anna" to "Khan", nameOf("1 NAME   Anna    /Khan/  "))
    }

    @Test
    fun aNoteCanBeASeparateRecordAndLongTextIsJoined() {
        val text = "0 HEAD\n0 @N1@ NOTE The shared note\n1 CONT with a second line\n" +
            "0 @I1@ INDI\n1 NAME A /B/\n1 NOTE @N1@\n1 NOTE Another\n2 CONC , joined\n0 TRLR"
        assertEquals("The shared note\nwith a second line\n\nAnother, joined", read(text).data.people.single().notes)
    }

    @Test
    fun doubledAtSignsInTextAreOneAtSign() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME A /B/\n1 NOTE mail me @@ home\n0 TRLR"
        assertEquals("mail me @ home", read(text).data.people.single().notes)
    }

    @Test
    fun linesEndedInAnyWayAreRead() {
        val lines = sample.lines()
        for (separator in listOf("\n", "\r\n", "\r")) {
            assertEquals(5, read(lines.joinToString(separator)).data.people.size)
        }
    }

    @Test
    fun blankLinesAndGarbageLinesAreSkipped() {
        val text = "\n\n0 HEAD\nthis is not a gedcom line\n\n0 @I1@ INDI\n   1 NAME A /B/   \n!!!\n0 TRLR\n\n"
        assertEquals("A", read(text).data.people.single().given)
    }

    @Test
    fun unknownTagsAndPrivateTagsDoNotBreakAnything() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME A /B/\n1 _CUSTOM stuff\n2 _MORE x\n1 FANCY thing\n0 _EXT @X1@ more\n0 TRLR"
        val r = read(text)
        assertEquals(1, r.data.people.size)
        assertEquals(1, r.report.ignoredOther) // FANCY; tags starting with _ are the program's own and not counted
    }

    @Test
    fun aChildListedOnlyUnderItsOwnFamcStillGetsItsParents() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME Dad /X/\n1 SEX M\n0 @I2@ INDI\n1 NAME Kid /X/\n1 FAMC @F1@\n" +
            "0 @F1@ FAM\n1 HUSB @I1@\n0 TRLR"
        val f = family(read(text))
        assertEquals(listOf("Dad"), f.parentsOf(f.people.first { it.given == "Kid" }.id).map { it.given })
    }

    @Test
    fun voidPointersAreIgnoredWithoutComplaint() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME A /B/\n0 @F1@ FAM\n1 HUSB @VOID@\n1 WIFE @I1@\n1 CHIL @VOID@\n0 TRLR"
        assertEquals(0, read(text).report.skippedLinks)
    }

    // ---------------------------------------------------------------- character sets

    @Test
    fun readsUtf8WithAndWithoutAByteOrderMark() {
        val text = sample.replace("Lahore", "Lähore Zürich")
        val plain = Gedcom.read(text.toByteArray(Charsets.UTF_8))
        val bom = Gedcom.read(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8))
        for (r in listOf(plain, bom)) assertEquals("Lähore Zürich, Punjab, Pakistan", r.data.people.first { it.given == "John" }.birthPlace)
    }

    @Test
    fun readsUtf16WithAndWithoutAByteOrderMark() {
        val text = sample.replace("1 CHAR UTF-8", "1 CHAR UNICODE").replace("Lahore", "Lähore")
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        for (bytes in listOf(le, be, text.toByteArray(Charsets.UTF_16LE), text.toByteArray(Charsets.UTF_16BE))) {
            val r = Gedcom.read(bytes)
            assertEquals(5, r.data.people.size)
            assertEquals("Lähore, Punjab, Pakistan", r.data.people.first { it.given == "John" }.birthPlace)
        }
    }

    @Test
    fun readsAnsiFilesAsWindows1252() {
        val text = "0 HEAD\n1 CHAR ANSI\n0 @I1@ INDI\n1 NAME José /Müller/\n0 TRLR"
        val p = Gedcom.read(text.toByteArray(charset("windows-1252"))).data.people.single()
        assertEquals("José", p.given)
        assertEquals("Müller", p.surname)
    }

    @Test
    fun aFileThatClaimsNothingButIsNotUtf8IsReadAsWindows1252() {
        val text = "0 HEAD\n0 @I1@ INDI\n1 NAME José /Müller/\n0 TRLR"
        assertEquals("José", Gedcom.read(text.toByteArray(charset("windows-1252"))).data.people.single().given)
    }

    // ---------------------------------------------------------------- files that cannot be used

    @Test
    fun filesThatAreNotGedcomAreRefusedWithAPlainMessage() {
        assertEquals("This file is not a GEDCOM family tree.", refused("hello there".toByteArray()).message)
        assertEquals("This file is not a GEDCOM family tree.", refused(ByteArray(0)).message)
        assertEquals("This file is not a GEDCOM family tree.", refused("{\"format\":\"kinhold-backup\"}".toByteArray()).message)
        assertEquals("This GEDCOM file has no people in it.", refused("0 HEAD\n1 CHAR UTF-8\n0 TRLR".toByteArray()).message)
    }

    @Test
    fun randomBytesAndCutShortFilesNeverCrash() {
        val rnd = Random(42)
        repeat(300) {
            val bytes = ByteArray(rnd.nextInt(600)).also { rnd.nextBytes(it) }
            try {
                Gedcom.read(bytes)
            } catch (e: GedcomException) {
                // Refused, as it should be.
            }
        }
        val bytes = sample.toByteArray()
        for (cut in 0..bytes.size step 7) {
            try {
                Gedcom.read(bytes.copyOf(cut))
            } catch (e: GedcomException) {
                // A file cut anywhere is either partly readable or refused, never a crash.
            }
        }
    }

    // ---------------------------------------------------------------- writing

    private fun written(family: Family) = Gedcom.write(family, "1.0", "7 NOV 2026")

    @Test
    fun theWrittenFileHasTheRequiredPartsAndNoLineIsTooLong() {
        val text = written(TestFamily.generateFamily(300))
        val lines = text.split("\r\n").filter { it.isNotEmpty() }
        assertEquals("0 HEAD", lines.first())
        assertEquals("0 TRLR", lines.last())
        assertTrue(lines.contains("2 VERS 5.5.1"))
        assertTrue(lines.contains("1 CHAR UTF-8"))
        assertTrue(lines.all { it.length <= 255 })
        assertEquals(300, lines.count { it.endsWith(" INDI") })
    }

    @Test
    fun aTreeSurvivesBeingWrittenAndReadBack() {
        val original = TestFamily.generateFamily(300)
        val again = family(Gedcom.read(written(original).toByteArray(Charsets.UTF_8)))
        assertEquals(300, again.size)
        // Reading numbers people from 1 in the order they appear in the file, which is the order they were written.
        val order = original.people.toList()
        val newId = order.withIndex().associate { (i, p) -> p.id to i + 1 }
        for ((i, p) in order.withIndex()) {
            val q = again.person(i + 1)
            assertEquals(p.given, q.given)
            assertEquals(p.surname, q.surname)
            assertEquals(p.sex, q.sex)
            assertEquals(p.born, q.born)
            assertEquals(p.died, q.died)
            assertEquals(original.parentsOf(p.id).map { newId.getValue(it.id) }, again.parentsOf(q.id).map { it.id })
            assertEquals(original.spousesOf(p.id).map { newId.getValue(it.id) }.sorted(), again.spousesOf(q.id).map { it.id }.sorted())
        }
    }

    @Test
    fun difficultTextSurvivesTheRoundTrip() {
        val long = "A very long note. ".repeat(40) + "end"
        val people = listOf(
            Person(1, "Ayesha \"Aisha\"", "O'Brien/Khan", Sex.Female, 1950, null, "Lahore, \\Punjab\\", "", "Line one\nLine two\n\nAfter a gap — ünïcode @home"),
            Person(2, "خالد", "خان", Sex.Male, null, 2001, "", "Karachi", long),
            Person(3, "Emoji 😀", "Name", Sex.Unknown),
        )
        val back = family(Gedcom.read(written(Family.of(people, emptyList(), emptyList())).toByteArray(Charsets.UTF_8)))
        val a = back.person(1)
        assertEquals("Ayesha \"Aisha\"", a.given)
        assertEquals("O'Brien Khan", a.surname) // a slash would break the name, so it becomes a space
        assertEquals("Lahore, \\Punjab\\", a.birthPlace)
        assertEquals("Line one\nLine two\n\nAfter a gap — ünïcode @home", a.notes)
        assertEquals("خالد", back.person(2).given)
        assertEquals(2001, back.person(2).died)
        assertEquals("Karachi", back.person(2).deathPlace)
        assertEquals(long, back.person(2).notes)
        assertEquals("Emoji 😀", back.person(3).given)
    }

    @Test
    fun aCoupleWithNoChildrenAndASingleParentAreBothKept() {
        val people = listOf(
            Person(1, "A", "X", Sex.Male), Person(2, "B", "X", Sex.Female),
            Person(3, "Solo", "Parent", Sex.Female), Person(4, "Kid", "Parent"),
        )
        val original = Family.of(people, listOf(4 to 3), listOf(1 to 2))
        val again = family(Gedcom.read(written(original).toByteArray(Charsets.UTF_8)))
        assertEquals(listOf(2), again.spousesOf(1).map { it.id })
        assertEquals(listOf(3), again.parentsOf(4).map { it.id })
        assertTrue(again.spousesOf(3).isEmpty())
    }

    @Test
    fun aChildWithMoreThanTwoParentsStillKeepsEveryParent() {
        val people = (1..4).map { Person(it, "P$it", "X", Sex.Unknown) }
        val original = Family.of(people, listOf(4 to 1, 4 to 2, 4 to 3), emptyList())
        val again = family(Gedcom.read(written(original).toByteArray(Charsets.UTF_8)))
        assertEquals(listOf(1, 2, 3), again.parentsOf(4).map { it.id }.sorted())
    }

    @Test
    fun parentsAreAssignedHusbandAndWifeBySex() {
        val people = listOf(Person(1, "Her", "X", Sex.Female), Person(2, "Him", "X", Sex.Male), Person(3, "Kid", "X"))
        val text = written(Family.of(people, listOf(3 to 1, 3 to 2), listOf(1 to 2)))
        assertTrue(text.contains("1 HUSB @I2@"))
        assertTrue(text.contains("1 WIFE @I1@"))
    }

    @Test
    fun anEmptyTreeWritesAValidFileThatHasNoPeople() {
        val text = written(Family.Empty)
        assertTrue(text.startsWith("0 HEAD"))
        assertEquals("This GEDCOM file has no people in it.", refused(text.toByteArray()).message)
    }

    @Test
    fun aBigTreeIsFastToWriteAndRead() {
        val big = TestFamily.generateFamily(3000)
        val start = System.nanoTime()
        val again = Gedcom.read(written(big).toByteArray(Charsets.UTF_8))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(3000, again.data.people.size)
        assertTrue("Took $ms ms", ms < 3000)
    }
}
