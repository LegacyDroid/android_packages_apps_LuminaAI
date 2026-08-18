/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object PrivacyAudit {

    private const val TAG = "LuminaPrivacyAudit"
    private const val AUDIT_WINDOW_MS = 24 * 60 * 60 * 1000L
    private const val BACKGROUND_THRESHOLD = 15

    private val INTEREST_OPS = listOf(
        "android:camera",
        "android:coarse_location",
        "android:fine_location",
        "android:record_audio",
        "android:read_contacts",
        "android:read_sms"
    )

    private val executor = Executors.newSingleThreadExecutor()

    private fun isEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            "legacydroid_luminaai_privacy_alerts", 1
        ) == 1
    }

    suspend fun audit(context: Context): JSONObject = withContext(Dispatchers.IO) {
        val result = JSONObject().put("violations", JSONArray())
        if (!isEnabled(context)) return@withContext result
        val pm = context.packageManager
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val end = System.currentTimeMillis()
        val begin = end - AUDIT_WINDOW_MS

        val request = AppOpsManager.HistoricalOpsRequest.Builder(begin, end)
            .setUid(Process.INVALID_UID)
            .setOpNames(INTEREST_OPS)
            .build()

        val ops = getHistoricalOps(appOps, request) ?: return@withContext result
        val violations = JSONArray()

        for (i in 0 until ops.uidCount) {
            val uidOps = ops.getUidOpsAt(i)
            val uid = uidOps.uid
            if (uid < Process.FIRST_APPLICATION_UID) continue
            for (p in 0 until uidOps.packageCount) {
                val packageOps = uidOps.getPackageOpsAt(p)
                val packageName = packageOps.packageName ?: continue
                if (packageName == context.packageName) continue
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrDefault(packageName)
                for (o in 0 until packageOps.opCount) {
                    val op = packageOps.getOpAt(o)
                    val opName = AppOpsManager.opToPublicName(op.opCode)
                        .removePrefix("android:").replace("_", " ")
                    val backgroundCount = op.getAccessCount(
                        AppOpsManager.UID_STATE_BACKGROUND,
                        AppOpsManager.UID_STATE_CACHED,
                        0
                    )
                    if (backgroundCount < BACKGROUND_THRESHOLD) continue
                    violations.put(
                        JSONObject()
                            .put("app", appLabel)
                            .put("package", packageName)
                            .put("permission", opName)
                            .put("background_accesses", backgroundCount)
                    )
                }
            }
        }
        result.put("violations", violations)
        result.put("window_hours", AUDIT_WINDOW_MS / 3_600_000)
        result.put("threshold", BACKGROUND_THRESHOLD)
        result
    }

    private suspend fun getHistoricalOps(
        appOps: AppOpsManager,
        request: AppOpsManager.HistoricalOpsRequest
    ): AppOpsManager.HistoricalOps? = suspendCancellableCoroutine { cont ->
        runCatching {
            appOps.getHistoricalOps(request, executor) { ops ->
                if (cont.isActive) cont.resume(ops)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }
}