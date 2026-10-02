package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Format float seconds into mm:ss timestamp string
 */
private fun formatTimeSeconds(sec: Float): String {
    val totalSec = sec.toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Apple Music style Time-Synced Lyrics View with Interactive Scrubbing Preview
 * Features:
 * - Left-aligned clean display typography
 * - Tap any line to instantly seek & jump to that position
 * - Interactive scrolling preview: scroll to browse lyrics with real-time timestamp preview
 * - Auto-scroll pauses while user is scrolling/browsing, and gracefully resumes
 * - Center-focused preview badge showing timestamp & "Tap to play from here"
 * - "Sync to Song" floating button to quickly snap back to currently playing lyric
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleMusicLyricsView(
    lines: List<LyricLine>,
    activeIndex: Int,
    onLineClicked: (Long) -> Unit,
    onBackgroundClicked: () -> Unit,
    modifier: Modifier = Modifier,
    fontScale: Float = 1.0f,
    currentMode: com.example.data.TranslationMode = com.example.data.TranslationMode.BILINGUAL,
    onModeChanged: ((com.example.data.TranslationMode) -> Unit)? = null,
    mediaState: com.example.service.MediaState? = null,
    syncOffsetSec: Float = 0.0f,
    onSyncOffsetChanged: ((Float) -> Unit)? = null
) {
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var localMode by remember { mutableStateOf(currentMode) }
    val effectiveMode = onModeChanged?.let { currentMode } ?: localMode
    var localOffset by remember { mutableStateOf(syncOffsetSec) }
    val effectiveOffset = onSyncOffsetChanged?.let { syncOffsetSec } ?: localOffset
    var showSyncTuner by remember { mutableStateOf(false) }
    var quoteCardLine by remember { mutableStateOf<LyricLine?>(null) }

    // Tracks whether user is actively exploring/scrolling lyrics
    var isUserBrowsing by remember { mutableStateOf(false) }

    // Detect user scrolling to activate browse mode
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            isUserBrowsing = true
        } else if (isUserBrowsing) {
            // After user stops scrolling, keep browsing preview active for 4.5 seconds before auto-resuming sync
            delay(4500)
            isUserBrowsing = false
        }
    }

    val animatedActiveIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "apple_lyrics_active_index"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onBackgroundClicked()
            }
    ) {
        val halfHeightDp = maxHeight * 0.36f
        val paddingOffsetDp = remember(halfHeightDp) { halfHeightDp.coerceAtLeast(80.dp) }
        val paddingOffsetPx = remember(density, paddingOffsetDp) {
            with(density) { paddingOffsetDp.roundToPx() }
        }

        // Live calculation of the line closest to the center while user is scrolling
        val centerLineIndex by remember {
            derivedStateOf {
                val layoutInfo = lazyListState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) return@derivedStateOf -1
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val closest = visibleItems
                    .filter { it.index in 1..lines.size } // ignore top spacer at index 0
                    .minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2
                        kotlin.math.abs(itemCenter - viewportCenter)
                    }
                if (closest != null) closest.index - 1 else -1
            }
        }

        // Auto-scroll to keep active line comfortably in view (Only when user is NOT browsing)
        LaunchedEffect(activeIndex, isUserBrowsing) {
            if (!isUserBrowsing && lines.isNotEmpty() && activeIndex >= 0) {
                try {
                    val targetIndex = activeIndex + 1 // offset by top spacer
                    val visibleItem = lazyListState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
                    if (visibleItem != null) {
                        val delta = visibleItem.offset - paddingOffsetPx
                        if (delta != 0) {
                            lazyListState.scroll {
                                var accumulated = 0f
                                animate(
                                    initialValue = 0f,
                                    targetValue = delta.toFloat(),
                                    animationSpec = tween(
                                        durationMillis = 550,
                                        easing = FastOutSlowInEasing
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
                            index = (activeIndex + 1).coerceAtMost(lines.size),
                            scrollOffset = -paddingOffsetPx
                        )
                    } catch (ex: Exception) {}
                }
            }
        }

        // Main Lyrics Scroll List
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Top spacer
            item(key = "apple_lyrics_top_spacer") {
                Spacer(modifier = Modifier.height(paddingOffsetDp))
            }

            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.timeSec}_${index}_apple" }
            ) { index, line ->
                val isActive = index == activeIndex
                val isCenterPreview = isUserBrowsing && index == centerLineIndex
                val diff = kotlin.math.abs(index.toFloat() - animatedActiveIndex)

                // Scaling and opacity
                val scale = remember(diff, isCenterPreview) {
                    if (isCenterPreview) {
                        1.05f
                    } else {
                        (1.0f + (0.06f - (diff * 0.04f))).coerceIn(0.96f, 1.06f)
                    }
                }

                val alpha = remember(diff, isCenterPreview, isUserBrowsing) {
                    if (isCenterPreview) {
                        0.95f
                    } else if (isUserBrowsing) {
                        (0.85f - (diff * 0.08f)).coerceIn(0.40f, 0.90f)
                    } else {
                        (1.0f - (diff * 0.60f)).coerceIn(0.35f, 1.0f)
                    }
                }

                val baseFontSize = (if (isActive || isCenterPreview) 30f else 24f) * fontScale
                val fontWeight = if (isActive || isCenterPreview) FontWeight.Bold else FontWeight.SemiBold

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCenterPreview) Color.White.copy(alpha = 0.08f) else Color.Transparent
                        )
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Instant Seek to clicked lyric line
                            val seekMs = (line.timeSec * 1000).toLong()
                            onLineClicked(seekMs)
                            isUserBrowsing = false

                            // Smooth scroll to center on tapped line immediately
                            coroutineScope.launch {
                                try {
                                    lazyListState.animateScrollToItem(
                                        index = index + 1,
                                        scrollOffset = -paddingOffsetPx
                                    )
                                } catch (e: Exception) {}
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    // Preview indicator row with timestamp when moving or browsing
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isUserBrowsing || isActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isActive) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.14f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = formatTimeSeconds(line.timeSec),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            if (isCenterPreview) {
                                Text(
                                    text = "터치하여 이 위치로 이동",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64D2FF)
                                )
                            }
                        }

                        // Quote Card Button for this line
                        if (isActive || isCenterPreview) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable { quoteCardLine = line }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FormatQuote,
                                        contentDescription = "가사 카드 만들기",
                                        tint = Color(0xFF67E8F9),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "카드",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF67E8F9)
                                    )
                                }
                            }
                        }
                    }

                    // Main Lyric Line Text based on effectiveMode
                    val mainText = when (effectiveMode) {
                        com.example.data.TranslationMode.ORIGINAL_ONLY -> line.text
                        com.example.data.TranslationMode.TRANSLATION_ONLY -> line.translation ?: line.text
                        com.example.data.TranslationMode.BILINGUAL,
                        com.example.data.TranslationMode.ROMANIZATION -> line.text
                    }

                    Text(
                        text = mainText,
                        color = if (isActive) Color.White else if (isCenterPreview) Color(0xFFF0F0F0) else Color.White,
                        fontSize = baseFontSize.sp,
                        fontWeight = fontWeight,
                        textAlign = TextAlign.Start,
                        lineHeight = (baseFontSize * 1.35f).sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Secondary Sub-text: Korean Translation (ivLyrics bilingual format)
                    if (effectiveMode == com.example.data.TranslationMode.BILINGUAL && !line.translation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = line.translation,
                            color = if (isActive) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.62f),
                            fontSize = (baseFontSize * 0.68f).coerceAtLeast(13f).sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            lineHeight = ((baseFontSize * 0.68f).coerceAtLeast(13f) * 1.32f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (effectiveMode == com.example.data.TranslationMode.ROMANIZATION && !line.romanization.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = line.romanization,
                            color = if (isActive) Color(0xFFFED7AA) else Color.White.copy(alpha = 0.58f),
                            fontSize = (baseFontSize * 0.62f).coerceAtLeast(12f).sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            lineHeight = ((baseFontSize * 0.62f).coerceAtLeast(12f) * 1.25f).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live Karaoke Ambient Breathing Glow Line for active lyric
                    if (isActive) {
                        val infiniteTransition = rememberInfiniteTransition(label = "karaoke_glow")
                        val breatheAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 0.85f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "breathe"
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(2.5.dp)
                                .fillMaxWidth(0.38f)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF38BDF8).copy(alpha = breatheAlpha),
                                            Color(0xFFA855F7).copy(alpha = breatheAlpha),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            // Bottom spacer
            item(key = "apple_lyrics_bottom_spacer") {
                Spacer(modifier = Modifier.height(maxHeight * 0.5f))
            }
        }

        // ivLyrics Inspired Translation Mode Switcher Floating Pill (Top-End)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.example.data.TranslationMode.values().forEach { mode ->
                val isSelected = mode == effectiveMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Color.White.copy(alpha = 0.28f) else Color.Transparent)
                        .clickable {
                            localMode = mode
                            onModeChanged?.invoke(mode)
                        }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // Top soft gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom soft gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        // Floating "Sync to Song" Button (Appears whenever user is browsing away)
        AnimatedVisibility(
            visible = isUserBrowsing,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E2128).copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = 8.dp,
                modifier = Modifier.clickable {
                    isUserBrowsing = false
                    if (activeIndex in lines.indices) {
                        coroutineScope.launch {
                            try {
                                lazyListState.animateScrollToItem(
                                    index = activeIndex + 1,
                                    scrollOffset = -paddingOffsetPx
                                )
                            } catch (e: Exception) {}
                        }
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = "실시간 가사 동기화",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "현재 가사로 동기화",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Live Moving Preview Floating Pill at Top Center (When browsing lyrics)
        AnimatedVisibility(
            visible = isUserBrowsing && centerLineIndex in lines.indices,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            val previewLine = lines.getOrNull(centerLineIndex)
            if (previewLine != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color(0xFF64D2FF).copy(alpha = 0.4f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clickable {
                            val seekMs = (previewLine.timeSec * 1000).toLong()
                            onLineClicked(seekMs)
                            isUserBrowsing = false
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(0xFF64D2FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = formatTimeSeconds(previewLine.timeSec),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64D2FF)
                        )
                        Text(
                            text = previewLine.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }
                }
            }
        }

        // Floating Sync Offset Fine-Tuner Bar at Bottom-Start
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.70f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.22f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSyncTuner = !showSyncTuner }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⏱️ 싱크 ${if (effectiveOffset >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1fs", effectiveOffset)}",
                            color = if (effectiveOffset != 0f) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (showSyncTuner) {
                        listOf(-0.5f, -0.1f, 0.0f, 0.1f, 0.5f).forEach { step ->
                            val label = if (step == 0.0f) "0" else if (step > 0) "+${step}" else "${step}"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable {
                                        val newOff = if (step == 0.0f) 0.0f else (effectiveOffset + step)
                                        localOffset = newOff
                                        onSyncOffsetChanged?.invoke(newOff)
                                    }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Lyric Quote Card Dialog
        if (quoteCardLine != null) {
            LyricQuoteCardDialog(
                mediaState = mediaState ?: com.example.service.MediaState(title = "현재 재생 곡"),
                lyricLine = quoteCardLine!!,
                onDismiss = { quoteCardLine = null }
            )
        }
    }
}
