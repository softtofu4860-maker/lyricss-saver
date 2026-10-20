package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
internal fun AmbientArtworkBackground(source: Bitmap?, first: Color, second: Color, third: Color) {
    val profile = LocalPerformanceMode.current.profile
    val artwork = rememberBoundedArtwork(source, profile.ambientMaxEdge)
    val image = remember(artwork) { artwork?.asImageBitmap() }
    val base = remember(first, second) { Brush.linearGradient(listOf(first.copy(alpha = 0.28f), second.copy(alpha = 0.2f))) }
    val firstOrb = remember(first, third) { Brush.radialGradient(listOf(first.copy(alpha = 0.55f), third.copy(alpha = 0.28f), Color.Transparent)) }
    val secondOrb = remember(first, second) { Brush.radialGradient(listOf(second.copy(alpha = 0.6f), first.copy(alpha = 0.3f), Color.Transparent)) }
    val vignette = remember { Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f), Color.Black.copy(alpha = 0.78f))) }
    Box(Modifier.fillMaxSize().background(base)) {
        if (profile.richEffects) {
            // Soft gradient orbs avoid two additional large blur/render layers.
            Box(Modifier.size(520.dp).offset((-80).dp, (-60).dp).background(firstOrb, CircleShape))
            Box(Modifier.size(560.dp).align(Alignment.BottomEnd).offset(100.dp, 80.dp).background(secondOrb, CircleShape))
        }
        if (image != null) Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (profile.richEffects) 0.32f else 0.16f }
                .then(if (profile.richEffects) Modifier.blur(48.dp) else Modifier))
        Box(Modifier.fillMaxSize().background(vignette))
    }
}
