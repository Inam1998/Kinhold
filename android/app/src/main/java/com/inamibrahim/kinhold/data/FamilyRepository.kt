package com.inamibrahim.kinhold.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.CoupleInfo
import com.inamibrahim.kinhold.model.CustomField
import com.inamibrahim.kinhold.model.Event
import com.inamibrahim.kinhold.model.EventKind
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.GeoPin
import com.inamibrahim.kinhold.model.Limits
import com.inamibrahim.kinhold.model.Merge
import com.inamibrahim.kinhold.model.MergeField
import com.inamibrahim.kinhold.model.MergeResult
import com.inamibrahim.kinhold.model.Share
import com.inamibrahim.kinhold.model.ShareScope
import com.inamibrahim.kinhold.model.PartialDate
import com.inamibrahim.kinhold.model.PersonDetails
import com.inamibrahim.kinhold.model.Source
import com.inamibrahim.kinhold.model.TreeDetails
import com.inamibrahim.kinhold.model.inDateOrder
import com.inamibrahim.kinhold.model.Gedcom
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDraft
import com.inamibrahim.kinhold.model.PersonUid
import com.inamibrahim.kinhold.model.Sex
import com.inamibrahim.kinhold.model.TestFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant

/** How a new person is connected to someone already in the tree. */
sealed interface Relation {
    /** The new person is a parent of [childId]. */
    data class ParentOf(val childId: Int) : Relation

    /** The new person is a child of [parentId] and, if given, also of [otherParentId] (usually that person's spouse). */
    data class ChildOf(val parentId: Int, val otherParentId: Int? = null) : Relation

    /** The new person is the partner of [personId]. */
    data class SpouseOf(val personId: Int) : Relation
}

/** A person just deleted, with who they were linked to, so the delete can be undone. */
class DeletedPerson(
    val person: Person,
    val parentIds: List<Int>,
    val childIds: List<Int>,
    val partnerIds: List<Int>,
    val photo: PreparedPhoto? = null,
    val details: PersonDetails = PersonDetails.Empty,
)

/**
 * Reads and changes the stored tree. Every change runs inside one database transaction and then publishes a fresh
 * [Family] snapshot, so the screen always shows what is really saved. Changes are applied one at a time.
 * [safetyFile] holds a copy of the tree as it was just before it was last replaced by a restore.
 */
