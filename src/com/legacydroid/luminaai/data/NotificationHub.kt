/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    private const val OTP_REGEX =
        "\\b(\\d{4,8})\\b"
    private const val OTP_KEYWORDS =
        "otp|one[- ]?time|verification|verify|code|mã|密码|пароль|passcode|p\\s?in|auth|login code"

    val buffer = ArrayDeque<NotifEntry>()
    var enabled = false
        internal set
    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun grantListenerAccess(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val cn = ComponentName(ctx, LuminaNotificationListener::class.java)
        val granted = nm.isNotificationListenerAccessGranted(cn)
        if (!granted) {
            runCatching { nm.setNotificationListenerAccessGranted(cn, true) }
                .onSuccess { Log.i(TAG, "Notification listener access granted (system)") }
                .onFailure {
                    Log.w(TAG, "System grant failed, opening settings: $it")
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
        }
    }

    fun record(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val extras = notif.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

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
        handleOtp(sbn.key, label, title, text)
    }

    fun remove(key: String) {
        buffer.removeAll { it.key == key }
    }

    fun recentJson(limit: Int): JSONObject {
        val arr = JSONArray()
        for (n in buffer.takeLast(limit.coerceIn(1, BUFFER_MAX))) {
            arr.put(
                JSONObject()
                    .put("app", n.appLabel)
                    .put("package", n.packageName)
                    .put("title", n.title)
                    .put("text", n.text)
                    .put("time", n.postTime)
            )
        }
        return JSONObject().put("notifications", arr)
    }

    fun extractOtp(): JSONObject {
        val match = buffer.lastOrNull { findOtp(it) != null } ?: return JSONObject().put("otp_found", false)
        val otp = findOtp(match)!!
        val result = JSONObject()
            .put("otp_found", true)
            .put("otp", otp)
            .put("app", match.appLabel)
            .put("message", "OTP $otp copied to clipboard and its notification was dismissed.")
        ClipboardTools.copy(context, otp)
        cancelNotification(match.key)
        return result
    }

    private fun handleOtp(key: String, label: String, title: String, text: String) {
        val enabled = Settings.Global.getInt(
            context.contentResolver,
            "legacydroid_luminaai_auto_otp", 1
        ) == 1
        if (!enabled) return
        val hay = "$title $text"
        if (!hay.lowercase().contains(Regex(OTP_KEYWORDS))) return
        val otp = findOtp(title, text) ?: return
        ClipboardTools.copy(context, otp)
        cancelNotification(key)
        Log.i(TAG, "Auto-copied OTP from $label")
    }

    private fun findOtp(entry: NotifEntry): String? = findOtp(entry.title, entry.text)

    private fun findOtp(title: String, text: String): String? {
        val hay = "$title $text"
        val hasKeyword = hay.lowercase().contains(Regex(OTP_KEYWORDS))
        if (!hasKeyword) return null
        return Regex(OTP_REGEX).find(hay)?.groupValues?.get(1)
    }

    private fun cancelNotification(key: String) {
        (listenerInstance)?.cancelNotification(key)
        buffer.removeAll { it.key == key }
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