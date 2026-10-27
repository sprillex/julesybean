package com.julesybean.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class PromptSortType {
    ALPHABETICAL,
    RECENTLY_USED,
    DATE_CREATED
}

class PromptDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "prompt_manager.db"
        const val DATABASE_VERSION = 1

        const val TABLE_SAVED_PROMPTS = "saved_prompts"
        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_LAST_USED_AT = "last_used_at"
        const val COLUMN_SORT_ORDER = "sort_order"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_SAVED_PROMPTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_LAST_USED_AT INTEGER,
                $COLUMN_SORT_ORDER INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic for future database versions
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SAVED_PROMPTS")
        onCreate(db)
    }

    fun insertPrompt(prompt: Prompt): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, prompt.title)
            put(COLUMN_CONTENT, prompt.content)
            put(COLUMN_CREATED_AT, prompt.createdAt)
            put(COLUMN_UPDATED_AT, prompt.updatedAt)
            if (prompt.lastUsedAt != null) {
                put(COLUMN_LAST_USED_AT, prompt.lastUsedAt)
            } else {
                putNull(COLUMN_LAST_USED_AT)
            }
            put(COLUMN_SORT_ORDER, prompt.sortOrder)
        }
        return db.insert(TABLE_SAVED_PROMPTS, null, values)
    }

    fun updatePrompt(prompt: Prompt): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, prompt.title)
            put(COLUMN_CONTENT, prompt.content)
            put(COLUMN_UPDATED_AT, prompt.updatedAt)
            if (prompt.lastUsedAt != null) {
                put(COLUMN_LAST_USED_AT, prompt.lastUsedAt)
            } else {
                putNull(COLUMN_LAST_USED_AT)
            }
            put(COLUMN_SORT_ORDER, prompt.sortOrder)
        }
        return db.update(
            TABLE_SAVED_PROMPTS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(prompt.id.toString())
        )
    }

    fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis()): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LAST_USED_AT, timestamp)
        }
        return db.update(
            TABLE_SAVED_PROMPTS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun deletePrompt(id: Long): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_SAVED_PROMPTS,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun getPromptById(id: Long): Prompt? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SAVED_PROMPTS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                parsePrompt(it)
            } else {
                null
            }
        }
    }

    fun getAllPrompts(query: String? = null, sortType: PromptSortType = PromptSortType.DATE_CREATED): List<Prompt> {
        val db = readableDatabase
        val prompts = mutableListOf<Prompt>()

        var selection: String? = null
        var selectionArgs: Array<String>? = null

        if (!query.isNullOrBlank()) {
            val trimmedQuery = query.trim()
            selection = "$COLUMN_TITLE LIKE ? OR $COLUMN_CONTENT LIKE ?"
            selectionArgs = arrayOf("%$trimmedQuery%", "%$trimmedQuery%")
        }

        val orderBy = when (sortType) {
            PromptSortType.ALPHABETICAL -> "$COLUMN_TITLE COLLATE NOCASE ASC"
            PromptSortType.RECENTLY_USED -> "COALESCE($COLUMN_LAST_USED_AT, $COLUMN_UPDATED_AT) DESC"
            PromptSortType.DATE_CREATED -> "$COLUMN_CREATED_AT DESC"
        }

        val cursor = db.query(
            TABLE_SAVED_PROMPTS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy
        )

        cursor.use {
            while (it.moveToNext()) {
                prompts.add(parsePrompt(it))
            }
        }

        return prompts
    }

    private fun parsePrompt(cursor: Cursor): Prompt {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE))
        val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
        val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
        val updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT))

        val lastUsedIdx = cursor.getColumnIndexOrThrow(COLUMN_LAST_USED_AT)
        val lastUsedAt = if (cursor.isNull(lastUsedIdx)) null else cursor.getLong(lastUsedIdx)

        val sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SORT_ORDER))

        return Prompt(
            id = id,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastUsedAt = lastUsedAt,
            sortOrder = sortOrder
        )
    }
}
