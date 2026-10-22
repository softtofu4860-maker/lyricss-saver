package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.PersonalApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun ApiKeySettings() {
    val context = LocalContext.current
    val store = remember(context) { PersonalApiKeyStore(context) }
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        try { configured = withContext(Dispatchers.IO) { !store.get().isNullOrBlank() } }
        catch (_: Exception) { error = "개인 키를 읽지 못했습니다. 다시 설정해 주세요." }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF131318))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("가사 검색 · Gemini 번역", color = Color.White)
            Text(if (configured) "개인 키 저장됨 · 요청 시 유효성을 확인합니다." else "번역 키 미설정 · 가사 검색은 키 없이 사용할 수 있습니다.", color = Color.LightGray)
            TextButton(onClick = { open = true }) { Text(if (configured) "개인 키 변경" else "개인 키 설정") }
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    try { withContext(Dispatchers.IO) { store.delete() }; configured = false; error = null }
                    catch (_: Exception) { error = "키를 삭제하지 못했습니다." }
                    finally { busy = false }
                }
            }) { Text("저장된 키 삭제") }
            error?.let { Text(it, color = Color(0xFFFDA4AF)) }
        }
    }
    if (open) {
        var input by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!busy) open = false }, title = { Text("Gemini 개인 API 키") },
            text = { Column {
                Text("입력한 개인 키로 번역을 요청합니다. 기기 Keystore로 암호화해 저장합니다.")
                OutlinedTextField(input, { input = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), label = { Text("새 API 키") })
                error?.let { Text(it) }
            } },
            confirmButton = { TextButton(enabled = input.isNotBlank() && !busy, onClick = {
                scope.launch {
                    busy = true
                    try { withContext(Dispatchers.IO) { store.set(input) }; input = ""; configured = true; error = null; open = false }
                    catch (_: Exception) { error = "키를 안전하게 저장하지 못했습니다." }
                    finally { busy = false }
                }
            }) { Text("암호화 저장") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { input = ""; open = false }) { Text("취소") } })
    }
}
