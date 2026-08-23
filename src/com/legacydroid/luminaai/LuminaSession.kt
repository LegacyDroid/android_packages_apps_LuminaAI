/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.legacydroid.luminaai.data.HistorySession
import com.legacydroid.luminaai.data.HistoryStore
import com.legacydroid.luminaai.data.LumiApi
import com.legacydroid.luminaai.data.LumiResult
import com.legacydroid.luminaai.data.MemoryEntry
import com.legacydroid.luminaai.data.MemoryStore
import com.legacydroid.luminaai.data.NotificationHub
import com.legacydroid.luminaai.data.PrivacyAudit
import com.legacydroid.luminaai.data.ToolRegistry
import com.legacydroid.luminaai.model.ApiMessage
import com.legacydroid.luminaai.model.ApiToolCall
import com.legacydroid.luminaai.model.Block
import com.legacydroid.luminaai.model.ChatMessage
import com.legacydroid.luminaai.model.LuminaState
import com.legacydroid.luminaai.model.Role
import com.legacydroid.luminaai.model.ToolStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.jvm.Volatile

object LuminaSession {

    private const val MAX_LOOP_ROUNDS = 6
    private const val MAX_HISTORY_MESSAGES = 20

    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var state by mutableStateOf(LuminaState.IDLE)
        private set
    var shockwaveToken by mutableLongStateOf(0L)
        private set
    val messages = mutableStateListOf<ChatMessage>()
    var isThinking by mutableStateOf(false)
        private set
    var pendingApproval by mutableStateOf<Block.ToolCall?>(null)
        private set
    var memories by mutableStateOf(MemoryStore.load())
        private set
    var engineLabel by mutableStateOf("")
        private set

    var onIdle: (() -> Unit)? = null

    private var runningJob: Job? = null
    private var memoryInjectedThisSession = false
    private var activeGate: ApprovalGate? = null

    @Volatile
    private var sessionGeneration = 0

    @Volatile
    private var historySessionId = -1L

    fun init(ctx: Context) {
        context = ctx.applicationContext
        refreshMemories()
        refreshEngineLabel()
    }

    private fun refreshEngineLabel() {
        val config = runCatching { LumiApi.readConfig(context) }.getOrNull() ?: return
        engineLabel = when (config.provider) {
            "custom" -> config.customModel.ifBlank { config.model }
            else -> config.model
        }
    }

    fun awaken() {
        if (state == LuminaState.AWAKENED) {
            shockwaveToken = System.currentTimeMillis()
            return
        }
        shockwaveToken = System.currentTimeMillis()
        sessionGeneration++
        runningJob?.cancel()
        isThinking = false
        pendingApproval = null
        activeGate = null
        finalizeHistory()
        messages.clear()
        memoryInjectedThisSession = false
        refreshEngineLabel()
        val gen = sessionGeneration
        scope.launch {
            delay(120)
            if (sessionGeneration != gen) return@launch
            state = LuminaState.AWAKENED
            if (messages.isEmpty()) {
                delay(2400)
                if (sessionGeneration != gen) return@launch
                if (messages.isEmpty()) {
                    greeting()
                }
            }
        }
    }

    private suspend fun greeting() {
        val violations = PrivacyAudit.audit(context)
        val list = violations.optJSONArray("violations")
        if (list != null && list.length() > 0) {
            val sb = StringBuilder(
                "I just ran my background privacy audit and found a few things you'll want to see, Boss.\n\n"
            )
            val suggestionBlocks = mutableListOf<Block>()
            for (i in 0 until list.length().coerceAtMost(3)) {
                val v = list.getJSONObject(i)
                val app = v.optString("app")
                val perm = v.optString("permission")
                val count = v.optInt("background_accesses")
                sb.append("• ${app} accessed ${perm} $count times in the background over the last 24h\n")
                suggestionBlocks += Block.ToolCall(
                    name = "revoke_permission",
                    label = "Revoke $perm from $app",
                    params = JSONObject()
                        .put("app", v.optString("package"))
                        .put("permission", perm)
                        .toString(),
                    risk = com.legacydroid.luminaai.model.ToolRisk.CONFIRM,
                    status = ToolStatus.PENDING
                )
            }
            sb.append("\nShould I revoke any of these? Tap a card to confirm. ( > ᴗ < )")
            messages += ChatMessage(
                role = Role.ASSISTANT,
                blocks = listOf(Block.Text(sb.toString())) + suggestionBlocks
            )
        } else {
            messages += ChatMessage(
                role = Role.ASSISTANT,
                blocks = listOf(
                    Block.Text("Hi, I'm Lumina. How can I help you today? ( ˘ω˘ )✨")
                )
            )
        }
    }