class FamilyRepository(
    context: Context,
    dbName: String? = FamilyDb.FILE_NAME,
    private val safetyFile: File = File(context.applicationContext.filesDir, "safety-before-restore.json"),
) {
    private val helper = FamilyDb(context.applicationContext, dbName)
    private val lock = Mutex()
    private val _family = MutableStateFlow(Family.Empty)

    /** The tree as last saved. Empty until [load] has finished. */
    val family: StateFlow<Family> = _family

    suspend fun load() = lock.withLock {
        withContext(Dispatchers.IO) { _family.value = read() }
    }

    /** Adds a person, connected to someone already in the tree if [relation] is given. Returns the new id. */
    suspend fun addPerson(
        draft: PersonDraft,
        relation: Relation? = null,
        photo: PreparedPhoto? = null,
        details: PersonDetails = PersonDetails.Empty,
    ): Int = changing { db ->
        val id = db.insertPerson(draft)
        photo?.let { db.putPhoto(id, it) }
        when (relation) {
            null -> Unit
            is Relation.ParentOf -> {
                db.insertLink(child = relation.childId, parent = id)
                // If the child already had another parent, the two parents are a couple.
                for (other in db.parentsOf(relation.childId)) if (other != id) db.insertPartnership(id, other)
            }
            is Relation.ChildOf -> {
                db.insertLink(child = id, parent = relation.parentId)
                relation.otherParentId?.let { db.insertLink(child = id, parent = it) }
            }
            is Relation.SpouseOf -> db.insertPartnership(relation.personId, id)
        }
        db.putDetails(id, details)
        id
    }

    /**
     * Saves the changes to a person. [details], if given, replaces the person's events and sources and the events of each
     * couple it names; null leaves them as they are.
     */
    suspend fun updatePerson(
        id: Int,
        draft: PersonDraft,
        photo: PhotoChange = PhotoChange.Keep,
        details: PersonDetails? = null,
    ) = changing { db ->
        db.update("person", draft.toValues(), "id = ?", arrayOf(id.toString()))
        if (details != null) db.putDetails(id, details)
        when (photo) {
            PhotoChange.Keep -> Unit
            is PhotoChange.Set -> db.putPhoto(id, photo.photo)
            PhotoChange.Remove -> db.delete("photo", "person_id = ?", arrayOf(id.toString()))
        }
        Unit
    }

    /** Marks [id] as the owner of this tree ("this is me"), or nobody if [id] is null. Only one person can be. */
    suspend fun setMe(id: Int?) = changing { db ->
        db.execSQL("UPDATE person SET is_me = 0 WHERE is_me <> 0")
        if (id != null) db.execSQL("UPDATE person SET is_me = 1 WHERE id = ?", arrayOf(id))
    }

    /** A person's events and sources and the events of each of their couples. Safe to call from any thread. */
    suspend fun details(id: Int): PersonDetails = withContext(Dispatchers.IO) {
        helper.readableDatabase.readDetails(id, _family.value.spousesOf(id).map { it.id })
    }

    /** The small square picture of a person, or null if they have none. Safe to call from any thread. */
    suspend fun thumb(id: Int): ByteArray? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT thumb FROM photo WHERE person_id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getBlob(0) else null
        }
    }

    /** Removes a person and every link to them. Their relatives stay. Returns what is needed to undo it. */
    suspend fun deletePerson(id: Int): DeletedPerson? = changing { db ->
        val snapshot = _family.value
        if (id !in snapshot) return@changing null
        val record = DeletedPerson(
            person = snapshot.person(id),
            parentIds = snapshot.parentsOf(id).map { it.id },
            childIds = snapshot.childrenOf(id).map { it.id },
            partnerIds = snapshot.spousesOf(id).map { it.id },
            photo = db.readPhoto(id),
            details = db.readDetails(id, snapshot.spousesOf(id).map { it.id }),
        )
        db.delete("person", "id = ?", arrayOf(id.toString()))
        record
    }

    /** Puts a deleted person back with the same id, photo and links to relatives who are still in the tree. */
    suspend fun restoreDeleted(deleted: DeletedPerson) = changing { db ->
        db.insertWithId(deleted.person.id, deleted.person.toDraft(), deleted.person.isMe)
        deleted.photo?.let { db.putPhoto(deleted.person.id, it) }
        for (parent in deleted.parentIds) if (db.exists(parent)) db.insertLink(child = deleted.person.id, parent = parent)
        for (child in deleted.childIds) if (db.exists(child)) db.insertLink(child = child, parent = deleted.person.id)
        for (partner in deleted.partnerIds) if (db.exists(partner)) db.insertPartnership(deleted.person.id, partner)
        // Couple events come back for partners who are still in the tree (putDetails skips the others).
        db.putDetails(deleted.person.id, deleted.details)
    }

    /** The whole tree, photos included, as backup-file text. */
    suspend fun exportBackup(): String = lock.withLock {
        withContext(Dispatchers.IO) {
            val db = helper.readableDatabase
            BackupCodec.encode(_family.value, Instant.now().toString(), db.readPhotos(), db.readTreeDetails())
        }
    }

    /**
     * Replaces the whole tree with [data]. The tree as it is now is first written to the safety copy, so a restore can
     * be undone, and the replacement happens in one transaction: either all of it is stored or none of it.
     */
    suspend fun replaceAll(data: BackupData) = changing { db ->
        writeSafetyCopy(db, _family.value)
        db.delete("person", null, null)
        for (p in data.people) db.insertWithId(p.id, p.toDraft(), p.isMe)
        for ((child, parent) in data.parentLinks) db.insertLink(child, parent)
        for ((a, b) in data.partnerships) db.insertPartnership(a, b)
        for ((id, jpeg) in data.photos) db.putPhoto(id, PreparedPhoto(jpeg, PhotoCodec.thumbOf(jpeg)))
        db.putTreeDetails(data.details) { it }
    }

    /** The whole tree as GEDCOM text, for other family tree programs. */
    suspend fun exportGedcom(appVersion: String, exportedOn: String): String = lock.withLock {
        withContext(Dispatchers.IO) { Gedcom.write(_family.value, helper.readableDatabase.readTreeDetails(), appVersion, exportedOn) }
    }

    /**
     * Adds everyone in [data] to the tree as new people, keeping everyone already there. People already in the tree are
     * not matched, so adding the same file twice makes duplicates. Like a restore, it first writes the safety copy, so
     * [undoRestore] takes it back.
     */
    suspend fun addAll(data: BackupData) = changing { db ->
        writeSafetyCopy(db, _family.value)
        val newIds = HashMap<Int, Int>()
        // A person whose permanent id is already in this tree is a copy of someone here: the copy gets a new id.
        val taken = db.rawQuery("SELECT uid FROM person", null).use { c -> HashSet<String>().apply { while (c.moveToNext()) c.getString(0)?.let(::add) } }
        for (p in data.people) newIds[p.id] = db.insertPerson(p.toDraft(), if (p.uid in taken) "" else p.uid)
        for ((child, parent) in data.parentLinks) db.insertLink(newIds.getValue(child), newIds.getValue(parent))
        for ((a, b) in data.partnerships) db.insertPartnership(newIds.getValue(a), newIds.getValue(b))
        for ((id, jpeg) in data.photos) db.putPhoto(newIds.getValue(id), PreparedPhoto(jpeg, PhotoCodec.thumbOf(jpeg)))
        db.putTreeDetails(data.details) { newIds.getValue(it) }
    }

    /**
     * A share file for the branch that starts at [rootId]: the same file format as a backup, holding only the people the
     * [scope] reaches, their links, events, sources and (if asked) photos. Returns the file text and how many people it has.
     */
    suspend fun exportShare(rootId: Int, scope: ShareScope, thisYear: Int): Pair<String, Int> = lock.withLock {
        withContext(Dispatchers.IO) {
            val family = _family.value
            val ids = Share.select(family, rootId, scope, thisYear)
            val db = helper.readableDatabase
            val photos = if (scope.withPhotos) db.readPhotos().filterKeys { it in ids } else emptyMap()
            val text = BackupCodec.encode(
                Share.subFamily(family, ids, scope.withContact), Instant.now().toString(), photos, Share.subDetails(db.readTreeDetails(), ids, scope.withContact),
            )
            text to ids.size
        }
    }

    /** Compares [data] with the tree as it is now. Nothing is changed. */
    suspend fun planMerge(data: BackupData): Merge = lock.withLock {
        withContext(Dispatchers.IO) { Merge(_family.value, helper.readableDatabase.readTreeDetails(), data) }
    }

    /**
     * Adds what [data] has and the tree lacks and applies the field choices, all in one transaction after writing the
     * safety copy (so [undoRestore] takes it back). The comparison is made again against the tree as it is now, so the
     * choices ([accepted], [useHis], [addEvents]) are the ones the person saw even if something changed meanwhile.
     */
    suspend fun applyMerge(
        data: BackupData,
        accepted: Set<Int>,
        useHis: Set<Pair<Int, MergeField>>,
        addEvents: Boolean,
    ): MergeResult = changing { db ->
        writeSafetyCopy(db, _family.value)
        val result = Merge(_family.value, db.readTreeDetails(), data).resolve(accepted, useHis, addEvents)
        val byId = data.people.associateBy { it.id }
        val taken = db.rawQuery("SELECT uid FROM person", null).use { c -> HashSet<String>().apply { while (c.moveToNext()) c.getString(0)?.let(::add) } }
        val newIds = HashMap<Int, Int>()
        for (incomingId in result.newPeople) {
            val p = byId.getValue(incomingId)
            val uid = if (p.uid in taken) "" else p.uid
            val id = db.insertPerson(p.toDraft(), uid)
            taken += db.rawQuery("SELECT uid FROM person WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else "" }
            newIds[incomingId] = id
            data.photos[incomingId]?.let { db.putPhoto(id, PreparedPhoto(it, PhotoCodec.thumbOf(it))) }
        }
        fun resolve(ref: Int): Int = if (ref > 0) ref else newIds.getValue(-ref)
        for ((id, draft) in result.updates) db.update("person", draft.toValues(), "id = ?", arrayOf(id.toString()))
        for ((id, incomingId) in result.photoFills) data.photos[incomingId]?.let { db.putPhoto(id, PreparedPhoto(it, PhotoCodec.thumbOf(it))) }
        for ((c, p) in result.links) db.insertLink(resolve(c), resolve(p))
        for ((a, b) in result.partnerships) db.insertPartnership(resolve(a), resolve(b))
        for (ref in result.personEvents.keys + result.personSources.keys + result.personFields.keys) {
            val id = resolve(ref)
            val existing = db.readDetails(id, emptyList())
            db.putDetails(
                id,
                PersonDetails(
                    existing.events + result.personEvents[ref].orEmpty(),
                    existing.sources + result.personSources[ref].orEmpty(),
                    fields = existing.fields + result.personFields[ref].orEmpty(),
                ),
            )
        }
        for ((pair, events) in result.coupleEvents) {
            val a = resolve(pair.first)
            val b = resolve(pair.second)
            val existing = db.readDetails(a, listOf(b)).coupleEvents[b].orEmpty()
            db.putCoupleEvents(a, b, existing + events)
        }
        result
    }

    /** Whether a tree from before the last restore is kept. */
    fun hasSafetyCopy(): Boolean = safetyFile.isFile && safetyFile.length() > 0

    /** Brings back the tree from before the last restore. The tree being replaced becomes the new safety copy. */
    suspend fun undoRestore() {
        val saved = withContext(Dispatchers.IO) { BackupCodec.decode(safetyFile.readText()) }
        replaceAll(saved)
    }

    // Written beside the real file and renamed, so a crash can never leave a half-written safety copy.
    private fun writeSafetyCopy(db: SQLiteDatabase, family: Family) {
        if (family.size == 0) return
        val temp = File(safetyFile.parentFile, safetyFile.name + ".tmp")
        temp.writeText(BackupCodec.encode(family, Instant.now().toString(), db.readPhotos(), db.readTreeDetails()))
        if (!temp.renameTo(safetyFile)) {
            safetyFile.delete()
            if (!temp.renameTo(safetyFile)) throw IOException("Could not save a safety copy of the tree")
        }
    }

    /** Fills an empty tree with an invented family. Does nothing if there are already people. */
    suspend fun addSampleFamily(count: Int) = changing { db ->
        val existing = db.rawQuery("SELECT COUNT(*) FROM person", null).use { it.moveToFirst(); it.getInt(0) }
        if (existing > 0) return@changing
        val sample = TestFamily.generateFamily(count)
        val newIds = HashMap<Int, Int>()
        for (p in sample.people) newIds[p.id] = db.insertPerson(p.toDraft())
        for (p in sample.people) {
            for (parent in sample.parentsOf(p.id)) db.insertLink(child = newIds.getValue(p.id), parent = newIds.getValue(parent.id))
            for (spouse in sample.spousesOf(p.id)) if (p.id < spouse.id) db.insertPartnership(newIds.getValue(p.id), newIds.getValue(spouse.id))
        }
    }

    /** Runs [block] in a transaction on a background thread, then publishes the new snapshot. */
    private suspend fun <T> changing(block: (SQLiteDatabase) -> T): T = lock.withLock {
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.beginTransaction()
            val result = try {
                val r = block(db)
                db.setTransactionSuccessful()
                r
            } finally {
                db.endTransaction()
            }
            _family.value = read()
            result
        }
    }

    private fun read(): Family {
        val db = helper.readableDatabase
        val people = ArrayList<Person>()
        // The photo table is joined only for its stamp; the picture bytes are read when a card needs them.
        db.rawQuery(
            "SELECT p.id, p.given, p.surname, p.sex, p.birth_year, p.death_year, p.birth_place, p.death_place, p.notes, " +
                "COALESCE(ph.stamp, 0), p.birth_month, p.birth_day, p.death_month, p.death_day, COALESCE(p.uid, ''), p.address, p.phone, p.geo_lat, p.geo_lng, p.is_me, p.email FROM person p LEFT JOIN photo ph ON ph.person_id = p.id ORDER BY p.id",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                people += Person(
                    id = c.getInt(0),
                    given = c.getString(1),
                    surname = c.getString(2),
                    sex = Sex.entries.getOrElse(c.getInt(3)) { Sex.Unknown },
                    born = if (c.isNull(4)) null else c.getInt(4),
                    died = if (c.isNull(5)) null else c.getInt(5),
                    birthPlace = c.getString(6),
                    deathPlace = c.getString(7),
                    notes = c.getString(8),
                    photoStamp = c.getLong(9),
                    birthMonth = c.intOrNull(10),
                    birthDay = c.intOrNull(11),
                    deathMonth = c.intOrNull(12),
                    deathDay = c.intOrNull(13),
                    uid = c.getString(14),
                    address = c.getString(15),
                    phone = c.getString(16),
                    lat = if (c.isNull(17)) null else c.getDouble(17),
                    lng = if (c.isNull(18)) null else c.getDouble(18),
                    isMe = c.getInt(19) == 1,
                    email = c.getString(20),
                )
            }
        }
        return Family.of(
            people,
            db.pairs("SELECT child_id, parent_id FROM parent_link"),
            db.pairs("SELECT a_id, b_id FROM partnership"),
            readCouples(db),
        )
    }

    /** Marriage dates and divorces for every couple that has one, so spouses can be ordered and former partners told apart. */
    private fun readCouples(db: SQLiteDatabase): Map<Pair<Int, Int>, CoupleInfo> {
        val married = HashMap<Pair<Int, Int>, Int>()
        val ended = HashSet<Pair<Int, Int>>()
        db.rawQuery(
            "SELECT a_id, b_id, kind, year, month, day FROM event WHERE a_id IS NOT NULL AND kind IN ('marriage', 'divorce', 'annulment')",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val key = c.getInt(0) to c.getInt(1)
                if (c.getString(2) == EventKind.Marriage.key) {
                    val date = PartialDate(c.intOrNull(3), c.intOrNull(4), c.intOrNull(5)).normalized()
                    if (date.year != null) married.merge(key, date.sortKey) { x, y -> minOf(x, y) }
                } else {
                    ended += key
                }
            }
        }
        val out = HashMap<Pair<Int, Int>, CoupleInfo>()
        for (key in married.keys + ended) out[key] = CoupleInfo(married[key], key in ended)
        return out
    }
}

