package com.example.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.diagnostics.AdminSession
import com.example.diagnostics.AdminEntryGesture
import com.example.diagnostics.LyricsDiagnostics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AdminDiagnosticsEntry(content: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val session = remember { AdminSession() }
    val gesture = remember { AdminEntryGesture() }
    var unlocked by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val close = { session.lock(); unlocked = false; gesture.reset() }

    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                session.lock(); unlocked = false; gesture.reset(); message = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.lock() }
    }
    LaunchedEffect(unlocked) {
        if (unlocked) { delay(AdminSession.SESSION_MS); close() }
    }

    val authentication = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val accepted = pending && result.resultCode == Activity.RESULT_OK &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        pending = false
        session.completeDeviceAuthentication(accepted, keyguard.isDeviceSecure)
        unlocked = session.isUnlocked()
        message = if (unlocked) null else "인증이 취소되었거나 완료되지 않았습니다."
    }

    content(Modifier.combinedClickable(
        enabled = !pending && !unlocked,
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        onClick = { gesture.tap() },
        onLongClick = {
            if (gesture.longPress()) {
                message = null
                if (!keyguard.isDeviceSecure) {
                    message = "휴대폰 설정에서 화면 잠금을 먼저 설정해 주세요."
                } else {
                    @Suppress("DEPRECATION")
                    val intent = keyguard.createConfirmDeviceCredentialIntent("관리자 모드", "검색 진단 로그를 보려면 기기 인증이 필요합니다.")
                    if (intent == null) message = "이 기기에서 화면잠금 인증을 시작할 수 없습니다."
                    else try { pending = true; authentication.launch(intent) }
                    catch (_: Exception) { pending = false; message = "기기 인증을 시작하지 못했습니다." }
                }
            }
        }
    ))

    message?.let { text ->
        AlertDialog(onDismissRequest = { message = null }, title = { Text("관리자 인증") },
            text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("닫기") } })
    }

    if (unlocked) AdminDiagnosticsDialog(session, close)
}

@Composable
private fun AdminDiagnosticsDialog(session: AdminSession, onClose: () -> Unit) {
    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh(clear: Boolean = false) {
        busy = true
        error = null
        try {
            if (clear) LyricsDiagnostics.clear(session)
            log = LyricsDiagnostics.read(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            log = ""
            onClose()
        } catch (_: Exception) {
            log = ""
            error = "진단 로그를 읽지 못했습니다. 다시 시도해 주세요."
        } finally { busy = false }
    }

    LaunchedEffect(session) { refresh() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(
        securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.88f), shape = MaterialTheme.shapes.large,
            color = Color(0xFF111118)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("관리자 · 검색 진단 로그", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("최근 200건 · 최신 순 · 시간은 UTC입니다.\n창을 닫거나 앱을 벗어나면 잠깁니다. 인증은 최대 5분간 유지됩니다.",
                    color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = { scope.launch { refresh() } }) { Text("새로고침") }
                    TextButton(enabled = !busy, onClick = { scope.launch { refresh(clear = true) } }) { Text("로그 비우기") }
                    TextButton(onClick = onClose) { Text("닫고 잠그기") }
                }
                error?.let { Text(it, color = Color(0xFFFDA4AF)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!busy && error == null && log.isBlank()) Text("아직 기록된 검색 로그가 없습니다.", color = Color.White)
                val entries = remember(log) { log.lineSequence().filter(String::isNotBlank).toList().asReversed() }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries) { entry ->
                        Text(entry, color = Color(0xFFD1D5DB), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}
