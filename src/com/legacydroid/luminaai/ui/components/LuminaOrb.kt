/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material-colored orb: rotating primary/secondary/tertiary conic gradient,
 * specular highlight, tertiary fill layer, inset 3D rim, pulsing white core,
 * slow rotation and vertical float.
 */
@Composable
fun LuminaOrb(
    modifier: Modifier = Modifier,
    size: Dp = 110.dp,
    isThinking: Boolean = false,
    scale: Float = 1f
) {
    val scheme = MaterialTheme.colorScheme

    val infiniteTransition = rememberInfiniteTransition(label = "orbAnimations")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isThinking) 6000 else 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbSlowRotate"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbFloat"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isThinking) 1.25f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isThinking) 750 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    val coreAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (isThinking) 1.0f else 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isThinking) 750 else 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbCoreAlpha"
    )

    Box(
        modifier = modifier
            .size(size * 2.2f)
            .offset(y = floatOffset.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 2.2f)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val orbRadius = (size.toPx() / 2f)

            // Far ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = 0.40f * corePulse),
                        scheme.secondary.copy(alpha = 0.22f * corePulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = orbRadius * 1.9f
                ),
                radius = orbRadius * 1.9f,
                center = center
            )

            // Rotating conic gradient orb body
            rotate(degrees = rotationAngle + 200f, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            scheme.primary,
                            scheme.secondary,
                            scheme.tertiary,
                            scheme.primary
                        ),
                        center = center
                    ),
                    radius = orbRadius,
                    center = center
                )
            }

            // Specular highlight (top-left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.50f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - orbRadius * 0.30f, center.y - orbRadius * 0.40f),
                    radius = orbRadius * 0.56f
                ),
                radius = orbRadius,
                center = center
            )

            // Tertiary fill layer (bottom-right)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        scheme.tertiary.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(center.x + orbRadius * 0.30f, center.y + orbRadius * 0.40f),
                    radius = orbRadius * 0.90f
                ),
                radius = orbRadius,
                center = center
            )

            // Inset 3D sphere shadow / rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.25f)
                    ),
                    center = Offset(center.x + orbRadius * 0.2f, center.y + orbRadius * 0.2f),
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = center
            )

            // Inner glowing core, pulsing with the orb
            val innerRadius = orbRadius * 0.40f * corePulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = coreAlpha),
                        Color.White.copy(alpha = coreAlpha * 0.30f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = innerRadius
                ),
                radius = innerRadius,
                center = center
            )
        }
    }
}