private fun SQLiteDatabase.pairs(sql: String): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    rawQuery(sql, null).use { while (it.moveToNext()) out += it.getInt(0) to it.getInt(1) }
    return out
}

private fun PersonDraft.toValues() = ContentValues().apply {
    put("given", given.trim())
    put("surname", surname.trim())
    put("sex", sex.ordinal)
    if (born != null) put("birth_year", born) else putNull("birth_year")
    if (died != null) put("death_year", died) else putNull("death_year")
    put("birth_place", birthPlace.trim())
    put("death_place", deathPlace.trim())
    put("notes", notes.trim())
    put("address", address.trim().take(500))
    put("phone", phone.trim().take(40))
    put("email", email.trim().take(200))
    val pin = if (lat != null && lng != null) GeoPin(lat, lng).takeIf { it.isValid } else null
    if (pin != null) { put("geo_lat", pin.lat); put("geo_lng", pin.lng) } else { putNull("geo_lat"); putNull("geo_lng") }
    val bm = if (born != null) birthMonth else null
    val dm = if (died != null) deathMonth else null
    if (bm != null) put("birth_month", bm) else putNull("birth_month")
    if (bm != null && birthDay != null) put("birth_day", birthDay) else putNull("birth_day")
    if (dm != null) put("death_month", dm) else putNull("death_month")
    if (dm != null && deathDay != null) put("death_day", deathDay) else putNull("death_day")
}

