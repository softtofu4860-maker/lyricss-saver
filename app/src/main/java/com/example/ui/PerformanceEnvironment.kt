package com.example.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.PerformanceMode
import com.example.data.PerformanceSettings
import com.example.service.MediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt

val LocalPerformanceMode = staticCompositionLocalOf { PerformanceMode.LOW }
val LocalEffectsActive = compositionLocalOf { true }
private val LocalSetPerformanceMode = staticCompositionLocalOf<(PerformanceMode) -> Unit> { {} }

@Composable
fun PerformanceEnvironment(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { PerformanceSettings(context.applicationContext) }
    var mode by remember(settings) { mutableStateOf(settings.mode()) }
    DisposableEffect(settings) {
        val subscription = settings.observe { mode = it }
        onDispose { subscription.close() }
    }
    val active = rememberEffectsActive()
    val selectMode = remember(settings) { settings::setMode }
    CompositionLocalProvider(LocalPerformanceMode provides mode, LocalEffectsActive provides active,
        LocalSetPerformanceMode provides selectMode, content = content)
}

@Composable
private fun rememberEffectsActive(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var interactive by remember(power) { mutableStateOf(power.isInteractive) }
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(context, lifecycle, power) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            interactive = power.isInteractive
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                interactive = intent.action != Intent.ACTION_SCREEN_OFF && power.isInteractive
            }
        }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    return interactive && started
}

/** Time-based phase, sampled at a bounded rate. Read its value in draw/graphicsLayer. */
@Composable
internal fun rememberEffectPhase(playing: Boolean, periodSeconds: Float): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    val active = LocalEffectsActive.current
    val fps = LocalPerformanceMode.current.profile.effectFps
    LaunchedEffect(playing, active, fps, periodSeconds) {
        if (!playing || !active) return@LaunchedEffect
        val clock = EffectFrameClock(fps)
        while (true) {
            withFrameNanos { now ->
                val elapsed = clock.step(now)
                if (elapsed > 0f) phase.floatValue =
                    (phase.floatValue + elapsed * (2 * PI.toFloat()) / periodSeconds) % (2 * PI.toFloat())
            }
        }
    }
    return phase
}

internal class EffectFrameClock(fps: Int) {
    private val intervalNanos = 1_000_000_000L / fps.coerceAtLeast(1)
    private var lastFrame: Long? = null
    private var nextFrame = 0L
    fun step(now: Long): Float {
        val previous = lastFrame
        if (previous == null) {
            lastFrame = now
            nextFrame = now + intervalNanos
            return 0f
        }
        if (now + 500_000L < nextFrame) return 0f
        val elapsed = now - previous
        lastFrame = now
        nextFrame += intervalNanos
        if (nextFrame < now) nextFrame = now + intervalNanos
        return (elapsed / 1_000_000_000f).coerceIn(0f, 0.1f)
    }
}

@Composable
internal fun rememberPlaybackPosition(mediaState: MediaState): MutableState<Long> {
    val position = remember { mutableLongStateOf(mediaState.getCurrentPositionMs()) }
    val active = LocalEffectsActive.current
    val interval = LocalPerformanceMode.current.profile.progressIntervalMs
    LaunchedEffect(mediaState, active, interval) {
        position.longValue = mediaState.getCurrentPositionMs()
        if (mediaState.isPlaying && active) while (true) {
            delay(interval)
            position.longValue = mediaState.getCurrentPositionMs()
        }
    }
    return position
}

internal fun boundedArtwork(source: Bitmap, maxEdge: Int): Bitmap? {
    if (source.isRecycled) return null
    val edge = maxOf(source.width, source.height)
    if (edge <= maxEdge) return source
    val scale = maxEdge.toFloat() / edge
    return Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt().coerceAtLeast(1),
        (source.height * scale).roundToInt().coerceAtLeast(1), true)
}

@Composable
internal fun rememberBoundedArtwork(source: Bitmap?, maxEdge: Int): Bitmap? {
    // Reuse each resized image across progress/lyric updates, and resize off the UI thread.
    val result by produceState<Bitmap?>(null, source, maxEdge) {
        value = null
        value = withContext(Dispatchers.Default) { source?.let { boundedArtwork(it, maxEdge) } }
    }
    return result
}

@Composable
fun PerformanceModeSettings(modifier: Modifier = Modifier) {
    val selected = LocalPerformanceMode.current
    val select = LocalSetPerformanceMode.current
    val context = LocalContext.current
    val displayPrefs = remember(context) { com.example.data.DisplayPreferences.get(context) }
    var dimDream by remember(displayPrefs) { mutableStateOf(displayPrefs.getBoolean(com.example.data.DisplayPreferences.DIM_DREAM, false)) }
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("기기 성능 설정", color = Color.White, style = MaterialTheme.typography.titleMedium)
        PerformanceMode.entries.forEach { mode ->
            Row(modifier = Modifier.fillMaxWidth().selectable(selected == mode,
                onClick = { select(mode) }, role = Role.RadioButton).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == mode, onClick = null)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(mode.label, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(mode.description, color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("선택한 모드는 앱과 시스템 화면보호기에 함께 적용됩니다.",
            color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("시스템 화면보호기 밝기 낮추기", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text("다음 화면보호기 실행부터 적용됩니다.", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = dimDream, onCheckedChange = {
                dimDream = it
                displayPrefs.edit().putBoolean(com.example.data.DisplayPreferences.DIM_DREAM, it).apply()
            })
        }
    }
}
