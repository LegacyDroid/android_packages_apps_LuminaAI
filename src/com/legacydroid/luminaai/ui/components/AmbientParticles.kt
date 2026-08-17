/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.legacydroid.luminaai.ui.theme.LuminaCoral
import com.legacydroid.luminaai.ui.theme.LuminaGold
import com.legacydroid.luminaai.ui.theme.LuminaMint
import com.legacydroid.luminaai.ui.theme.LuminaPink
import kotlin.random.Random

private data class ParticleData(
    val initialX: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val maxAlpha: Float,
    val phaseOffset: Float
)

/** Rising floating light motes with a sine wobble and soft glow. */
@Composable
fun AmbientParticles(
    modifier: Modifier = Modifier,
    particleCount: Int = 22,
    intensityMultiplier: Float = 1f
) {
    val particles = remember(particleCount) {
        val colors = listOf(LuminaGold, LuminaCoral, LuminaMint, LuminaPink)
        List(particleCount) {
            ParticleData(
                initialX = Random.nextFloat(),
                speed = 0.6f + Random.nextFloat() * 0.8f,
                size = 2.5f + Random.nextFloat() * 3.5f,
                color = colors[Random.nextInt(colors.size)],
                maxAlpha = 0.25f + Random.nextFloat() * 0.45f,
                phaseOffset = Random.nextFloat()
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val timeProgression = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleTime"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        particles.forEach { p ->
            val progress = (timeProgression.value * p.speed + p.phaseOffset) % 1f
            val y = height * (1f - progress)
            val wobbleX =
                kotlin.math.sin((progress * 2 * Math.PI + p.phaseOffset * 5).toFloat()) * 24f
            val x = (p.initialX * width + wobbleX).coerceIn(0f, width)

            // Fade in from the bottom, fade out near the top
            val alphaProgress = when {
                progress < 0.15f -> progress / 0.15f
                progress > 0.85f -> (1f - progress) / 0.15f
                else -> 1f
            }
            val alpha = (p.maxAlpha * alphaProgress * intensityMultiplier).coerceIn(0f, 1f)

            if (alpha > 0.01f) {
                drawCircle(
                    color = p.color.copy(alpha = alpha * 0.4f),
                    radius = p.size * 2.2f,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.size,
                    center = Offset(x, y)
                )
            }
        }
    }
}