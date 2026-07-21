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

    val animatedActiveIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = tween(durationMillis = 280),
        label = "animated_active_index"
    )

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
                
                Text(
                    text = line.text,
                    color = Color.White,
                    fontSize = baseFontSize.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium, // Modern font hierarchy
                    textAlign = TextAlign.Center,
                    lineHeight = (baseFontSize * 1.45f).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .graphicsLayer {
                            // Hardware-accelerated scaling and opacity fading based on distance to animated index
                            val diff = kotlin.math.abs(index.toFloat() - animatedActiveIndex)
                            val scaleVal = (1.12f - (diff * 0.18f)).coerceAtLeast(0.94f)
                            val opacityVal = (1.0f - (diff * 0.50f)).coerceAtLeast(0.35f)
                            
                            scaleX = scaleVal
                            scaleY = scaleVal
                            this.alpha = opacityVal
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinimalLyricsView(
    lines: List<LyricLine>,
    activeIndex: Int,
    activeColor: Color,
    onLineClicked: (Long) -> Unit,
    onBackgroundClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    val animatedActiveIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = tween(durationMillis = 280),
        label = "animated_active_index"
    )

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
        val paddingOffsetDp = remember(halfHeightDp) { (halfHeightDp - 44.dp).coerceAtLeast(100.dp) }

        val paddingOffsetPx = remember(density, paddingOffsetDp) {
            with(density) { paddingOffsetDp.roundToPx() }
        }

        // Auto-scroll to active lyric line with high precision
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
                    } catch (ex: Exception) {}
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(32.dp), // Spacious breathing gap
            horizontalAlignment = Alignment.End, // Align layout of LazyColumn items to the Right
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    onBackgroundClicked()
                }
        ) {
            item {
                Spacer(modifier = Modifier.height(paddingOffsetDp))
            }

            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.timeSec}_${index}" }
            ) { index, line ->
                val isActive = index == activeIndex
                val subLines = remember(line.text) { line.text.split("\n") }

                Column(
                    horizontalAlignment = Alignment.End, // Align sub-items inside Column to the Right
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp)
                        .graphicsLayer {
                            val diff = kotlin.math.abs(index.toFloat() - animatedActiveIndex)
                            val scaleVal = (1.12f - (diff * 0.16f)).coerceAtLeast(0.92f)
                            val opacityVal = (1.0f - (diff * 0.45f)).coerceAtLeast(0.30f)

                            scaleX = scaleVal
                            scaleY = scaleVal
                            this.alpha = opacityVal
                        }
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            onLineClicked((line.timeSec * 1000).toLong())
                        }
                ) {
                    subLines.forEachIndexed { subIndex, subLineText ->
                        val fontSize = when (subIndex) {
                            0 -> if (isActive) 21.sp else 18.sp
                            1 -> if (isActive) 14.sp else 12.sp
                            else -> if (isActive) 16.sp else 14.sp
                        }
                        val fontWeight = when (subIndex) {
                            0 -> if (isActive) FontWeight.ExtraBold else FontWeight.Medium
                            1 -> FontWeight.Normal
                            else -> if (isActive) FontWeight.Bold else FontWeight.Medium
                        }
                        val textColor = when (subIndex) {
                            0 -> if (isActive) activeColor else Color.White.copy(alpha = 0.85f)
                            1 -> Color.White.copy(alpha = 0.60f)
                            else -> if (isActive) Color.White else Color.White.copy(alpha = 0.75f)
                        }

                        Text(
                            text = subLineText,
                            color = textColor,
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            textAlign = TextAlign.End,
                            lineHeight = (fontSize.value * 1.35f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (subIndex < subLines.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(paddingOffsetDp))
            }
        }

        val vignetteHeight = remember(maxHeight) { (maxHeight * 0.25f).coerceAtMost(160.dp) }

        // Subtle gradient fades to make scrolling lyrics dissolve beautifully
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(vignetteHeight)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.50f),
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
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
    }
}
