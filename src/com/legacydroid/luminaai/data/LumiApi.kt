/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.content.Context
import android.provider.Settings
import com.legacydroid.luminaai.model.ApiMessage
import com.legacydroid.luminaai.model.ApiToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

sealed class LumiResult {
    data class Success(val reply: String, val finishReason: String) : LumiResult()
    data class ToolCalls(val calls: List<ApiToolCall>) : LumiResult()
    data class RateLimited(val message: String) : LumiResult()
    data class TrialExpired(val message: String) : LumiResult()
    data class Error(val message: String) : LumiResult()
}

data class EngineConfig(
    val provider: String,
    val model: String,
    val customBaseUrl: String,
    val customApiKey: String,
    val customModel: String
)

object LumiApi {

    private const val LUMI_ENDPOINT =
        "https://vrfwejrggvoalzddqncw.supabase.co/functions/v1/lumina-ai"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_TOKENS = 2048

    const val PROVIDER_LUMINA = "lumina"
    const val PROVIDER_GEMINI = "gemini"
    const val PROVIDER_OPENCODE = "opencode"

    val GEMINI_MODELS = listOf(
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3-flash",
        "gemini-3.5-flash-lite",
        "gemma-4-26b-a4b-it",
        "gemma-4-31b-it"
    )

    val OPENCODE_MODELS = listOf(
        "x-preview-f-free",
        "big-pickle",
        "nemotron-3.5-lightning-free",
        "laguna-s-2.1-free",
        "hy3-free",
        "nemotron-3-ultra-free",
        "mimo-v2.5-free"
    )

    const val DEFAULT_GEMINI_MODEL = "gemini-3.7-flash"
    const val DEFAULT_OPENCODE_MODEL = "x-preview-f-free"
    private const val TITLE_MODEL = "gemini-3.5-flash-lite"

    suspend fun summarizeTitle(context: Context, transcript: String): String =
        withContext(Dispatchers.IO) {
            if (transcript.isBlank()) return@withContext ""
            val systemPrompt = "You write ultra-short chat session titles. Reply with ONLY the title: " +
                "3 to 6 words, no quotes, no punctuation at the end, no emoji. " +
                "Base it on what the conversation is about."
            val userContent = "Conversation:\n$transcript\n\nTitle:"
            val result = lumiChat(
                context,
                listOf(ApiMessage(role = "user", content = userContent)),
                null,
                PROVIDER_GEMINI,
                TITLE_MODEL,
                systemPrompt
            )
            val raw = when (result) {
                is LumiResult.Success -> result.reply
                else -> ""
            }
            raw.trim()
                .removeSurrounding("\"")
                .replace(Regex("^[#*\\-\\s]+"), "")
                .lineFirst()
                .take(60)
                .trim()
                .trimEnd('.', '!', '?', ',', ':', ';')
        }

    private fun String.lineFirst(): String = lineSequence().firstOrNull { it.isNotBlank() } ?: ""

    fun readConfig(context: Context): EngineConfig {
        val provider = (
            Settings.Global.getString(context.contentResolver, "legacydroid_luminaai_provider")
                ?: PROVIDER_LUMINA
            ).let { if (it == "custom") "custom" else PROVIDER_LUMINA }
        val rawModel = Settings.Global.getString(context.contentResolver, "legacydroid_luminaai_model")
            ?.trim().orEmpty()
        val model = if (rawModel in GEMINI_MODELS || rawModel in OPENCODE_MODELS) {
            rawModel
        } else {
            DEFAULT_GEMINI_MODEL
        }
        return EngineConfig(
            provider = provider,
            model = model,
            customBaseUrl = Settings.Global.getString(context.contentResolver, "legacydroid_luminaai_custom_base_url")
                ?: "",
            customApiKey = Settings.Global.getString(context.contentResolver, "legacydroid_luminaai_custom_api_key")
                ?: "",
            customModel = Settings.Global.getString(context.contentResolver, "legacydroid_luminaai_custom_model")
                ?: ""
        )
    }

    suspend fun chat(
        context: Context,
        messages: List<ApiMessage>,
        toolsJson: JSONArray?,
        systemPrompt: String? = null
    ): LumiResult = withContext(Dispatchers.IO) {
        val config = readConfig(context)
        when (config.provider) {
            "custom" -> {
                if (config.customBaseUrl.isBlank() || config.customApiKey.isBlank()) {
                    LumiResult.Error("Custom engine is not configured. Add the base URL and API key in Settings.")
                } else {
                    openAiChat(
                        config.customBaseUrl,
                        config.customApiKey,
                        config.customModel.ifBlank { config.model },
                        messages,
                        toolsJson,
                        systemPrompt
                    )
                }
            }
            else -> {
                val wireProvider =
                    if (config.model in OPENCODE_MODELS) PROVIDER_OPENCODE else PROVIDER_GEMINI
                lumiChat(context, messages, toolsJson, wireProvider, config.model, systemPrompt)
            }
        }
    }