    fun dismiss() {
        if (state != LuminaState.AWAKENED) return
        val gen = sessionGeneration
        runningJob?.cancel()
        isThinking = false
        pendingApproval = null
        finalizeHistory()
        state = LuminaState.CLOSING
        scope.launch {
            delay(700)
            if (sessionGeneration != gen) return@launch
            messages.clear()
            memoryInjectedThisSession = false
            state = LuminaState.IDLE
            onIdle?.invoke()
        }
    }

    private fun finalizeHistory() {
        val sid = historySessionId
        historySessionId = -1L
        if (sid < 0) return
        scope.launch(Dispatchers.IO) {
            val entries = runCatching { HistoryStore.entries(context, sid) }.getOrDefault(emptyList())
            if (entries.isEmpty()) {
                runCatching { HistoryStore.deleteSession(context, sid) }
                return@launch
            }
            val titled = runCatching {
                HistoryStore.listSessions(context).any { it.id == sid && it.title.isNotBlank() }
            }.getOrDefault(false)
            if (titled) return@launch
            val transcript = entries.joinToString("\n") { e ->
                "${if (e.role == "user") "User" else "Lumina"}: ${e.content.take(400)}"
            }
            var title = runCatching {
                LumiApi.summarizeTitle(context, transcript.take(4000))
            }.getOrDefault("")
            if (title.isBlank()) {
                title = entries.firstOrNull { it.role == "user" }?.content?.take(48) ?: ""
            }
            if (title.isNotBlank()) {
                runCatching { HistoryStore.setTitle(context, sid, title) }
            }
        }
    }

    fun clearChat() {
        runningJob?.cancel()
        isThinking = false
        pendingApproval = null
        messages.clear()
        memoryInjectedThisSession = false
        messages += ChatMessage(
            role = Role.ASSISTANT,
            blocks = listOf(Block.Text("Conversation cleared. How can I assist you? ( ˘ω˘ )"))
        )
    }

    fun sendMessage(query: String) {
        if (query.isBlank() || isThinking || state != LuminaState.AWAKENED) return
        val gate = ApprovalGate()
        messages += ChatMessage(role = Role.USER, blocks = listOf(Block.Text(query)))
        runningJob = scope.launch {
            isThinking = true
            try {
                agentLoop(gate)
                persistExchange(query)
            } finally {
                isThinking = false
                pendingApproval = null
            }
        }
    }

    fun cancelGeneration() {
        runningJob?.cancel()
        runningJob = null
        isThinking = false
        pendingApproval = null
        activeGate = null
    }

    fun rerunLast() {
        if (isThinking || state != LuminaState.AWAKENED) return
        val idx = messages.indexOfLast { msg ->
            msg.role == Role.USER &&
                msg.blocks.any { it is Block.Text && it.content.isNotBlank() }
        }
        if (idx < 0) return
        val text = messages[idx].blocks
            .filterIsInstance<Block.Text>()
            .joinToString("\n") { it.content }
        if (text.isBlank()) return
        messages.subList(idx, messages.size).clear()
        sendMessage(text)
    }

    fun listHistory(): List<HistorySession> =
        runCatching { HistoryStore.listSessions(context) }.getOrDefault(emptyList())

    fun resumeHistory(sessionId: Long): Boolean {
        if (state != LuminaState.AWAKENED || isThinking) return false
        val entries = runCatching { HistoryStore.entries(context, sessionId) }
            .getOrDefault(emptyList())
        if (entries.isEmpty()) return false
        runningJob?.cancel()
        pendingApproval = null
        isThinking = false
        activeGate = null
        messages.clear()
        for (e in entries) {
            messages += ChatMessage(
                role = if (e.role == "user") Role.USER else Role.ASSISTANT,
                blocks = listOf(Block.Text(e.content))
            )
        }
        historySessionId = sessionId
        memoryInjectedThisSession = true
        return true
    }

    fun deleteHistorySession(sessionId: Long) {
        scope.launch(Dispatchers.IO) {
            runCatching { HistoryStore.deleteSession(context, sessionId) }
        }
    }

    fun clearHistory() {
        scope.launch(Dispatchers.IO) {
            runCatching { HistoryStore.clearAll(context) }
        }
    }

