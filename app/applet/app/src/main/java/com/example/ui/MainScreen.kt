package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.MusicNotificationListener
import com.example.ui.components.AppleMusicLandscapeScreensaver
import com.example.ui.components.LyricsCorrectionDialog
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val mediaState by viewModel.mediaState.collectAsState()
    val lyricsUiState by viewModel.lyricsUiState.collectAsState()
    val savedLyrics by viewModel.savedLyrics.collectAsState()

    var isNotificationPermissionGranted by remember {
        mutableStateOf(MusicNotificationListener.isNotificationServiceEnabled(context))
    }

    var isScreensaverPreviewActive by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }

    // 재생 중일 때 주기적 틱
    var ticker by remember { mutableStateOf(0L) }
    LaunchedEffect(mediaState.isPlaying) {
        while (mediaState.isPlaying) {
            delay(250)
            ticker = System.currentTimeMillis()
        }
    }

    // 화면보호기 전체화면 미리보기 모드
    if (isScreensaverPreviewActive) {
        val lyrics = (lyricsUiState as? LyricsUiState.Success)?.lyrics ?: emptyList()
        val colors = (lyricsUiState as? LyricsUiState.Success)?.auraColors ?: listOf(
            Color(0xFF1E1B4B),
            Color(0xFF312E81),
            Color(0xFF0F172A)
        )

        AppleMusicLandscapeScreensaver(
            mediaState = mediaState,
            lyrics = lyrics,
            auraColors = colors,
            onSeekTo = { viewModel.seekTo(it) },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSkipToNext = { viewModel.skipToNext() },
            onSkipToPrevious = { viewModel.skipToPrevious() },
            onOpenCorrectionDialog = { showCorrectionDialog = true },
            onClose = { isScreensaverPreviewActive = false }
        )

        if (showCorrectionDialog) {
            LyricsCorrectionDialog(
                initialTitle = mediaState.title ?: "",
                initialArtist = mediaState.artist ?: "",
                onDismiss = { showCorrectionDialog = false },
                onSearchWithQuery = { query ->
                    viewModel.loadLyrics(
                        title = mediaState.title ?: "",
                        artist = mediaState.artist ?: "",
                        durationMs = mediaState.durationMs,
                        customQuery = query
                    )
                },
                onSaveCustomLrc = { lrc ->
                    viewModel.updateCustomLyrics(
                        title = mediaState.title ?: "",
                        artist = mediaState.artist ?: "",
                        lrcText = lrc,
                        durationMs = mediaState.durationMs
                    )
                }
            )
        }
        return
    }

    // 일반 관리 대시보드 화면
    Scaffold(
        containerColor = Color(0xFF0B0C10)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. 헤더 타이틀
            item {
                Column {
                    Text(
                        text = "스마트 가사 화면보호기",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "아이패드 가로 애플뮤직 스타일 실시간 가사 화면보호기",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                }
            }

            // 2. 권한 알림 배너
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNotificationPermissionGranted) Color(0xFF14291F) else Color(0xFF332014)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isNotificationPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isNotificationPermissionGranted) Color(0xFF4ADE80) else Color(0xFFFB923C),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isNotificationPermissionGranted) "음악 감지 권한 연결됨" else "미디어 알림 접근 권한 필요",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isNotificationPermissionGranted) "Spotify, YouTube Music 등 현재 곡을 감지 중입니다."
                                else "실시간 곡과 싱크 가사를 받으려면 권한 허용이 필요합니다.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                        if (!isNotificationPermissionGranted) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("설정")
                            }
                        }
                    }
                }
            }

            // 3. 현재 재생 중인 음악 카드 & 화면보호기 실행 버튼
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C24)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 앨범 아트
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                val art = mediaState.albumArt
                                if (art != null && !art.isRecycled) {
                                    Image(
                                        bitmap = art.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mediaState.title ?: "재생 중인 곡 없음",
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mediaState.artist ?: "음악 앱에서 노래를 재생해보세요",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 애플뮤직 가로 화면보호기 미리보기 버튼
                        Button(
                            onClick = { isScreensaverPreviewActive = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFA2D48)), // Apple Music Red
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "아이패드 가로 애플뮤직 화면보호기 미리보기",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_DREAM_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("안드로이드 화면보호기 기본 설정 열기")
                        }
                    }
                }
            }

            // 4. 저장된 가사 캐시 목록
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "저장된 가사 (${savedLyrics.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (savedLyrics.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.clearAllCache() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
                        ) {
                            Text("캐시 비우기", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (savedLyrics.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "아직 저장된 가사가 없습니다.\n음악을 재생하면 자동으로 가사가 캐시됩니다.",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(savedLyrics) { cached ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cached.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = cached.artist,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteCachedSong(cached.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "삭제",
                                    tint = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
