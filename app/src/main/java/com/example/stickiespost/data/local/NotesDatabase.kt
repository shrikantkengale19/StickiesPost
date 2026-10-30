package com.example.stickiespost.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NoteEntity::class, RecurringTaskEntity::class],
    version = 5,
    exportSchema = false
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun recurringTaskDao(): RecurringTaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_items RENAME TO note_items_v1")
                db.execSQL(
                    """
                    CREATE TABLE note_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        text TEXT NOT NULL,
                        category TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        reminderAtMillis INTEGER,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO note_items (id, text, category, createdAtMillis, reminderAtMillis, status)
                    SELECT id, text, category, createdAtMillis, reminderAtMillis,
                        CASE WHEN isCompleted = 1 THEN 'CLOSED' ELSE 'OPEN' END
                    FROM note_items_v1
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE note_items_v1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        weekday INTEGER,
                        dayOfMonth INTEGER,
                        nextReminderAtMillis INTEGER NOT NULL,
                        isFulfilled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_items ADD COLUMN remoteId TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_items ADD COLUMN ownerUserId TEXT")
            }
        }
    }
}
