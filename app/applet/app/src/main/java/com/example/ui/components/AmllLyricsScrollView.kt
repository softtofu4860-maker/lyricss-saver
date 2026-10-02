package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LyricLine
import kotlinx.coroutines.delay

/**
 * Apple Music-Like Lyrics (AMLL) 스타일의 가사 스크롤 뷰
 * - 부드러운 스프링 오토 스크롤
 * - 활성 가사 대형 텍스트 및 발광/강조
 * - 비활성 가사 페이드아웃
 * - 가사 터치 시 즉시 타임스탬프 이동 인터랙션
 * - 상/하단 그라디언트 페이드 마스크
 */
@Composable
fun AmllLyricsScrollView(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    val listState = rememberLazyListState()
    val currentTimeSec = currentPositionMs / 1000f

    // 현재 재생 중인 가사 라인 찾기
    val activeIndex = remember(lyrics, currentTimeSec) {
        if (lyrics.isEmpty()) -1
        else {
            val idx = lyrics.indexOfLast { it.timeSec <= currentTimeSec }
            if (idx == -1) 0 else idx
        }
    }

    var isUserInteracting by remember { mutableStateOf(false) }

    // 가사 변경 시 부드러운 센터 오토 스크롤
    LaunchedEffect(activeIndex, isUserInteracting) {
        if (!isUserInteracting && activeIndex in lyrics.indices) {
            // 화면 상단 25% 지점에 정렬되도록 오프셋 계산
            val targetIndex = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0
            )
        }
    }

    // 스크롤 감지: 사용자가 드래그하면 3초간 오토스크롤 일시정지
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserInteracting = true
        } else if (isUserInteracting) {
            delay(3500)
            isUserInteracting = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 상단 및 하단 부드러운 페이드 마스크
            .graphicsLayer { alpha = 0.99f }
            .drawWithContent {
                drawContent()
                val fadeHeight = 120.dp.toPx()
                // Top Fade Mask
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = fadeHeight
                    ),
                    blendMode = BlendMode.DstIn
                )
                // Bottom Fade Mask
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - fadeHeight,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        if (lyrics.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "가사 정보가 없습니다",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 180.dp, bottom = 240.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeIndex

                    val textColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                        label = "lyricTextColor"
                    )

                    val textScale by animateFloatAsState(
                        targetValue = if (isActive) 1.05f else 0.98f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "lyricTextScale"
                    )

                    val fontSize = if (isActive) 30.sp else 24.sp
                    val fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold

                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .scale(textScale)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onSeekTo((line.timeSec * 1000).toLong())
                            }
                    ) {
                        Text(
                            text = line.text,
                            color = textColor,
                            fontSize = fontSize,
                            fontWeight = fontWeight,
                            lineHeight = fontSize * 1.35f,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
