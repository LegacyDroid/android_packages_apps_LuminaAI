/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat

/**
 * Transparent fullscreen host for the Lumina overlay. Started explicitly
 * (Settings preview now, PhoneWindowManager in M2): the modern broadcast
 * queue refuses to cold-start targetSdk-O+ manifest receivers in the
 * background, so a receiver-driven service can never appear from a cold app.
 */
class LuminaOverlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(ComposeView(this).apply {
            setContent { LuminaRoot() }
        })
        LuminaSession.onIdle = { finishAndRemoveTask() }
        LuminaSession.awaken()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LuminaSession.awaken()
    }

    override fun onBackPressed() {
        LuminaSession.dismiss()
    }

    override fun onDestroy() {
        LuminaSession.onIdle = null
        super.onDestroy()
    }
}