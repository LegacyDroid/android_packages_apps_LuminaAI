/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class NotifEntry(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long
)

object NotificationHub {

    private const val TAG = "LuminaNotificationHub"
    private const val BUFFER_MAX = 50

    // Unicode-safe word boundaries so "shipping" does not match "pin"
    // and "barcode" does not match "code".
    private val OTP_KEYWORDS_REGEX = Regex(
        "(?<![\\p{L}\\p{N}])(?:[o0]?tp|one[- ]?time|verification|verify|verify code|" +
            "passcode|pass code|login[ -]?code|access code|security code|activation code|" +
            "auth|code|mã|xác minh|密码|验证码|пароль|код|p\\s?in)(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private val SEGMENT_SPLIT_REGEX = Regex("[\\n.,;:!?()\\[\\]{}]+")
    private val DIGIT_GROUP_SEPARATOR_REGEX = Regex("(?<=\\d)[ \\u00A0\\u2013-](?=\\d)")
    private val OTP_DIGITS_REGEX = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")

    val buffer = ArrayDeque<NotifEntry>()
    var enabled = false
        internal set
    private lateinit var context: Context

    @Volatile
    private var lastAutoOtp: String? = null

    @Volatile
    private var lastAutoTime: Long = 0L

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun grantListenerAccess(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val cn = ComponentName(ctx, LuminaNotificationListener::class.java)
        val alreadyGranted = runCatching { nm.isNotificationListenerAccessGranted(cn) }
            .getOrDefault(false)
        if (alreadyGranted) return

        val grantedDirectly = runCatching { nm.setNotificationListenerAccessGranted(cn, true) }
            .onSuccess { Log.i(TAG, "Notification listener access granted (system)") }
            .isSuccess
        if (grantedDirectly) return

        // Fallback: write enabled_notification_listeners directly; the app holds
        // WRITE_SECURE_SETTINGS. Note NotificationManagerService may not adopt a
        // raw write whose value is unchanged, so callers re-run this grant later;
        // once ACCESS_NOTIFICATIONS is effective the primary path binds instantly.
        runCatching {
            val flat = cn.flattenToString()
            val current = Settings.Secure.getString(
                ctx.contentResolver,
                Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
            ) ?: ""
            val parts = current.split(':').filter { it.isNotBlank() }
            if (!parts.contains(flat)) {
                Settings.Secure.putString(
                    ctx.contentResolver,
                    Settings.Secure.ENABLED_NOTIFICATION_LISTENERS,
                    (parts + flat).joinToString(":")
                )
                Log.i(TAG, "Notification listener access granted via secure settings")
            } else {
                Log.i(TAG, "Listener already in secure settings; awaiting system bind")
            }
        }.onFailure { Log.w(TAG, "Secure-settings listener grant failed", it) }
    }

    fun record(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras = notif.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        var text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (text.isBlank()) {
            text = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        }
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

        synchronized(buffer) {
            buffer.removeAll { it.key == sbn.key }
            buffer.addLast(
                NotifEntry(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appLabel = label,
                    title = title,
                    text = text,
                    postTime = sbn.postTime
                )
            )
            while (buffer.size > BUFFER_MAX) buffer.removeFirst()
        }
        handleOtp(sbn.key, label, title, text)
    }

    fun remove(key: String) {
        synchronized(buffer) {
            buffer.removeAll { it.key == key }
        }
    }

    fun recentJson(limit: Int): JSONObject {
        val snapshot = synchronized(buffer) {
            buffer.takeLast(limit.coerceIn(1, BUFFER_MAX))
        }
        val arr = JSONArray()
        for (n in snapshot) {
            arr.put(
                JSONObject()
                    .put("app", n.appLabel)
                    .put("package", n.packageName)
                    .put("title", n.title)
                    .put("text", n.text)
                    .put("time", n.postTime)
            )
        }
        return JSONObject()
            .put("notifications", arr)
            .put("listener_connected", enabled)
    }

    fun extractOtp(): JSONObject {
        val match = synchronized(buffer) {
            buffer.lastOrNull { findOtp(it.title, it.text) != null }
        } ?: return JSONObject()
            .put("otp_found", false)
            .put("listener_connected", enabled)
            .put("buffered_notifications", synchronized(buffer) { buffer.size })

        val otp = findOtp(match.title, match.text)!!
        ClipboardTools.copy(context, otp, sensitive = true)
        val dismissed = cancelNotification(match.key)
        return JSONObject()
            .put("otp_found", true)
            .put("otp", otp)
            .put("app", match.appLabel)
            .put("dismissed", dismissed)
            .put(
                "message",
                if (dismissed) "OTP $otp copied to clipboard and its notification was dismissed."
                else "OTP $otp copied to clipboard (notification could not be dismissed)."
            )
    }

    private fun handleOtp(key: String, label: String, title: String, text: String) {
        val enabled = Settings.Global.getInt(
            context.contentResolver,
            "legacydroid_luminaai_auto_otp", 1
        ) == 1
        if (!enabled) return
        val now = System.currentTimeMillis()
        val hay = "$title $text"
        if (!OTP_KEYWORDS_REGEX.containsMatchIn(hay)) return
        val otp = findOtp(title, text) ?: return
        if (otp == lastAutoOtp && now - lastAutoTime < 60_000) return
        lastAutoOtp = otp
        lastAutoTime = now
        ClipboardTools.copy(context, otp, sensitive = true)
        cancelNotification(key)
        Log.i(TAG, "Auto-copied OTP from $label")
    }

    /**
     * Picks the OTP from notification text. Digits are searched first inside the
     * sentence fragments that contain an OTP keyword; the candidate closest to
     * the keyword wins. Falls back to a whole-text scan. Grouped digits such as
     * "123 456" are joined before matching.
     */
    private fun findOtp(title: String, text: String): String? {
        val hay = "$title $text"
        if (!OTP_KEYWORDS_REGEX.containsMatchIn(hay)) return null

        val segments = hay.split(SEGMENT_SPLIT_REGEX).filter { it.isNotBlank() }
        val keywordSegments = segments.filter { OTP_KEYWORDS_REGEX.containsMatchIn(it) }
            .ifEmpty { segments }

        for (segment in keywordSegments) {
            val joined = DIGIT_GROUP_SEPARATOR_REGEX.replace(segment, "")
            val candidates = OTP_DIGITS_REGEX.findAll(joined)
                .map { it.groupValues[1] to it.range.first }
                .toList()
            if (candidates.isEmpty()) continue
            val keywordIndex = OTP_KEYWORDS_REGEX.find(joined)?.range?.first ?: -1
            val best = candidates
                .filterNot { (value, _) -> value.toIntOrNull() in 1900..2099 }
                .ifEmpty { candidates }
                .minByOrNull { (_, idx) ->
                    if (keywordIndex < 0) Int.MAX_VALUE else kotlin.math.abs(idx - keywordIndex)
                }
            best?.let { return it.first }
        }
        return null
    }

    private fun cancelNotification(key: String): Boolean {
        val service = listenerInstance ?: return false
        val ok = runCatching { service.cancelNotification(key) }.isSuccess
        synchronized(buffer) {
            buffer.removeAll { it.key == key }
        }
        return ok
    }

    private var listenerInstance: LuminaNotificationListener? = null

    fun attachListener(service: LuminaNotificationListener) {
        listenerInstance = service
    }

    fun detachListener() {
        listenerInstance = null
    }
}

class LuminaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotificationHub.attachListener(this)
        NotificationHub.enabled = true
        for (sbn in activeNotifications) NotificationHub.record(sbn)
    }

    override fun onListenerDisconnected() {
        NotificationHub.enabled = false
        NotificationHub.detachListener()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationHub.record(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationHub.remove(sbn.key)
    }
}
