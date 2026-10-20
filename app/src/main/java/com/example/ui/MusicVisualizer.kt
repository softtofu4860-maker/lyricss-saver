package com.example.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import com.example.data.RenderProfile
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp // Import Compose color lerp function
import kotlin.math.*

enum class VisualizerMode {
    NEBULA_RING,   // Orbital particles and glowing concentric rings
    LIQUID_WAVES,  // Fluid Bezier wave overlays
    CYBER_BARS     // Concentric circular frequency spectrum bars
}

@Composable
fun MusicVisualizer(
    isPlaying: Boolean,
    bpm: Int,
    vibeColors: List<Color>,
    mode: VisualizerMode,
    modifier: Modifier = Modifier
) {
    val profile = LocalPerformanceMode.current.profile
    val phase = rememberEffectPhase(isPlaying, periodSeconds = 4.65f)
    val wavePaths = remember(profile.waveCount) { List(profile.waveCount) { Path() } }

    val bpmToUse = if (bpm > 0) bpm else 120
    val beatDurationMs = 60000L / bpmToUse

    val rawPrimary = vibeColors.getOrElse(0) { Color(0xFF00FFFF) }
    val rawSecondary = vibeColors.getOrElse(1) { Color(0xFF8A2BE2) }
    val rawAccent = vibeColors.getOrElse(2) { Color(0xFFFF007F) }

    // Convert neon vivid colors into elegant, premium Slate/Silver/Gray tones for a desaturated, professional aesthetic
    val primaryColor = remember(rawPrimary) {
        Color(0xFF94A3B8).copy(alpha = 0.4f) // Sleek Metallic Gray
    }
    val secondaryColor = remember(rawSecondary) {
        Color(0xFF475569).copy(alpha = 0.25f) // Quiet Slate Gray
    }
    val accentColor = remember(rawAccent) {
        Color(0xFFE2E8F0).copy(alpha = 0.15f) // Soft Off-White
    }

    Spacer(modifier = modifier.fillMaxSize().drawWithCache {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f
        val baseRadius = maxRadius * 0.45f
        val glowBrushes = listOf(
            Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.08f), Color.Transparent),
                center, (baseRadius * 1.8f).coerceAtLeast(1f)),
            Brush.radialGradient(listOf(accentColor.copy(alpha = 0.12f), Color.Transparent),
                center, (baseRadius * 0.4f).coerceAtLeast(1f))
        )
        val ringEffects = List(2) { i -> PathEffect.dashPathEffect(floatArrayOf(60f, 40f + i * 20f)) }
        val waveGradients = List(profile.waveCount) { i ->
            Brush.verticalGradient(listOf(listOf(primaryColor, secondaryColor, accentColor)[i], Color.Transparent),
                startY = size.height * (0.78f - 0.075f * (1f - i * 0.25f)), endY = size.height)
        }
        onDrawBehind {
            val beatPulse = if (isPlaying) {
                val currentBeatProgress = (System.currentTimeMillis() % beatDurationMs).toFloat() / beatDurationMs
                exp(-4.0f * currentBeatProgress)
            } else 0f
            val currentPhase = phase.value
            when (mode) {
                VisualizerMode.NEBULA_RING -> drawNebulaRing(center, maxRadius, currentPhase, beatPulse,
                    primaryColor, secondaryColor, accentColor, isPlaying, profile, glowBrushes, ringEffects)
                VisualizerMode.LIQUID_WAVES -> drawLiquidWaves(center, size.width, size.height, currentPhase,
                    beatPulse, isPlaying, wavePaths, waveGradients, profile)
                VisualizerMode.CYBER_BARS -> drawCyberBars(center, maxRadius, currentPhase, beatPulse,
                    primaryColor, secondaryColor, accentColor, isPlaying, profile)
            }
        }
    })
}

