package com.offlineai.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.offlineai.app.R
import com.offlineai.app.ui.screens.ChatScreen
import com.offlineai.app.ui.screens.ModelScreen
import com.offlineai.app.ui.screens.PrivacyPolicyScreen
import com.offlineai.app.ui.screens.SettingsScreen

@Composable
fun OfflineAINavHost(chatViewModel: ChatViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "chat"

    Scaffold(
        bottomBar = {
            if (currentRoute != "privacy") {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "chat",
                        onClick = {
                            navController.navigate("chat") {
                                launchSingleTop = true
                                popUpTo("chat") { inclusive = false }
                            }
                        },
                        icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                        label = { Text(stringResource(R.string.chat)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "models",
                        onClick = {
                            navController.navigate("models") {
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        label = { Text(stringResource(R.string.models)) }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "settings",
                        onClick = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.settings)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(padding)
        ) {
            composable("chat") { ChatScreen(vm = chatViewModel) }
            composable("models") { ModelScreen(vm = chatViewModel) }
            composable("settings") {
                SettingsScreen(
                    vm = chatViewModel,
                    onOpenPrivacy = { navController.navigate("privacy") }
                )
            }
            composable("privacy") {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
