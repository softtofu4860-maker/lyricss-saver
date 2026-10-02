package com.example.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CachedLyrics
import com.example.data.LyricLine
import com.example.data.SampleDataProvider
import com.example.data.TranslationMode
import com.example.service.MusicNotificationListener
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screensaver Layout Modes for Unified Phone & Tablet Experience
 */
enum class ScreensaverLayoutMode(val label: String, val shortDesc: String, val icon: ImageVector) {
    AUTO("자동 반응형", "화면 회전에 맞춰 자동 조절", Icons.Rounded.Devices),
    SPLIT_SIDE_BY_SIDE("좌우 분할 뷰", "태블릿 프리뷰와 100% 동일한 좌우 분할", Icons.Rounded.Splitscreen),
    BALANCED_VERTICAL("세로 턴테이블 뷰", "상단 LP 턴테이블 + 하단 실시간 가사", Icons.Rounded.ViewAgenda),
    LYRICS_FULL("가사 전면 뷰", "가사 몰입형 전면 화면 + 미니 플레이어", Icons.Rounded.FormatAlignLeft)
}

/**
 * Aura Theme Presets for Fluid Mesh Ambient Background
 */
enum class AuraThemePreset(val label: String, val color1: Color, val color2: Color, val color3: Color) {
    APPLE_MUSIC("애플 뮤직 오로라", Color(0xFFC84B31), Color(0xFF1B4D3E), Color(0xFFD97706)),
    CYBER_NEON("사이버 네온", Color(0xFF00FFFF), Color(0xFF8A2BE2), Color(0xFFFF007F)),
    SUNSET_AMBER("선셋 엠버", Color(0xFFFF5E36), Color(0xFFFFAE34), Color(0xFF7B1FA2)),
    DEEP_OCEAN("딥 오션", Color(0xFF0284C7), Color(0xFF1E3A8A), Color(0xFF0F172A)),
    OLED_BLACK("OLED 딥블랙", Color(0xFF0B0C10), Color(0xFF14151C), Color(0xFF050508))
}

/**
 * Fullscreen Apple Music Style Time-Synced Lyrics Player
 * Supports both Phone and Tablet with 100% visual parity:
 * - Fluid multi-color ambient mesh blurred aura background
 * - Vinyl Turntable with realistic rotating LP record disc
 * - Apple Music scrubber timeline with current time and negative remaining time
 * - Full media playback controls and volume slider
 * - Time-synced lyrics with ivLyrics bilingual translation mode pill
 * - One-tap layout toggle between Split, Balanced Vertical, and Full Lyrics
 * - Quick song selector for instant switching between popular sample tracks
 * - Keep Screen On and Anti-Burn-in OLED drift protection
 */
