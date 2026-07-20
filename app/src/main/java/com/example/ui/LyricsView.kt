package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LyricLine

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    activeIndex: Int,
    activeColor: Color,
    onLineClicked: (Long) -> Unit, // Allows the user to tap on any line to seek directly to that lyric in the song!
    onBackgroundClicked: () -> Unit, // Toggles playback controls when clicking empty spaces
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                onBackgroundClicked()
            }
    ) {
        val maxWidthDp = maxWidth.value
        val halfHeightDp = maxHeight / 2
        // Spacer height to ensure the active lyric line is positioned exactly in the center of the screen
        val paddingOffsetDp = remember(halfHeightDp) { (halfHeightDp - 36.dp).coerceAtLeast(100.dp) }

        val paddingOffsetPx = remember(density, paddingOffsetDp) {
            with(density) { paddingOffsetDp.roundToPx() }
        }

        // Auto-scroll to active lyric line with high precision (Centering)
        LaunchedEffect(activeIndex) {
            if (lines.isNotEmpty() && activeIndex >= 0) {
                try {
                    val targetIndex = activeIndex + 1
                    val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
                    if (visibleItem != null) {
                        val delta = visibleItem.offset - paddingOffsetPx
                        if (delta != 0) {
                            lazyListState.scroll {
                                var accumulated = 0f
                                androidx.compose.animation.core.animate(
                                    initialValue = 0f,
                                    targetValue = delta.toFloat(),
                                    animationSpec = androidx.compose.animation.core.tween(
                                        durationMillis = 650,
                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                    )
                                ) { value, _ ->
                                    val deltaToScroll = value - accumulated
                                    scrollBy(deltaToScroll)
                                    accumulated = value
                                }
                            }
                        }
                    } else {
                        lazyListState.animateScrollToItem(
                            index = targetIndex,
                            scrollOffset = -paddingOffsetPx
                        )
                    }
                } catch (e: Exception) {
                    try {
                        lazyListState.animateScrollToItem(
                            index = activeIndex + 1,
                            scrollOffset = -paddingOffsetPx
                        )
                    } catch (ex: Exception) {
                        // Complete safety
                    }
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(28.dp), // More breathing space for high readability
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    onBackgroundClicked()
                }
        ) {
            // 1. Top Centering Spacer
            item {
                Spacer(modifier = Modifier.height(paddingOffsetDp))
            }

            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.timeSec}_${index}" } // Stable keys for high performance and smooth animation
            ) { index, line ->
                val isActive = index == activeIndex
                
                // Calculate responsive static base size based on text length to fit screen width
                val baseFontSize = remember(line.text, maxWidthDp) {
                    val availableWidthDp = (maxWidthDp - 80f).coerceAtLeast(180f)
                    val charWidthDp = 18f * 0.48f // Roughly 48% of base font size
                    val maxComfortableChars = (availableWidthDp / charWidthDp).coerceAtLeast(15f)
                    val length = line.text.length.coerceAtLeast(1)
                    if (length > maxComfortableChars) {
                        val scale = (maxComfortableChars / length.toFloat()).coerceIn(0.7f, 1.0f)
                        18f * scale
                    } else {
                        18f
                    }
                }
                
                // Animate properties for smooth focus transitions
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.12f else 0.94f,
                    animationSpec = tween(durationMillis = 250),
                    label = "scale"
                )
                
                val opacity by animateFloatAsState(
                    targetValue = if (isActive) 1.0f else 0.50f, // 50% opacity for previous/next lyrics
                    animationSpec = tween(durationMillis = 250),
                    label = "opacity"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isActive) Color.White else Color.White, // Always base of pure white
                    animationSpec = tween(durationMillis = 250),
                    label = "text_color"
                )

                Text(
                    text = line.text,
                    color = textColor,
                    fontSize = baseFontSize.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium, // Modern font hierarchy
                    textAlign = TextAlign.Center,
                    lineHeight = (baseFontSize * 1.45f).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .graphicsLayer {
                            // Hardware-accelerated scaling and opacity fading completely bypass composition and layout!
                            scaleX = scale
                            scaleY = scale
                            this.alpha = opacity
                        }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null // Clean hover, no heavy ripple on plain text click
                        ) {
                            // Seek to song position of the clicked line
                            onLineClicked((line.timeSec * 1000).toLong())
                        }
                )
            }

            // 2. Bottom Centering Spacer
            item {
                Spacer(modifier = Modifier.height(paddingOffsetDp))
            }
        }

        val vignetteHeight = remember(maxHeight) { (maxHeight * 0.25f).coerceAtMost(160.dp) }

        // Elegant vignette overlays at top and bottom to make lyrics fade out beautifully
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(vignetteHeight)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF030304),
                            Color(0xFF030304).copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(vignetteHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF030304).copy(alpha = 0.8f),
                            Color(0xFF030304)
                        )
                    )
                )
        )
    }
}
