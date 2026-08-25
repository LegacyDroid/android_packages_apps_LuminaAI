/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.live2d

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Facade over the native Live2D engine for the tools and the overlay UI.
 * The model's expression names are Chinese, so friendly English aliases are
 * mapped onto them here, e.g. angry becomes 生气 and catears becomes 猫耳.
 */
object Live2DController {

    /** Lib loaded and the model staged in the APK. */
    var available by mutableStateOf(false)
        private set

    /** True once the GL thread finished loading the model. */
    var loaded by mutableStateOf(false)
        private set

    private var initialized = false

    /** English aliases for the expression names that ship with the model. */
    val EXPRESSION_ALIASES: Map<String, String> = mapOf(
        "surprised" to "惊讶",
        "shocked" to "惊讶",
        "angry" to "生气",
        "mad" to "生气",
        "confused" to "疑惑",
        "question" to "疑惑",
        "crying" to "流泪",
        "tears" to "流泪",
        "sad" to "流泪",
        "despair" to "脸黑",
        "deadpan" to "脸黑",
        "eyeroll" to "白眼",
        "sarcastic" to "白眼",
        "stareyes" to "星星眼",
        "amazed" to "星星眼",
        "excited" to "星星眼",
        "hearteyes" to "爱心眼",
        "love" to "爱心眼",
        "moneyeyes" to "金钱眼",
        "greedy" to "金钱眼",
        "blush" to "脸红",
        "smirk_left" to "←歪嘴",
        "smirk_right" to "歪嘴→",
        "smug" to "歪嘴→",
        "tongue" to "舌头",
        "cheeky" to "舌头",
        "catears" to "猫耳",
        "cat_mode" to "猫耳",
        "crown" to "王冠",
        "boss_mode" to "王冠",
        "wings" to "翅膀",
        "hair_down" to "披发",
        "ponytail" to "马尾",
        "streamer_desk" to "直播套装",
        "gamepad" to "手柄"
    )

    val MOTION_ALIASES: Map<String, String> = mapOf(
        "idle" to "DaiJi",     // standby
        "standby" to "DaiJi",
        "wave" to "HuiShou",   // wave
        "wink" to "MeiYan"     // flirty wink
    )

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        val bridgeReady = runCatching {
            // also triggers System.loadLibrary through the bridge init
            Live2DBridge.initialize(appContext)
            android.util.Log.i(TAG, "bridge initialized ok")
            true
        }.onFailure {
            android.util.Log.w(TAG, "bridge init failed", it)
        }.getOrDefault(false)

        val modelStaged = runCatching {
            appContext.assets.list("")?.contains("IceGirl.model3.json") == true
        }.onFailure {
            android.util.Log.w(TAG, "model staging probe failed", it)
        }.getOrDefault(false)

        available = bridgeReady && modelStaged
        if (!available) {
            android.util.Log.w(
                TAG,
                "Avatar unavailable (lib=$bridgeReady, modelStaged=$modelStaged). " +
                    "Run live2d/get_vendor.sh to stage it."
            )
        }
    }

    private const val TAG = "LuminaLive2D"

    /** Called by the render view once loading finished. */
    internal fun markLoaded() {
        loaded = true
    }

    internal fun markUnloaded() {
        loaded = false
    }

    fun expressionNames(): List<String> {
        if (!loaded) return emptyList()
        return runCatching {
            (0 until Live2DBridge.nativeGetExpressionCount())
                .mapNotNull { Live2DBridge.nativeGetExpressionName(it) }
        }.getOrDefault(emptyList())
    }

    /** Sets an expression by friendly alias or raw model name. */
    fun setExpression(name: String): Boolean {
        if (!loaded) return false
        val target = EXPRESSION_ALIASES[name.lowercase().replace(" ", "").replace("_", "")] ?: name
        val names = expressionNames()
        val index = names.indexOfFirst { it.equals(target, ignoreCase = true) }
        if (index < 0) return false
        runCatching { Live2DBridge.nativeSetExpression(index) }
        return true
    }

    fun clearExpressions() {
        if (!loaded) return
        runCatching { Live2DBridge.nativeClearExpressions() }
    }

    fun motionGroups(): List<String> {
        if (!loaded) return emptyList()
        return runCatching {
            (0 until Live2DBridge.nativeGetMotionGroupCount())
                .mapNotNull { Live2DBridge.nativeGetMotionGroupName(it) }
        }.getOrDefault(emptyList())
    }

    /** Plays a motion by friendly alias or raw group name. */
    fun playMotion(name: String): Boolean {
        if (!loaded) return false
        val target = MOTION_ALIASES[name.lowercase().replace(" ", "")] ?: name
        val groups = motionGroups()
        val index = groups.indexOfFirst { it.equals(target, ignoreCase = true) }
        if (index < 0) return false
        runCatching { Live2DBridge.nativePlayMotionGroup(index) }
        return true
    }
}
