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

enum class ToolRisk {
    AUTO,
    CONFIRM,
    LOCKED
}

enum class ToolStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}

enum class Role {
    USER,
    ASSISTANT
}

sealed interface Block {
    data class Text(val content: String) : Block

    data class ToolCall(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val label: String,
        val params: String,
        val risk: ToolRisk,
        val status: ToolStatus = ToolStatus.PENDING,
        val result: String? = null
    ) : Block

    data class MemoryEvent(
        val action: String,
        val content: String,
        val importance: String = "normal"
    ) : Block

    data class Note(val content: String) : Block

    data class Error(
        val title: String,
        val message: String
    ) : Block
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val blocks: List<Block>,
    val timestamp: Long = System.currentTimeMillis()
)

data class ApiToolCall(
    val id: String,
    val name: String,
    val paramsJson: String
)

data class ApiMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ApiToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
)

data class SuggestionItem(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val title: String,
    val query: String
)