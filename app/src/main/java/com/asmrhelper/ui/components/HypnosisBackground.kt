package com.asmrhelper.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

enum class HypnosisBgType(val label: String, val index: Int) {
    CIRCLE_DIFFUSION("黑白圆圈扩散", 0),
    WHITE_PENDULUM("白底黑摆锤", 1),
    BLACK_PENDULUM("黑底白摆锤", 2);

    companion object {
        fun fromIndex(index: Int): HypnosisBgType =
            entries.find { it.index == index } ?: CIRCLE_DIFFUSION
    }
}

@Composable
fun HypnosisBackground(
    type: HypnosisBgType,
    modifier: Modifier = Modifier
) {
    when (type) {
        HypnosisBgType.CIRCLE_DIFFUSION -> CircleDiffusion(modifier)
        HypnosisBgType.WHITE_PENDULUM -> PendulumAnimation(
            backgroundColor = Color.White,
            pendulumColor = Color.Black,
            modifier = modifier
        )
        HypnosisBgType.BLACK_PENDULUM -> PendulumAnimation(
            backgroundColor = Color.Black,
            pendulumColor = Color.White,
            modifier = modifier
        )
    }
}

// ── Circle diffusion: concentric rings expanding from center ─────

@Composable
private fun CircleDiffusion(modifier: Modifier = Modifier) {
    val phase = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        phase.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    val progress = phase.value

    Canvas(modifier = modifier.background(Color.Black)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = size.minDimension * 0.7f

        // Draw multiple concentric rings at different phases
        val ringCount = 5
        for (i in 0 until ringCount) {
            val ringPhase = (progress + i.toFloat() / ringCount) % 1f
            val radius = maxRadius * ringPhase
            val alpha = (1f - ringPhase).coerceIn(0f, 1f)
            val strokeWidth = 3f + (1f - ringPhase) * 4f

            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.7f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = strokeWidth)
            )
        }

        // Center dot
        val dotPulse = 0.8f + 0.2f * sin(progress * 3.14159f * 6).toFloat()
        drawCircle(
            color = Color.White.copy(alpha = dotPulse),
            radius = 8f,
            center = Offset(cx, cy)
        )
    }
}

// ── Pendulum animation ───────────────────────────────────────────

@Composable
private fun PendulumAnimation(
    backgroundColor: Color,
    pendulumColor: Color,
    modifier: Modifier = Modifier
) {
    val phase = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        phase.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    val progress = phase.value

    Canvas(modifier = modifier.background(backgroundColor)) {
        val pivotX = size.width / 2f
        val pivotY = size.height * 0.08f
        val armLength = size.height * 0.55f
        val bobRadius = size.minDimension * 0.06f

        // Pendulum angle: swing from -30° to +30° using sine
        val maxAngle = Math.toRadians(30.0)
        val angle = maxAngle * sin(progress * 2 * Math.PI)

        val bobX = pivotX + armLength * sin(angle).toFloat()
        val bobY = pivotY + armLength * cos(angle).toFloat()

        // Draw arm
        drawLine(
            color = pendulumColor.copy(alpha = 0.8f),
            start = Offset(pivotX, pivotY),
            end = Offset(bobX, bobY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Draw pivot point
        drawCircle(
            color = pendulumColor.copy(alpha = 0.5f),
            radius = 6f,
            center = Offset(pivotX, pivotY)
        )

        // Draw bob (weight at end)
        drawCircle(
            color = pendulumColor,
            radius = bobRadius,
            center = Offset(bobX, bobY)
        )

        // Draw motion trail (ghost arcs)
        for (t in 0..5) {
            val trailAngle = maxAngle * sin((progress - t * 0.03f - 0.1f) * 2 * Math.PI)
            val tx = pivotX + armLength * sin(trailAngle).toFloat()
            val ty = pivotY + armLength * cos(trailAngle).toFloat()
            drawCircle(
                color = pendulumColor.copy(alpha = 0.08f),
                radius = bobRadius * 0.6f,
                center = Offset(tx, ty)
            )
        }
    }
}