private fun SQLiteDatabase.insertPerson(draft: PersonDraft, uid: String = draft.uid): Int =
    insertOrThrow("person", null, draft.toValues().apply { put("uid", uid.ifBlank { PersonUid.create() }) }).toInt()

private fun SQLiteDatabase.insertWithId(id: Int, draft: PersonDraft, isMe: Boolean = false) {
    insertOrThrow("person", null, draft.toValues().apply { put("id", id); put("uid", draft.uid.ifBlank { PersonUid.create() }); put("is_me", if (isMe) 1 else 0) })
}

private fun SQLiteDatabase.putPhoto(personId: Int, photo: PreparedPhoto) {
    val values = ContentValues().apply {
        put("person_id", personId)
        put("stamp", System.currentTimeMillis())
        put("data", photo.main)
        put("thumb", photo.thumb)
    }
    insertWithOnConflict("photo", null, values, SQLiteDatabase.CONFLICT_REPLACE)
}

private fun SQLiteDatabase.readPhoto(personId: Int): PreparedPhoto? =
    rawQuery("SELECT data, thumb FROM photo WHERE person_id = ?", arrayOf(personId.toString())).use {
        if (it.moveToFirst()) PreparedPhoto(it.getBlob(0), it.getBlob(1)) else null
    }

/** Every stored photo's main picture, by person id. */
private fun SQLiteDatabase.readPhotos(): Map<Int, ByteArray> {
    val out = LinkedHashMap<Int, ByteArray>()
    rawQuery("SELECT person_id, data FROM photo ORDER BY person_id", null).use { while (it.moveToNext()) out[it.getInt(0)] = it.getBlob(1) }
    return out
}

