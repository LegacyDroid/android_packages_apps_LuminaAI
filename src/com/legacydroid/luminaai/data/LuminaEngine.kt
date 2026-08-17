/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

/**
 * Canned responses until the real AI backend lands (M3). Keeps the chat
 * experience demoable without network access.
 */
object LuminaEngine {

    fun getResponse(query: String): String = when {
        query.contains("weather", ignoreCase = true) ->
            "I can't reach the weather service yet, but once my backend is wired in, " +
                "I'll pull a live forecast for your location."
        query.contains("summar", ignoreCase = true) ->
            "Summaries are on my roadmap. Right now I'm running on a demo brain, " +
                "so I can't read your notifications yet."
        query.contains("music", ignoreCase = true) ->
            "I'd love to queue some focus music. Once audio control lands, " +
                "just say the word."
        query.contains("remind", ignoreCase = true) ->
            "Reminders need system access I don't have yet. Check back after the " +
                "deep integration milestone."
        query.contains("joke", ignoreCase = true) ->
            "Why did the AI go to therapy? Too many unresolved merge conflicts."
        query.contains("inspir", ignoreCase = true) ->
            "Keep building. Every shipped pixel of your ROM is a step toward " +
                "something real."
        else ->
            "I'm running the LuminaUI demo build, so I don't have real answers yet. " +
                "Ask me about weather, summaries, music, reminders, a joke, or " +
                "an inspiring thought."
    }
}