/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MemoryEntry(
    val id: String,
    val content: String,
    val importance: String,
    val source: String,
    val createdAt: Long
)

object MemoryStore {

    private const val PREFS = "lumina_memory"
    private const val KEY_MEMORIES = "memories"
    private const val MAX_MEMORIES = 200

    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<MemoryEntry> {
        val raw = prefs().getString(KEY_MEMORIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MemoryEntry(
                    id = o.optString("id"),
                    content = o.optString("content"),
                    importance = o.optString("importance", "normal"),
                    source = o.optString("source", "manual"),
                    createdAt = o.optLong("createdAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(entry: MemoryEntry) {
        val list = load().filter { it.id != entry.id }.toMutableList()
        list.add(0, entry.copy(importance = normalizeImportance(entry.importance)))
        if (list.size > MAX_MEMORIES) list.subList(MAX_MEMORIES, list.size).clear()
        persist(list)
    }

    @Synchronized
    fun delete(id: String) {
        persist(load().filter { it.id != id })
    }

    @Synchronized
    fun deleteAll() {
        persist(emptyList())
    }

    @Synchronized
    fun search(query: String): List<MemoryEntry> {
        val q = query.trim()
        if (q.isEmpty()) return load()
        val terms = q.lowercase().split(Regex("\\s+"))
        return load().filter { e ->
            val hay = e.content.lowercase()
            terms.all { hay.contains(it) }
        }
    }

    fun normalizeImportance(value: String): String {
        return when (value.trim().lowercase()) {
            "high" -> "high"
            "low" -> "low"
            else -> "normal"
        }
    }

    fun topMemories(limit: Int = 8): List<MemoryEntry> {
        val order = mapOf("high" to 0, "normal" to 1, "low" to 2)
        return load()
            .sortedWith(compareBy({ order[it.importance] ?: 1 }, { -it.createdAt }))
            .take(limit)
    }

    fun summary(limit: Int = 8): String {
        val mems = topMemories(limit)
        if (mems.isEmpty()) return ""
        return mems.joinToString("\n") { "- [${it.importance}] ${it.content}" }
    }

    private fun persist(list: List<MemoryEntry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("content", e.content)
                    .put("importance", e.importance)
                    .put("source", e.source)
                    .put("createdAt", e.createdAt)
            )
        }
        prefs().edit().putString(KEY_MEMORIES, arr.toString()).apply()
    }
}