private fun SQLiteDatabase.exists(id: Int): Boolean =
    rawQuery("SELECT 1 FROM person WHERE id = ?", arrayOf(id.toString())).use { it.moveToFirst() }

private fun SQLiteDatabase.insertLink(child: Int, parent: Int) {
    insertWithOnConflict(
        "parent_link", null,
        ContentValues().apply { put("child_id", child); put("parent_id", parent) },
        SQLiteDatabase.CONFLICT_IGNORE,
    )
}

private fun SQLiteDatabase.insertPartnership(x: Int, y: Int) {
    if (x == y) return
    insertWithOnConflict(
        "partnership", null,
        ContentValues().apply { put("a_id", minOf(x, y)); put("b_id", maxOf(x, y)) },
        SQLiteDatabase.CONFLICT_IGNORE,
    )
}

private fun SQLiteDatabase.parentsOf(child: Int): List<Int> {
    val out = ArrayList<Int>()
    rawQuery("SELECT parent_id FROM parent_link WHERE child_id = ?", arrayOf(child.toString())).use { while (it.moveToNext()) out += it.getInt(0) }
    return out
}

private fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)

private val EVENT_COLUMNS = "id, person_id, a_id, b_id, kind, detail, year, month, day, place, note"

private fun SQLiteDatabase.insertEvent(personId: Int?, a: Int?, b: Int?, event: Event) {
    val values = ContentValues().apply {
        if (personId != null) put("person_id", personId)
        if (a != null) put("a_id", a)
        if (b != null) put("b_id", b)
        put("kind", event.kind.key)
        put("detail", event.detail)
        event.date.year?.let { put("year", it) }
        event.date.month?.let { put("month", it) }
        event.date.day?.let { put("day", it) }
        put("place", event.place)
        put("note", event.note)
    }
    val id = insertOrThrow("event", null, values)
    for (s in event.sources) insertSource(null, id, s)
}

