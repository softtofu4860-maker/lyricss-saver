package com.example.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.service.MediaStateHolder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionOnboardingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `first launch without notification access offers permission and optional demo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
        MediaStateHolder.clear()
        compose.setContent { MaterialTheme { MainScreen() } }
        compose.onNodeWithText("알림 액세스 권한 허용하기").assertExists()
        compose.onNodeWithText("🎵 샘플 음악으로 앱 바로 체험하기").assertExists()
    }
}
