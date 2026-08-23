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
import androidx.compose.material3.MaterialTheme
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
import com.legacydroid.luminaai.ui.theme.LuminaTheme

/**
 * Fullscreen overlay root. Transparent so the app behind stays visible; the
 * dim layer, ambient glow, shockwave and chat appear over it.
 */
@Composable
fun LuminaRoot() {
    LuminaTheme {
        LuminaRootContent()
    }
}

@Composable
private fun LuminaRootContent() {
    val state = LuminaSession.state
    val shockwaveToken = LuminaSession.shockwaveToken
    val messages = LuminaSession.messages
    val isThinking = LuminaSession.isThinking
    val pendingApproval = LuminaSession.pendingApproval
    val memories = LuminaSession.memories
    val engineLabel = LuminaSession.engineLabel

    val scheme = MaterialTheme.colorScheme

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

            // Top-left primary glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.35f * ambientGlowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.30f, size.height * 0.20f),
                    radius = maxDimension * 0.50f
                ),
                radius = maxDimension * 0.50f,
                center = Offset(size.width * 0.30f, size.height * 0.20f)
            )

            // Right-center secondary glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.secondary.copy(alpha = 0.28f * ambientGlowIntensity),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.70f, size.height * 0.60f),
                    radius = maxDimension * 0.50f
                ),
                radius = maxDimension * 0.50f,
                center = Offset(size.width * 0.70f, size.height * 0.60f)
            )

            // Bottom-center tertiary glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.tertiary.copy(alpha = 0.15f * ambientGlowIntensity),
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
                pendingApproval = pendingApproval,
                memories = memories,
                engineLabel = engineLabel,
                onSendMessage = LuminaSession::sendMessage,
                onCancelGeneration = LuminaSession::cancelGeneration,
                onRerunLast = LuminaSession::rerunLast,
                onClose = LuminaSession::dismiss,
                onClearChat = LuminaSession::clearChat,
                onApprove = LuminaSession::approvePending,
                onDeny = LuminaSession::denyPending,
                onSuggestionToolAction = LuminaSession::onSuggestionToolAction,
                onAddMemory = LuminaSession::addMemory,
                onEditMemory = LuminaSession::editMemory,
                onDeleteMemory = LuminaSession::deleteMemory,
                onClearMemories = LuminaSession::clearMemories,
                onLoadHistorySessions = LuminaSession::listHistory,
                onResumeHistory = LuminaSession::resumeHistory,
                onDeleteHistorySession = LuminaSession::deleteHistorySession
            )
        }
    }
}