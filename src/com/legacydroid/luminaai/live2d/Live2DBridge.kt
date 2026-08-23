/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.legacydroid.luminaai.live2d

import android.content.Context
import android.content.res.AssetManager
import java.io.IOException

/**
 * Bridge between Kotlin and the native library (libluminalive2d.so).
 *
 * The native side calls the static [loadFile] / [getAssetList] functions to
 * read the APK assets (model files + Cubism shaders), and Kotlin calls the
 * `native*` functions to drive the render engine.
 */
object Live2DBridge {

    private var assetManager: AssetManager? = null

    /** Must be called once before any native use (from LuminaApp.onCreate). */
    fun initialize(context: Context) {
        assetManager = context.assets
    }

    private fun sanitizeAssetPath(path: String): String {
        val p = path.trim()
        if (p.isEmpty() || p.startsWith("/") || p.contains('\u0000')) return ""
        if (p.split('/').any { it == ".." }) return ""
        return p
    }

    /** Reads an asset file as raw bytes; null when missing or unsafe. */
    @JvmStatic
    fun loadFile(path: String): ByteArray? {
        val assets = assetManager ?: return null
        val safe = sanitizeAssetPath(path)
        if (safe.isEmpty()) return null
        return try {
            assets.open(safe).use { it.readBytes() }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Lists an asset directory ("" = root - the native side auto-discovers
     * expressions/motions there). Directory entries have a trailing '/'.
     */
    @JvmStatic
    fun getAssetList(path: String): Array<String> {
        val assets = assetManager ?: return emptyArray()
        val p = path.trim()
        if (p.isEmpty()) return assets.list("") ?: emptyArray()
        val safe = sanitizeAssetPath(p)
        if (safe.isEmpty()) return emptyArray()
        return assets.list(safe) ?: emptyArray()
    }

    // --- Native render engine ------------------------------------------------

    external fun nativeOnSurfaceCreated()
    external fun nativeOnSurfaceChanged(width: Int, height: Int)
    external fun nativeOnDrawFrame()
    external fun nativeOnTouchesBegan(x: Float, y: Float)
    external fun nativeOnTouchesMoved(x: Float, y: Float)
    external fun nativeOnTouchesEnded(x: Float, y: Float)
    external fun nativeOnStop()

    /** True once the model is fully loaded on the GL thread. */
    external fun nativeIsModelLoaded(): Boolean

    // Expressions (Add-blended, stackable).
    external fun nativeGetExpressionCount(): Int
    external fun nativeGetExpressionName(index: Int): String?
    external fun nativeSetExpression(index: Int)
    external fun nativeClearExpressions()

    // Motion groups.
    external fun nativeGetMotionGroupCount(): Int
    external fun nativeGetMotionGroupName(index: Int): String?
    external fun nativePlayMotionGroup(index: Int)

    init {
        System.loadLibrary("luminalive2d")
    }
}
