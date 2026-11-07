package com.inamibrahim.kinhold

import com.inamibrahim.kinhold.data.AutoBackupStore
import com.inamibrahim.kinhold.model.AutoBackupPolicy
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.TestFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class AutoBackupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun name(day: Int, hour: Int, minute: Int = 0) = AutoBackupPolicy.fileName(LocalDateTime.of(2026, 11, day, hour, minute))

    @Test
    fun fileNamesCarryTheirTimeAndOnlyAutomaticOnesAreRecognised() {
        val n = name(7, 14, 5)
        assertEquals("kinhold-auto-2026-11-07-1405.json", n)
        assertTrue(AutoBackupPolicy.isAutoBackup(n))
        assertEquals(LocalDateTime.of(2026, 11, 7, 14, 5), AutoBackupPolicy.timeOf(n))
        for (other in listOf("kinhold-backup-2026-11-07.json", "kinhold-auto-2026-11-07.json", "notes.txt", "kinhold-auto-2026-11-07-1405.json.tmp")) {
            assertFalse(other, AutoBackupPolicy.isAutoBackup(other))
            assertNull(AutoBackupPolicy.timeOf(other))
        }
    }

    @Test
    fun aBackupIsDueOnlyWhenTheTreeChangedAndTheLastOneIsOldEnough() {
        val now = 10_000_000L
        assertTrue(AutoBackupPolicy.isDue(null, now, changed = true))
        assertFalse(AutoBackupPolicy.isDue(null, now, changed = false))
        assertFalse(AutoBackupPolicy.isDue(now - 60_000, now, changed = true)) // a minute ago: too soon
        assertTrue(AutoBackupPolicy.isDue(now - AutoBackupPolicy.MIN_GAP_MILLIS, now, changed = true))
        assertFalse(AutoBackupPolicy.isDue(now - AutoBackupPolicy.MIN_GAP_MILLIS - 1, now, changed = false))
        assertTrue(AutoBackupPolicy.isDue(now - 1, now, changed = false, force = true))
    }

    @Test
    fun fewBackupsAreNeverPruned() {
        val names = (1..6).map { name(it, 9) }
        assertTrue(AutoBackupPolicy.toDelete(names).isEmpty())
    }

    @Test
    fun pruningKeepsTheNewestTenAndTheNewestOfEachOfTheLastSevenDays() {
        // 30 backups on one day: only the newest ten stay (all on that day).
        val oneDay = (0 until 30).map { name(7, it % 24, it) }
        val keptOneDay = oneDay - AutoBackupPolicy.toDelete(oneDay).toSet()
        assertEquals(10, keptOneDay.size)

        // Many backups on the last day plus one old good copy on each of nine earlier days: the newest per day for seven days stays.
        val today = (0 until 20).map { name(20, 8, it) }
        val earlier = (11..19).map { name(it, 12) }
        val all = today + earlier
        val kept = all - AutoBackupPolicy.toDelete(all).toSet()
        assertTrue(today.takeLast(10).all { it in kept })
        // The newest of each of the seven most recent days (20 and 19 down to 14) is kept, even though ten newer files exist.
        for (day in 14..19) assertTrue("day $day kept", name(day, 12) in kept)
        assertTrue(name(20, 8, 19) in kept)
        assertFalse(name(13, 12) in kept)
        assertFalse(name(11, 12) in kept)
    }

    @Test
    fun pruningIgnoresFilesThatAreNotAutomaticBackups() {
        val names = listOf("kinhold-backup-2026-11-07.json", "readme.txt") + (1..15).map { name(7, 9, it) }
        val doomed = AutoBackupPolicy.toDelete(names)
        assertTrue(doomed.all { AutoBackupPolicy.isAutoBackup(it) })
        assertEquals(5, doomed.size)
    }

    @Test
    fun theStoreWritesWholeFilesListsNewestFirstAndPrunes() {
        val store = AutoBackupStore(folder.newFolder("auto"))
        assertNull(store.latest())
        val text = BackupCodec.encode(TestFamily.generateFamily(60), "now")
        for (m in 0 until 14) store.write(name(7, 9, m), text)
        assertEquals(14, store.list().size)
        assertEquals(name(7, 9, 13), store.latest()!!.name)
        store.prune()
        assertEquals(10, store.list().size)
        assertEquals(name(7, 9, 13), store.list().first().name)
        // What was written reads back as the same tree.
        assertEquals(60, BackupCodec.decode(store.latest()!!.readText()).people.size)
        // No temporary files are left behind.
        assertTrue(folder.root.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun writingTheSameNameAgainReplacesTheFileCompletely() {
        val store = AutoBackupStore(folder.newFolder("auto2"))
        store.write("kinhold-auto-2026-11-07-0900.json", "x".repeat(1000))
        store.write("kinhold-auto-2026-11-07-0900.json", "short")
        assertEquals("short", store.latest()!!.readText())
    }
}
