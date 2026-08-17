/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** External entry point (Settings preview, later PhoneWindowManager). */
class LuminaTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, LuminaOverlayService::class.java).apply {
            action = intent.action
        }
        context.startService(serviceIntent)
    }
}