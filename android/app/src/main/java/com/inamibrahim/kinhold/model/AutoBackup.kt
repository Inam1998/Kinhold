package com.inamibrahim.kinhold.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The rules for the automatic backups. A backup is written after the tree changes, at most once every
 * [MIN_GAP_MILLIS], into files named by their date and time. Old ones are pruned, but never the newest ten, and never the
 * newest of each of the last seven days, so one bad change cannot push every good copy out in a day. Pure Kotlin.
 */
object AutoBackupPolicy {
    const val MIN_GAP_MILLIS = 15L * 60_000L
    const val KEEP_NEWEST = 10
    const val KEEP_DAYS = 7
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    private val NAME = Regex("""^kinhold-auto-(\d{4}-\d{2}-\d{2})-(\d{4})\.json$""")

    fun fileName(at: LocalDateTime): String = "kinhold-auto-${STAMP.format(at)}.json"

    fun isAutoBackup(name: String): Boolean = NAME.matches(name)

    /** The time stamped into [name], or null if it is not an automatic backup file. */
    fun timeOf(name: String): LocalDateTime? {
        val m = NAME.matchEntire(name) ?: return null
        return runCatching { LocalDateTime.parse("${m.groupValues[1]}-${m.groupValues[2]}", STAMP) }.getOrNull()
    }

    /** Which of [names] to delete: automatic backups that are neither among the newest [KEEP_NEWEST] nor the newest of a recent day. */
    fun toDelete(names: List<String>): List<String> {
        val dated = names.mapNotNull { n -> timeOf(n)?.let { n to it } }.sortedByDescending { it.second }
        val keep = HashSet<String>()
        dated.take(KEEP_NEWEST).forEach { keep += it.first }
        dated.groupBy { it.second.toLocalDate() }.entries.sortedByDescending { it.key }.take(KEEP_DAYS)
            .forEach { (_, list) -> keep += list.first().first }
        return dated.map { it.first }.filter { it !in keep }
    }

    /** Whether a new automatic backup should be written now: the tree changed and the last one is old enough (or [force]). */
    fun isDue(lastMillis: Long?, nowMillis: Long, changed: Boolean, force: Boolean = false): Boolean =
        force || (changed && (lastMillis == null || nowMillis - lastMillis >= MIN_GAP_MILLIS))
}

/** A size limit on top of [AutoBackupPolicy]: when the copies together pass [CAP_BYTES], the oldest go, but the newest [MIN_KEPT] always stay. */
object AutoBackupSizeCap {
    const val CAP_BYTES = 200L * 1024 * 1024
    const val MIN_KEPT = 3

    /** Names (from [sizes], name to bytes) to delete so the total fits [cap], oldest first, never touching the newest [MIN_KEPT]. */
    fun toDelete(sizes: Map<String, Long>, cap: Long = CAP_BYTES): List<String> {
        val newestFirst = sizes.keys.mapNotNull { n -> AutoBackupPolicy.timeOf(n)?.let { n to it } }.sortedByDescending { it.second }.map { it.first }
        var total = newestFirst.sumOf { sizes.getValue(it) }
        val doomed = ArrayList<String>()
        for (n in newestFirst.drop(MIN_KEPT).reversed()) {
            if (total <= cap) break
            doomed += n
            total -= sizes.getValue(n)
        }
        return doomed
    }
}
