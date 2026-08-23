/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import android.app.Application
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.os.Process
import com.legacydroid.luminaai.data.LocalSearchIndex
import com.legacydroid.luminaai.data.NotificationHub
import com.legacydroid.luminaai.data.ToolRegistry
import com.legacydroid.luminaai.live2d.Live2DController

class LuminaApp : Application() {

    private val grantedRuntimePermissions = listOf(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreate() {
        super.onCreate()
        grantRuntimePermissions()
        ToolRegistry.init(this)
        LuminaSession.init(this)
        NotificationHub.grantListenerAccess(this)
        LocalSearchIndex.init(this)
        Live2DController.init(this)
        grantUsageStatsAccess()
        scheduleListenerRecheck()
    }

    // A raw secure-settings write with an unchanged value produces no change
    // notification, so NotificationManagerService may skip adoption. Re-run the
    // grant once the permission state has settled; if access is already live
    // this is a no-op.
    private fun scheduleListenerRecheck() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!NotificationHub.enabled) {
                NotificationHub.grantListenerAccess(this)
            }
        }, 20_000L)
    }

    private fun grantRuntimePermissions() {
        val pm = packageManager
        for (perm in grantedRuntimePermissions) {
            runCatching {
                if (pm.checkPermission(perm, packageName) != PackageManager.PERMISSION_GRANTED) {
                    pm.grantRuntimePermission(packageName, perm, Process.myUserHandle())
                }
            }
        }
    }

    private fun grantUsageStatsAccess() {
        runCatching {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            appOps.setMode(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName,
                AppOpsManager.MODE_ALLOWED
            )
        }
    }
}