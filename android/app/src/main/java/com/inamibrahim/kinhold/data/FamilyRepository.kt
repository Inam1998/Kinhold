package com.inamibrahim.kinhold.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.inamibrahim.kinhold.model.BackupCodec
import com.inamibrahim.kinhold.model.BackupData
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.Person
import com.inamibrahim.kinhold.model.PersonDraft
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

    /** The new person is a child of [parentId] (and of that person's spouse, if they have one). */
    data class ChildOf(val parentId: Int) : Relation

    /** The new person is the partner of [personId]. */
    data class SpouseOf(val personId: Int) : Relation
}

/** A person just deleted, with who they were linked to, so the delete can be undone. */
class DeletedPerson(
    val person: Person,
    val parentIds: List<Int>,
    val childIds: List<Int>,
    val partnerIds: List<Int>,
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
    suspend fun addPerson(draft: PersonDraft, relation: Relation? = null): Int = changing { db ->
        val id = db.insertPerson(draft)
        when (relation) {
            null -> Unit
            is Relation.ParentOf -> {
                db.insertLink(child = relation.childId, parent = id)
                // If the child already had another parent, the two parents are a couple.
                for (other in db.parentsOf(relation.childId)) if (other != id) db.insertPartnership(id, other)
            }
            is Relation.ChildOf -> {
                db.insertLink(child = id, parent = relation.parentId)
                for (spouse in db.partnersOf(relation.parentId)) db.insertLink(child = id, parent = spouse)
            }
            is Relation.SpouseOf -> db.insertPartnership(relation.personId, id)
        }
        id
    }

    suspend fun updatePerson(id: Int, draft: PersonDraft) = changing { db ->
        db.update("person", draft.toValues(), "id = ?", arrayOf(id.toString()))
        Unit
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
        )
        db.delete("person", "id = ?", arrayOf(id.toString()))
        record
    }

    /** Puts a deleted person back with the same id and the same links to relatives who are still in the tree. */
    suspend fun restoreDeleted(deleted: DeletedPerson) = changing { db ->
        db.insertWithId(deleted.person.id, deleted.person.toDraft())
        for (parent in deleted.parentIds) if (db.exists(parent)) db.insertLink(child = deleted.person.id, parent = parent)
        for (child in deleted.childIds) if (db.exists(child)) db.insertLink(child = child, parent = deleted.person.id)
        for (partner in deleted.partnerIds) if (db.exists(partner)) db.insertPartnership(deleted.person.id, partner)
    }

    /** The whole tree as backup-file text. */
    suspend fun exportBackup(): String = lock.withLock { BackupCodec.encode(_family.value, Instant.now().toString()) }

    /**
     * Replaces the whole tree with [data]. The tree as it is now is first written to the safety copy, so a restore can
     * be undone, and the replacement happens in one transaction: either all of it is stored or none of it.
     */
    suspend fun replaceAll(data: BackupData) = changing { db ->
        writeSafetyCopy(_family.value)
        db.delete("person", null, null)
        for (p in data.people) db.insertWithId(p.id, p.toDraft())
        for ((child, parent) in data.parentLinks) db.insertLink(child, parent)
        for ((a, b) in data.partnerships) db.insertPartnership(a, b)
    }

    /** Whether a tree from before the last restore is kept. */
    fun hasSafetyCopy(): Boolean = safetyFile.isFile && safetyFile.length() > 0

    /** Brings back the tree from before the last restore. The tree being replaced becomes the new safety copy. */
    suspend fun undoRestore() {
        val saved = withContext(Dispatchers.IO) { BackupCodec.decode(safetyFile.readText()) }
        replaceAll(saved)
    }

    // Written beside the real file and renamed, so a crash can never leave a half-written safety copy.
    private fun writeSafetyCopy(family: Family) {
        if (family.size == 0) return
        val temp = File(safetyFile.parentFile, safetyFile.name + ".tmp")
        temp.writeText(BackupCodec.encode(family, Instant.now().toString()))
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
        db.rawQuery(
            "SELECT id, given, surname, sex, birth_year, death_year, birth_place, death_place, notes FROM person ORDER BY id",
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
                )
            }
        }
        return Family.of(people, db.pairs("SELECT child_id, parent_id FROM parent_link"), db.pairs("SELECT a_id, b_id FROM partnership"))
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
}

private fun SQLiteDatabase.insertPerson(draft: PersonDraft): Int = insertOrThrow("person", null, draft.toValues()).toInt()

private fun SQLiteDatabase.insertWithId(id: Int, draft: PersonDraft) {
    insertOrThrow("person", null, draft.toValues().apply { put("id", id) })
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

private fun SQLiteDatabase.partnersOf(person: Int): List<Int> {
    val out = ArrayList<Int>()
    rawQuery(
        "SELECT CASE WHEN a_id = ?1 THEN b_id ELSE a_id END FROM partnership WHERE a_id = ?1 OR b_id = ?1",
        arrayOf(person.toString()),
    ).use { while (it.moveToNext()) out += it.getInt(0) }
    return out
}
