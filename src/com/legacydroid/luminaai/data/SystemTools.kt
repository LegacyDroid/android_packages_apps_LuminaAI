/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.data

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

object SystemTools {

    fun batteryInfo(context: Context): JSONObject {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val j = JSONObject()
        if (intent == null) {
            j.put("available", false)
            return j
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        j.put("available", true)
        j.put("level_percent", if (scale > 0) (level * 100f / scale).roundToInt() else level)
        j.put("status", when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        })
        j.put("plugged", plugged != 0)
        j.put("temperature_c", temp)
        return j
    }

    fun memoryInfo(context: Context): JSONObject {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val used = mem.totalMem - mem.availMem
        val usedPct = if (mem.totalMem > 0) (used * 100f / mem.totalMem).roundToInt() else 0
        return JSONObject()
            .put("total_gb", round1(mem.totalMem / 1073741824.0))
            .put("available_gb", round1(mem.availMem / 1073741824.0))
            .put("used_percent", usedPct)
            .put("low_memory", mem.lowMemory)
    }

    fun storageInfo(): JSONObject {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        val usedPct = if (total > 0) ((total - avail) * 100f / total).roundToInt() else 0
        return JSONObject()
            .put("total_gb", round1(total / 1073741824.0))
            .put("available_gb", round1(avail / 1073741824.0))
            .put("used_percent", usedPct)
    }

    fun cpuLoad(): JSONObject {
        val (idle1, total1) = readCpuTimes()
        Thread.sleep(450)
        val (idle2, total2) = readCpuTimes()
        val idleDelta = idle2 - idle1
        val totalDelta = total2 - total1
        val load = if (totalDelta > 0) ((totalDelta - idleDelta) * 100f / totalDelta).roundToInt() else 0
        return JSONObject().put("cpu_percent", load.coerceIn(0, 100))
    }

    private fun readCpuTimes(): Pair<Long, Long> {
        return runCatching {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return 0L to 0L
            val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.isEmpty()) return 0L to 0L
            val idle = parts.getOrElse(3) { 0 } + parts.getOrElse(4) { 0 }
            val total = parts.sum()
            idle to total
        }.getOrDefault(0L to 0L)
    }

    fun recentApps(context: Context): JSONObject {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24 * 60 * 60 * 1000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .filter { it.totalTimeInForeground > 60_000 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(5)
        val pm = context.packageManager
        val arr = org.json.JSONArray()
        for (s in stats) {
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString() }
                .getOrDefault(s.packageName)
            arr.put(
                JSONObject()
                    .put("app", label)
                    .put("package", s.packageName)
                    .put("foreground_minutes", (s.totalTimeInForeground / 60000).coerceAtLeast(1))
            )
        }
        return JSONObject().put("top_apps", arr)
    }

    fun deviceInfo(): JSONObject {
        return JSONObject()
            .put("model", android.os.Build.MODEL)
            .put("brand", android.os.Build.BRAND)
            .put("android", android.os.Build.VERSION.RELEASE)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
    }

    private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0
}