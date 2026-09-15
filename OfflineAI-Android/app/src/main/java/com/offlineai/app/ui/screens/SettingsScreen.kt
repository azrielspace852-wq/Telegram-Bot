package com.offlineai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineai.app.ui.ChatViewModel

@Composable
fun SettingsScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var portText by remember { mutableStateOf(state.serverPort.toString()) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall)

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
                TextButton(onClick = { vm.refreshHardware() }) {
                    Text("Refresh")
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

        // About
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Offline AI v1.0.0", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aplikasi AI offline untuk Android 10+.\n" +
                            "• Pilih model GGUF dari storage\n" +
                            "• Chat + lampirkan file (txt/md/json/pdf/zip/png/jpg/mp3/mp4)\n" +
                            "• ZIP dibaca & bisa di-generate\n" +
                            "• Deteksi RAM/Storage + peringatan\n" +
                            "• Pilihan CPU / GPU / Auto\n" +
                            "• Text-to-Speech\n" +
                            "• Mode server LAN\n\n" +
                            "Inference engine saat ini: Stub (ganti dengan llama.cpp di production)."
                )
            }
        }
    }
}
