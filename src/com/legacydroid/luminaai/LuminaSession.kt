/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.legacydroid.luminaai.data.LuminaEngine
import com.legacydroid.luminaai.model.ChatMessage
import com.legacydroid.luminaai.model.LuminaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Singleton Compose state holder shared by the overlay window and the service.
 * The orchestration timings mirror the HTML/CSS prototype: overlay 120ms after
 * the ripple begins, greeting 2400ms after awakening, 700ms close fade.
 */
object LuminaSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var state by mutableStateOf(LuminaState.IDLE)
        private set
    var shockwaveToken by mutableLongStateOf(0L)
        private set
    val messages = mutableStateListOf<ChatMessage>()
    var isThinking by mutableStateOf(false)
        private set

    /** Called by the service once the overlay window is torn down (state idle). */
    var onIdle: (() -> Unit)? = null

    fun awaken() {
        if (state == LuminaState.AWAKENED) return
        shockwaveToken = System.currentTimeMillis()
        scope.launch {
            delay(120)
            state = LuminaState.AWAKENED
            if (messages.isEmpty()) {
                delay(2400)
                messages += ChatMessage(
                    text = "Hi, I'm Lumina. How can I help you today?", isUser = false)
            }
        }
    }

    fun dismiss() {
        if (state != LuminaState.AWAKENED) return
        scope.launch {
            state = LuminaState.CLOSING
            delay(700)
            messages.clear()
            state = LuminaState.IDLE
            onIdle?.invoke()
        }
    }

    fun clearChat() {
        messages.clear()
        messages += ChatMessage(
            text = "Conversation cleared. How can I assist you?", isUser = false)
    }

    fun sendMessage(query: String) {
        if (query.isBlank()) return
        messages += ChatMessage(text = query, isUser = true)
        isThinking = true
        scope.launch {
            delay(280)
            val response = LuminaEngine.getResponse(query)
            delay(1000)
            isThinking = false
            messages += ChatMessage(text = response, isUser = false)
        }
    }
}