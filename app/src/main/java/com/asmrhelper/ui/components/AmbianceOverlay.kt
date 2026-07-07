package com.asmrhelper.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

/**
 * Immersive particle system + breathing glow for the ASMR play screen.
 * Particles float upward; glow pulses when playing.
 */
@Composable
fun AmbianceOverlay(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    // --- Breathing glow animation ---
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val effectiveGlowAlpha = if (isPlaying) glowAlpha else 0f

    // --- Particles ---
    data class Particle(
        var x: Float,
        var y: Float,
        val speed: Float,
        val size: Float,
        val alpha: Float,
        val phase: Float
    )

    val particles = remember {
        (0 until 12).map {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = 0.2f + Random.nextFloat() * 0.4f,
                size = 2f + Random.nextFloat() * 4f,
                alpha = 0.1f + Random.nextFloat() * 0.25f,
                phase = Random.nextFloat() * 6.28f
            )
        }.toMutableList()
    }

    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { } // wait for next frame
            time += 0.016f
            for (p in particles) {
                p.y -= p.speed * 0.003f
                p.x += sin(time * 2f + p.phase) * 0.0015f
                if (p.y < -0.05f) {
                    p.y = 1.05f
                    p.x = Random.nextFloat()
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // --- Breathing glow ---
        if (effectiveGlowAlpha > 0.01f) {
            val glowRadius = w * 0.35f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = effectiveGlowAlpha),
                        accentColor.copy(alpha = effectiveGlowAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(cx, cy)
            )
        }

        // --- Particles ---
        for (p in particles) {
            val px = p.x * w
            val py = p.y * h
            drawCircle(
                color = accentColor.copy(alpha = p.alpha * (if (isPlaying) 1f else 0f)),
                radius = p.size,
                center = Offset(px, py)
            )
        }
    }
}
