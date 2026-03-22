package com.nanobot.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nanobot.android.ui.theme.NanoBotTheme

/**
 * 主 Activity
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NanoBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NanoBotApp()
                }
            }
        }
    }
}

/**
 * 应用根 Composable（简单导航：Chat ↔ Settings）
 */
@Composable
fun NanoBotApp() {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false }
        )
    } else {
        ChatScreen(
            onOpenSettings = { showSettings = true }
        )
    }
}
