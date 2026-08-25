/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.legacydroid.luminaai.live2d

import android.content.Context
import android.content.res.AssetManager
import java.io.IOException

/**
 * Kotlin side of the JNI bridge to libluminalive2d. The native engine reads
 * APK assets through the loadFile and getAssetList functions here.
 */
object Live2DBridge {

    private var assetManager: AssetManager? = null

    /** Call once before any native use. */
    fun initialize(context: Context) {
        assetManager = context.assets
    }

    private fun sanitizeAssetPath(path: String): String {
        val p = path.trim()
        if (p.isEmpty() || p.startsWith("/") || p.contains('\u0000')) return ""
        if (p.split('/').any { it == ".." }) return ""
        return p
    }

    /** Reads an asset file, null if missing or unsafe. */
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
     * Lists an asset directory. Empty path means the assets root, where the
     * native side discovers expressions and motions. Directory entries end
     * with a slash.
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

    // native render engine entry points

    external fun nativeOnSurfaceCreated()
    external fun nativeOnSurfaceChanged(width: Int, height: Int)
    external fun nativeOnDrawFrame()
    external fun nativeOnTouchesBegan(x: Float, y: Float)
    external fun nativeOnTouchesMoved(x: Float, y: Float)
    external fun nativeOnTouchesEnded(x: Float, y: Float)
    external fun nativeOnStop()

    /** True once the model is loaded on the GL thread. */
    external fun nativeIsModelLoaded(): Boolean

    // expressions, add blended and stackable
    external fun nativeGetExpressionCount(): Int
    external fun nativeGetExpressionName(index: Int): String?
    external fun nativeSetExpression(index: Int)
    external fun nativeClearExpressions()

    // motion groups
    external fun nativeGetMotionGroupCount(): Int
    external fun nativeGetMotionGroupName(index: Int): String?
    external fun nativePlayMotionGroup(index: Int)

    init {
        System.loadLibrary("luminalive2d")
    }
}
