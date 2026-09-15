package com.offlineai.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlineai.app.R
import com.offlineai.app.data.ThemeMode
import com.offlineai.app.ui.ChatViewModel
import com.offlineai.app.util.SwapManager

@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    onOpenPrivacy: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    var portText by remember { mutableStateOf(state.serverPort.toString()) }
    var swapSizeText by remember { mutableStateOf("2048") }
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) { }
    }

    fun openEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "Feedback AXION AI offline")
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)

        // Theme
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.themeMode == ThemeMode.SYSTEM,
                        onClick = { vm.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) }
                    )
                    FilterChip(
                        selected = state.themeMode == ThemeMode.LIGHT,
                        onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) }
                    )
                    FilterChip(
                        selected = state.themeMode == ThemeMode.DARK,
                        onClick = { vm.setThemeMode(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) }
                    )
                }
            }
        }

        // Hardware info
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Info Perangkat", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                state.deviceStats?.let { s ->
                    Text("Device: ${s.deviceName}")
                    Text("RAM Total / Tersedia: ${s.totalRamMb} / ${s.availableRamMb} MB")
                    Text("Storage Total / Tersedia: ${s.totalStorageMb} / ${s.availableStorageMb} MB")
                    Text("CPU Cores: ${s.cpuCores}")
                    Text("Vulkan GPU: ${if (s.hasVulkan) "Ya" else "Tidak / Tidak terdeteksi"}")
                }
                TextButton(onClick = {
                    vm.refreshHardware()
                    vm.refreshSwap()
                }) {
                    Text("Refresh")
                }
            }
        }

        // Swap
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SdStorage, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.swap), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Buat file swap (maks ${SwapManager.MAX_SWAP_MB / 1024} GB) untuk membantu model besar. " +
                            "Pada perangkat non-root, file ini berfungsi sebagai virtual memory disk-backed; " +
                            "engine native dapat memanfaatkan mmap. Model dapat dijalankan dengan mempertimbangkan swap " +
                            "sebagai tambahan kapasitas memori.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                state.swapInfo?.let { si ->
                    if (si.exists) {
                        Text("Swap aktif: ${si.sizeMb} MB\nPath: ${si.path}", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Belum ada swap. Free storage: ~${si.freeStorageMb} MB")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.swapInfo?.exists != true) {
                    OutlinedTextField(
                        value = swapSizeText,
                        onValueChange = { swapSizeText = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Ukuran (MB)") },
                        supportingText = { Text(stringResource(R.string.swap_max)) },
                        singleLine = true,
                        modifier = Modifier.width(160.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val mb = swapSizeText.toLongOrNull() ?: 2048L
                            vm.createSwap(mb)
                        },
                        enabled = !state.isCreatingSwap
                    ) {
                        if (state.isCreatingSwap) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.create_swap))
                    }
                } else {
                    OutlinedButton(onClick = { vm.deleteSwap() }) {
                        Text(stringResource(R.string.delete_swap))
                    }
                }
            }
        }

        // Thinking (also on Model screen, but useful here)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.thinking_mode), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                val canThink = state.currentModel?.supportsReasoning == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.thinkingEnabled && canThink,
                        onCheckedChange = { vm.setThinkingEnabled(it) },
                        enabled = canThink || state.currentModel == null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            state.currentModel == null -> "Pilih model dulu"
                            !canThink -> stringResource(R.string.thinking_unsupported)
                            state.thinkingEnabled -> stringResource(R.string.thinking_on)
                            else -> stringResource(R.string.thinking_off)
                        }
                    )
                }
            }
        }

        // Server
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mode Server (LAN)", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Jadikan perangkat ini sebagai server AI lokal (OpenAI-compatible). " +
                            "Perangkat lain di jaringan yang sama bisa memanggil API."
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val p = portText.toIntOrNull() ?: 8080
                            vm.toggleServer(true, p)
                        },
                        enabled = !state.serverRunning && state.currentModel != null
                    ) { Text("Start Server") }
                    OutlinedButton(
                        onClick = { vm.toggleServer(false) },
                        enabled = state.serverRunning
                    ) { Text("Stop") }
                }
                if (state.serverRunning) {
                    Text(
                        "Server aktif di 0.0.0.0:${state.serverPort}\n" +
                                "Contoh: http://<IP-HP>:${state.serverPort}/v1/chat/completions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Privacy Policy
        Card(
            onClick = onOpenPrivacy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PrivacyTip, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null)
            }
        }

        // GitHub star
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.github_star),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openUrl("https://github.com/azrielspace852-wq/offlineAI") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Star, null)
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub – Star")
                }
            }
        }

        // Romance Engine promo
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.romance_promo),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openUrl("https://romance-engine.pages.dev") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Favorite, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buka Romance Engine")
                }
            }
        }

        // Company docs
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.docs), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openUrl("https://axion-neuralis.axn.cc.cd") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Description, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dokumentasi")
                }
            }
        }

        // Feedback
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.feedback), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Laporkan bug atau saran lewat GitHub Issues atau email.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openUrl("https://github.com/azrielspace852-wq/offlineAI/issues") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BugReport, null)
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub Issues")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { openEmail("azrielspace852@gmail.com") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, null)
                    Spacer(Modifier.width(8.dp))
                    Text("azrielspace852@gmail.com")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { openEmail("axionneuralis@gmail.com") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Email, null)
                    Spacer(Modifier.width(8.dp))
                    Text("axionneuralis@gmail.com")
                }
            }
        }

        // About
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("AXION AI offline v1.1.0", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aplikasi AI offline untuk Android 10+.\n" +
                            "• Multi-format model (GGUF, ONNX, PT, SafeTensors, …)\n" +
                            "• Chat + lampiran file\n" +
                            "• Tema System / Light / Dark\n" +
                            "• Mode Thinking (otomatis jika model support)\n" +
                            "• Swap virtual hingga 7 GB\n" +
                            "• Server LAN OpenAI-compatible\n" +
                            "• TTS, deteksi hardware\n\n" +
                            "Inference engine saat ini: Stub (ganti dengan llama.cpp / engine native di production).\n" +
                            "Bahasa mengikuti pengaturan perangkat."
                )
            }
        }
    }
}