    private fun persistExchange(query: String) {
        val last = messages.lastOrNull { it.role == Role.ASSISTANT } ?: return
        if (last.blocks.any { it is Block.Error }) return
        val answer = last.blocks
            .filterIsInstance<Block.Text>()
            .joinToString("\n") { it.content }
        if (answer.isBlank()) return
        scope.launch(Dispatchers.IO) {
            var sid = historySessionId
            if (sid < 0) {
                sid = runCatching { HistoryStore.createSession(context) }.getOrDefault(-1L)
                historySessionId = sid
            }
            if (sid < 0) return@launch
            runCatching { HistoryStore.addEntry(context, sid, "user", query) }
            runCatching { HistoryStore.addEntry(context, sid, "assistant", answer) }
        }
    }

    private suspend fun agentLoop(gate: ApprovalGate) {
        var rounds = 0
        while (true) {
            if (++rounds > MAX_LOOP_ROUNDS) {
                appendAssistant(
                    listOf(
                        Block.Error(
                            "Tool limit reached",
                            "Lumina stopped after $MAX_LOOP_ROUNDS tool rounds. Try a simpler request."
                        )
                    )
                )
                return
            }
            val apiMessages = buildApiMessages()
            val tools = ToolRegistry.enabledToolsJson()
            when (val result = LumiApi.chat(context, apiMessages, tools, ToolRegistry.protocolSystemPrompt())) {
                is LumiResult.Success -> {
                    val protocol = parseProtocolReply(result.reply)
                    if (protocol == null) {
                        appendAssistant(listOf(Block.Text(result.reply)))
                        return
                    }
                    val blocks = mutableListOf<Block>()
                    if (protocol.message.isNotBlank()) blocks += Block.Text(protocol.message)
                    if (protocol.calls.isEmpty()) {
                        appendAssistant(blocks)
                        return
                    }
                    appendAssistant(blocks)
                    handleToolCalls(protocol.calls, gate, appendResultNotes = true)
                }
                is LumiResult.ToolCalls -> {
                    val continueLoop = handleToolCalls(result.calls, gate)
                    if (!continueLoop) return
                }
                is LumiResult.RateLimited -> {
                    appendAssistant(
                        listOf(
                            Block.Error(
                                "Slow down, Boss",
                                result.message.ifBlank { "Rate limit reached. Wait a minute and retry." }
                            )
                        )
                    )
                    return
                }
                is LumiResult.TrialExpired -> {
                    appendAssistant(
                        listOf(
                            Block.Error("Trial expired", result.message)
                        )
                    )
                    return
                }
                is LumiResult.Error -> {
                    appendAssistant(listOf(Block.Error("Connection problem", result.message)))
                    return
                }
            }
        }
    }

    private suspend fun handleToolCalls(
        calls: List<ApiToolCall>,
        gate: ApprovalGate,
        appendResultNotes: Boolean = false
    ): Boolean {
        val assistantBlocks = mutableListOf<Block>()
        val protocolNotes = mutableListOf<String>()
        for (call in calls) {
            val spec = ToolRegistry.find(call.name)
            val block = when {
                spec == null -> Block.ToolCall(
                    name = call.name,
                    label = call.name.replace('_', ' '),
                    params = call.paramsJson,
                    risk = com.legacydroid.luminaai.model.ToolRisk.AUTO,
                    status = ToolStatus.FAILED,
                    result = """{"success":false,"error":"unknown tool '${call.name}'"}""",
                    protocol = appendResultNotes
                )
                !ToolRegistry.isEnabled(spec) -> Block.ToolCall(
                    name = spec.name,
                    label = spec.label,
                    params = call.paramsJson,
                    risk = spec.risk,
                    status = ToolStatus.FAILED,
                    result = """{"success":false,"error":"tool '${spec.name}' is disabled in Settings"}""",
                    protocol = appendResultNotes
                )
                spec.risk == com.legacydroid.luminaai.model.ToolRisk.LOCKED && !ToolRegistry.isDevMode() ->
                    Block.ToolCall(
                        name = spec.name,
                        label = spec.label,
                        params = call.paramsJson,
                        risk = spec.risk,
                        status = ToolStatus.FAILED,
                        result = """{"success":false,"error":"tool '${spec.name}' is locked; enable Unsafe Developer Mode in Settings"}""",
                        protocol = appendResultNotes
                    )
                else -> Block.ToolCall(
                    name = spec.name,
                    label = spec.label,
                    params = call.paramsJson,
                    risk = spec.risk,
                    status = ToolStatus.PENDING,
                    protocol = appendResultNotes
                )
            }
            assistantBlocks += block
        }
        appendAssistant(assistantBlocks)

        for (block in assistantBlocks.filterIsInstance<Block.ToolCall>()) {
            if (block.status == ToolStatus.FAILED) {
                if (appendResultNotes) {
                    protocolNotes += "tool ${block.name} result: ${block.result ?: "{}"}"
                }
                continue
            }
            val approved = if (block.risk != com.legacydroid.luminaai.model.ToolRisk.AUTO) {
                pendingApproval = block
                activeGate = gate
                val decision = gate.await()
                activeGate = null
                pendingApproval = null
                if (!decision) {
                    updateBlock(block.id) { it.copy(status = ToolStatus.FAILED, result = """{"success":false,"error":"declined by user"}""") }
                    if (appendResultNotes) {
                        protocolNotes += "tool ${block.name} declined by user"
                    }
                    continue
                }
                true
            } else true

            if (approved) {
                updateBlock(block.id) { it.copy(status = ToolStatus.RUNNING) }
                val params = runCatching { JSONObject(block.params) }.getOrElse { JSONObject() }
                val spec = ToolRegistry.find(block.name) ?: continue
                val resultJson = ToolRegistry.execute(spec, params)
                val ok = resultJson.optBoolean("success")
                updateBlock(block.id) {
                    it.copy(
                        status = if (ok) ToolStatus.DONE else ToolStatus.FAILED,
                        result = resultJson.toString()
                    )
                }
                onToolExecuted(block.name, resultJson)
                if (appendResultNotes) {
                    protocolNotes += "tool ${block.name} result: $resultJson"
                }
            }
        }
        if (appendResultNotes && protocolNotes.isNotEmpty()) {
            messages += ChatMessage(
                role = Role.USER,
                blocks = protocolNotes.map { Block.Note(content = it) }
            )
        }
        return true
    }