@Composable
fun AppleMusicScreensaverView(
    mediaState: com.example.service.MediaState,
    lyricsLines: List<LyricLine>,
    onCloseClicked: () -> Unit,
    onSwitchTheme: (() -> Unit)? = null,
    onSelectSampleSong: ((CachedLyrics) -> Unit)? = null,
    onOpenDashboard: (() -> Unit)? = null,
    isPermissionGranted: Boolean = true,
    onRequestPermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volumeLevel by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    // Keep screen on while screensaver is active
    val activity = context as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Volume listener
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: android.content.Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    try {
                        volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    } catch (e: Exception) {}
                }
            }
        }
        val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Battery listener
    var batteryLevel by remember { mutableStateOf(99) }
    var isCharging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: android.content.Intent) {
                if (intent.action == android.content.Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    if (level != -1 && scale != -1) {
                        batteryLevel = (level * 100 / scale.toFloat()).toInt()
                    }
                    val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == android.os.BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Current time and date
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("a h:mm M월 d일 EEEE", Locale.KOREA)
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(15000)
        }
    }

    // Playback state
    var currentPositionMs by remember { mutableStateOf(mediaState.getCurrentPositionMs()) }
    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }

    LaunchedEffect(mediaState.positionMs, mediaState.isPlaying) {
        currentPositionMs = mediaState.positionMs
    }

    LaunchedEffect(mediaState.isPlaying) {
        if (mediaState.isPlaying) {
            while (true) {
                currentPositionMs = mediaState.getCurrentPositionMs()
                delay(200)
            }
        }
    }

    var syncOffsetSec by remember { mutableStateOf(0.0f) }

    val lyricsActiveIndex = remember(lyricsLines, currentPositionMs, syncOffsetSec) {
        val currentSec = (currentPositionMs / 1000f) + 0.35f + syncOffsetSec
        val idx = lyricsLines.indexOfLast { currentSec >= it.timeSec }
        if (idx == -1) -1 else idx
    }

    // Settings & View state
    val sharedPrefs = remember { context.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE) }
    var layoutMode by remember {
        mutableStateOf(
            try {
                ScreensaverLayoutMode.valueOf(
                    sharedPrefs.getString("layout_mode", ScreensaverLayoutMode.AUTO.name) ?: ScreensaverLayoutMode.AUTO.name
                )
            } catch (e: Exception) {
                ScreensaverLayoutMode.AUTO
            }
        )
    }

    var selectedAuraTheme by remember {
        mutableStateOf(
            try {
                AuraThemePreset.valueOf(
                    sharedPrefs.getString("aura_theme", AuraThemePreset.APPLE_MUSIC.name) ?: AuraThemePreset.APPLE_MUSIC.name
                )
            } catch (e: Exception) {
                AuraThemePreset.APPLE_MUSIC
            }
        )
    }

    var fontScale by remember {
        mutableStateOf(sharedPrefs.getFloat("font_scale", 1.0f))
    }

    var translationMode by remember {
        mutableStateOf(TranslationMode.BILINGUAL)
    }

    var showSongSelectorDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }

    // OLED Burn-in Protection Ambient Drift
    var driftStep by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(45000)
            driftStep = (driftStep + 1) % 4
        }
    }
    val driftOffsetX by animateFloatAsState(
        targetValue = when (driftStep) {
            0 -> 0f
            1 -> 2f
            2 -> -2f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 2500),
        label = "drift_x"
    )
    val driftOffsetY by animateFloatAsState(
        targetValue = when (driftStep) {
            0 -> 0f
            1 -> -2f
            2 -> 1f
            else -> 2f
        },
        animationSpec = tween(durationMillis = 2500),
        label = "drift_y"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val totalDuration = mediaState.durationMs.coerceAtLeast(1L)
    val remainingMs = (totalDuration - currentPositionMs).coerceAtLeast(0L)

    // Determine actual effective layout (Split vs Balanced vs Lyrics Full)
    val effectiveIsSplit = remember(layoutMode, isLandscape, configuration.screenWidthDp) {
        when (layoutMode) {
            ScreensaverLayoutMode.SPLIT_SIDE_BY_SIDE -> true
            ScreensaverLayoutMode.BALANCED_VERTICAL -> false
            ScreensaverLayoutMode.LYRICS_FULL -> false
            ScreensaverLayoutMode.AUTO -> isLandscape || configuration.screenWidthDp >= 600
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
    ) {
        // 1. Organic Fluid Multi-Color Ambient Mesh Blurred Background
        val albumArtBitmap = remember(mediaState.albumArt) {
            mediaState.albumArt?.asImageBitmap()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // First Aura Orb (top-left & center)
            Box(
                modifier = Modifier
                    .size(520.dp)
                    .offset(x = (-80).dp, y = (-60).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                selectedAuraTheme.color1.copy(alpha = 0.55f),
                                selectedAuraTheme.color3.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .blur(65.dp)
            )

            // Second Aura Orb (bottom-right & center-right)
            Box(
                modifier = Modifier
                    .size(560.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 100.dp, y = 80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                selectedAuraTheme.color2.copy(alpha = 0.60f),
                                selectedAuraTheme.color1.copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .blur(70.dp)
            )

            // Dynamic blur overlay of actual album art if present
            if (albumArtBitmap != null) {
                Image(
                    bitmap = albumArtBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.32f }
                        .blur(80.dp)
                )
            }

            // Darkening vignette for high text contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
        }

        // 2. Main Content Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = driftOffsetX.dp, y = driftOffsetY.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = if (effectiveIsSplit) 24.dp else 16.dp, vertical = 10.dp)
        ) {
            // Optional Non-intrusive Permission Notice Pill
            if (!isPermissionGranted && onRequestPermission != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(Color(0xFF6366F1).copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable { onRequestPermission() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "스포티파이/유튜브뮤직 실시간 연동을 위해 알림 권한을 켜주세요",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "설정 >",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC)
                    )
                }
            }

            // Top Status & Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Time & Date or App Brand
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = currentTimeString.ifEmpty { "스마트 음악 화면보호기" },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right: Quick Action Controls (Layout, Songs, Aura, Dashboard, Close)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Song Selector Button
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .clickable { showSongSelectorDialog = true }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryMusic,
                                contentDescription = "추천곡 변경",
                                tint = Color(0xFF67E8F9),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "곡 변경",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 2. Layout Mode Toggle Button
                    Box {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable { showLayoutMenu = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = layoutMode.icon,
                                    contentDescription = "뷰 모드",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = when (layoutMode) {
                                        ScreensaverLayoutMode.AUTO -> if (effectiveIsSplit) "분할" else "세로"
                                        ScreensaverLayoutMode.SPLIT_SIDE_BY_SIDE -> "분할"
                                        ScreensaverLayoutMode.BALANCED_VERTICAL -> "세로"
                                        ScreensaverLayoutMode.LYRICS_FULL -> "가사"
                                    },
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showLayoutMenu,
                            onDismissRequest = { showLayoutMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF181920), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                        ) {
                            ScreensaverLayoutMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = mode.label,
                                                color = if (layoutMode == mode) Color(0xFF67E8F9) else Color.White,
                                                fontWeight = if (layoutMode == mode) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = mode.shortDesc,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = mode.icon,
                                            contentDescription = null,
                                            tint = if (layoutMode == mode) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        layoutMode = mode
                                        sharedPrefs.edit().putString("layout_mode", mode.name).apply()
                                        showLayoutMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // 3. Font Scale Toggle (A- / A+)
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .clickable {
                                fontScale = when (fontScale) {
                                    1.0f -> 1.2f
                                    1.2f -> 1.4f
                                    1.4f -> 0.85f
                                    else -> 1.0f
                                }
                                sharedPrefs.edit().putFloat("font_scale", fontScale).apply()
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "A ${when (fontScale) { 1.2f -> "大" 1.4f -> "特大" 0.85f -> "小" else -> "中" }}",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 4. Aura Theme Selector Button
                    IconButton(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "오라 테마",
                            tint = selectedAuraTheme.color1,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // 5. Dashboard Link
                    if (onOpenDashboard != null) {
                        IconButton(
                            onClick = onOpenDashboard,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dashboard,
                                contentDescription = "대시보드",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // 6. Close / Dismiss
                    IconButton(
                        onClick = onCloseClicked,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "닫기",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 3. Body: Split View (Tablet / Landscape / Split-Force) vs Balanced (Phone Portrait) vs Lyrics Full
            if (layoutMode == ScreensaverLayoutMode.LYRICS_FULL) {
                // Fullscreen Lyrics with Compact Floating Glass Player Bar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    AppleMusicRightLyricsPanel(
                        lyricsLines = lyricsLines,
                        activeIndex = lyricsActiveIndex,
                        onLineClicked = { seekPosMs ->
                            currentPositionMs = seekPosMs
                            try {
                                val controller = MusicNotificationListener.activeController
                                controller?.transportControls?.seekTo(seekPosMs)
                                if (!mediaState.isPlaying) controller?.transportControls?.play()
                            } catch (e: Exception) {}
                        },
                        fontScale = fontScale,
                        translationMode = translationMode,
                        onTranslationModeChanged = { translationMode = it },
                        mediaState = mediaState,
                        syncOffsetSec = syncOffsetSec,
                        onSyncOffsetChanged = { syncOffsetSec = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating Mini Turntable Bar at Bottom
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xDD12131A)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF22242F))
                                ) {
                                    val art = mediaState.albumArt
                                    if (art != null) {
                                        Image(
                                            bitmap = art.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = mediaState.title ?: "음악 재생 중",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = mediaState.artist ?: "아티스트",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        try {
                                            MusicNotificationListener.activeController?.transportControls?.skipToPrevious()
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = Color.White)
                                }

                                IconButton(
                                    onClick = {
                                        try {
                                            val c = MusicNotificationListener.activeController
                                            if (mediaState.isPlaying) c?.transportControls?.pause() else c?.transportControls?.play()
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        try {
                                            MusicNotificationListener.activeController?.transportControls?.skipToNext()
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            } else if (effectiveIsSplit) {
                // Split View: Left Turntable Player + Right Lyrics Panel (Preview & Tablet look!)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Vinyl Turntable & Controls
                    AppleMusicLeftPlayerPanel(
                        mediaState = mediaState,
                        currentPositionMs = currentPositionMs,
                        totalDuration = totalDuration,
                        remainingMs = remainingMs,
                        isShuffle = isShuffle,
                        isRepeat = isRepeat,
                        volumeLevel = volumeLevel,
                        maxVolume = maxVolume,
                        audioManager = audioManager,
                        onSeekTo = { newPos ->
                            currentPositionMs = newPos
                            try {
                                MusicNotificationListener.activeController?.transportControls?.seekTo(newPos)
                            } catch (e: Exception) {}
                        },
                        onShuffleToggle = { isShuffle = !isShuffle },
                        onRepeatToggle = { isRepeat = !isRepeat },
                        onVolumeChange = { vol ->
                            volumeLevel = vol
                            try {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                            } catch (e: Exception) {}
                        },
                        onSwitchTheme = onSwitchTheme,
                        onCloseClicked = onCloseClicked,
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                    )

                    // Right Column: Flowing Time-synced Lyrics
                    AppleMusicRightLyricsPanel(
                        lyricsLines = lyricsLines,
                        activeIndex = lyricsActiveIndex,
                        onLineClicked = { seekPosMs ->
                            currentPositionMs = seekPosMs
                            try {
                                val controller = MusicNotificationListener.activeController
                                controller?.transportControls?.seekTo(seekPosMs)
                                if (!mediaState.isPlaying) controller?.transportControls?.play()
                            } catch (e: Exception) {}
                        },
                        fontScale = fontScale,
                        translationMode = translationMode,
                        onTranslationModeChanged = { translationMode = it },
                        mediaState = mediaState,
                        syncOffsetSec = syncOffsetSec,
                        onSyncOffsetChanged = { syncOffsetSec = it },
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // Balanced Vertical Layout for Phone Portrait:
                // Upper: Full Vinyl LP Turntable + Controls
                // Lower: Flowing Synchronized Lyrics
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppleMusicPortraitTurntablePlayer(
                        mediaState = mediaState,
                        currentPositionMs = currentPositionMs,
                        totalDuration = totalDuration,
                        remainingMs = remainingMs,
                        isShuffle = isShuffle,
                        isRepeat = isRepeat,
                        volumeLevel = volumeLevel,
                        maxVolume = maxVolume,
                        onSeekTo = { newPos ->
                            currentPositionMs = newPos
                            try {
                                MusicNotificationListener.activeController?.transportControls?.seekTo(newPos)
                            } catch (e: Exception) {}
                        },
                        onShuffleToggle = { isShuffle = !isShuffle },
                        onRepeatToggle = { isRepeat = !isRepeat },
                        onVolumeChange = { vol ->
                            volumeLevel = vol
                            try {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                            } catch (e: Exception) {}
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.05f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.15f)
                    ) {
                        AppleMusicRightLyricsPanel(
                            lyricsLines = lyricsLines,
                            activeIndex = lyricsActiveIndex,
                            onLineClicked = { seekPosMs ->
                                currentPositionMs = seekPosMs
                                try {
                                    val controller = MusicNotificationListener.activeController
                                    controller?.transportControls?.seekTo(seekPosMs)
                                    if (!mediaState.isPlaying) controller?.transportControls?.play()
                                } catch (e: Exception) {}
                            },
                            fontScale = fontScale,
                            translationMode = translationMode,
                            onTranslationModeChanged = { translationMode = it },
                            mediaState = mediaState,
                            syncOffsetSec = syncOffsetSec,
                            onSyncOffsetChanged = { syncOffsetSec = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Dialog: Sample Song Selector (NewJeans, IU, Taylor Swift, Billie Eilish)
        if (showSongSelectorDialog) {
            AlertDialog(
                onDismissRequest = { showSongSelectorDialog = false },
                containerColor = Color(0xFF13141D),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = Color(0xFF67E8F9),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "체험 및 추천 음악 선택",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "원터치로 실시간 싱크 및 이중 번역 가사를 즉시 감상할 수 있습니다.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        SampleDataProvider.getSampleSongs().forEach { song ->
                            val isCurrentSong = mediaState.title.equals(song.title, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isCurrentSong) Color(0xFF67E8F9).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrentSong) Color(0xFF67E8F9).copy(alpha = 0.6f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        SampleDataProvider.playSampleSong(song)
                                        onSelectSampleSong?.invoke(song)
                                        showSongSelectorDialog = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = if (isCurrentSong) Color(0xFF67E8F9) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${song.artist} • ${song.bpm} BPM",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (isCurrentSong) Icons.Rounded.PlayCircleFilled else Icons.Rounded.PlayCircleOutline,
                                    contentDescription = "재생",
                                    tint = if (isCurrentSong) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSongSelectorDialog = false }) {
                        Text("닫기", color = Color(0xFF67E8F9), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Dialog: Aura Theme Picker
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                containerColor = Color(0xFF13141D),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = Color(0xFFFFAE34),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "앰비언트 오라 테마",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AuraThemePreset.values().forEach { theme ->
                            val isSelected = selectedAuraTheme == theme
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedAuraTheme = theme
                                        sharedPrefs.edit().putString("aura_theme", theme.name).apply()
                                        showThemeDialog = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Three color swatches
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Box(modifier = Modifier.size(12.dp).background(theme.color1, CircleShape))
                                        Box(modifier = Modifier.size(12.dp).background(theme.color2, CircleShape))
                                        Box(modifier = Modifier.size(12.dp).background(theme.color3, CircleShape))
                                    }
                                    Text(
                                        text = theme.label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "선택됨",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text("완료", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/**
 * Procedural Vinyl Record Disc with Grooves & Rotating Center Label
 */
@Composable
fun VinylTurntableDisc(
    albumArt: android.graphics.Bitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 9000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Vinyl LP Disc
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation.value
                }
                .shadow(16.dp, CircleShape)
                .background(Color(0xFF0D0E13), CircleShape)
                .border(2.5.dp, Color(0xFF22242D), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Concentric Grooves
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                for (i in 1..6) {
                    val r = maxRadius * (0.38f + i * 0.085f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = r,
                        center = centerOffset,
                        style = Stroke(width = 1f)
                    )
                }
            }

            // Center Label matching Album Artwork
            Box(
                modifier = Modifier
                    .fillMaxSize(0.40f)
                    .clip(CircleShape)
                    .background(Color(0xFF232530))
                    .border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFFC84B31), Color(0xFF1B4D3E)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // Center Spindle Hole
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF090A0F), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )
            }
        }
    }
}

/**
 * Left Player Column for Tablet & Landscape Split View
 * Contains Album Artwork with Vinyl LP peeking out, Track Title/Artist, Scrubber, Media Controls, Volume
 */
@Composable
private fun AppleMusicLeftPlayerPanel(
    mediaState: com.example.service.MediaState,
    currentPositionMs: Long,
    totalDuration: Long,
    remainingMs: Long,
    isShuffle: Boolean,
    isRepeat: Boolean,
    volumeLevel: Int,
    maxVolume: Int,
    audioManager: AudioManager,
    onSeekTo: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onSwitchTheme: (() -> Unit)? = null,
    onCloseClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
    ) {
        // 1. Album Artwork Jacket & Vinyl Turntable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .aspectRatio(1.25f),
                contentAlignment = Alignment.CenterStart
            ) {
                // Vinyl Disc peeking out to the right
                VinylTurntableDisc(
                    albumArt = mediaState.albumArt,
                    isPlaying = mediaState.isPlaying,
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterEnd)
                )

                // Front Album Jacket Card
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.96f)
                        .aspectRatio(1f)
                        .shadow(24.dp, RoundedCornerShape(18.dp), clip = false)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E2128))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                ) {
                    val art = mediaState.albumArt
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = "앨범 아트",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF2C3E50), Color(0xFF0F172A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Song Metadata: Title, Artist, and More Options (...)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaState.title ?: "대기 중",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mediaState.artist ?: "아티스트",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { showOptionsMenu = true },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "옵션",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                    modifier = Modifier
                        .background(Color(0xFF1E2128), RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("네온 비주얼 테마 전환", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null, tint = Color.White) },
                        onClick = {
                            showOptionsMenu = false
                            onSwitchTheme?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("화면보호기 종료", color = Color(0xFFFF6B6B), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = Color(0xFFFF6B6B)) },
                        onClick = {
                            showOptionsMenu = false
                            onCloseClicked?.invoke()
                        }
                    )
                }
            }
        }

        // 3. Apple Music Scrubber Slider
        val progressFraction = (currentPositionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progressFraction,
                onValueChange = { fraction ->
                    val newPos = (fraction * totalDuration).toLong()
                    onSeekTo(newPos)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMinutesSeconds(currentPositionMs),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "-${formatMinutesSeconds(remainingMs)}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. Media Playback Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onShuffleToggle,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "셔플",
                    tint = if (isShuffle) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        MusicNotificationListener.activeController?.transportControls?.skipToPrevious()
                    } catch (e: Exception) {}
                },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "이전 곡",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Big crisp Play/Pause button
            IconButton(
                onClick = {
                    try {
                        val controller = MusicNotificationListener.activeController
                        if (mediaState.isPlaying) controller?.transportControls?.pause() else controller?.transportControls?.play()
                    } catch (e: Exception) {}
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
            ) {
                Icon(
                    imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (mediaState.isPlaying) "일시정지" else "재생",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        MusicNotificationListener.activeController?.transportControls?.skipToNext()
                    } catch (e: Exception) {}
                },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "다음 곡",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onRepeatToggle,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = "반복",
                    tint = if (isRepeat) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 5. Volume Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )

            val volumeFraction = (volumeLevel.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
            Slider(
                value = volumeFraction,
                onValueChange = { fraction ->
                    val newVol = (fraction * maxVolume).toInt()
                    onVolumeChange(newVol)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Top Player for Phone Portrait:
 * Features identical signature Vinyl LP record turntable with rotating animation,
 * track typography, scrubber, media controls, and volume.
 */
@Composable
private fun AppleMusicPortraitTurntablePlayer(
    mediaState: com.example.service.MediaState,
    currentPositionMs: Long,
    totalDuration: Long,
    remainingMs: Long,
    isShuffle: Boolean,
    isRepeat: Boolean,
    volumeLevel: Int,
    maxVolume: Int,
    onSeekTo: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: Turntable + Title & Artist
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Vinyl LP + Album Jacket Mini Turntable
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 76.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Vinyl LP peaking out
                VinylTurntableDisc(
                    albumArt = mediaState.albumArt,
                    isPlaying = mediaState.isPlaying,
                    modifier = Modifier
                        .size(70.dp)
                        .align(Alignment.CenterEnd)
                )

                // Front Album Jacket
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(12.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2128))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                ) {
                    val art = mediaState.albumArt
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = "앨범 아트",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Song Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaState.title ?: "음악 재생 중",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mediaState.artist ?: "아티스트",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Middle: Apple Music Scrubber Slider
        val progressFraction = (currentPositionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progressFraction,
                onValueChange = { fraction ->
                    val newPos = (fraction * totalDuration).toLong()
                    onSeekTo(newPos)
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMinutesSeconds(currentPositionMs),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "-${formatMinutesSeconds(remainingMs)}",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Bottom: Media Playback Buttons (Shuffle, Prev, Play, Next, Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onShuffleToggle,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "셔플",
                    tint = if (isShuffle) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        MusicNotificationListener.activeController?.transportControls?.skipToPrevious()
                    } catch (e: Exception) {}
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "이전 곡",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        val controller = MusicNotificationListener.activeController
                        if (mediaState.isPlaying) controller?.transportControls?.pause() else controller?.transportControls?.play()
                    } catch (e: Exception) {}
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape)
            ) {
                Icon(
                    imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (mediaState.isPlaying) "일시정지" else "재생",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        MusicNotificationListener.activeController?.transportControls?.skipToNext()
                    } catch (e: Exception) {}
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "다음 곡",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onRepeatToggle,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = "반복",
                    tint = if (isRepeat) Color(0xFF67E8F9) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Right Column (or Bottom Column): Large Time-Synced Flowing Lyrics with ivLyrics Translation Pill
 */
@Composable
private fun AppleMusicRightLyricsPanel(
    lyricsLines: List<LyricLine>,
    activeIndex: Int,
    onLineClicked: (Long) -> Unit,
    fontScale: Float,
    translationMode: TranslationMode,
    onTranslationModeChanged: (TranslationMode) -> Unit,
    mediaState: com.example.service.MediaState,
    syncOffsetSec: Float = 0.0f,
    onSyncOffsetChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top sheet grab handle ("—")
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(3.5.dp)
                .background(Color.White.copy(alpha = 0.30f), RoundedCornerShape(2.dp))
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Center: Full-height Apple Music synchronized lyrics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (lyricsLines.isNotEmpty()) {
                AppleMusicLyricsView(
                    lines = lyricsLines,
                    activeIndex = activeIndex,
                    onLineClicked = onLineClicked,
                    onBackgroundClicked = {},
                    fontScale = fontScale,
                    currentMode = translationMode,
                    onModeChanged = onTranslationModeChanged,
                    mediaState = mediaState,
                    syncOffsetSec = syncOffsetSec,
                    onSyncOffsetChanged = onSyncOffsetChanged,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "가사를 동기화하여 불러오는 중...",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun formatMinutesSeconds(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
