package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun LyricsCorrectionDialog(
    initialTitle: String,
    initialArtist: String,
    onDismiss: () -> Unit,
    onSearchWithQuery: (String) -> Unit,
    onSaveCustomLrc: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("$initialTitle $initialArtist".trim()) }
    var customLrc by remember { mutableStateOf("") }
    var isLrcMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E2E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "가사 검색 및 오차 수정",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isLrcMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isLrcMode) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("검색어로 찾기")
                    }

                    Button(
                        onClick = { isLrcMode = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLrcMode) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("직접 LRC 입력")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isLrcMode) {
                    Text(
                        text = "원하는 곡명과 가수 이름을 입력하여 다시 검색합니다.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("검색어 (곡명 아티스트)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                } else {
                    Text(
                        text = "LRC 형식의 가사를 직접 붙여넣어 완벽한 타임싱크를 적용합니다.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customLrc,
                        onValueChange = { customLrc = it },
                        label = { Text("[00:12.34]가사...") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("취소")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (!isLrcMode) {
                                onSearchWithQuery(searchQuery)
                            } else {
                                onSaveCustomLrc(customLrc)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text("적용하기")
                    }
                }
            }
        }
    }
}
