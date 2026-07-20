package com.example.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sin
import kotlin.math.cos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.LyricLine
import com.example.service.MusicNotificationListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()

    var isPermissionEnabled by remember {
        mutableStateOf(MusicNotificationListener.isNotificationServiceEnabled(context))
    }

    var screensaverActive by remember { mutableStateOf(false) }
    var selectedVisualizerMode by remember { mutableStateOf(VisualizerMode.NEBULA_RING) }

    // Periodically poll for the permission status only if it's currently disabled, stopping completely once enabled!
    LaunchedEffect(isPermissionEnabled) {
        if (!isPermissionEnabled) {
            while (true) {
                isPermissionEnabled = MusicNotificationListener.isNotificationServiceEnabled(context)
                delay(4000)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06060F))
                .padding(innerPadding)
        ) {
            when {
                !isPermissionEnabled -> {
                    PermissionOnboardingScreen(
                        onGrantClicked = {
                            try {
                                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
                screensaverActive -> {
                    // Extract active lyrics data and custom vibe colors if Success
                    val (parsedLines, colors) = when (val state = lyricsState) {
                        is LyricsUiState.Success -> Pair(state.parsedLines, state.vibeColors)
                        is LyricsUiState.Error -> {
                            val title = mediaState.title ?: "알 수 없는 곡명"
                            val artist = mediaState.artist ?: "알 수 없는 아티스트"
                            val songId = com.example.api.GeminiLyricsService.generateSongId(title, artist)
                            val fallback = com.example.api.GeminiLyricsService.generateSmartFallback(songId, title, artist)
                            
                            val moshi = com.squareup.moshi.Moshi.Builder()
                                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                .build()
                            val typeLines = com.squareup.moshi.Types.newParameterizedType(List::class.java, LyricLine::class.java)
                            val linesAdapter = moshi.adapter<List<LyricLine>>(typeLines)
                            val lines = try { linesAdapter.fromJson(fallback.lyricsJson) ?: emptyList() } catch (e: Exception) { emptyList() }
                            
                            val typeColors = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
                            val colorsAdapter = moshi.adapter<List<String>>(typeColors)
                            val vibeColors = try { colorsAdapter.fromJson(fallback.hexColorsJson) ?: listOf("#00FFFF", "#8A2BE2") } catch (e: Exception) { listOf("#00FFFF", "#8A2BE2") }
                            
                            Pair(lines, vibeColors)
                        }
                        else -> Pair(emptyList<LyricLine>(), listOf("#00FFFF", "#8A2BE2", "#FF007F"))
                    }

                    // Convert hex colors to Compose Colors safely
                    val themeColors = remember(colors) {
                        colors.map { parseHexColor(it) }
                    }

                    val activeBpm = when (val state = lyricsState) {
                        is LyricsUiState.Success -> state.cachedLyrics.bpm
                        else -> 100
                    }

                    ImmersiveScreensaverView(
                        mediaState = mediaState,
                        lyricsLines = parsedLines,
                        vibeColors = themeColors,
                        bpm = activeBpm,
                        visualizerMode = selectedVisualizerMode,
                        onCloseClicked = { screensaverActive = false },
                        onModeChanged = { selectedVisualizerMode = it }
                    )
                }
                else -> {
                    DashboardScreen(
                        mediaState = mediaState,
                        lyricsState = lyricsState,
                        selectedMode = selectedVisualizerMode,
                        onModeSelected = { selectedVisualizerMode = it },
                        onLaunchScreensaver = { screensaverActive = true },
                        onClearCacheClicked = { viewModel.clearDatabaseCache() },
                        onReSearchRequested = { query ->
                            viewModel.loadLyrics(
                                title = mediaState.title ?: "",
                                artist = mediaState.artist ?: "",
                                metadataLyrics = mediaState.lyrics,
                                durationMs = mediaState.durationMs,
                                customQuery = query
                            )
                        },
                        onSaveManualLyrics = { lines ->
                            viewModel.saveManualLyrics(
                                title = mediaState.title ?: "",
                                artist = mediaState.artist ?: "",
                                editedLines = lines
                            )
                        },
                        onTranslateRequested = {
                            viewModel.translateActiveLyrics()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingScreen(
    onGrantClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Simplified, extremely clean app logo unified with dark minimalist aesthetics
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color.White.copy(alpha = 0.03f), CircleShape)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Audiotrack,
                contentDescription = "App Logo",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "스마트 음악 화면보호기",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "기기에서 재생 중인 소리를 감지하여 요동치는 비트 시각 효과와 함께 노래 진도에 딱 맞는 실시간 가사를 띄워줍니다.",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Info details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111116)), // Rich charcoal black instead of purple-dark
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PermissionFeatureRow(
                    icon = Icons.Rounded.Monitor,
                    title = "백그라운드 미디어 연동",
                    desc = "멜론, 스포티파이, 유튜브 등 사용 중인 음악 플레이어의 곡 메타데이터를 실시간으로 가로챕니다."
                )
                PermissionFeatureRow(
                    icon = Icons.Rounded.Lyrics,
                    title = "Gemini 가사 동기화",
                    desc = "재생 중인 곡의 가사를 완벽하게 검색 및 생성하여, 흘러가는 재생 초 단위 진도에 동기화해 줍니다."
                )
                PermissionFeatureRow(
                    icon = Icons.Rounded.BubbleChart,
                    title = "지능형 오라 시각화",
                    desc = "추출된 곡의 템포(BPM) 및 장르 분위기에 알맞는 고해상도 테마 비주얼라이저를 그립니다."
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGrantClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp), clip = false)
        ) {
            Text(
                text = "알림 액세스 권한 허용하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "*이 애플리케이션은 알림 액세스 권한만을 사용하여 안전하게 미디어 상태를 연동하며 개인정보를 유출하지 않습니다.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun PermissionFeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun DashboardScreen(
    mediaState: com.example.service.MediaState,
    lyricsState: LyricsUiState,
    selectedMode: VisualizerMode,
    onModeSelected: (VisualizerMode) -> Unit,
    onLaunchScreensaver: () -> Unit,
    onClearCacheClicked: () -> Unit,
    onReSearchRequested: (String) -> Unit,
    onSaveManualLyrics: (List<LyricLine>) -> Unit,
    onTranslateRequested: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE) }
    var userApiKey by remember { mutableStateOf(sharedPrefs.getString("gemini_api_key", "") ?: "") }
    var showKeyDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val useTwoColumnLayout = isLandscape || isTablet

    val isActive = !mediaState.title.isNullOrEmpty()

    // 1. Header Row
    val headerContent = @Composable {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SMART SCREENSAVER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "뮤직 시각화 보드",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            IconButton(
                onClick = onClearCacheClicked,
                modifier = Modifier
                    .background(Color(0xFF18181B), shape = RoundedCornerShape(10.dp))
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "Clear Cache",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }

    // 2. Active Player Monitor Card
    val playerCardContent = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) Color(0xFF131318) else Color(0xFF0E0E12)
            ),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isActive) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isActive) Color(0xFF4ADE80) else Color(0xFFFF3B30),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "기기 사운드 검출 활성화" else "미디어 오디오 감지 대기 중",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isActive) {
                    Text(
                        text = mediaState.title ?: "알 수 없는 곡명",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = mediaState.artist ?: "알 수 없는 아티스트",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress indicators
                    val currentPos = mediaState.getCurrentPositionMs()
                    val totalDuration = mediaState.durationMs
                    val progressFraction = if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPos),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatTime(totalDuration),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // App source badge
                    val sourceApp = remember(mediaState.packageName) {
                        getAppFriendlyName(mediaState.packageName)
                    }
                    Text(
                        text = "발신 소스: $sourceApp",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                } else {
                    // Empty state instruction
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "멜론, 지니, 스포티파이, 유튜브 등\n외부 음악 앱을 작동시키고 돌아오세요!",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    // 3. Quick Selector for visualizer mode
    val visualizerSelectorContent = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "화면보호기 테마 선택",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VisualizerModeCard(
                    title = "성운 오라",
                    desc = "궤도 파티클 고리",
                    mode = VisualizerMode.NEBULA_RING,
                    selected = selectedMode == VisualizerMode.NEBULA_RING,
                    onClick = { onModeSelected(VisualizerMode.NEBULA_RING) },
                    modifier = Modifier.weight(1f)
                )

                VisualizerModeCard(
                    title = "리퀴드 웨이브",
                    desc = "부드러운 주파수 파형",
                    mode = VisualizerMode.LIQUID_WAVES,
                    selected = selectedMode == VisualizerMode.LIQUID_WAVES,
                    onClick = { onModeSelected(VisualizerMode.LIQUID_WAVES) },
                    modifier = Modifier.weight(1f)
                )

                VisualizerModeCard(
                    title = "사이버 이퀄라이저",
                    desc = "디지털 원형 기둥",
                    mode = VisualizerMode.CYBER_BARS,
                    selected = selectedMode == VisualizerMode.CYBER_BARS,
                    onClick = { onModeSelected(VisualizerMode.CYBER_BARS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 4. Lyrics Sync Gemini API Monitor Card
    val lyricsCacheContent = @Composable {
        if (isActive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131318)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "스마트 가사 캐시 현황",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    when (lyricsState) {
                        is LyricsUiState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Gemini 가사 큐레이팅 및 로딩 중...",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                        is LyricsUiState.Success -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "실시간 싱크로 가사 준비 완료 (${lyricsState.parsedLines.size} 소절)",
                                        fontSize = 13.sp,
                                        color = Color(0xFF4ADE80),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "곡 템포: ${lyricsState.cachedLyrics.bpm} BPM | 장르: ${lyricsState.cachedLyrics.genre ?: "일반"}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        is LyricsUiState.Error -> {
                            Text(
                                text = "가사 동기화 도중 오류가 발생해 자동 생성기로 선회했습니다.\n(${lyricsState.message})",
                                fontSize = 12.sp,
                                color = Color(0xFFFF9500),
                                lineHeight = 16.sp
                            )
                        }
                        else -> {
                            Text(
                                text = "대기 상태",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.5f)
                              )
                        }
                    }

                    // Show translation button if the successful lyrics lack Korean characters
                    val hasKorean = remember(lyricsState) {
                        if (lyricsState is LyricsUiState.Success) {
                            lyricsState.parsedLines.any { line -> line.text.any { it in '\uAC00'..'\uD7A3' } }
                        } else {
                            true
                        }
                    }
                    if (lyricsState is LyricsUiState.Success && !hasKorean) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onTranslateRequested,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF27272A),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini AI 한국어 가사 번역 요청",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "가사 오차 교정 및 직접 편집",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (showEditDialog) {
                        val currentLines = when (val state = lyricsState) {
                            is LyricsUiState.Success -> state.parsedLines
                            else -> emptyList()
                        }
                        LyricsCorrectionDialog(
                            title = mediaState.title ?: "",
                            artist = mediaState.artist ?: "",
                            currentLines = currentLines,
                            durationMs = mediaState.durationMs,
                            onDismiss = { showEditDialog = false },
                            onReSearchRequested = onReSearchRequested,
                            onSaveManualLyrics = onSaveManualLyrics
                        )
                    }
                }
            }
        }
    }

    // 5. Dual Mode Status & API Key Setting Card
    val apiKeySettingContent = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131318)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "가사 연동 모드",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    
                    TextButton(
                        onClick = { showKeyDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (userApiKey.isNotEmpty()) "API 키 수정" else "개인 API 키 설정",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val hasBuildKey = com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
                val isAiMode = userApiKey.isNotEmpty() || hasBuildKey
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (isAiMode) Color(0xFFD8B4FE) else Color(0xFF4ADE80),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAiMode) "AI 고급 모드 (Gemini 실시간 연동)" else "기본 모드 (무료 LRCLIB 연동)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAiMode) Color(0xFFD8B4FE) else Color(0xFF4ADE80)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (isAiMode) {
                        if (userApiKey.isNotEmpty()) "사용자의 개인 Gemini API 키를 사용하여 전 세계의 어떤 희귀곡이나 최신곡이든 실시간 구글 검색을 통해 100% 매칭된 싱크 가사를 생성합니다."
                        else "시스템에 빌드된 Gemini API 키를 사용하여 실시간 구글 검색을 통해 싱크 가사를 생성합니다."
                    } else {
                        "기본적으로 완전 무료 오픈소스 LRCLIB API와 LrcMux 통합 집계 API를 순서대로 자동 검색합니다. 키 없이도 국내외 인기 곡의 가사를 풍부하게 연동합니다."
                    },
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    lineHeight = 15.sp
                )
            }
        }

        if (showKeyDialog) {
            var tempKey by remember { mutableStateOf(userApiKey) }
            AlertDialog(
                onDismissRequest = { showKeyDialog = false },
                title = {
                    Text(
                        text = "Gemini API 개인 키 설정",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "AI 고급 모드를 사용하려면 Google AI Studio에서 발급받은 API 키를 입력해 주세요.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "※ API 키 발급은 PC에서만 가능합니다.",
                            fontSize = 12.sp,
                            color = Color(0xFFFFB74D), // Warning amber/orange color
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            placeholder = { Text("AIzaSy...", color = Color.White.copy(alpha = 0.3f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (tempKey.isNotEmpty()) {
                            Text(
                                text = "개인 키가 등록되면 LRCLIB에 없는 노래도 Gemini 2.5 Flash가 실시간 구글 검색을 통해 싱크로 가사를 완벽히 생성합니다.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            sharedPrefs.edit().putString("gemini_api_key", tempKey.trim()).apply()
                            userApiKey = tempKey.trim()
                            showKeyDialog = false
                            onClearCacheClicked() // Triggers reload with new key configuration
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("저장", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showKeyDialog = false }) {
                        Text("취소", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF131318)
            )
        }
    }

    val systemDreamContent = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131318)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "시스템 화면보호기 등록",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = "충전 중이거나 거치했을 때 이 뮤직 시각화 보드가 자동으로 실행되도록 시스템 화면보호기로 등록해 보세요.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    lineHeight = 16.sp
                )
                Button(
                    onClick = {
                        try {
                            val intent = Intent("android.settings.DREAM_SETTINGS").apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                android.widget.Toast.makeText(context, "화면보호기 설정을 열 수 없습니다. [설정 -> 디스플레이 -> 화면보호기]로 직접 가주세요.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("시스템 화면보호기 설정 열기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 6. Massive launch button
    val launchButtonContent = @Composable {
        Button(
            onClick = onLaunchScreensaver,
            enabled = isActive,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.05f)
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = if (isActive) 12.dp else 0.dp,
                    shape = RoundedCornerShape(14.dp),
                    clip = false
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = if (isActive) Color.Black else Color.White.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "시각 화면보호기 몰입하기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.Black else Color.White.copy(alpha = 0.3f)
            )
        }
    }

    if (useTwoColumnLayout) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                headerContent()
                Spacer(modifier = Modifier.height(4.dp))
                playerCardContent()
                Spacer(modifier = Modifier.height(4.dp))
                visualizerSelectorContent()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                lyricsCacheContent()
                apiKeySettingContent()
                systemDreamContent()
                Spacer(modifier = Modifier.weight(1f))
                launchButtonContent()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            headerContent()
            playerCardContent()
            visualizerSelectorContent()
            lyricsCacheContent()
            apiKeySettingContent()
            systemDreamContent()
            Spacer(modifier = Modifier.height(16.dp))
            launchButtonContent()
        }
    }
}

@Composable
fun VisualizerModeCard(
    title: String,
    desc: String,
    mode: VisualizerMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(
                width = 1.5.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF27272A) else Color(0xFF131318)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (mode) {
                    VisualizerMode.NEBULA_RING -> Icons.Rounded.TrackChanges
                    VisualizerMode.LIQUID_WAVES -> Icons.Rounded.Waves
                    VisualizerMode.CYBER_BARS -> Icons.Rounded.Equalizer
                },
                contentDescription = null,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
fun MusicVinylDisc(
    title: String,
    artist: String,
    isPlaying: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val rotationAngle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotationAngle.animateTo(
                targetValue = rotationAngle.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 10000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    val glowScale = remember { Animatable(1.0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            glowScale.animateTo(
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            glowScale.animateTo(1.0f, tween(500))
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 1. Dynamic Breathing Aura Glow
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    val scale = glowScale.value
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.5f),
                            secondaryColor.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 2. Vinyl Disc Structure (spinning together with text)
        Box(
            modifier = Modifier
                .size(220.dp)
                .shadow(16.dp, shape = CircleShape)
                .graphicsLayer {
                    rotationZ = rotationAngle.value
                }
                .background(Color(0xFF0C0C10), shape = CircleShape)
                .border(6.dp, Color(0xFF1E1E24), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Concentric Groove Lines (Procedural Vinyl Grooves)
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.minDimension / 2f
                for (i in 1..6) {
                    val r = maxRadius * (0.4f + i * 0.08f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = r,
                        center = centerOffset,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                    )
                }
            }

            // Center Label (Vinyl Label)
            Box(
                modifier = Modifier
                    .size(85.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.9f), secondaryColor.copy(alpha = 0.9f))
                        ),
                        shape = CircleShape
                    )
                    .border(2.dp, Color(0xFF0F172A), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Song Title & Artist inside Vinyl Center (spinning together!)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(6.dp)
                ) {
                    Text(
                        text = title.take(12),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        lineHeight = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist.take(12),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        lineHeight = 9.sp
                    )
                }

                // Vinyl Spindle Hole
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(0xFF040409), shape = CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.15f), shape = CircleShape)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImmersiveScreensaverView(
    mediaState: com.example.service.MediaState,
    lyricsLines: List<LyricLine>,
    vibeColors: List<Color>,
    bpm: Int,
    visualizerMode: VisualizerMode,
    onCloseClicked: () -> Unit,
    onModeChanged: (VisualizerMode) -> Unit
) {
    val context = LocalContext.current
    val rawPrimary = vibeColors.getOrElse(0) { Color(0xFF38BDF8) }
    val rawSecondary = vibeColors.getOrElse(1) { Color(0xFF818CF8) }

    // Convert neon vivid colors into modern, eye-friendly desaturated slate tones
    val primaryColor = remember(rawPrimary) {
        if (rawPrimary == Color(0xFF00FFFF)) Color(0xFF38BDF8) else rawPrimary.copy(alpha = 1.0f)
    }
    val secondaryColor = remember(rawSecondary) {
        if (rawSecondary == Color(0xFF8A2BE2)) Color(0xFF818CF8) else rawSecondary.copy(alpha = 1.0f)
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volumeLevel by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    // Event-driven volume listener: consumes 0% CPU compared to continuous polling!
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
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

    // Local controller status overrides for shuffle/repeat simulation indicators
    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }

    // Track position state that advances in UI
    var currentPositionMs by remember { mutableStateOf(mediaState.getCurrentPositionMs()) }

    // Feature 5: Lyric Sync Calibration and OLED Burn-in drift
    var syncOffsetMs by remember { mutableStateOf(0L) }
    var driftX by remember { mutableStateOf(0f) }
    var driftY by remember { mutableStateOf(0f) }
    
    val calibratedPositionMs = currentPositionMs + syncOffsetMs

    val lyricsActiveIndex = remember(lyricsLines, calibratedPositionMs) {
        val currentSec = (calibratedPositionMs / 1000f) + 0.40f
        val idx = lyricsLines.indexOfLast { currentSec >= it.timeSec }
        if (idx == -1) -1 else idx
    }

    LaunchedEffect(Unit) {
        val random = java.util.Random()
        while (true) {
            delay(30000) // every 30 seconds shift pixel footprint
            driftX = (random.nextFloat() * 12f - 6f) // -6dp to +6dp
            driftY = (random.nextFloat() * 12f - 6f)
        }
    }

    val activeBeatScale = 1.0f

    // Resync when state changes
    LaunchedEffect(mediaState.positionMs, mediaState.isPlaying) {
        currentPositionMs = mediaState.positionMs
    }

    // Smoothly advance progress bar when playing (optimized update interval to cut wakeups by 2.5x)
    LaunchedEffect(mediaState.isPlaying) {
        if (mediaState.isPlaying) {
            while (true) {
                currentPositionMs = mediaState.getCurrentPositionMs()
                delay(250) // update progress ~4fps for extremely low CPU overhead
            }
        }
    }

    // Clock Status for Top-Left Corner (optimized to 30s delay)
    var currentTimeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = java.text.SimpleDateFormat("h:mm", java.util.Locale.getDefault())
        while (true) {
            currentTimeStr = sdf.format(java.util.Date())
            delay(30000)
        }
    }

    // Event-driven battery listener: consumes 0% CPU compared to continuous polling!
    var batteryLevel by remember { mutableStateOf(100) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                if (intent.action == android.content.Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    if (level != -1 && scale != -1) {
                        batteryLevel = (level * 100 / scale.toFloat()).toInt()
                    }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val sharedPrefs = context.getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE)
    var isControlsPinned by remember {
        mutableStateOf(sharedPrefs.getBoolean("controls_pinned", true))
    }

    // Auto-hide controls when playing after 6 seconds of inactivity (if not pinned)
    LaunchedEffect(showControls, lastInteractionTime, mediaState.isPlaying, isControlsPinned) {
        if (showControls && mediaState.isPlaying && !isControlsPinned) {
            delay(6000)
            showControls = false
        }
    }

    // Friendly App / Source mapping
    val appLabel = remember(mediaState.packageName) {
        getAppFriendlyName(mediaState.packageName)
    }

    // Parse bookmarks based on lyrics structure or standard intervals
    val bookmarks = remember(lyricsLines, mediaState.durationMs) {
        parseSongBookmarks(lyricsLines, mediaState.durationMs)
    }

    // Dynamic time-based night mode calculation
    val currentHour = remember(currentTimeStr) {
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    }
    val isNightTime = remember(currentHour) {
        currentHour >= 20 || currentHour < 6 // Night Mode between 8:00 PM (20:00) and 6:00 AM
    }

    // Ensure the active lyric text color is always pristine white for clean, modern minimalism (slightly softened during night hours)
    val activeLyricColor = remember(isNightTime) {
        if (isNightTime) Color(0xE6FFFFFF) else Color.White // 90% opacity white to reduce glare at night
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030304))
            .combinedClickable(
                onDoubleClick = { onCloseClicked() },
                onClick = {
                    showControls = !showControls
                    lastInteractionTime = System.currentTimeMillis()
                },
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
    ) {
        // 1. Ambient blurred background using downscaled album artwork to save 99% CPU/GPU overhead!
        val ambientBgBitmap = remember(mediaState.albumArt) {
            mediaState.albumArt?.let { raw ->
                try {
                    // Downscale to 64x64 for a highly performant yet beautifully smooth ambient background blur
                    android.graphics.Bitmap.createScaledBitmap(raw, 64, 64, true).asImageBitmap()
                } catch (e: Exception) {
                    raw.asImageBitmap()
                }
            }
        }
        if (ambientBgBitmap != null) {
            Image(
                bitmap = ambientBgBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isNightTime) 0.008f else 0.02f) // Auto-dim ambient artwork at night
                    .blur(28.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = if (isNightTime) 0.05f else 0.15f), // Softer glow at night
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // 2. Dynamic background music visualizer
        MusicVisualizer(
            isPlaying = mediaState.isPlaying,
            bpm = bpm,
            vibeColors = vibeColors,
            mode = visualizerMode,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (isNightTime) 0.35f else 0.85f // Auto-dim music visualizer at night
                }
        )

        // Dark dim layer for high visual contrast and legibility (darkened automatically at night)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (isNightTime) 0.80f else 0.65f),
                            Color.Black.copy(alpha = if (isNightTime) 0.35f else 0.0f),
                            Color.Black.copy(alpha = if (isNightTime) 0.90f else 0.80f)
                        )
                    )
                )
        )

        // Main layout container (with top status bar and side-by-side or stacked layout)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = driftX.dp.toPx()
                    translationY = driftY.dp.toPx()
                }
                .padding(top = if (isLandscape) 52.dp else 64.dp)
        ) {
            if (isLandscape) {
                // Landscape: 2-Column Asymmetric Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Prominent Square Artwork Card with Dynamic Ambient Glow (Feature 2)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1.0f)
                            .aspectRatio(1f)
                    ) {
                        // Dynamic Neon Ambient Backlight Glow pulsing with BPM
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.96f)
                                .graphicsLayer {
                                    scaleX = activeBeatScale * 1.15f
                                    scaleY = activeBeatScale * 1.15f
                                    alpha = if (mediaState.isPlaying) {
                                        if (isNightTime) 0.35f else 0.7f
                                    } else {
                                        if (isNightTime) 0.15f else 0.35f
                                    }
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = if (isNightTime) 0.25f else 0.55f),
                                            secondaryColor.copy(alpha = if (isNightTime) 0.08f else 0.2f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                )
                        )

                        // Core Album Artwork Card with subtle breathing animation
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .graphicsLayer {
                                    scaleX = activeBeatScale
                                    scaleY = activeBeatScale
                                    alpha = if (isNightTime) 0.8f else 1.0f // Softly dim album art at night
                                }
                                .shadow(24.dp, RoundedCornerShape(28.dp), clip = false)
                                .background(Color(0xFF131318), RoundedCornerShape(28.dp))
                                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = if (isNightTime) 0.08f else 0.15f)), RoundedCornerShape(28.dp))
                                .clip(RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mediaState.albumArt != null) {
                                Image(
                                    bitmap = mediaState.albumArt.asImageBitmap(),
                                    contentDescription = "Album Artwork",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF1E1E24), Color(0xFF0F0F12))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Audiotrack,
                                        contentDescription = "No Artwork",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Right Column: Music Info, Scrollable Lyrics, & Persistent Controllers
                    Column(
                        modifier = Modifier
                            .weight(1.4f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Title and Artist
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mediaState.title ?: "알 수 없는 곡명",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mediaState.artist ?: "알 수 없는 아티스트",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Time-sync Neon Lyrics Scroll View
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(20.dp))
                                .padding(vertical = 4.dp, horizontal = 12.dp)
                        ) {
                            if (lyricsLines.isNotEmpty()) {
                                LyricsView(
                                    lines = lyricsLines,
                                    activeIndex = lyricsActiveIndex,
                                    activeColor = activeLyricColor,
                                    onLineClicked = { seekPosMs ->
                                        try {
                                            MusicNotificationListener.activeController?.transportControls?.seekTo(seekPosMs)
                                            currentPositionMs = seekPosMs
                                            lastInteractionTime = System.currentTimeMillis()
                                        } catch (e: Exception) {}
                                    },
                                    onBackgroundClicked = {
                                        showControls = !showControls
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = primaryColor, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("스마트 연동 가사 정렬 중...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Always-On Bottom Controllers Panel
                        PersistentControlsCard(
                            mediaState = mediaState,
                            currentPositionMs = currentPositionMs,
                            totalDuration = mediaState.durationMs,
                            isShuffle = isShuffle,
                            isRepeat = isRepeat,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            bookmarks = bookmarks,
                            volumeLevel = volumeLevel,
                            maxVolume = maxVolume,
                            audioManager = audioManager,
                            onPositionChange = { newPos ->
                                currentPositionMs = newPos
                            },
                            onVolumeChange = { vol -> volumeLevel = vol },
                            onShuffleToggle = { isShuffle = !isShuffle },
                            onRepeatToggle = { isRepeat = !isRepeat },
                            onLastInteraction = { lastInteractionTime = System.currentTimeMillis() },
                            isLandscape = true,
                            syncOffsetMs = syncOffsetMs,
                            onSyncOffsetChange = { syncOffsetMs = it }
                        )
                    }
                }
            } else {
                // Portrait: Top-to-Bottom stacked layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top: Square Artwork Card with Dynamic Ambient Glow (Feature 2)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1.0f)
                            .aspectRatio(1f)
                    ) {
                        // Dynamic Neon Ambient Backlight Glow pulsing with BPM
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.96f)
                                .graphicsLayer {
                                    scaleX = activeBeatScale * 1.15f
                                    scaleY = activeBeatScale * 1.15f
                                    alpha = if (mediaState.isPlaying) {
                                        if (isNightTime) 0.35f else 0.7f
                                    } else {
                                        if (isNightTime) 0.15f else 0.35f
                                    }
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = if (isNightTime) 0.25f else 0.55f),
                                            secondaryColor.copy(alpha = if (isNightTime) 0.08f else 0.2f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                        )

                        // Core Album Artwork Card with subtle breathing animation
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .graphicsLayer {
                                    scaleX = activeBeatScale
                                    scaleY = activeBeatScale
                                    alpha = if (isNightTime) 0.8f else 1.0f // Softly dim album art at night
                                }
                                .shadow(20.dp, RoundedCornerShape(24.dp), clip = false)
                                .background(Color(0xFF131318), RoundedCornerShape(24.dp))
                                .border(BorderStroke(1.2.dp, Color.White.copy(alpha = if (isNightTime) 0.06f else 0.12f)), RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (mediaState.albumArt != null) {
                                Image(
                                    bitmap = mediaState.albumArt.asImageBitmap(),
                                    contentDescription = "Album Artwork",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF1E1E24), Color(0xFF0F0F12))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Audiotrack,
                                        contentDescription = "No Artwork",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Music Metadata Info
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = mediaState.title ?: "알 수 없는 곡명",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mediaState.artist ?: "알 수 없는 아티스트",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Time-sync Neon Lyrics Scroll View
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(20.dp))
                            .padding(vertical = 4.dp, horizontal = 12.dp)
                    ) {
                        if (lyricsLines.isNotEmpty()) {
                            LyricsView(
                                lines = lyricsLines,
                                activeIndex = lyricsActiveIndex,
                                activeColor = activeLyricColor,
                                onLineClicked = { seekPosMs ->
                                    try {
                                        MusicNotificationListener.activeController?.transportControls?.seekTo(seekPosMs)
                                        currentPositionMs = seekPosMs
                                        lastInteractionTime = System.currentTimeMillis()
                                    } catch (e: Exception) {}
                                },
                                onBackgroundClicked = {
                                    showControls = !showControls
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = primaryColor, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("스마트 연동 가사 정렬 중...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Always-On Bottom Controllers Panel
                    PersistentControlsCard(
                        mediaState = mediaState,
                        currentPositionMs = currentPositionMs,
                        totalDuration = mediaState.durationMs,
                        isShuffle = isShuffle,
                        isRepeat = isRepeat,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        bookmarks = bookmarks,
                        volumeLevel = volumeLevel,
                        maxVolume = maxVolume,
                        audioManager = audioManager,
                        onPositionChange = { newPos ->
                            currentPositionMs = newPos
                        },
                        onVolumeChange = { vol -> volumeLevel = vol },
                        onShuffleToggle = { isShuffle = !isShuffle },
                        onRepeatToggle = { isRepeat = !isRepeat },
                        onLastInteraction = { lastInteractionTime = System.currentTimeMillis() },
                        isLandscape = false,
                        syncOffsetMs = syncOffsetMs,
                        onSyncOffsetChange = { syncOffsetMs = it }
                    )
                }
            }
        }

        // Header Overlay (Always small and classy, animated on tap)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it / 2 },
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { -it / 2 },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = if (isLandscape) 14.dp else 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top Left: Clock, Battery, and App Source Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryStd,
                                contentDescription = "Battery Status",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "$batteryLevel%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Text(
                        text = currentTimeStr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace
                    )

                    if (appLabel.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(primaryColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = appLabel,
                                color = primaryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isNightTime) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF08A).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color(0xFFFEF08A).copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NightsStay,
                                    contentDescription = "Night Mode Active",
                                    tint = Color(0xFFFEF08A),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "야간 감광",
                                    color = Color(0xFFFEF08A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Top Right: Visualizer Modes & Direct Close
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VisualizerSmallIconToggle(
                        icon = Icons.Rounded.TrackChanges,
                        active = visualizerMode == VisualizerMode.NEBULA_RING,
                        onClick = {
                            onModeChanged(VisualizerMode.NEBULA_RING)
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )
                    VisualizerSmallIconToggle(
                        icon = Icons.Rounded.Waves,
                        active = visualizerMode == VisualizerMode.LIQUID_WAVES,
                        onClick = {
                            onModeChanged(VisualizerMode.LIQUID_WAVES)
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )
                    VisualizerSmallIconToggle(
                        icon = Icons.Rounded.Equalizer,
                        active = visualizerMode == VisualizerMode.CYBER_BARS,
                        onClick = {
                            onModeChanged(VisualizerMode.CYBER_BARS)
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )

                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.2f)))

                    VisualizerSmallIconToggle(
                        icon = if (isControlsPinned) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        active = isControlsPinned,
                        onClick = {
                            isControlsPinned = !isControlsPinned
                            sharedPrefs.edit().putBoolean("controls_pinned", isControlsPinned).apply()
                            if (isControlsPinned) {
                                showControls = true
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { onCloseClicked() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit Screensaver",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    isPlaying: Boolean,
    bpm: Int,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val piFloat = 3.14159265f
    var wavePhase by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val frameTimeMs = 1000L / 30L // ~33ms (30 FPS constraint)
            while (true) {
                wavePhase += 0.07f
                if (wavePhase > 2f * piFloat) {
                    wavePhase -= 2f * piFloat
                }
                delay(frameTimeMs)
            }
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val density = LocalDensity.current
        val widthPx = remember(maxWidth, density) {
            with(density) { maxWidth.toPx() }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (widthPx > 0f) {
                            onValueChange((offset.x / widthPx).coerceIn(0f, 1f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (widthPx > 0f) {
                            onValueChange((change.position.x / widthPx).coerceIn(0f, 1f))
                        }
                    }
                }
        ) {
            val h = size.height
            val centerY = h / 2f
            val activeWidth = widthPx * value

            // 1. Inactive track background (straight subtle line)
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(activeWidth, centerY),
                end = Offset(widthPx, centerY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 2. Wavy Active track
            if (activeWidth > 0f) {
                val path = Path()
                path.moveTo(0f, centerY)

                val stepPx = 4.dp.toPx()
                val segmentCount = (activeWidth / stepPx).toInt().coerceAtLeast(10)
                val baseAmplitude = 3.dp.toPx()
                val frequency = 0.05f

                for (i in 0..segmentCount) {
                    val x = (i.toFloat() / segmentCount) * activeWidth
                    val waveY = if (isPlaying && i > 0 && i < segmentCount) {
                        // Muted fade towards the edges to keep it connected smoothly
                        val edgeFade = sin((i.toFloat() / segmentCount) * piFloat)
                        centerY + sin(x * frequency - wavePhase) * baseAmplitude * edgeFade
                    } else {
                        centerY
                    }
                    path.lineTo(x, waveY)
                }

                // Draw backing glow
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.25f),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw main wavy active track
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 3. Slidable Thumb
            drawCircle(
                color = Color.White,
                center = Offset(activeWidth, centerY),
                radius = 6.dp.toPx()
            )
            drawCircle(
                color = primaryColor,
                center = Offset(activeWidth, centerY),
                radius = 3.dp.toPx()
            )
        }
    }
}

// Extract persistent controls layout for high-quality, neat styling
@Composable
fun PersistentControlsCard(
    mediaState: com.example.service.MediaState,
    currentPositionMs: Long,
    totalDuration: Long,
    isShuffle: Boolean,
    isRepeat: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    bookmarks: List<SongBookmark>,
    volumeLevel: Int,
    maxVolume: Int,
    audioManager: AudioManager,
    onPositionChange: (Long) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onLastInteraction: () -> Unit,
    isLandscape: Boolean,
    syncOffsetMs: Long,
    onSyncOffsetChange: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = if (isLandscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
    ) {
        // Seek Bar Slider Row
        val progressFraction = if (totalDuration > 0) currentPositionMs.toFloat() / totalDuration else 0f
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(currentPositionMs),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace
            )

            WavySlider(
                value = progressFraction.coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    try {
                        val newPos = (fraction * totalDuration).toLong()
                        MusicNotificationListener.activeController?.transportControls?.seekTo(newPos)
                        onPositionChange(newPos)
                        onLastInteraction()
                    } catch (e: Exception) {}
                },
                isPlaying = mediaState.isPlaying,
                bpm = 100,
                primaryColor = primaryColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            Text(
                text = formatTime(totalDuration),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace
            )
        }

        // Media Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onShuffleToggle()
                    onLastInteraction()
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (isShuffle) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) primaryColor else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        MusicNotificationListener.activeController?.transportControls?.skipToPrevious()
                        onLastInteraction()
                    } catch (e: Exception) {}
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Song",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(secondaryColor, secondaryColor.copy(alpha = 0.4f))
                        ),
                        shape = CircleShape
                    )
                    .clickable {
                        try {
                            val controller = MusicNotificationListener.activeController
                            if (mediaState.isPlaying) {
                                controller?.transportControls?.pause()
                            } else {
                                controller?.transportControls?.play()
                            }
                            onLastInteraction()
                        } catch (e: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play or Pause",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = {
                    try {
                        MusicNotificationListener.activeController?.transportControls?.skipToNext()
                        onLastInteraction()
                    } catch (e: Exception) {}
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next Song",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = {
                    onRepeatToggle()
                    onLastInteraction()
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (isRepeat) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = "Repeat Mode",
                    tint = if (isRepeat) primaryColor else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Volume Slider Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .background(Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val volumeIcon = when {
                volumeLevel == 0 -> Icons.AutoMirrored.Rounded.VolumeMute
                volumeLevel < maxVolume / 2 -> Icons.AutoMirrored.Rounded.VolumeDown
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            }

            IconButton(
                onClick = {
                    try {
                        val targetVol = if (volumeLevel > 0) 0 else maxVolume / 3
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        onVolumeChange(targetVol)
                        onLastInteraction()
                    } catch (e: Exception) {}
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = volumeIcon,
                    contentDescription = "System Volume",
                    tint = primaryColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Slider(
                value = volumeLevel.toFloat(),
                valueRange = 0f..maxVolume.toFloat(),
                onValueChange = { level ->
                    try {
                        val newLevel = level.toInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newLevel, 0)
                        onVolumeChange(newLevel)
                        onLastInteraction()
                    } catch (e: Exception) {}
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = primaryColor,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                    thumbColor = primaryColor
                ),
                modifier = Modifier.weight(1.0f)
            )

            Text(
                text = "${(volumeLevel * 100 / maxVolume.coerceAtLeast(1))}%",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End
            )
        }

        // Sync Calibration Panel (Feature 5: Sync Calibration - High Precision and Non-Jumping Ergonomics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .background(Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = "Sync Adjustment",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "가사 싱크 미세 조절",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp) // Generous touch targets and spacing
            ) {
                IconButton(
                    onClick = {
                        onSyncOffsetChange(syncOffsetMs - 500)
                        onLastInteraction()
                    },
                    modifier = Modifier
                        .size(32.dp) // Comfort touch target
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Text("-0.5s", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                val offsetText = if (syncOffsetMs == 0L) {
                    "기본 싱크"
                } else {
                    val sign = if (syncOffsetMs > 0) "+" else ""
                    "${sign}${syncOffsetMs / 1000f}초"
                }

                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = offsetText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = {
                        onSyncOffsetChange(syncOffsetMs + 500)
                        onLastInteraction()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
                ) {
                    Text("+0.5s", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Non-jumping layout pattern: Reset button is always positioned and dims out when not in use
                val hasOffset = syncOffsetMs != 0L
                IconButton(
                    onClick = {
                        if (hasOffset) {
                            onSyncOffsetChange(0L)
                            onLastInteraction()
                        }
                    },
                    enabled = hasOffset,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = if (hasOffset) 0.08f else 0.02f), CircleShape)
                        .graphicsLayer { alpha = if (hasOffset) 1.0f else 0.25f }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset Sync",
                        tint = if (hasOffset) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

data class SongBookmark(
    val label: String,
    val timeMs: Long
)

fun parseSongBookmarks(lines: List<com.example.data.LyricLine>, durationMs: Long): List<SongBookmark> {
    val list = mutableListOf<SongBookmark>()
    
    // Check if we can extract headings from bracketed lyrics
    for (line in lines) {
        val t = line.text.trim()
        if (t.startsWith("[") && t.endsWith("]") && t.length > 2) {
            val label = t.substring(1, t.length - 1)
            list.add(SongBookmark(label, (line.timeSec * 1000).toLong()))
        } else if (t.startsWith("(") && t.endsWith(")") && t.length > 2) {
            val label = t.substring(1, t.length - 1)
            if (label.contains("Verse", true) || label.contains("Chorus", true) || 
                label.contains("Bridge", true) || label.contains("Intro", true) || 
                label.contains("Outro", true) || label.contains("Hook", true)) {
                list.add(SongBookmark(label, (line.timeSec * 1000).toLong()))
            }
        }
    }
    
    // Fallback if no sections are found in the text
    if (list.isEmpty() && durationMs > 0) {
        list.add(SongBookmark("Intro", 0L))
        list.add(SongBookmark("Verse 1", (durationMs * 0.15f).toLong()))
        list.add(SongBookmark("Chorus 1", (durationMs * 0.38f).toLong()))
        list.add(SongBookmark("Verse 2", (durationMs * 0.58f).toLong()))
        list.add(SongBookmark("Chorus 2", (durationMs * 0.78f).toLong()))
        list.add(SongBookmark("Outro", (durationMs * 0.90f).toLong()))
    }
    return list.sortedBy { it.timeMs }.take(6)
}

@Composable
fun VisualizerSmallIconToggle(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (active) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (active) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

// Utility to convert hex strings to Jetpack Compose Colors safely
fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        // Return a smart fallback colors if parsing fails
        when (hex.lowercase(Locale.getDefault())) {
            "pink" -> Color(0xFFFF1493)
            "cyan" -> Color(0xFF00FFFF)
            "purple" -> Color(0xFF8A2BE2)
            "gold" -> Color(0xFFFFD700)
            else -> Color(0xFF00FFFF)
        }
    }
}

// Utility to nicely convert package names to friendly names
fun getAppFriendlyName(packageName: String?): String {
    if (packageName == null) return "알 수 없는 플레이어"
    return when {
        packageName.contains("spotify") -> "Spotify"
        packageName.contains("melon") -> "Melon"
        packageName.contains("genie") -> "Genie Music"
        packageName.contains("youtube.music") -> "YouTube Music"
        packageName.contains("youtube") -> "YouTube"
        packageName.contains("music") -> "Music Player"
        else -> packageName.substringAfterLast(".").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

// Format milliseconds (Long) to clean minutes:seconds string (e.g. 03:45)
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun LyricsCorrectionDialog(
    title: String,
    artist: String,
    currentLines: List<LyricLine>,
    durationMs: Long,
    onDismiss: () -> Unit,
    onReSearchRequested: (String) -> Unit,
    onSaveManualLyrics: (List<LyricLine>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: AI Re-search, 1: DB Search, 2: Manual Edit
    var customQueryText by remember { mutableStateOf("") }
    
    // Unified Search states
    var searchSource by remember { mutableStateOf(0) } // 0: LRCLIB, 1: NetEase
    var dbQueryText by remember { mutableStateOf("$artist - $title") }
    
    // LRCLIB States
    var lrclibResults by remember { mutableStateOf<List<com.example.api.LrclibResponse>>(emptyList()) }
    var selectedLrclibResult by remember { mutableStateOf<com.example.api.LrclibResponse?>(null) }
    
    // NetEase States
    var netEaseResults by remember { mutableStateOf<List<com.example.api.NetEaseSong>>(emptyList()) }
    var selectedNetEaseResult by remember { mutableStateOf<com.example.api.NetEaseSong?>(null) }
    var netEaseLyricsPreview by remember { mutableStateOf<String?>(null) }

    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun performDbSearch() {
        if (dbQueryText.isBlank()) return
        isSearching = true
        searchError = null
        selectedLrclibResult = null
        selectedNetEaseResult = null
        netEaseLyricsPreview = null
        scope.launch {
            try {
                if (searchSource == 0) {
                    val results = com.example.api.GeminiLyricsService.searchLrclib(dbQueryText)
                    lrclibResults = results
                    if (results.isEmpty()) {
                        searchError = "LRCLIB 검색 결과가 없습니다."
                    }
                } else {
                    val results = com.example.api.GeminiLyricsService.searchNetEase(dbQueryText)
                    netEaseResults = results
                    if (results.isEmpty()) {
                        searchError = "NetEase 검색 결과가 없습니다."
                    }
                }
            } catch (e: Exception) {
                searchError = "검색 중 오류 발생: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    LaunchedEffect(selectedTab, searchSource) {
        if (selectedTab == 0) {
            if (searchSource == 0 && lrclibResults.isEmpty() && dbQueryText.isNotBlank()) {
                performDbSearch()
            } else if (searchSource == 1 && netEaseResults.isEmpty() && dbQueryText.isNotBlank()) {
                performDbSearch()
            }
        }
    }

    val initialLrcText = remember(currentLines) {
        currentLines.joinToString("\n") { line ->
            val totalSec = line.timeSec
            val min = (totalSec / 60).toInt()
            val sec = (totalSec % 60).toInt()
            val ms = ((totalSec % 1) * 100).toInt()
            String.format(Locale.US, "[%02d:%02d.%02d] %s", min, sec, ms, line.text)
        }
    }
    var lrcEditText by remember { mutableStateOf(initialLrcText) }
    var parseError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "가사 오차 교정 및 편집",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tab Selection Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0F23), shape = RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .background(
                                color = if (selectedTab == 0) Color(0xFF1E293B) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (selectedTab == 0) Color(0xFF00FFFF) else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DB 가사 검색",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (selectedTab == 1) Color(0xFF1E293B) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = null,
                                tint = if (selectedTab == 1) Color(0xFF00FFFF) else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "직접 편집",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    // DB Search Section (LRCLIB & NetEase)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "원하는 가사 데이터베이스를 골라 가사를 검색하고, 연동할 가사 버전을 직접 골라 화면에 적용할 수 있습니다.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 15.sp
                        )

                        // Database Selector Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("LRCLIB (기본)", "LrcMux (통합 집계)").forEachIndexed { index, name ->
                                val isSelected = searchSource == index
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) Color(0xFF00FFFF).copy(alpha = 0.2f) else Color(0xFF1E293B),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFF00FFFF) else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { 
                                            searchSource = index
                                            selectedLrclibResult = null
                                            selectedNetEaseResult = null
                                            netEaseLyricsPreview = null
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color(0xFF00FFFF) else Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val isNoResultSelected = if (searchSource == 0) selectedLrclibResult == null else selectedNetEaseResult == null

                        if (isNoResultSelected) {
                            // Search Box
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = dbQueryText,
                                    onValueChange = { dbQueryText = it },
                                    placeholder = { Text("곡명 또는 가수명 입력", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00FFFF),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { performDbSearch() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    enabled = !isSearching,
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    if (isSearching) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00FFFF), strokeWidth = 2.dp)
                                    } else {
                                        Icon(imageVector = Icons.Rounded.Search, contentDescription = "검색", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Results List
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(Color(0xFF090916), shape = RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                if (isSearching) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFF00FFFF))
                                    }
                                } else if (searchError != null) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(searchError!!, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                    }
                                } else if (searchSource == 0 && lrclibResults.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("검색어를 입력하고 검색 버튼을 누르세요.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                                    }
                                } else if (searchSource == 1 && netEaseResults.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("검색어를 입력하고 검색 버튼을 누르세요.", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                                    }
                                } else {
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (searchSource == 0) {
                                            items(lrclibResults) { result ->
                                                val hasSynced = !result.syncedLyrics.isNullOrEmpty()
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF13132A), shape = RoundedCornerShape(6.dp))
                                                        .clickable { selectedLrclibResult = result }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = result.trackName ?: "알 수 없는 제목",
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "${result.artistName ?: "알 수 없음"} • ${result.albumName ?: "알 수 없음"}",
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    
                                                    val badgeBg = if (hasSynced) Color(0xFF005F4B) else Color(0xFF334155)
                                                    val badgeText = if (hasSynced) "⏱️ 싱크" else "📄 일반"
                                                    val badgeTextColor = if (hasSynced) Color(0xFF00FFCC) else Color.White.copy(alpha = 0.8f)
                                                    Box(
                                                        modifier = Modifier
                                                            .background(badgeBg, shape = RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(text = badgeText, color = badgeTextColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        } else {
                                            items(netEaseResults) { song ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF13132A), shape = RoundedCornerShape(6.dp))
                                                        .clickable { 
                                                            selectedNetEaseResult = song
                                                            isSearching = true
                                                            netEaseLyricsPreview = null
                                                            scope.launch {
                                                                try {
                                                                    val artistName = song.artists?.joinToString(", ") { it.name ?: "" } ?: ""
                                                                    val lyrics = com.example.api.GeminiLyricsService.fetchLrcmuxLyrics(song.name ?: "", artistName)
                                                                    netEaseLyricsPreview = lyrics ?: "[00:00.00] 이 곡은 등록된 가사가 없거나 빈 내용입니다."
                                                                } catch (e: Exception) {
                                                                    netEaseLyricsPreview = "[00:00.00] 가사 로드 실패: ${e.message}"
                                                                } finally {
                                                                    isSearching = false
                                                                }
                                                            }
                                                        }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = song.name ?: "알 수 없는 제목",
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = song.artists?.joinToString(", ") { it.name ?: "알 수 없음" } ?: "알 수 없음",
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFF8B0000), shape = RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(text = "🎵 LrcMux", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Result selected - Show Info & Preview
                            val selectedTitle = if (searchSource == 0) selectedLrclibResult?.trackName else selectedNetEaseResult?.name
                            val selectedArtist = if (searchSource == 0) selectedLrclibResult?.artistName else selectedNetEaseResult?.artists?.joinToString(", ") { it.name ?: "" }
                            val selectedAlbum = if (searchSource == 0) selectedLrclibResult?.albumName else "LrcMux Aggregated"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedTitle ?: "선택된 곡",
                                        color = Color(0xFF00FFFF),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${selectedArtist ?: "알 수 없음"} • $selectedAlbum",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = { 
                                        selectedLrclibResult = null
                                        selectedNetEaseResult = null
                                        netEaseLyricsPreview = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "이전", modifier = Modifier.size(14.dp), tint = Color(0xFF00FFFF))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("목록", color = Color(0xFF00FFFF), fontSize = 12.sp)
                                }
                            }

                            val rawLyricsText = if (searchSource == 0) {
                                (selectedLrclibResult?.syncedLyrics ?: selectedLrclibResult?.plainLyrics ?: "")
                            } else {
                                (netEaseLyricsPreview ?: "가사를 가져오는 중...")
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(Color(0xFF090916), shape = RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                if (isSearching && searchSource == 1 && netEaseLyricsPreview == null) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFF00FFFF))
                                    }
                                } else if (rawLyricsText.isBlank()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("가사 내용이 없는 곡입니다.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = rawLyricsText,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Manual Edit Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "LRC 포맷([분:초.밀리초] 가사) 형식 혹은 일반 줄바꿈 형식으로 가사와 싱크를 자유롭게 수정하고 편집할 수 있습니다.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = lrcEditText,
                            onValueChange = { 
                                lrcEditText = it 
                                parseError = null
                            },
                            minLines = 6,
                            maxLines = 10,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FFFF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (parseError != null) {
                            Text(
                                text = parseError!!,
                                fontSize = 11.sp,
                                color = Color(0xFFFF3B30),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                val isSelectionValid = if (searchSource == 0) selectedLrclibResult != null else (selectedNetEaseResult != null && !netEaseLyricsPreview.isNullOrBlank())
                Button(
                    onClick = {
                        if (searchSource == 0) {
                            val selected = selectedLrclibResult
                            if (selected != null) {
                                val rawLyrics = selected.syncedLyrics ?: selected.plainLyrics ?: ""
                                val parsed = com.example.api.GeminiLyricsService.parseLrcLyrics(rawLyrics, durationMs)
                                onSaveManualLyrics(parsed)
                                onDismiss()
                            }
                        } else {
                            val selected = selectedNetEaseResult
                            val rawLyrics = netEaseLyricsPreview ?: ""
                            if (selected != null && rawLyrics.isNotBlank()) {
                                val parsed = com.example.api.GeminiLyricsService.parseLrcLyrics(rawLyrics, durationMs)
                                onSaveManualLyrics(parsed)
                                onDismiss()
                            }
                        }
                    },
                    enabled = isSelectionValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FF66),
                        disabledContainerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (isSelectionValid) Color.Black else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "이 가사 적용",
                        color = if (isSelectionValid) Color.Black else Color.White.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {
                        val parsed = com.example.api.GeminiLyricsService.parseLrcLyrics(lrcEditText, durationMs)
                        if (parsed.isEmpty() && lrcEditText.trim().isNotEmpty()) {
                            parseError = "올바른 LRC 형식([00:00] 가사)이거나 일반 줄바꿈 가사여야 합니다."
                        } else {
                            onSaveManualLyrics(parsed)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("저장", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF141430)
    )
}
