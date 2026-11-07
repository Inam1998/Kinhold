package com.inamibrahim.kinhold.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything in a tree, as stored in a backup file. [created] is when the backup was made (ISO time), or empty. */
class BackupData(
    val people: List<Person>,
    val parentLinks: List<Pair<Int, Int>>,
    val partnerships: List<Pair<Int, Int>>,
    val created: String = "",
)

/** A backup file that cannot be used. [message] is written for the person, not for a programmer. */
class BackupException(message: String) : Exception(message)

/**
 * The backup file: one JSON document holding every person and link, with a format name and version so a future
 * Kinhold can still read an old backup. Reading checks everything before the tree is touched, so a damaged or wrong
 * file is refused and nothing is lost. Fields this version does not know are ignored.
 */
object BackupCodec {
    const val FORMAT = "kinhold-backup"
    const val VERSION = 1
    private const val MAX_PEOPLE = 200_000
    private const val NOT_A_BACKUP = "This file is not a Kinhold backup."
    private const val DAMAGED = "This backup is damaged and cannot be read."

    fun encode(family: Family, created: String): String {
        val people = JSONArray()
        for (p in family.people) {
            people.put(
                JSONObject()
                    .put("id", p.id)
                    .put("given", p.given)
                    .put("surname", p.surname)
                    .put("sex", p.sex.ordinal)
                    .put("born", p.born ?: JSONObject.NULL)
                    .put("died", p.died ?: JSONObject.NULL)
                    .put("birthPlace", p.birthPlace)
                    .put("deathPlace", p.deathPlace)
                    .put("notes", p.notes),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("created", created)
            .put("people", people)
            .put("parentLinks", pairs(family.parentLinks()))
            .put("partnerships", pairs(family.partnerships()))
            .toString()
    }

    /** Reads a backup, or throws [BackupException] saying what is wrong. */
    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(NOT_A_BACKUP)
        }
        if (root.optString("format") != FORMAT) throw BackupException(NOT_A_BACKUP)
        val version = root.optInt("version", 0)
        if (version < 1) throw BackupException(NOT_A_BACKUP)
        if (version > VERSION) throw BackupException("This backup was made by a newer version of Kinhold. Update the app to open it.")

        try {
            val array = root.getJSONArray("people")
            if (array.length() > MAX_PEOPLE) throw BackupException(DAMAGED)
            val people = ArrayList<Person>(array.length())
            val ids = HashSet<Int>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.getInt("id")
                if (!ids.add(id)) throw BackupException(DAMAGED)
                val born = year(o, "born")
                val died = year(o, "died")
                people += Person(
                    id = id,
                    given = o.optString("given", ""),
                    surname = o.optString("surname", ""),
                    sex = Sex.entries.getOrElse(o.optInt("sex", Sex.Unknown.ordinal)) { Sex.Unknown },
                    born = born,
                    died = died,
                    birthPlace = o.optString("birthPlace", ""),
                    deathPlace = o.optString("deathPlace", ""),
                    notes = o.optString("notes", ""),
                )
            }
            val links = readPairs(root.optJSONArray("parentLinks"), ids).distinct()
            val partnerships = readPairs(root.optJSONArray("partnerships"), ids)
                .map { (a, b) -> minOf(a, b) to maxOf(a, b) }
                .distinct()
            return BackupData(people, links, partnerships, root.optString("created", ""))
        } catch (e: JSONException) {
            throw BackupException(DAMAGED)
        }
    }

    private fun year(o: JSONObject, key: String): Int? {
        if (o.isNull(key)) return null
        val y = o.getInt(key)
        if (y !in 0..9999) throw BackupException(DAMAGED)
        return y
    }

    private fun pairs(list: List<Pair<Int, Int>>): JSONArray {
        val out = JSONArray()
        for ((a, b) in list) out.put(JSONArray().put(a).put(b))
        return out
    }

    private fun readPairs(array: JSONArray?, ids: Set<Int>): List<Pair<Int, Int>> {
        if (array == null) return emptyList()
        val out = ArrayList<Pair<Int, Int>>(array.length())
        for (i in 0 until array.length()) {
            val pair = array.getJSONArray(i)
            val a = pair.getInt(0)
            val b = pair.getInt(1)
            // A link to someone who is not in the file, or to themselves, means the file was altered or cut short.
            if (a !in ids || b !in ids || a == b) throw BackupException(DAMAGED)
            out += a to b
        }
        return out
    }
}

/** "today", "yesterday", "5 days ago", "3 months ago": how long ago [then] was, as shown next to a backup. */
fun describeAge(then: Long, now: Long): String {
    val days = ((now - then) / 86_400_000L).toInt().coerceAtLeast(0)
    return when {
        days == 0 -> "today"
        days == 1 -> "yesterday"
        days < 60 -> "$days days ago"
        else -> "${days / 30} months ago"
    }
}
