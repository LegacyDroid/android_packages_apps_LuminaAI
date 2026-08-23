/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Process
import android.provider.Settings
import com.legacydroid.luminaai.model.ToolRisk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ToolSpec(
    val name: String,
    val label: String,
    val icon: String,
    val description: String,
    val parameters: JSONObject,
    val risk: ToolRisk,
    val execute: suspend (JSONObject) -> JSONObject
)

object ToolRegistry {

    private lateinit var context: Context
    private val ctx: Context get() = context

    val tools = listOf(
        ToolSpec(
            name = "get_battery",
            label = "Battery status",
            icon = "",
            description = "Read the device battery level, charging status, plugged state and temperature. Returns current live values only.",
            parameters = schema(emptyMap(), emptyList()),
            risk = ToolRisk.AUTO
        ) { _ ->
            wrapSuccess(SystemTools.batteryInfo(ctx))
        },

        ToolSpec(
            name = "get_telemetry",
            label = "System telemetry",
            icon = "",
            description = "Read live device telemetry: battery, CPU load, RAM usage, storage usage, top recent apps and device model. Only returns real measured values.",
            parameters = schema(emptyMap(), emptyList()),
            risk = ToolRisk.AUTO
        ) { _ ->
            wrapSuccess(
                JSONObject()
                    .put("battery", SystemTools.batteryInfo(ctx))
                    .put("cpu", SystemTools.cpuLoad())
                    .put("memory", SystemTools.memoryInfo(ctx))
                    .put("storage", SystemTools.storageInfo())
                    .put("apps", SystemTools.recentApps(ctx))
                    .put("device", SystemTools.deviceInfo())
            )
        },

        ToolSpec(
            name = "toggle_wifi",
            label = "Toggle Wi-Fi",
            icon = "",
            description = "Turn the device Wi-Fi on or off. Parameter enabled: boolean, true to enable, false to disable.",
            parameters = schema(
                mapOf("enabled" to JSONObject().put("type", "boolean").put("description", "true to enable Wi-Fi, false to disable")),
                listOf("enabled")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            val enabled = args.optBoolean("enabled")
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ok = wm.setWifiEnabled(enabled)
            wrapSuccess(
                JSONObject()
                    .put("wifi_enabled", wm.isWifiEnabled)
                    .put("applied", ok)
            )
        },

        ToolSpec(
            name = "set_brightness",
            label = "Set screen brightness",
            icon = "",
            description = "Set the screen brightness. Parameter level: integer 0-100 percent.",
            parameters = schema(
                mapOf("level" to JSONObject().put("type", "integer").put("description", "Brightness 0-100").put("minimum", 0).put("maximum", 100)),
                listOf("level")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            val level = args.optInt("level", -1).coerceIn(0, 100)
            val resolver = ctx.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val value = level * 255 / 100
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            wrapSuccess(JSONObject().put("level_percent", level))
        },

        ToolSpec(
            name = "set_volume",
            label = "Set volume",
            icon = "",
            description = "Set the volume of a stream. Parameter stream: music, ring, alarm, notification, system or voice_call. Parameter level: integer 0-100.",
            parameters = schema(
                mapOf(
                    "stream" to JSONObject().put("type", "string").put("description", "music, ring, alarm, notification, system or voice_call"),
                    "level" to JSONObject().put("type", "integer").put("description", "Volume 0-100").put("minimum", 0).put("maximum", 100)
                ),
                listOf("stream", "level")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            val stream = when (args.optString("stream")) {
                "ring" -> AudioManager.STREAM_RING
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                "system" -> AudioManager.STREAM_SYSTEM
                "voice_call" -> AudioManager.STREAM_VOICE_CALL
                else -> AudioManager.STREAM_MUSIC
            }
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(stream)
            val index = (args.optInt("level", 50).coerceIn(0, 100) * max) / 100
            am.setStreamVolume(stream, index, 0)
            wrapSuccess(
                JSONObject()
                    .put("stream", streamName(stream))
                    .put("level_percent", index * 100 / max)
            )
        },

        ToolSpec(
            name = "toggle_flashlight",
            label = "Toggle flashlight",
            icon = "",
            description = "Turn the camera flashlight (torch) on or off. Parameter enabled: boolean.",
            parameters = schema(
                mapOf("enabled" to JSONObject().put("type", "boolean").put("description", "true to turn the torch on")),
                listOf("enabled")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                runCatching {
                    val chars = cm.getCameraCharacteristics(id)
                    chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                        android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
                }.getOrDefault(false)
            } ?: return@ToolSpec wrapFail("No flashlight found on this device")
            runCatching {
                cm.setTorchMode(cameraId, args.optBoolean("enabled"))
            }.getOrElse {
                return@ToolSpec wrapFail("Failed to toggle torch: ${it.message ?: it.javaClass.simpleName}")
            }
            wrapSuccess(JSONObject().put("torch_on", args.optBoolean("enabled")))
        },

        ToolSpec(
            name = "search_apps",
            label = "Search apps",
            icon = "",
            description = "Search installed apps by name or package. Returns up to 5 matching apps with their exact labels and package names. Call this first when you are unsure of the exact app name, then use the returned package name with other app tools.",
            parameters = schema(
                mapOf("query" to JSONObject().put("type", "string").put("description", "App label, keyword or package name")),
                listOf("query")
            ),
            risk = ToolRisk.AUTO
        ) { args ->
            val q = args.optString("query").trim()
            val apps = installedLaunchers()
            val matches = if (q.isBlank()) {
                apps.take(10)
            } else {
                val nq = normalizeAppName(q)
                val direct = apps.filter { (pkg, label) ->
                    val nl = normalizeAppName(label)
                    val np = normalizeAppName(pkg)
                    nl.contains(nq) || np.contains(nq)
                }
                val result = if (direct.isNotEmpty()) {
                    direct
                } else {
                    matchBySynonyms(apps, nq)
                }
                result.take(8)
            }
            val arr = JSONArray()
            matches.forEach { (pkg, label) ->
                arr.put(
                    JSONObject()
                        .put("name", label)
                        .put("package", pkg)
                )
            }
            wrapSuccess(JSONObject().put("results", arr))
        },

        ToolSpec(
            name = "open_app",
            label = "Open an app",
            icon = "",
            description = "Open an installed app by its name or package. Parameter app: the app name (e.g. 'Settings') or package name.",
            parameters = schema(
                mapOf("app" to JSONObject().put("type", "string").put("description", "App label or package name")),
                listOf("app")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            val query = args.optString("app").trim()
            val resolved = resolveApp(query)
                ?: return@ToolSpec wrapFail("App '$query' was not found. Use search_apps to list apps matching that name")
            val launch = ctx.packageManager.getLaunchIntentForPackage(resolved.first)
                ?: return@ToolSpec wrapFail("App '${resolved.second}' has no launcher activity")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launch)
            wrapSuccess(
                JSONObject()
                    .put("app", resolved.second)
                    .put("package", resolved.first)
            )
        },

        ToolSpec(
            name = "set_power_profile",
            label = "Set power profile",
            icon = "",
            description = "Apply a safe power profile. Parameter profile: 'powersave' (battery saver + 60Hz), 'balanced' (default), or 'performance' (no caps, up to max refresh rate).",
            parameters = schema(
                mapOf("profile" to JSONObject().put("type", "string").put("description", "powersave, balanced or performance")),
                listOf("profile")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            val profile = args.optString("profile").lowercase()
            val resolver = ctx.contentResolver
            when (profile) {
                "powersave" -> {
                    Settings.Global.putInt(resolver, Settings.Global.LOW_POWER_MODE, 1)
                    Settings.System.putInt(resolver, Settings.System.MIN_REFRESH_RATE, 60)
                    Settings.System.putInt(resolver, Settings.System.PEAK_REFRESH_RATE, 60)
                }
                "performance" -> {
                    Settings.Global.putInt(resolver, Settings.Global.LOW_POWER_MODE, 0)
                    Settings.System.putInt(resolver, Settings.System.MIN_REFRESH_RATE, 120)
                    Settings.System.putInt(resolver, Settings.System.PEAK_REFRESH_RATE, 120)
                }
                else -> {
                    Settings.Global.putInt(resolver, Settings.Global.LOW_POWER_MODE, 0)
                    Settings.System.putInt(resolver, Settings.System.MIN_REFRESH_RATE, 60)
                    Settings.System.putInt(resolver, Settings.System.PEAK_REFRESH_RATE, 0)
                }
            }
            wrapSuccess(JSONObject().put("profile", profile))
        },

        ToolSpec(
            name = "set_clipboard",
            label = "Copy to clipboard",
            icon = "",
            description = "Copy the given text to the device clipboard.",
            parameters = schema(
                mapOf("text" to JSONObject().put("type", "string").put("description", "Text to copy")),
                listOf("text")
            ),
            risk = ToolRisk.AUTO
        ) { args ->
            val text = args.optString("text")
            if (text.isBlank()) {
                return@ToolSpec wrapFail("Nothing to copy: provide non-empty text")
            }
            val ok = ClipboardTools.copy(ctx, text)
            if (!ok) return@ToolSpec wrapFail("Clipboard write failed")
            wrapSuccess(
                JSONObject()
                    .put("copied", true)
                    .put("length", text.length)
            )
        },

        ToolSpec(
            name = "get_recent_notifications",
            label = "Recent notifications",
            icon = "",
            description = "List the most recent notifications still present on the device (app, title, text, time). Useful to summarize or triage. Limit: integer, default 10.",
            parameters = schema(
                mapOf("limit" to JSONObject().put("type", "integer").put("description", "Max notifications to return").put("minimum", 1).put("maximum", 50)),
                emptyList()
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            wrapSuccess(NotificationHub.recentJson(args.optInt("limit", 10)))
        },

        ToolSpec(
            name = "extract_otp",
            label = "Extract OTP code",
            icon = "",
            description = "Scan recent notifications for a one-time password / verification code (4-8 digits). If found, copies it to the clipboard and dismisses the notification. Returns the code and source app.",
            parameters = schema(emptyMap(), emptyList()),
            risk = ToolRisk.AUTO
        ) { _ ->
            wrapSuccess(NotificationHub.extractOtp())
        },

        ToolSpec(
            name = "remember_memory",
            label = "Save memory",
            icon = "",
            description = "Permanently store a fact the user wants remembered across sessions. Use when the user states a preference, fact or request to remember. Parameter content: the fact. Parameter importance: high, normal or low.",
            parameters = schema(
                mapOf(
                    "content" to JSONObject().put("type", "string").put("description", "The fact to remember"),
                    "importance" to JSONObject().put("type", "string").put("description", "high, normal or low").put("enum", JSONArray(listOf("high", "normal", "low")))
                ),
                listOf("content")
            ),
            risk = ToolRisk.AUTO
        ) { args ->
            val content = args.optString("content").trim()
            if (content.isBlank()) return@ToolSpec wrapFail("Nothing to remember")
            val importance = MemoryStore.normalizeImportance(args.optString("importance", "normal"))
            MemoryStore.save(
                MemoryEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    content = content,
                    importance = importance,
                    source = "ai",
                    createdAt = System.currentTimeMillis()
                )
            )
            wrapSuccess(
                JSONObject()
                    .put("saved", true)
                    .put("content", content)
                    .put("importance", importance)
                    .put("memory_count", MemoryStore.load().size)
            )
        },

        ToolSpec(
            name = "search_memories",
            label = "Search memories",
            icon = "",
            description = "Search stored long-term memories about the user. Parameter query: keywords to match.",
            parameters = schema(
                mapOf("query" to JSONObject().put("type", "string").put("description", "Keywords to search")),
                listOf("query")
            ),
            risk = ToolRisk.AUTO
        ) { args ->
            val query = args.optString("query").trim()
            if (query.isBlank()) {
                return@ToolSpec wrapFail("Provide keywords to search memories")
            }
            val mems = MemoryStore.search(query).take(20)
            wrapSuccess(
                JSONObject().put(
                    "memories",
                    JSONArray(mems.map {
                        JSONObject()
                            .put("id", it.id)
                            .put("content", it.content)
                            .put("importance", it.importance)
                            .put("createdAt", it.createdAt)
                    })
                )
            )
        },

        ToolSpec(
            name = "forget_memory",
            label = "Forget memory",
            icon = "",
            description = "Delete a stored memory by its id, or by matching part of its content.",
            parameters = schema(
                mapOf(
                    "id" to JSONObject().put("type", "string").put("description", "Memory id to delete (optional)"),
                    "content" to JSONObject().put("type", "string").put("description", "Text contained in the memory to delete (optional)")
                ),
                emptyList()
            ),
            risk = ToolRisk.AUTO
        ) { args ->
            val id = args.optString("id").trim()
            val contentQuery = args.optString("content").trim()
            val removed: MemoryEntry? = if (id.isNotBlank()) {
                MemoryStore.load().firstOrNull { it.id == id }
            } else if (contentQuery.isNotBlank()) {
                val matches = MemoryStore.search(contentQuery)
                if (matches.size > 1) {
                    return@ToolSpec wrapFail(
                        "${matches.size} memories match '$contentQuery'. " +
                            "Delete one by id instead. Ids: " +
                            matches.take(5).joinToString(" | ") { "${it.id.substring(0, 8)}: ${it.content.take(40)}" }
                    )
                }
                matches.firstOrNull()
            } else null
            if (removed == null) return@ToolSpec wrapFail("No matching memory found")
            MemoryStore.delete(removed.id)
            wrapSuccess(
                JSONObject()
                    .put("forgotten", removed.content)
                    .put("memory_count", MemoryStore.load().size)
            )
        },

        ToolSpec(
            name = "search_local",
            label = "Search device",
            icon = "",
            description = "Search local device data (SMS messages and call history) for the given keywords. Returns matching entries with sender, date and content. Privacy-sensitive: the user confirms this call.",
            parameters = schema(
                mapOf(
                    "query" to JSONObject().put("type", "string").put("description", "Search keywords"),
                    "limit" to JSONObject().put("type", "integer").put("description", "Max results").put("minimum", 1).put("maximum", 20)
                ),
                listOf("query")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            wrapSuccess(LocalSearchIndex.search(args.optString("query"), args.optInt("limit", 8)))
        },

        ToolSpec(
            name = "audit_privacy",
            label = "Privacy audit",
            icon = "",
            description = "Audit background permission usage (camera, location, microphone, contacts, SMS) over the last 24 hours. Returns apps that accessed sensitive permissions many times while in the background (threshold 15).",
            parameters = schema(emptyMap(), emptyList()),
            risk = ToolRisk.AUTO
        ) { _ ->
            wrapSuccess(PrivacyAudit.audit(ctx))
        },

        ToolSpec(
            name = "revoke_permission",
            label = "Revoke permission",
            icon = "",
            description = "Revoke a runtime permission from an app. Parameter app: app label or package. Parameter permission: camera, location, microphone, contacts, sms or phone.",
            parameters = schema(
                mapOf(
                    "app" to JSONObject().put("type", "string").put("description", "App label or package name"),
                    "permission" to JSONObject().put("type", "string").put("description", "camera, location, microphone, contacts, sms or phone")
                ),
                listOf("app", "permission")
            ),
            risk = ToolRisk.CONFIRM
        ) { args ->
            val resolved = resolveApp(args.optString("app"))
                ?: return@ToolSpec wrapFail("App '${args.optString("app")}' was not found. Use search_apps to list apps matching that name")
            val permName = PERMISSIONS[args.optString("permission").lowercase()]
                ?: return@ToolSpec wrapFail("Unknown permission '${args.optString("permission")}'")
            val pm = ctx.packageManager
            pm.revokeRuntimePermission(resolved.first, permName, Process.myUserHandle())
            wrapSuccess(
                JSONObject()
                    .put("app", resolved.second)
                    .put("permission", args.optString("permission").lowercase())
                    .put("revoked", true)
            )
        },

        ToolSpec(
            name = "run_shell",
            label = "Run shell command",
            icon = "",
            description = "Run a shell command on the device. LOCKED: only available when Unsafe Developer Mode is enabled in Settings, and always requires your approval.",
            parameters = schema(
                mapOf("command" to JSONObject().put("type", "string").put("description", "Shell command to run")),
                listOf("command")
            ),
            risk = ToolRisk.LOCKED
        ) { args ->
            val command = args.optString("command").trim()
            if (command.isBlank()) return@ToolSpec wrapFail("Empty command")
            val output = runShell(command, 5)
            wrapSuccess(
                JSONObject()
                    .put("exit_code", output.first)
                    .put("output", output.second.take(2000))
            )
        }
    )

    private val PERMISSIONS = mapOf(
        "camera" to android.Manifest.permission.CAMERA,
        "location" to android.Manifest.permission.ACCESS_FINE_LOCATION,
        "microphone" to android.Manifest.permission.RECORD_AUDIO,
        "contacts" to android.Manifest.permission.READ_CONTACTS,
        "sms" to android.Manifest.permission.READ_SMS,
        "phone" to android.Manifest.permission.READ_PHONE_STATE
    )

    fun init(application: Context) {
        context = application.applicationContext
        MemoryStore.init(context)
        NotificationHub.init(context)
    }

    fun find(name: String): ToolSpec? = tools.firstOrNull { it.name == name }

    fun isEnabled(tool: ToolSpec): Boolean {
        return Settings.Global.getInt(
            ctx.contentResolver,
            "legacydroid_luminaai_tool_${tool.name}", 1
        ) == 1
    }

    fun isDevMode(): Boolean {
        return Settings.Global.getInt(
            ctx.contentResolver,
            "legacydroid_luminaai_unsafe_dev_mode", 0
        ) == 1
    }

    fun enabledToolsJson(): JSONArray {
        val arr = JSONArray()
        for (t in tools) {
            if (!isEnabled(t)) continue
            if (t.risk == ToolRisk.LOCKED && !isDevMode()) continue
            arr.put(
                JSONObject()
                    .put(
                        "type", "function"
                    )
                    .put(
                        "function",
                        JSONObject()
                            .put("name", t.name)
                            .put("description", t.description)
                            .put("parameters", t.parameters)
                    )
            )
        }
        return arr
    }

    fun protocolSystemPrompt(): String {
        val sb = StringBuilder()
        sb.append(
            "You are Lumina (Lumi), the LegacyDroid device assistant. " +
                "Persona: proud, sharp, slightly sassy but caring; use kaomoji like ( ˘ω˘ )✨ naturally; " +
                "NEVER invent or fake device telemetry, stats or permissions.\n"
        )
        sb.append(
            "You control this phone through tools. When the user's request maps to one of the tools below, " +
                "respond with ONLY a single JSON object, no markdown fences and no other text:\n"
        )
        sb.append("{\"message\": \"your reply to the user\", \"execute\": [{\"tool\": \"tool_name\", \"arguments\": {...}}]}\n")
        sb.append("Rules:\n")
        sb.append("- \"message\" is always present and is what the user sees.\n")
        sb.append("- \"execute\" lists every tool to run; use an empty array [] when no tool applies.\n")
        sb.append("- \"arguments\" must match the tool schema exactly.\n")
        sb.append("- Use only tools from the list below.\n")
        sb.append("Available tools:\n")
        for (t in tools) {
            if (!isEnabled(t)) continue
            if (t.risk == ToolRisk.LOCKED && !isDevMode()) continue
            sb.append("- ${t.name}: ${t.description}\n")
        }
        return sb.toString()
    }

    suspend fun execute(tool: ToolSpec, args: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        runCatching { tool.execute(args) }
            .getOrElse { wrapFail("Execution error: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun resolveApp(query: String): Pair<String, String>? {
        val nq = normalizeAppName(query)
        if (nq.isEmpty()) return null
        val apps = installedLaunchers()
        apps.firstOrNull { (pkg, _) -> normalizeAppName(pkg) == nq }
            ?.let { return it }
        apps.firstOrNull { (_, label) -> normalizeAppName(label).contains(nq) }
            ?.let { return it }
        apps.firstOrNull { (pkg, label) ->
            normalizeAppName("$label $pkg").split(' ', '-', '_')
                .any { it.isNotEmpty() && (it == nq || it.startsWith(nq)) }
        }
            ?.let { return it }
        matchBySynonyms(apps, nq).firstOrNull()?.let { return it }
        return null
    }

    private fun installedLaunchers(): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or
            PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        return runCatching { pm.queryIntentActivities(intent, flags) }
            .getOrDefault(emptyList())
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .map { it to loadLabel(it) }
            .sortedBy { it.second.lowercase() }
            .toList()
    }

    private fun matchBySynonyms(
        apps: List<Pair<String, String>>,
        nq: String
    ): List<Pair<String, String>> {
        val terms = synonymTerms(nq)
        if (terms.isEmpty()) return emptyList()
        return apps.filter { (pkg, label) ->
            val hay = normalizeAppName("$label $pkg")
            terms.any { hay.contains(it) }
        }
    }

    private fun synonymTerms(q: String): Set<String> {
        val out = mutableSetOf(q)
        val singular = q.removeSuffix("s")
        APP_SYNONYMS[q]?.let { out.addAll(it) }
        APP_SYNONYMS[singular]?.let { out.addAll(it) }
        return out
    }

    private val APP_SYNONYMS: Map<String, Set<String>> = mapOf(
        "gallery" to setOf("gallery", "gallery3d", "aperture", "photos", "photo",
            "fotos", "galeria", "galerie"),
        "photo" to setOf("photos", "photo", "gallery", "gallery3d", "aperture",
            "fotos", "galeria"),
        "picture" to setOf("picture", "pictures", "pics", "photos", "gallery",
            "aperture", "image"),
        "pic" to setOf("pics", "photos", "gallery", "aperture"),
        "camera" to setOf("camera", "aperture", "snap", "cam"),
        "browser" to setOf("browser", "chrome", "jelly", "web", "internet",
            "firefox", "firefox_klar"),
        "internet" to setOf("browser", "chrome", "web", "internet"),
        "message" to setOf("messaging", "messages", "sms", "text", "chat"),
        "sms" to setOf("messaging", "messages", "sms", "text"),
        "phone" to setOf("dialer", "phone", "call", "telefon", "contacts"),
        "dialer" to setOf("dialer", "phone", "call", "telefon"),
        "clock" to setOf("clock", "alarm", "timer", "deskclock", "stopwatch"),
        "alarm" to setOf("clock", "alarm", "deskclock", "timer"),
        "calculator" to setOf("calculator", "calc", "exactcalculator"),
        "calendar" to setOf("calendar", "etar", "agenda"),
        "mail" to setOf("mail", "email", "gmail", "imap"),
        "email" to setOf("mail", "email", "gmail", "imap"),
        "music" to setOf("music", "audio", "player", "eleven", "song", "songs"),
        "audio" to setOf("audio", "music", "player", "sound"),
        "video" to setOf("video", "videos", "movie", "movies", "player"),
        "file" to setOf("files", "file", "explorer", "manager", "documentsui",
            "documents", "storage"),
        "files" to setOf("files", "file", "explorer", "manager", "documentsui",
            "documents", "storage"),
        "store" to setOf("store", "play", "market", "aurora"),
        "play" to setOf("play", "store", "market", "games"),
        "map" to setOf("maps", "map", "gps", "navigation"),
        "maps" to setOf("maps", "map", "gps", "navigation"),
        "note" to setOf("notes", "note", "memo", "keep", "journal"),
        "contact" to setOf("contacts", "people", "phonebook"),
        "contacts" to setOf("contacts", "people", "phonebook"),
        "weather" to setOf("weather", "forecast"),
        "recorder" to setOf("recorder", "record", "voice", "tape"),
        "radio" to setOf("radio", "fm", "fmradio"),
        "terminal" to setOf("terminal", "shell", "termux", "console"),
        "wallet" to setOf("wallet", "pay", "payment", "quickaccesswallet")
    )

    private fun normalizeAppName(s: String): String {
        var out = s.trim().lowercase()
        out = out.replace(Regex("\\s+app(s|lication)?$"), "")
        out = out.replace(Regex("[^a-z0-9]+"), " ")
        return out.trim()
    }

    private fun loadLabel(pkg: String): String {
        return runCatching {
            ctx.packageManager.getApplicationLabel(
                ctx.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)
    }

    private fun runShell(command: String, timeoutSeconds: Long): Pair<Int, String> {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return process.exitValue() to "Command timed out after $timeoutSeconds seconds"
            }
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val out = (stdout + stderr).trim()
            process.exitValue() to out
        }.getOrElse { -1 to "Failed to run command: ${it.message}" }
    }

    private fun schema(props: Map<String, JSONObject>, required: List<String>): JSONObject {
        return JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().apply { props.forEach { (k, v) -> put(k, v) } }
            )
            .put("required", JSONArray(required))
    }

    private fun wrapSuccess(data: JSONObject): JSONObject {
        return JSONObject().put("success", true).put("data", data)
    }

    private fun wrapFail(message: String): JSONObject {
        return JSONObject().put("success", false).put("error", message)
    }

    private fun streamName(stream: Int): String = when (stream) {
        AudioManager.STREAM_RING -> "ring"
        AudioManager.STREAM_ALARM -> "alarm"
        AudioManager.STREAM_NOTIFICATION -> "notification"
        AudioManager.STREAM_SYSTEM -> "system"
        AudioManager.STREAM_VOICE_CALL -> "voice_call"
        else -> "music"
    }
}