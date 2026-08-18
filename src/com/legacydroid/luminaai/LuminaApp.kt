/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import android.app.Application
import android.app.AppOpsManager
import android.os.Process
import com.legacydroid.luminaai.data.LocalSearchIndex
import com.legacydroid.luminaai.data.NotificationHub
import com.legacydroid.luminaai.data.ToolRegistry

class LuminaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ToolRegistry.init(this)
        LuminaSession.init(this)
        NotificationHub.grantListenerAccess(this)
        LocalSearchIndex.init(this)
        grantUsageStatsAccess()
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