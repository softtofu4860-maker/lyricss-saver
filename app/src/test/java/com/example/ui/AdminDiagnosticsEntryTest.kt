package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminDiagnosticsEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `admin entry stays hidden until the exact gesture and still requires device authentication`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceSecure(false)
        compose.setContent { MaterialTheme { AdminDiagnosticsEntry { Text("스마트 화면보호기", modifier = it) } } }
        compose.onNodeWithText("관리자 모드").assertDoesNotExist()
        compose.onNodeWithText("인증하고 진단 로그 보기").assertDoesNotExist()
        compose.onNodeWithText("관리자 · 검색 진단 로그").assertDoesNotExist()
        repeat(6) { compose.onNodeWithText("스마트 화면보호기").performClick() }
        compose.onNodeWithText("스마트 화면보호기").performTouchInput { longClick() }
        compose.onNodeWithText("관리자 인증").assertDoesNotExist()
        repeat(7) { compose.onNodeWithText("스마트 화면보호기").performClick() }
        compose.onNodeWithText("관리자 인증").assertDoesNotExist()
        compose.onNodeWithText("스마트 화면보호기").performTouchInput { longClick() }
        compose.onNodeWithText("휴대폰 설정에서 화면 잠금을 먼저 설정해 주세요.").assertIsDisplayed()
        compose.onNodeWithText("관리자 · 검색 진단 로그").assertDoesNotExist()
        compose.onNodeWithText("로그 비우기").assertDoesNotExist()
    }
}
