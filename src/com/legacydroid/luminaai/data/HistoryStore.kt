/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistorySession(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class HistoryEntry(
    val id: Long,
    val sessionId: Long,
    val role: String,
    val content: String,
    val createdAt: Long
)

object HistoryStore {

    private const val DB_NAME = "lumina_history.db"
    private const val DB_VERSION = 1

    private class Db(context: Context) :
        SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE sessions(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT NOT NULL DEFAULT ''," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE entries(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "session_id INTEGER NOT NULL," +
                    "role TEXT NOT NULL," +
                    "content TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX idx_entries_session ON entries(session_id, id)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS entries")
            db.execSQL("DROP TABLE IF EXISTS sessions")
            onCreate(db)
        }
    }

    fun createSession(context: Context): Long {
        val db = Db(context).writableDatabase
        return try {
            val now = System.currentTimeMillis()
            val values = android.content.ContentValues().apply {
                put("title", "")
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } finally {
            db.close()
        }
    }

    fun addEntry(context: Context, sessionId: Long, role: String, content: String): Boolean {
        if (sessionId < 0 || content.isBlank()) return false
        val db = Db(context).writableDatabase
        return try {
            val now = System.currentTimeMillis()
            val values = android.content.ContentValues().apply {
                put("session_id", sessionId)
                put("role", role)
                put("content", content.trim())
                put("created_at", now)
            }
            val rowId = db.insert("entries", null, values)
            if (rowId > 0) {
                val upd = android.content.ContentValues().apply { put("updated_at", now) }
                db.update("sessions", upd, "id = ?", arrayOf(sessionId.toString()))
            }
            rowId > 0
        } finally {
            db.close()
        }
    }

    fun setTitle(context: Context, sessionId: Long, title: String) {
        if (sessionId < 0) return
        val db = Db(context).writableDatabase
        try {
            val values = android.content.ContentValues().apply {
                put("title", title.trim().take(80))
            }
            db.update("sessions", values, "id = ?", arrayOf(sessionId.toString()))
        } finally {
            db.close()
        }
    }

    fun listSessions(context: Context): List<HistorySession> {
        val db = Db(context).readableDatabase
        return try {
            val cursor = db.rawQuery(
                "SELECT id, title, created_at, updated_at FROM sessions ORDER BY updated_at DESC LIMIT 100",
                null
            )
            val out = mutableListOf<HistorySession>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    out += HistorySession(
                        id = c.getLong(0),
                        title = c.getString(1) ?: "",
                        createdAt = c.getLong(2),
                        updatedAt = c.getLong(3)
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        } finally {
            db.close()
        }
    }

    fun entries(context: Context, sessionId: Long): List<HistoryEntry> {
        if (sessionId < 0) return emptyList()
        val db = Db(context).readableDatabase
        return try {
            val cursor = db.rawQuery(
                "SELECT id, session_id, role, content, created_at FROM entries " +
                    "WHERE session_id = ? ORDER BY id ASC",
                arrayOf(sessionId.toString())
            )
            val out = mutableListOf<HistoryEntry>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    out += HistoryEntry(
                        id = c.getLong(0),
                        sessionId = c.getLong(1),
                        role = c.getString(2) ?: "user",
                        content = c.getString(3) ?: "",
                        createdAt = c.getLong(4)
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        } finally {
            db.close()
        }
    }

    fun deleteSession(context: Context, sessionId: Long) {
        val db = Db(context).writableDatabase
        try {
            db.delete("entries", "session_id = ?", arrayOf(sessionId.toString()))
            db.delete("sessions", "id = ?", arrayOf(sessionId.toString()))
        } finally {
            db.close()
        }
    }

    fun clearAll(context: Context) {
        val db = Db(context).writableDatabase
        try {
            db.delete("entries", null, null)
            db.delete("sessions", null, null)
        } finally {
            db.close()
        }
    }
}
