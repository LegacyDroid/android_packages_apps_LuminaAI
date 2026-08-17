/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.legacydroid.luminaai.model.LuminaState
import com.legacydroid.luminaai.ui.components.AmbientParticles
import com.legacydroid.luminaai.ui.components.LuminaChatOverlay
import com.legacydroid.luminaai.ui.components.LuminaPowerRippleEffect
import com.legacydroid.luminaai.ui.theme.LuminaAmber
import com.legacydroid.luminaai.ui.theme.LuminaBgCard
import com.legacydroid.luminaai.ui.theme.LuminaCoral
import com.legacydroid.luminaai.ui.theme.LuminaMint
import com.legacydroid.luminaai.ui.theme.LuminaTextPrimary

/**
 * Fullscreen overlay root. Transparent so the app behind stays visible; the
 * dim layer, ambient glow, shockwave and chat appear over it.
 */
@Composable
fun LuminaRoot() {
    MaterialTheme(
        colors = darkColors(
            primary = LuminaCoral,
            secondary = LuminaAmber,
            background = Color.Transparent,
            surface = LuminaBgCard,
            onPrimary = Color.White,
            onBackground = LuminaTextPrimary,
            onSurface = LuminaTextPrimary
        )
    ) {
        LuminaRootContent()
    }
}

@Composable
private fun LuminaRootContent() {
    val state = LuminaSession.state
    val shockwaveToken = LuminaSession.shockwaveToken
    val messages = LuminaSession.messages
    val isThinking = LuminaSession.isThinking

    val ambientGlowIntensity by animateFloatAsState(
        targetValue = if (state == LuminaState.AWAKENED) 0.65f else 0.15f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ambientGlow"
    )

    val glowBlur by animateDpAsState(
        targetValue = if (state == LuminaState.AWAKENED) 12.dp else 0.dp,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "glowBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Atmospheric glows over the dimmed app
        Canvas(modifier = Modifier.fillMaxSize().blur(glowBlur)) {
            val maxDimension = kotlin.math.max(size.width, size.height)

            // Top-left coral glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LuminaCoral.copy(alpha = 0.35f * ambientGlowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.30f, size.height * 0.20f),
                    radius = maxDimension * 0.50f
                ),
                radius = maxDimension * 0.50f,
                center = Offset(size.width * 0.30f, size.height * 0.20f)
            )

            // Right-center amber glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LuminaAmber.copy(alpha = 0.28f * ambientGlowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.70f, size.height * 0.60f),
                    radius = maxDimension * 0.50f
                ),
                radius = maxDimension * 0.50f,
                center = Offset(size.width * 0.70f, size.height * 0.60f)
            )

            // Bottom-center mint glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LuminaMint.copy(alpha = 0.15f * ambientGlowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.50f, size.height * 0.90f),
                    radius = maxDimension * 0.50f
                ),
                radius = maxDimension * 0.50f,
                center = Offset(size.width * 0.50f, size.height * 0.90f)
            )
        }

        // Floating light motes
        AmbientParticles(
            particleCount = if (state == LuminaState.AWAKENED) 20 else 12,
            intensityMultiplier = if (state == LuminaState.AWAKENED) 1.0f else 0.5f
        )

        // Power-button shockwave ripples and glow bloom
        LuminaPowerRippleEffect(
            triggerToken = shockwaveToken,
            isActive = state == LuminaState.AWAKENED
        )

        // Dim the app behind when awake
        AnimatedVisibility(
            visible = state == LuminaState.AWAKENED,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(700))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f))
            )
        }

        // Orb, label and chat overlay
        AnimatedVisibility(
            visible = state == LuminaState.AWAKENED,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(700))
        ) {
            LuminaChatOverlay(
                messages = messages,
                isThinking = isThinking,
                onSendMessage = LuminaSession::sendMessage,
                onClose = LuminaSession::dismiss,
                onClearChat = LuminaSession::clearChat
            )
        }
    }
}