    private fun onToolExecuted(toolName: String, result: JSONObject) {
        when (toolName) {
            "remember_memory", "forget_memory" -> {
                refreshMemories()
                if (result.optBoolean("success")) {
                    val data = result.optJSONObject("data")
                    val action = if (toolName == "remember_memory") "saved" else "forgotten"
                    val mem = data?.optString(if (toolName == "remember_memory") "content" else "forgotten")
                        ?: ""
                    appendMemoryEvent(action, mem)
                }
            }
            "search_memories" -> {
                val mems = result.optJSONObject("data")?.optJSONArray("memories")
                if (mems != null && mems.length() > 0) {
                    appendMemoryEvent("recalled", "found ${mems.length()} memory")
                }
            }
        }
    }

    fun approvePending() {
        activeGate?.resolve(true)
    }

    fun denyPending() {
        activeGate?.resolve(false)
    }

    fun onSuggestionToolAction(blockId: String, approved: Boolean) {
        val messageIdx = messages.indexOfFirst { m ->
            m.blocks.any { it is Block.ToolCall && it.id == blockId }
        }
        if (messageIdx < 0) return
        val block = (messages[messageIdx].blocks.first { it is Block.ToolCall && it.id == blockId } as Block.ToolCall)
        scope.launch {
            if (!approved) {
                updateBlock(blockId) {
                    it.copy(status = ToolStatus.FAILED, result = """{"success":false,"error":"declined by user"}""")
                }
                return@launch
            }
            updateBlock(blockId) { it.copy(status = ToolStatus.RUNNING) }
            val spec = ToolRegistry.find(block.name)
            if (spec == null) {
                updateBlock(blockId) { it.copy(status = ToolStatus.FAILED, result = """{"success":false,"error":"unknown tool"}""") }
                return@launch
            }
            val params = runCatching { JSONObject(block.params) }.getOrElse { JSONObject() }
            val result = ToolRegistry.execute(spec, params)
            val ok = result.optBoolean("success")
            updateBlock(blockId) {
                it.copy(status = if (ok) ToolStatus.DONE else ToolStatus.FAILED, result = result.toString())
            }
            onToolExecuted(block.name, result)
        }
    }

    private fun appendUser(text: String) {
        messages += ChatMessage(role = Role.USER, blocks = listOf(Block.Text(text)))
    }

    private fun appendAssistant(blocks: List<Block>) {
        if (blocks.isEmpty()) return
        messages += ChatMessage(role = Role.ASSISTANT, blocks = blocks)
    }

    private fun appendMemoryEvent(action: String, content: String) {
        val lastAssistant = messages.lastOrNull { it.role == Role.ASSISTANT } ?: return
        val idx = messages.indexOf(lastAssistant)
        messages[idx] = lastAssistant.copy(
            blocks = lastAssistant.blocks + Block.MemoryEvent(action = action, content = content)
        )
    }

