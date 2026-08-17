/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.model

import java.util.UUID

enum class LuminaState {
    IDLE,
    AWAKENED,
    CLOSING
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class SuggestionItem(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val title: String,
    val query: String
)