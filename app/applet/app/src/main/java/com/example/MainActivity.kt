package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.service.SpotifyBroadcastReceiver
import com.example.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val spotifyReceiver = SpotifyBroadcastReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SpotifyBroadcastReceiver.register(this, spotifyReceiver)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B0C10)
            ) {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        SpotifyBroadcastReceiver.unregister(this, spotifyReceiver)
        super.onDestroy()
    }
}
