/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView

/**
 * Hosts the Lumina overlay as a system-level window above the current app,
 * the same way Android 16's Gemini overlay floats over content.
 */
class LuminaOverlayService : Service() {

    private var composeView: ComposeView? = null

    private val windowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER -> {
                ensureOverlay()
                LuminaSession.awaken()
            }
            ACTION_DISMISS -> LuminaSession.dismiss()
        }
        return START_NOT_STICKY
    }

    private fun ensureOverlay() {
        if (composeView != null) return
        val view = ComposeView(this).apply {
            setContent { LuminaRoot() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        windowManager.addView(view, params)
        composeView = view
        LuminaSession.onIdle = { removeOverlay() }
    }

    private fun removeOverlay() {
        composeView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        composeView = null
        LuminaSession.onIdle = null
        stopSelf()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TRIGGER = "com.legacydroid.luminaai.TRIGGER"
        const val ACTION_DISMISS = "com.legacydroid.luminaai.DISMISS"
    }
}