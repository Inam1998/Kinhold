package com.inamibrahim.kinhold.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Everything in a tree, as stored in a backup file. [created] is when the backup was made (ISO time), or empty.
 * [photos] holds each photo as JPEG bytes, by the id of the person it belongs to. [details] holds events and sources.
 */
class BackupData(
    val people: List<Person>,
    val parentLinks: List<Pair<Int, Int>>,
    val partnerships: List<Pair<Int, Int>>,
    val created: String = "",
    val photos: Map<Int, ByteArray> = emptyMap(),
    val details: TreeDetails = TreeDetails.Empty,
)

/** A backup file that cannot be used. [message] is written for the person, not for a programmer. */
class BackupException(message: String) : Exception(message)

/**
 * The backup file: one JSON document holding every person, link and photo (as Base64 text), with a format name and
 * version so a future Kinhold can still read an old backup. Version 1 had no photos and version 2 no events or sources; both are still read. Reading checks
 * everything before the tree is touched, so a damaged or wrong file is refused and nothing is lost. Fields this
 * version does not know are ignored.
 */
object BackupCodec {
    const val FORMAT = "kinhold-backup"
    const val VERSION = 6
    private const val MAX_PEOPLE = 200_000
    private const val MAX_PHOTO_BYTES = 3_000_000
    private const val NOT_A_BACKUP = "This file is not a Kinhold backup."
    private const val DAMAGED = "This backup is damaged and cannot be read."