    private fun lumiChat(
        context: Context,
        messages: List<ApiMessage>,
        toolsJson: JSONArray?,
        provider: String,
        model: String,
        systemPrompt: String? = null
    ): LumiResult {
        val body = JSONObject()
            .put("messages", toLumiMessages(messages))
            .put("provider", provider)
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
        if (!systemPrompt.isNullOrBlank()) {
            body.put("system", systemPrompt)
        }
        if (toolsJson != null && toolsJson.length() > 0) {
            body.put("tools", toolsJson)
            body.put("tool_choice", "auto")
        }

        return postJson(LUMI_ENDPOINT, body, null) { code, json ->
            when (code) {
                429 -> LumiResult.RateLimited(json.optString("message", "Rate limit reached. Slow down."))
                403 -> LumiResult.TrialExpired(json.optString("message", "Trial expired. Log in to continue."))
                200 -> {
                    val calls = json.optJSONArray("tool_calls")
                    if (calls != null && calls.length() > 0) {
                        LumiResult.ToolCalls(parseToolCalls(calls))
                    } else {
                        LumiResult.Success(
                            reply = json.optString("reply", "").ifBlank { "( ˘ω˘ )✨ No response generated." },
                            finishReason = json.optString("finish_reason", "stop")
                        )
                    }
                }
                else -> LumiResult.Error(json.optString("message", "Server error ($code)."))
            }
        }
    }

    private fun openAiChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        toolsJson: JSONArray?,
        systemPrompt: String? = null
    ): LumiResult {
        var url = baseUrl.trim().trimEnd('/')
        if (!url.endsWith("/chat/completions")) {
            url = if (url.endsWith("/v1")) "$url/chat/completions" else "$url/v1/chat/completions"
        }

        val fullMessages = if (systemPrompt.isNullOrBlank()) {
            messages
        } else {
            listOf(ApiMessage(role = "system", content = systemPrompt)) + messages
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", toLumiMessages(fullMessages))
            .put("temperature", 0.7)
            .put("max_tokens", MAX_TOKENS)
        if (toolsJson != null && toolsJson.length() > 0) {
            body.put("tools", toolsJson)
            body.put("tool_choice", "auto")
        }

        return postJson(url, body, apiKey) { code, json ->
            if (code != 200) {
                LumiResult.Error(json.optString("message", "Custom engine error ($code)."))
            } else {
                val choice = json.optJSONArray("choices")?.optJSONObject(0)
                val message = choice?.optJSONObject("message")
                val calls = message?.optJSONArray("tool_calls")
                if (calls != null && calls.length() > 0) {
                    LumiResult.ToolCalls(parseToolCalls(calls))
                } else {
                    LumiResult.Success(
                        reply = message?.optString("content").orEmpty().ifBlank { "( ˘ω˘ )✨ No response generated." },
                        finishReason = choice?.optString("finish_reason", "stop") ?: "stop"
                    )
                }
            }
        }
    }

    private fun parseToolCalls(arr: JSONArray): List<ApiToolCall> {
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val fn = o.optJSONObject("function") ?: o
            val name = fn.optString("name").ifBlank { o.optString("name") }
            if (name.isBlank()) return@mapNotNull null
            val args = fn.opt("arguments")
            ApiToolCall(
                id = o.optString("id").ifBlank { "call_${i}_${System.currentTimeMillis()}" },
                name = name,
                paramsJson = when (args) {
                    is JSONObject -> args.toString()
                    is String -> args
                    else -> "{}"
                },
                thoughtSignature = o.optString("thought_signature").ifBlank { null }
            )
        }
    }

    private fun toLumiMessages(messages: List<ApiMessage>): JSONArray {
        val arr = JSONArray()
        for (m in messages) {
            val o = JSONObject()
                .put("role", m.role)
                .put("content", m.content)
            if (m.role == "assistant" && !m.toolCalls.isNullOrEmpty()) {
                val calls = JSONArray()
                for (tc in m.toolCalls) {
                    val call = JSONObject()
                        .put("id", tc.id)
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", tc.name)
                                .put("arguments", tc.paramsJson)
                        )
                    tc.thoughtSignature?.let { call.put("thought_signature", it) }
                    calls.put(call)
                }
                o.put("tool_calls", calls)
            }
            if (m.role == "tool") {
                m.toolCallId?.let { o.put("tool_call_id", it) }
                m.name?.let { o.put("name", it) }
            }
            arr.put(o)
        }
        return arr
    }

    private fun postJson(
        urlString: String,
        body: JSONObject,
        authToken: String?,
        handle: (Int, JSONObject) -> LumiResult
    ): LumiResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/json")
            if (!authToken.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $authToken")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.let { s ->
                BufferedReader(InputStreamReader(s)).use { it.readText() }
            } ?: ""
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("message", raw.take(300)) }
            handle(code, json)
        } catch (e: Exception) {
            LumiResult.Error(
                when (e) {
                    is java.net.UnknownHostException, is java.net.ConnectException ->
                        "No internet connection. Check your network and try again."
                    is java.net.SocketTimeoutException ->
                        "Lumina took too long to respond. Try again."
                    else -> "Request failed: ${e.message ?: e.javaClass.simpleName}"
                }
            )
        } finally {
            conn?.disconnect()
        }
    }
}