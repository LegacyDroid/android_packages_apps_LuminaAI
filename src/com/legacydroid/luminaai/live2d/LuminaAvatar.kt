/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.live2d

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.MotionEvent
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders the Live2D avatar into a TextureView. A TextureView is used over a
 * SurfaceView because it needs to sit between the dim layer and the chat,
 * which only works inside the normal view hierarchy. Owns a dedicated EGL
 * render thread and forwards touch events in view local pixels.
 */
@Composable
fun LuminaAvatar(
    modifier: Modifier = Modifier,
    touchEnabled: Boolean = true,
    onModelLoaded: () -> Unit = {}
) {
    val holder = remember { AvatarHolder() }
    holder.touchEnabled = touchEnabled

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = TextureView(context)
            view.isOpaque = false
            // touches go to the engine in view local pixels for drag and
            // hit tests. The caller disables this during chat sessions.
            view.setOnTouchListener { _, event ->
                if (!holder.touchEnabled || !Live2DController.loaded) {
                    return@setOnTouchListener false
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        Live2DBridge.nativeOnTouchesBegan(event.x, event.y)
                    MotionEvent.ACTION_MOVE ->
                        Live2DBridge.nativeOnTouchesMoved(event.x, event.y)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        Live2DBridge.nativeOnTouchesEnded(event.x, event.y)
                }
                true
            }
            view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    holder.start(st, w, h, onModelLoaded)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    // no EGL context on this thread, the render thread
                    // picks the size up itself
                    holder.requestSize(w, h)
                }

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    holder.stop()
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
            view
        },
        onRelease = { holder.stop() }
    )
}

/**
 * Owns the EGL render thread for one TextureView surface.
 */
private class AvatarHolder {

    @Volatile
    var touchEnabled = true

    private var thread: RenderThread? = null

    fun requestSize(width: Int, height: Int) {
        thread?.requestSize(width, height)
    }

    @Synchronized
    fun start(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
        onModelLoaded: () -> Unit
    ) {
        stop()
        val t = RenderThread(surfaceTexture).also { it.start() }
        t.requestSize(width, height)
        thread = t
        // the model loads on the render thread, poll until it is there so
        // the UI can fade it in right away
        Thread {
            repeat(100) {
                if (runCatching { Live2DBridge.nativeIsModelLoaded() }.getOrDefault(false)) {
                    Live2DController.markLoaded()
                    onModelLoaded()
                    return@Thread
                }
                Thread.sleep(50)
            }
        }.start()
    }

    @Synchronized
    fun stop() {
        thread?.shutdown()
        thread = null
        Live2DController.markUnloaded()
    }
}

/**
 * Small EGL14 and GLES2 loop driving the engine at 30fps. Higher rates barely
 * help a Live2D model and cost noticeably more battery.
 */
private class RenderThread(
    private val surfaceTexture: SurfaceTexture
) : Thread("LuminaLive2D-GL") {

    @Volatile
    private var running = true

    // requested surface size, only applied here on the GL thread where the
    // EGL context lives
    @Volatile
    private var pendingWidth = 0

    @Volatile
    private var pendingHeight = 0
    private var appliedWidth = 0
    private var appliedHeight = 0

    fun requestSize(width: Int, height: Int) {
        pendingWidth = width
        pendingHeight = height
    }

    private lateinit var display: EGLDisplay
    private var context: EGLContext? = null
    private var surface: EGLSurface? = null

    override fun run() {
        if (!initEgl()) {
            shutdownEgl()
            return
        }
        runCatching {
            Live2DBridge.nativeOnSurfaceCreated()

            val frameMillis = 33L
            var lastFrame = System.currentTimeMillis()
            while (running && !isInterrupted) {
                if (pendingWidth != appliedWidth || pendingHeight != appliedHeight) {
                    appliedWidth = pendingWidth
                    appliedHeight = pendingHeight
                    if (appliedWidth > 0 && appliedHeight > 0) {
                        Live2DBridge.nativeOnSurfaceChanged(appliedWidth, appliedHeight)
                    }
                }
                Live2DBridge.nativeOnDrawFrame()
                if (!EGL14.eglSwapBuffers(display, surface)) break

                val now = System.currentTimeMillis()
                val wait = frameMillis - (now - lastFrame)
                if (wait > 0) sleep(wait) else sleep(1) // frame overran, never spin hot
                lastFrame = System.currentTimeMillis()
            }
        }
        shutdownEgl()
    }

    fun shutdown() {
        running = false
        interrupt()
        join(1500)
    }

    private fun initEgl(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false

        // translucent so the dimmed app shows through, depth for mask
        // rendering
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0) ||
            numConfigs[0] == 0
        ) {
            return false
        }

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (context == null || context == EGL14.EGL_NO_CONTEXT) return false

        surface = EGL14.eglCreateWindowSurface(display, configs[0], surfaceTexture, intArrayOf(EGL14.EGL_NONE), 0)
        if (surface == null || surface == EGL14.EGL_NO_SURFACE) return false

        return EGL14.eglMakeCurrent(display, surface, surface, context)
    }

    private fun shutdownEgl() {
        runCatching {
            // free the model and GL state before killing the context
            Live2DBridge.nativeOnStop()
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            surface?.let { EGL14.eglDestroySurface(display, it) }
            context?.let { EGL14.eglDestroyContext(display, it) }
            EGL14.eglTerminate(display)
        }
        surface = null
        context = null
    }
}