    private fun updateBlock(blockId: String, transform: (Block.ToolCall) -> Block.ToolCall) {
        val idx = messages.indexOfFirst { m -> m.blocks.any { it is Block.ToolCall && it.id == blockId } }
        if (idx < 0) return
        val msg = messages[idx]
        messages[idx] = msg.copy(
            blocks = msg.blocks.map { b ->
                if (b is Block.ToolCall && b.id == blockId) transform(b) else b
            }
        )
    }

    private fun buildApiMessages(): List<ApiMessage> {
        val out = mutableListOf<ApiMessage>()
        val memorySummary = if (!memoryInjectedThisSession) MemoryStore.summary(8) else ""
        val history = messages.takeLast(MAX_HISTORY_MESSAGES)

        var injected = false
        for (msg in history) {
            when (msg.role) {
                Role.USER -> {
                    val text = msg.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.content }
                    val notes = msg.blocks.filterIsInstance<Block.Note>().joinToString("\n") { it.content }
                    var content = listOf(text, notes)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    if (content.isBlank()) continue
                    if (!injected && memorySummary.isNotEmpty()) {
                        content = "[Memory context]\n$memorySummary\n[/Memory context]\n\n$content"
                        injected = true
                        memoryInjectedThisSession = true
                    }
                    out += ApiMessage(role = "user", content = content)
                }
                Role.ASSISTANT -> {
                    val text = msg.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.content }
                    val notes = msg.blocks.filterIsInstance<Block.Note>().joinToString("\n") { it.content }
                    val content = listOf(text, notes)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    val calls = msg.blocks.filterIsInstance<Block.ToolCall>().filter { !it.protocol }
                    if (content.isNotBlank() || calls.isNotEmpty()) {
                        out += ApiMessage(
                            role = "assistant",
                            content = content,
                            toolCalls = calls.map {
                                ApiToolCall(
                                    id = it.id,
                                    name = it.name,
                                    paramsJson = it.params
                                )
                            }
                        )
                        for (call in calls) {
                            if (call.result != null) {
                                out += ApiMessage(
                                    role = "tool",
                                    content = call.result,
                                    toolCallId = call.id,
                                    name = call.name
                                )
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    fun refreshMemories() {
        memories = MemoryStore.load()
    }

    fun addMemory(content: String, importance: String) {
        val c = content.trim()
        if (c.isBlank()) return
        MemoryStore.save(
            MemoryEntry(
                id = UUID.randomUUID().toString(),
                content = c,
                importance = importance,
                source = "manual",
                createdAt = System.currentTimeMillis()
            )
        )
        refreshMemories()
    }

    fun editMemory(id: String, content: String, importance: String) {
        val existing = MemoryStore.load().firstOrNull { it.id == id } ?: return
        if (content.isBlank()) return
        MemoryStore.save(existing.copy(content = content.trim(), importance = importance))
        refreshMemories()
    }

    fun deleteMemory(id: String) {
        MemoryStore.delete(id)
        refreshMemories()
    }

    fun clearMemories() {
        MemoryStore.deleteAll()
        refreshMemories()
    }

    private data class ProtocolReply(
        val message: String,
        val calls: List<ApiToolCall>
    )

    private fun parseProtocolReply(reply: String): ProtocolReply? {
        var text = reply.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val message = json.optString("message", "")
        val calls = mutableListOf<ApiToolCall>()
        val executes = json.optJSONArray("execute")
        if (executes != null) {
            for (i in 0 until executes.length()) {
                val e = executes.optJSONObject(i) ?: continue
                val tool = e.optString("tool").trim()
                if (tool.isBlank()) continue
                val args = e.opt("arguments")
                calls += ApiToolCall(
                    id = "call_${tool}_${System.currentTimeMillis()}_$i",
                    name = tool,
                    paramsJson = when (args) {
                        is JSONObject -> args.toString()
                        is String -> args
                        else -> "{}"
                    }
                )
            }
        }
        if (message.isBlank() && calls.isEmpty()) return null
        return ProtocolReply(message, calls)
    }

    private class ApprovalGate {
        private var continuation: CancellableContinuation<Boolean>? = null

        suspend fun await(): Boolean = suspendCancellableCoroutine { cont ->
            continuation = cont
        }

        fun resolve(approved: Boolean) {
            continuation?.resume(approved)
            continuation = null
        }
    }
}