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
 * Renders the Live2D avatar into a TextureView.
 *
 * A TextureView (not GLSurfaceView) is required because it participates in
 * the normal view hierarchy: the model sits between the dim layer and the
 * chat overlay, which is impossible with a SurfaceView's window-level layer.
 *
 * The view owns a dedicated EGL render thread that mirrors GLSurfaceView's
 * lifecycle: surface created -> nativeOnSurfaceCreated/Changed, continuous
 * frames -> nativeOnDrawFrame, destroyed -> nativeOnStop. Touch events are
 * forwarded in view-local pixels exactly like the demo's GLSurfaceView.
 */
@Composable
fun LuminaAvatar(
    modifier: Modifier = Modifier,
    onModelLoaded: () -> Unit = {}
) {
    val holder = remember { AvatarHolder() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = TextureView(context)
            view.isOpaque = false
            // Forward touches in view-local pixels - the native engine maps
            // them through deviceToScreen for drag-follow and hit tests.
            view.setOnTouchListener { _, event ->
                if (!Live2DController.loaded) return@setOnTouchListener false
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
                    holder.start(view, st, w, h, onModelLoaded)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                    Live2DBridge.nativeOnSurfaceChanged(w, h)
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

    private var thread: RenderThread? = null

    @Synchronized
    fun start(
        view: TextureView,
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
        onModelLoaded: () -> Unit
    ) {
        stop()
        thread = RenderThread(surfaceTexture, width, height).also { it.start() }
        // Initialize() loads the model synchronously on the render thread; poll
        // briefly so the UI can crossfade as soon as the first frame exists.
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
 * Minimal EGL14/GLES2 render loop driving the native engine at ~30 fps.
 * 30 fps keeps idle animation smooth while halving GPU/battery cost versus
 * 60 fps - Live2D deformation does not benefit much from higher rates.
 */
private class RenderThread(
    private val surfaceTexture: SurfaceTexture,
    private val width: Int,
    private val height: Int
) : Thread("LuminaLive2D-GL") {

    @Volatile
    private var running = true

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
            Live2DBridge.nativeOnSurfaceChanged(width, height)

            val frameMillis = 33L
            var lastFrame = System.currentTimeMillis()
            while (running && !isInterrupted) {
                Live2DBridge.nativeOnDrawFrame()
                if (!EGL14.eglSwapBuffers(display, surface)) break

                val now = System.currentTimeMillis()
                val wait = frameMillis - (now - lastFrame)
                if (wait > 0) sleep(wait)
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

        // RGBA8888 + depth16: translucent so the dimmed app shows through,
        // depth for the offscreen mask rendering path.
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
            // Release model + GL resources before tearing the context down.
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
