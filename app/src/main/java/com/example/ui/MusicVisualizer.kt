package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp // Import Compose color lerp function
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    // 30 FPS frame-rate control: manually advance phase at ~30Hz to save CPU/GPU cycles!
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val frameTimeMs = 1000L / 30L // ~33ms (30 FPS constraint)
            while (true) {
                phase += 0.045f
                if (phase > 2 * PI.toFloat()) {
                    phase -= 2 * PI.toFloat()
                }
                delay(frameTimeMs)
            }
        }
    }

    // Pre-allocate paths to prevent garbage collection overhead during drawing
    val wavePaths = remember { List(3) { Path() } }

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

    // Pre-calculate screen sizes and cache gradient brushes to prevent dynamic heap allocations at 60fps/120fps
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val waveBaseY = screenHeightPx * 0.78f

    val gradient0 = remember(primaryColor, screenHeightPx) {
        Brush.verticalGradient(
            colors = listOf(primaryColor, Color.Transparent),
            startY = waveBaseY - (screenHeightPx * 0.05f) * 1.5f,
            endY = screenHeightPx
        )
    }
    val gradient1 = remember(secondaryColor, screenHeightPx) {
        Brush.verticalGradient(
            colors = listOf(secondaryColor, Color.Transparent),
            startY = waveBaseY - (screenHeightPx * 0.05f * 0.75f) * 1.5f,
            endY = screenHeightPx
        )
    }
    val gradient2 = remember(accentColor, screenHeightPx) {
        Brush.verticalGradient(
            colors = listOf(accentColor, Color.Transparent),
            startY = waveBaseY - (screenHeightPx * 0.05f * 0.5f) * 1.5f,
            endY = screenHeightPx
        )
    }
    val waveGradients = remember(gradient0, gradient1, gradient2) {
        listOf(gradient0, gradient1, gradient2)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = min(size.width, size.height) / 2f

        // Calculate beat pulse on the fly during the draw phase based on current time
        val beatPulse = if (isPlaying) {
            val systemTime = System.currentTimeMillis()
            val currentBeatProgress = (systemTime % beatDurationMs).toFloat() / beatDurationMs
            exp(-4.0f * currentBeatProgress)
        } else {
            0f
        }

        when (mode) {
            VisualizerMode.NEBULA_RING -> {
                drawNebulaRing(center, maxRadius, phase, beatPulse, primaryColor, secondaryColor, accentColor, isPlaying)
            }
            VisualizerMode.LIQUID_WAVES -> {
                drawLiquidWaves(center, size.width, size.height, phase, beatPulse, isPlaying, wavePaths, waveGradients)
            }
            VisualizerMode.CYBER_BARS -> {
                drawCyberBars(center, maxRadius, phase, beatPulse, primaryColor, secondaryColor, accentColor, isPlaying)
            }
        }
    }
}

private fun DrawScope.drawNebulaRing(
    center: Offset,
    maxRadius: Float,
    phase: Float,
    beatPulse: Float,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    isPlaying: Boolean
) {
    val baseRadius = maxRadius * 0.45f
    val pulseOffset = beatPulse * 25f // Muted pulsing scale
    val activeRadius = baseRadius + if (isPlaying) pulseOffset else 0f

    // 1. Draw glowing background radial gradient (Much softer)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primaryColor.copy(alpha = 0.08f * (0.5f + beatPulse * 0.5f)), Color.Transparent),
            center = center,
            radius = activeRadius * 1.8f
        ),
        center = center,
        radius = activeRadius * 1.8f
    )

    // 2. Draw orbiting rings (Thinner, more transparent)
    val ringCount = 2
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
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(60f, 40f + i * 20f), 0f)
                )
            )
        }
    }

    // 3. Draw a central pulsing core (Extremely soft)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accentColor.copy(alpha = 0.12f), accentColor.copy(alpha = 0f)),
            center = center,
            radius = activeRadius * 0.4f
        ),
        center = center,
        radius = activeRadius * 0.4f
    )

    // 4. Orbiting particles (Fewer, smaller, more elegant)
    val particleCount = 18
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
            alpha = alphaVal,
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
    waveGradients: List<Brush>
) {
    val waveCount = 3
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

        for (x in 0..width.toInt() step 6) {
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
    isPlaying: Boolean
) {
    val innerRadius = maxRadius * 0.52f // Pushed outward to clear the center text
    val barCount = 36 // Reduced density
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
