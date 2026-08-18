/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.CallLog
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.Volatile

object LocalSearchIndex {

    private const val DB_NAME = "lumina_search.db"
    private const val DB_VERSION = 1
    private const val REINDEX_INTERVAL_MS = 60_000L

    private var helper: SearchDbHelper? = null
    private var contextRef: Context? = null
    private var lastIndexedAt = 0L
    @Volatile
    private var dirty = false

    fun init(ctx: Context) {
        if (helper != null) return
        contextRef = ctx.applicationContext
        helper = SearchDbHelper(ctx.applicationContext)
        ctx.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, SearchObserver())
        ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, SearchObserver())
    }

    private fun ensureFresh(db: SQLiteDatabase) {
        if (System.currentTimeMillis() - lastIndexedAt < REINDEX_INTERVAL_MS && !dirty) return
        rebuild(db)
        dirty = false
        lastIndexedAt = System.currentTimeMillis()
    }

    private fun rebuild(db: SQLiteDatabase) {
        val ctx = contextRef ?: return
        db.execSQL("DELETE FROM messages")
        val values = ContentValues()
        runCatching {
            val cursor = ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null, null
            )
            cursor?.use { c ->
                val idI = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrI = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyI = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateI = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val body = c.getString(bodyI) ?: continue
                    values.clear()
                    values.put("body", body)
                    values.put("sender", c.getString(addrI) ?: "?")
                    values.put("ts", c.getLong(dateI))
                    values.put("kind", "sms")
                    db.insert("messages", null, values)
                }
            }
        }
        runCatching {
            val cursor = ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE, CallLog.Calls.TYPE
                ),
                null, null, null
            )
            cursor?.use { c ->
                val numI = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameI = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val dateI = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val typeI = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (c.moveToNext()) {
                    val num = c.getString(numI) ?: continue
                    val name = c.getString(nameI)
                    val type = when (c.getInt(typeI)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "call"
                    }
                    values.clear()
                    values.put("body", "$type call ${name ?: num}")
                    values.put("sender", name ?: num)
                    values.put("ts", c.getLong(dateI))
                    values.put("kind", "call")
                    db.insert("messages", null, values)
                }
            }
        }
    }

    fun search(query: String, limit: Int = 8): JSONObject {
        val db = helper?.readableDatabase ?: return JSONObject().put("results", JSONArray())
        ensureFresh(db)
        val matchQuery = query.trim()
            .split(Regex("\\s+"))
            .joinToString(" AND ") { term ->
                val escaped = term.replace("\"", "\"\"")
                "\"$escaped\"*"
            }
        val arr = JSONArray()
        runCatching {
            val cursor = db.rawQuery(
                "SELECT body, sender, ts, kind FROM messages WHERE messages MATCH ? ORDER BY rank LIMIT ?",
                arrayOf(matchQuery, limit.toString())
            )
            cursor.use { c ->
                val bodyI = c.getColumnIndexOrThrow("body")
                val senderI = c.getColumnIndexOrThrow("sender")
                val tsI = c.getColumnIndexOrThrow("ts")
                val kindI = c.getColumnIndexOrThrow("kind")
                while (c.moveToNext()) {
                    arr.put(
                        JSONObject()
                            .put("kind", c.getString(kindI))
                            .put("sender", c.getString(senderI))
                            .put("date", c.getLong(tsI))
                            .put("body", c.getString(bodyI))
                    )
                }
            }
        }
        return JSONObject().put("results", arr)
    }

    private class SearchObserver : android.database.ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            dirty = true
        }
    }

    private class SearchDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE VIRTUAL TABLE messages USING fts5(body, sender UNINDEXED, ts UNINDEXED, kind UNINDEXED)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}