private fun DrawScope.drawNebulaRing(
    center: Offset,
    maxRadius: Float,
    phase: Float,
    beatPulse: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    isPlaying: Boolean,
    profile: RenderProfile,
    glowBrushes: List<Brush>,
    ringEffects: List<PathEffect>
) {
    val baseRadius = maxRadius * 0.45f
    val pulseOffset = beatPulse * 25f // Muted pulsing scale
    val activeRadius = baseRadius + if (isPlaying) pulseOffset else 0f

    if (profile.richEffects) drawCircle(
        brush = glowBrushes[0], alpha = 0.5f + beatPulse * 0.5f,
        center = center, radius = activeRadius * 1.8f
    )

    // 2. Draw orbiting rings (Thinner, more transparent)
    val ringCount = if (profile.richEffects) 2 else 1
    for (i in 0 until ringCount) {
        val angleOffset = i * (PI / ringCount).toFloat()
        val rotationAngle = (phase * (0.6f + i * 0.2f) + angleOffset) * (180f / PI.toFloat())
        
        rotate(rotationAngle, center) {
            drawCircle(
                color = secondaryColor.copy(alpha = 0.18f - i * 0.06f),
                center = center,
                radius = activeRadius * (1f + i * 0.12f),
                style = Stroke(
                    width = (1.5f + beatPulse * 1.5f),
                    pathEffect = ringEffects[i]
                )
            )
        }
    }

    if (profile.richEffects) drawCircle(
        brush = glowBrushes[1], center = center, radius = activeRadius * 0.4f
    )

    // 4. Orbiting particles (Fewer, smaller, more elegant)
    val particleCount = profile.particleCount
    for (i in 0 until particleCount) {
        val angle = (i * (2 * PI / particleCount) + phase * 0.25f).toFloat()
        val noise = sin(angle * 4 + phase) * 8f
        val distance = activeRadius + noise + (if (isPlaying) beatPulse * 12f * sin(angle * 6) else 0f)
        val particleX = center.x + cos(angle) * distance
        val particleY = center.y + sin(angle) * distance
        
        val sizeVal = (2f + beatPulse * 3f) * (0.6f + 0.4f * sin(angle * 2))
        val alphaVal = 0.15f + 0.25f * cos(angle + phase)

        // Optimized: Passing alphaVal directly to drawCircle instead of creating new instances via Color.copy()
        drawCircle(
            color = if (i % 2 == 0) primaryColor else accentColor,
            alpha = alphaVal.coerceIn(0f, 1f),
            center = Offset(particleX, particleY),
            radius = sizeVal
        )
    }
}

private fun DrawScope.drawLiquidWaves(
    center: Offset,
    width: Float,
    height: Float,
    phase: Float,
    beatPulse: Float,
    isPlaying: Boolean,
    wavePaths: List<Path>,
    waveGradients: List<Brush>,
    profile: RenderProfile
) {
    val waveCount = profile.waveCount
    val baseAmplitude = height * 0.05f
    val speedMultiplier = if (isPlaying) 1.0f + beatPulse * 1.2f else 0.3f
    val waveBaseY = height * 0.78f // Shifted to lower part of the screen so it doesn't cross behind the center lyric lines!

    for (w in 0 until waveCount) {
        val path = wavePaths[w]
        path.reset() // Reuse path object to eliminate GC pressure completely

        val amplitude = baseAmplitude * (1f - w * 0.25f) * (1f + beatPulse * 0.3f)
        val frequency = 0.004f + w * 0.0015f
        val wavePhase = phase * speedMultiplier + w * (PI.toFloat() / 3f)

        path.moveTo(0f, waveBaseY)

        for (x in 0..width.toInt() step profile.waveStepPx) {
            val radians = x * frequency + wavePhase
            val y = waveBaseY + sin(radians) * amplitude + cos(radians * 0.4f) * (amplitude * 0.25f)
            path.lineTo(x.toFloat(), y)
        }

        path.lineTo(width, height)
        path.lineTo(0f, height)
        path.close()

        val gradient = waveGradients.getOrNull(w) ?: Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        val waveAlpha = when (w) {
            0 -> 0.12f + beatPulse * 0.08f
            1 -> 0.09f
            else -> 0.06f
        }

        // Optimized: Drawing with pre-allocated cached gradients, modulating alpha via the drawPath parameter
        drawPath(
            path = path,
            brush = gradient,
            alpha = waveAlpha
        )
    }
}

private fun DrawScope.drawCyberBars(
    center: Offset,
    maxRadius: Float,
    phase: Float,
    beatPulse: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    isPlaying: Boolean,
    profile: RenderProfile
) {
    val innerRadius = maxRadius * 0.52f // Pushed outward to clear the center text
    val barCount = profile.barCount // Reduced density
    val maxBarHeight = maxRadius * 0.28f

    for (i in 0 until barCount) {
        val angle = (i * (2 * PI / barCount) + phase * 0.08f).toFloat()
        
        val noiseFreq = (sin(angle * 8 + phase * 1.2f) * cos(angle * 3)).absoluteValue
        val activityFactor = if (isPlaying) 0.2f + 0.6f * noiseFreq + beatPulse * 0.3f else 0.04f + 0.04f * sin(angle * 6 + phase)
        val barHeight = (activityFactor * maxBarHeight).coerceAtMost(maxBarHeight)

        val startX = center.x + cos(angle) * innerRadius
        val startY = center.y + sin(angle) * innerRadius
        
        val endX = center.x + cos(angle) * (innerRadius + barHeight)
        val endY = center.y + sin(angle) * (innerRadius + barHeight)

        val barAlpha = 0.08f + 0.18f * activityFactor
        
        // Optimized: Bypass linear gradient shader allocations per bar per frame!
        // Using Color.lerp on the GPU is incredibly fast and produces perfect high-performance solid color blending.
        val blendedColor = lerp(primaryColor, accentColor, activityFactor)

        // Optimized: Passing barAlpha directly to drawLine instead of using .copy(alpha = barAlpha)
        drawLine(
            color = blendedColor,
            alpha = barAlpha,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 3.5f, // Thinner lines
            cap = StrokeCap.Round
        )
    }
}
