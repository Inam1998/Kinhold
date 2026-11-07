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
                notes TEXT NOT NULL DEFAULT ''
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first release of the format. Each later version adds a step here, with a test.
    }

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "kinhold.db"
    }
}