    fun encode(
        family: Family,
        created: String,
        photos: Map<Int, ByteArray> = emptyMap(),
        details: TreeDetails = TreeDetails.Empty,
    ): String {
        val people = JSONArray()
        val base64 = java.util.Base64.getEncoder()
        for (p in family.people) {
            val o = JSONObject()
                .put("id", p.id)
                .put("given", p.given)
                .put("surname", p.surname)
                .put("sex", p.sex.ordinal)
                .put("born", p.born ?: JSONObject.NULL)
                .put("died", p.died ?: JSONObject.NULL)
                .put("birthPlace", p.birthPlace)
                .put("deathPlace", p.deathPlace)
                .put("notes", p.notes)
            if (p.uid.isNotEmpty()) o.put("uid", p.uid)
            if (p.address.isNotEmpty()) o.put("address", p.address)
            if (p.phone.isNotEmpty()) o.put("phone", p.phone)
            if (p.email.isNotEmpty()) o.put("email", p.email)
            details.fields[p.id]?.takeIf { it.isNotEmpty() }?.let { o.put("fields", EventJson.fields(it)) }
            if (p.lat != null && p.lng != null) o.put("lat", p.lat).put("lng", p.lng)
            if (p.isMe) o.put("me", true)
            p.birthMonth?.let { o.put("birthMonth", it) }
            p.birthDay?.let { o.put("birthDay", it) }
            p.deathMonth?.let { o.put("deathMonth", it) }
            p.deathDay?.let { o.put("deathDay", it) }
            details.events[p.id]?.takeIf { it.isNotEmpty() }?.let { o.put("events", EventJson.events(it)) }
            details.sources[p.id]?.takeIf { it.isNotEmpty() }?.let { o.put("sources", EventJson.sources(it)) }
            photos[p.id]?.let { o.put("photo", base64.encodeToString(it)) }
            people.put(o)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("created", created)
            .put("people", people)
            .put("parentLinks", pairs(family.parentLinks()))
            .put("partnerships", pairs(family.partnerships()))
            .put(
                "coupleEvents",
                JSONArray().apply {
                    for ((pair, events) in details.couples) {
                        if (events.isEmpty()) continue
                        put(JSONObject().put("a", pair.first).put("b", pair.second).put("events", EventJson.events(events)))
                    }
                },
            )
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
            val uids = HashSet<String>()
            var meCount = 0
            val photos = HashMap<Int, ByteArray>()
            val personEvents = LinkedHashMap<Int, List<Event>>()
            val personSources = LinkedHashMap<Int, List<Source>>()
            val personFields = LinkedHashMap<Int, List<CustomField>>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.getInt("id")
                if (!ids.add(id)) throw BackupException(DAMAGED)
                val born = year(o, "born")
                val died = year(o, "died")
                val uid = o.optString("uid", "")
                // Files from before version 4 have none (the person gets one when stored); a bad or repeated one is damage.
                if (uid.isNotEmpty() && (!PersonUid.isValid(uid) || !uids.add(uid))) throw BackupException(DAMAGED)
                val lat = if (o.has("lat") && !o.isNull("lat")) o.getDouble("lat") else null
                val lng = if (o.has("lng") && !o.isNull("lng")) o.getDouble("lng") else null
                if ((lat == null) != (lng == null) || (lat != null && !GeoPin(lat, lng!!).isValid)) throw BackupException(DAMAGED)
                val isMe = o.optBoolean("me", false)
                if (isMe && meCount++ > 0) throw BackupException(DAMAGED)
                val birthMonth = small(o, "birthMonth")
                val birthDay = small(o, "birthDay")
                val deathMonth = small(o, "deathMonth")
                val deathDay = small(o, "deathDay")
                if (!PartialDate(born, birthMonth, birthDay).isValid || !PartialDate(died, deathMonth, deathDay).isValid) {
                    throw BackupException(DAMAGED)
                }
                if (o.has("events") && !o.isNull("events")) personEvents[id] = EventJson.readEvents(o.getJSONArray("events"))
                if (o.has("sources") && !o.isNull("sources")) personSources[id] = EventJson.readSources(o.getJSONArray("sources"))
                if (o.has("fields") && !o.isNull("fields")) personFields[id] = EventJson.readFields(o.getJSONArray("fields"))
                if (!o.isNull("photo")) photos[id] = photo(o.getString("photo"))
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
                    birthMonth = birthMonth,
                    birthDay = birthDay,
                    deathMonth = deathMonth,
                    deathDay = deathDay,
                    uid = uid,
                    address = o.optString("address", "").take(500),
                    phone = o.optString("phone", "").take(40),
                    email = o.optString("email", "").take(200),
                    lat = lat,
                    lng = lng,
                    isMe = isMe,
                )
            }
            val links = readPairs(root.optJSONArray("parentLinks"), ids).distinct()
            val partnerships = readPairs(root.optJSONArray("partnerships"), ids)
                .map { (a, b) -> minOf(a, b) to maxOf(a, b) }
                .distinct()
            val coupleEvents = LinkedHashMap<Pair<Int, Int>, List<Event>>()
            root.optJSONArray("coupleEvents")?.let { list ->
                for (i in 0 until list.length()) {
                    val o = list.getJSONObject(i)
                    val a = o.getInt("a")
                    val b = o.getInt("b")
                    val key = minOf(a, b) to maxOf(a, b)
                    // Events of a couple that is not in the file mean the file was altered or cut short.
                    if (key !in partnerships) throw BackupException(DAMAGED)
                    coupleEvents[key] = EventJson.readEvents(o.getJSONArray("events"))
                }
            }
            return BackupData(
                people, links, partnerships, root.optString("created", ""), photos,
                TreeDetails(personEvents, personSources, coupleEvents, personFields),
            )
        } catch (e: JSONException) {
            throw BackupException(DAMAGED)
        }
    }

    /** A photo from its Base64 text. It must be a JPEG of a sensible size, as the app only ever stores those. */
    private fun photo(text: String): ByteArray {
        val bytes = try {
            java.util.Base64.getDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            throw BackupException(DAMAGED)
        }
        val isJpeg = bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        if (!isJpeg || bytes.size > MAX_PHOTO_BYTES) throw BackupException(DAMAGED)
        return bytes
    }

    private fun year(o: JSONObject, key: String): Int? {
        if (o.isNull(key)) return null
        val y = o.getInt(key)
        if (y !in 0..9999) throw BackupException(DAMAGED)
        return y
    }

    /** A month or day: a small whole number, or null when the field is missing. Whether it fits a date is checked after. */
    private fun small(o: JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        val n = o.getInt(key)
        if (n !in 0..99) throw BackupException(DAMAGED)
        return n
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

/**
 * Events and sources as JSON, for backups and for keeping a half-filled form across a screen rotation. Reading is strict:
 * a date that cannot exist, or more events than [Limits.EVENTS], means the file is damaged. Text longer than the limits
 * is cut. Fields that are not known are ignored, and an event kind this version does not know becomes a general event.
 */
object EventJson {
    private const val DAMAGED = "This backup is damaged and cannot be read."

    fun sources(list: List<Source>): JSONArray = JSONArray().apply {
        for (s in list) put(JSONObject().put("title", s.title).put("citation", s.citation).put("note", s.note))
    }

    fun fields(list: List<CustomField>): JSONArray = JSONArray().apply {
        for (f in list) put(JSONObject().put("label", f.label).put("value", f.value))
    }

    /** Custom fields from a list; blank ones are kept while [editing] a form. More than [Limits.FIELDS] means a damaged file. */
    fun readFields(array: JSONArray, editing: Boolean = false): List<CustomField> {
        if (array.length() > Limits.FIELDS) throw BackupException(DAMAGED)
        val out = ArrayList<CustomField>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val raw = CustomField(o.optString("label", ""), o.optString("value", ""))
            val f = if (editing) raw else raw.clamped()
            if (editing || !f.isBlank) out += f
        }
        return out
    }

    fun events(list: List<Event>): JSONArray = JSONArray().apply {
        for (e in list) {
            val o = JSONObject().put("kind", e.kind.key).put("detail", e.detail).put("place", e.place).put("note", e.note)
            e.date.year?.let { o.put("year", it) }
            e.date.month?.let { o.put("month", it) }
            e.date.day?.let { o.put("day", it) }
            if (e.sources.isNotEmpty()) o.put("sources", sources(e.sources))
            put(o)
        }
    }

    fun readSources(array: JSONArray, editing: Boolean = false): List<Source> {
        if (array.length() > Limits.SOURCES) throw BackupException(DAMAGED)
        val out = ArrayList<Source>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val raw = Source(o.optString("title", ""), o.optString("citation", ""), o.optString("note", ""))
            // While a form is being edited, blank and untrimmed entries are kept so typing a space or adding a source works.
            val s = if (editing) raw else raw.clamped()
            if (editing) {
                out += s
                continue
            }
            if (!s.isBlank) out += s
        }
        return out
    }

    fun readEvents(array: JSONArray): List<Event> {
        if (array.length() > Limits.EVENTS) throw BackupException(DAMAGED)
        val out = ArrayList<Event>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val key = o.optString("kind", "")
            val kind = EventKind.fromKey(key)
            val date = PartialDate(number(o, "year"), number(o, "month"), number(o, "day"))
            if (!date.isValid) throw BackupException(DAMAGED)
            val detail = o.optString("detail", "").ifBlank { if (kind == null) key else "" }
            val sources = o.optJSONArray("sources")?.let(::readSources).orEmpty()
            out += Event(kind ?: EventKind.Custom, detail, date, o.optString("place", ""), o.optString("note", ""), sources).clamped()
        }
        return out
    }

    private fun number(o: JSONObject, key: String): Int? = if (!o.has(key) || o.isNull(key)) null else o.getInt(key)

    /** A person's details as one piece of text, for the form to keep across rotation. */
    fun encodeDetails(d: PersonDetails): String = JSONObject()
        .put("events", events(d.events))
        .put("sources", sources(d.sources))
        .put("fields", fields(d.fields))
        .put(
            "couples",
            JSONArray().apply {
                for ((partner, list) in d.coupleEvents) put(JSONObject().put("partner", partner).put("events", events(list)))
            },
        )
        .toString()

    /** Reads what [encodeDetails] wrote. Nothing (empty details) if the text cannot be read. */
    fun decodeDetails(text: String, editing: Boolean = false): PersonDetails = try {
        val o = JSONObject(text)
        val couples = LinkedHashMap<Int, List<Event>>()
        o.optJSONArray("couples")?.let { a ->
            for (i in 0 until a.length()) {
                val c = a.getJSONObject(i)
                couples[c.getInt("partner")] = readEvents(c.getJSONArray("events"))
            }
        }
        PersonDetails(
            o.optJSONArray("events")?.let(::readEvents).orEmpty(),
            o.optJSONArray("sources")?.let { readSources(it, editing) }.orEmpty(),
            coupleEvents = couples,
            fields = o.optJSONArray("fields")?.let { readFields(it, editing) }.orEmpty(),
        )
    } catch (e: JSONException) {
        PersonDetails.Empty
    } catch (e: BackupException) {
        PersonDetails.Empty
    }
}