private fun SQLiteDatabase.insertSource(personId: Int?, eventId: Long?, source: Source) {
    val values = ContentValues().apply {
        if (personId != null) put("person_id", personId)
        if (eventId != null) put("event_id", eventId)
        put("title", source.title)
        put("citation", source.citation)
        put("note", source.note)
    }
    insertOrThrow("source", null, values)
}

/** Replaces a person's events and sources, and the events of each couple named in [details] (if the couple exists). */
private fun SQLiteDatabase.putDetails(personId: Int, details: PersonDetails) {
    delete("event", "person_id = ?", arrayOf(personId.toString()))
    delete("source", "person_id = ?", arrayOf(personId.toString()))
    delete("person_field", "person_id = ?", arrayOf(personId.toString()))
    for (fld in details.fields.map { it.clamped() }.filter { !it.isBlank }.take(Limits.FIELDS)) insertField(personId, fld)
    for (e in details.events.inDateOrder()) insertEvent(personId, null, null, e)
    for (s in details.sources.map { it.clamped() }.filter { !it.isBlank }.take(Limits.SOURCES)) insertSource(personId, null, s)
    for ((partner, events) in details.coupleEvents) putCoupleEvents(personId, partner, events)
}

private fun SQLiteDatabase.putCoupleEvents(x: Int, y: Int, events: List<Event>) {
    val a = minOf(x, y)
    val b = maxOf(x, y)
    val args = arrayOf(a.toString(), b.toString())
    val exists = rawQuery("SELECT 1 FROM partnership WHERE a_id = ? AND b_id = ?", args).use { it.moveToFirst() }
    if (!exists) return
    delete("event", "a_id = ? AND b_id = ?", args)
    for (e in events.inDateOrder()) insertEvent(null, a, b, e)
}

