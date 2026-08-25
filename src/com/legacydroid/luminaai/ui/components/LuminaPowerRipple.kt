/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Ripples and glow that spread from the power button when it fires.
// Two waves, the second one slightly delayed.
@Composable
fun LuminaPowerRippleEffect(
    modifier: Modifier = Modifier,
    triggerToken: Long,
    isActive: Boolean
) {
    val ripple1Progress = remember(triggerToken) { Animatable(0f) }
    val ripple2Progress = remember(triggerToken) { Animatable(0f) }
    val glowBloomProgress = remember(triggerToken) { Animatable(0f) }

    val scheme = MaterialTheme.colorScheme

    val infiniteTransition = rememberInfiniteTransition(label = "glowDrift")
    val glowDrift by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    LaunchedEffect(triggerToken) {
        if (triggerToken == 0L) return@LaunchedEffect

        launch {
            ripple1Progress.snapTo(0f)
            ripple1Progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1800, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            )
        }
        launch {
            delay(280)
            ripple2Progress.snapTo(0f)
            ripple2Progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1800, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            )
        }
        launch {
            delay(100)
            glowBloomProgress.snapTo(0f)
            glowBloomProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1400, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val origin = Offset(width, height * 0.367f)

        // soft glow behind the ripples
        if (isActive || glowBloomProgress.value > 0f) {
            val bloomFactor = glowBloomProgress.value
            val maxBloomRadius = 450.dp.toPx()
            val currentBloomRadius = maxBloomRadius * bloomFactor

            val bloomAlpha = if (isActive) {
                if (bloomFactor < 0.4f) (bloomFactor / 0.4f) else glowDrift
            } else {
                (1f - bloomFactor) * 0.5f
            }

            if (currentBloomRadius > 1f && bloomAlpha > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            scheme.secondary.copy(alpha = 0.60f * bloomAlpha),
                            scheme.primary.copy(alpha = 0.35f * bloomAlpha),
                            scheme.tertiary.copy(alpha = 0.15f * bloomAlpha),
                            Color.Transparent
                        ),
                        center = origin,
                        radius = currentBloomRadius
                    ),
                    radius = currentBloomRadius,
                    center = origin
                )
            }
        }

        // the wave fades out as it expands
        fun drawSingleRipple(progress: Float, maxRadiusDp: Float, baseAlpha: Float) {
            if (progress in 0.001f..0.999f) {
                val maxRadius = maxRadiusDp.dp.toPx()
                val radius = progress * maxRadius

                val alpha = when {
                    progress < 0.08f -> (progress / 0.08f)
                    progress < 0.60f -> 1f - (progress - 0.08f) * 0.96f
                    else -> 0.5f * (1f - (progress - 0.60f) / 0.40f)
                } * baseAlpha

                val strokeWidth =
                    (2.dp.toPx() * (1f - progress * 0.75f)).coerceAtLeast(0.5.dp.toPx())

                // soft ring behind the line
                drawCircle(
                    color = scheme.primary.copy(alpha = alpha * 0.45f),
                    radius = radius,
                    center = origin,
                    style = Stroke(width = strokeWidth * 4f)
                )

                // the visible line itself
                drawCircle(
                    color = scheme.primary.copy(alpha = alpha * 0.95f),
                    radius = radius,
                    center = origin,
                    style = Stroke(width = strokeWidth)
                )

                // faint fill inside the ring
                if (radius > 10f) {
                    drawCircle(
                        color = scheme.secondary.copy(alpha = alpha * 0.25f),
                        radius = radius - strokeWidth * 2f,
                        center = origin,
                        style = Stroke(width = strokeWidth * 2.5f)
                    )
                }
            }
        }

        drawSingleRipple(ripple1Progress.value, 550f, 1.0f)
        drawSingleRipple(ripple2Progress.value, 500f, 0.5f)
    }
}