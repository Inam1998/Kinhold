package com.inamibrahim.kinhold.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The tree on disk. SQLite writes are atomic, so a save that is interrupted leaves the tree as it was, and everything
 * the repository changes at once happens inside one transaction. Pass a null [name] for a throwaway in-memory database.
 */
class FamilyDb(context: Context, name: String?) : SQLiteOpenHelper(context, name, null, VERSION) {
    init {
        // Write-ahead logging keeps a reader from blocking a writer and survives a crash mid-write.
        if (name != null) setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Deleting a person removes their links, so no link can point at someone who is gone.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE person (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                given TEXT NOT NULL DEFAULT '',
                surname TEXT NOT NULL DEFAULT '',
                sex INTEGER NOT NULL DEFAULT 2,
                birth_year INTEGER,
                death_year INTEGER,
                birth_place TEXT NOT NULL DEFAULT '',
                death_place TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                birth_month INTEGER,
                birth_day INTEGER,
                death_month INTEGER,
                death_day INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE parent_link (
                child_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
                parent_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
                PRIMARY KEY (child_id, parent_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX parent_link_parent ON parent_link(parent_id)")
        // One row per couple, stored with the smaller id first so the same couple cannot be added twice.
        db.execSQL(
            """
            CREATE TABLE partnership (
                a_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
                b_id INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
                PRIMARY KEY (a_id, b_id),
                CHECK (a_id < b_id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX partnership_b ON partnership(b_id)")
        createPhotoTable(db)
        createEventTables(db)
    }

    /** Photos live in their own table so the main query never reads their bytes; deleting a person deletes theirs. */
    private fun createPhotoTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE photo (
                person_id INTEGER PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE,
                stamp INTEGER NOT NULL,
                data BLOB NOT NULL,
                thumb BLOB NOT NULL
            )
            """.trimIndent(),
        )
    }

    /**
     * Events belong to one person (person_id) or to one couple (a_id, b_id), never both; deleting the person or the couple
     * deletes them. A source is a citation for one event or for a person in general. Rows are stored in the order they
     * are shown, so reading by id gives that order.
     */
    private fun createEventTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE event (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                person_id INTEGER REFERENCES person(id) ON DELETE CASCADE,
                a_id INTEGER,
                b_id INTEGER,
                kind TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT '',
                year INTEGER,
                month INTEGER,
                day INTEGER,
                place TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (a_id, b_id) REFERENCES partnership(a_id, b_id) ON DELETE CASCADE,
                CHECK ((person_id IS NOT NULL AND a_id IS NULL AND b_id IS NULL) OR (person_id IS NULL AND a_id IS NOT NULL AND b_id IS NOT NULL))
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX event_person ON event(person_id)")
        db.execSQL("CREATE INDEX event_couple ON event(a_id, b_id)")
        db.execSQL(
            """
            CREATE TABLE source (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                person_id INTEGER REFERENCES person(id) ON DELETE CASCADE,
                event_id INTEGER REFERENCES event(id) ON DELETE CASCADE,
                title TEXT NOT NULL DEFAULT '',
                citation TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                CHECK ((person_id IS NOT NULL) <> (event_id IS NOT NULL))
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX source_person ON source(person_id)")
        db.execSQL("CREATE INDEX source_event ON source(event_id)")
    }

    /**
     * Each step runs inside the transaction SQLite opens for an upgrade, so a failed step leaves the old tree as it was.
     * Never edit a step that has shipped; add the next one, with a test that upgrades a database of the older version.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createPhotoTable(db)
        if (oldVersion < 3) {
            for (column in listOf("birth_month", "birth_day", "death_month", "death_day")) {
                db.execSQL("ALTER TABLE person ADD COLUMN $column INTEGER")
            }
            createEventTables(db)
        }
    }

    companion object {
        const val VERSION = 3
        const val FILE_NAME = "kinhold.db"
    }
}