/** Stores a whole tree's events and sources; [map] turns the ids in [details] into the ids the people now have. */
private fun SQLiteDatabase.putTreeDetails(details: TreeDetails, map: (Int) -> Int) {
    for ((person, events) in details.events) for (e in events.inDateOrder()) insertEvent(map(person), null, null, e)
    for ((person, list) in details.fields) {
        for (fld in list.map { it.clamped() }.filter { !it.isBlank }.take(Limits.FIELDS)) insertField(map(person), fld)
    }
    for ((person, sources) in details.sources) {
        for (s in sources.map { it.clamped() }.filter { !it.isBlank }.take(Limits.SOURCES)) insertSource(map(person), null, s)
    }
    for ((pair, events) in details.couples) {
        val a = map(pair.first)
        val b = map(pair.second)
        putCoupleEvents(a, b, events)
    }
}

private fun eventFrom(c: Cursor, sources: Map<Long, List<Source>>): Event {
    val key = c.getString(4)
    val kind = EventKind.fromKey(key)
    return Event(
        kind = kind ?: EventKind.Custom,
        detail = c.getString(5).ifBlank { if (kind == null) key else "" },
        date = PartialDate(c.intOrNull(6), c.intOrNull(7), c.intOrNull(8)),
        place = c.getString(9),
        note = c.getString(10),
        sources = sources[c.getLong(0)].orEmpty(),
    )
}

private fun SQLiteDatabase.eventSources(where: String, args: Array<String>): Map<Long, List<Source>> {
    val out = LinkedHashMap<Long, MutableList<Source>>()
    rawQuery(
        "SELECT s.event_id, s.title, s.citation, s.note FROM source s JOIN event e ON e.id = s.event_id WHERE $where ORDER BY s.id",
        args,
    ).use { while (it.moveToNext()) out.getOrPut(it.getLong(0)) { mutableListOf() } += Source(it.getString(1), it.getString(2), it.getString(3)) }
    return out
}

