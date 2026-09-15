package com.offlineai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineai.app.data.AppPreferences
import com.offlineai.app.data.ThemeMode
import com.offlineai.app.ui.ChatViewModel
import com.offlineai.app.ui.OfflineAINavHost
import com.offlineai.app.ui.theme.OfflineAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = AppPreferences(applicationContext)
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            // Shared ViewModel scoped to Activity so model selection survives navigation
            val chatVm: ChatViewModel = viewModel()
            OfflineAITheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OfflineAINavHost(chatViewModel = chatVm)
                }
            }
        }
    }
}
