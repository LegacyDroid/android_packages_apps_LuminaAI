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

    fun readConfig(context: Context): EngineConfig {
        return EngineConfig(
            provider = Settings.Global.getString(context.contentResolver, "legacydroid_luminaai_provider")
                ?: "gemini",
            model = Settings.Global.getString(context.contentResolver, "legacydroid_luminaai_model")
                ?: "gemini-3.5-flash-lite",
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
            "opencode" -> lumiChat(context, messages, toolsJson, "opencode", config.model, systemPrompt)
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
            else -> lumiChat(context, messages, toolsJson, "gemini", config.model, systemPrompt)
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
                }
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
                    calls.put(
                        JSONObject()
                            .put("id", tc.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", tc.name)
                                    .put("arguments", tc.paramsJson)
                            )
                    )
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