/** One person's events, sources, and the events of the couples they are in with [partnerIds]. */
private fun SQLiteDatabase.readDetails(personId: Int, partnerIds: List<Int>): PersonDetails {
    val id = personId.toString()
    val sources = eventSources("e.person_id = ?", arrayOf(id))
    val events = ArrayList<Event>()
    rawQuery("SELECT $EVENT_COLUMNS FROM event WHERE person_id = ? ORDER BY id", arrayOf(id)).use { while (it.moveToNext()) events += eventFrom(it, sources) }
    val general = ArrayList<Source>()
    rawQuery("SELECT title, citation, note FROM source WHERE person_id = ? ORDER BY id", arrayOf(id)).use {
        while (it.moveToNext()) general += Source(it.getString(0), it.getString(1), it.getString(2))
    }
    val couples = LinkedHashMap<Int, List<Event>>()
    for (partner in partnerIds) {
        val a = minOf(personId, partner).toString()
        val b = maxOf(personId, partner).toString()
        val coupleSources = eventSources("e.a_id = ? AND e.b_id = ?", arrayOf(a, b))
        val list = ArrayList<Event>()
        rawQuery("SELECT $EVENT_COLUMNS FROM event WHERE a_id = ? AND b_id = ? ORDER BY id", arrayOf(a, b)).use {
            while (it.moveToNext()) list += eventFrom(it, coupleSources)
        }
        if (list.isNotEmpty()) couples[partner] = list
    }
    return PersonDetails(events, general, couples, readFields(personId))
}

/** Every event and source in the tree, for a backup or a GEDCOM file. */
private fun SQLiteDatabase.readTreeDetails(): TreeDetails {
    val sources = eventSources("1 = 1", emptyArray())
    val events = LinkedHashMap<Int, MutableList<Event>>()
    val couples = LinkedHashMap<Pair<Int, Int>, MutableList<Event>>()
    rawQuery("SELECT $EVENT_COLUMNS FROM event ORDER BY id", null).use {
        while (it.moveToNext()) {
            val e = eventFrom(it, sources)
            if (!it.isNull(1)) events.getOrPut(it.getInt(1)) { mutableListOf() } += e
            else couples.getOrPut(it.getInt(2) to it.getInt(3)) { mutableListOf() } += e
        }
    }
    val general = LinkedHashMap<Int, MutableList<Source>>()
    rawQuery("SELECT person_id, title, citation, note FROM source WHERE person_id IS NOT NULL ORDER BY id", null).use {
        while (it.moveToNext()) general.getOrPut(it.getInt(0)) { mutableListOf() } += Source(it.getString(1), it.getString(2), it.getString(3))
    }
    return TreeDetails(events, general, couples, readAllFields())
}

private fun SQLiteDatabase.insertField(personId: Int, field: CustomField) {
    insertOrThrow(
        "person_field", null,
        ContentValues().apply { put("person_id", personId); put("label", field.label); put("value", field.value) },
    )
}

private fun SQLiteDatabase.readFields(personId: Int): List<CustomField> {
    val out = ArrayList<CustomField>()
    rawQuery("SELECT label, value FROM person_field WHERE person_id = ? ORDER BY id", arrayOf(personId.toString())).use {
        while (it.moveToNext()) out += CustomField(it.getString(0), it.getString(1))
    }
    return out
}

private fun SQLiteDatabase.readAllFields(): Map<Int, List<CustomField>> {
    val out = LinkedHashMap<Int, MutableList<CustomField>>()
    rawQuery("SELECT person_id, label, value FROM person_field ORDER BY id", null).use {
        while (it.moveToNext()) out.getOrPut(it.getInt(0)) { mutableListOf() } += CustomField(it.getString(1), it.getString(2))
    }
    return out
}
