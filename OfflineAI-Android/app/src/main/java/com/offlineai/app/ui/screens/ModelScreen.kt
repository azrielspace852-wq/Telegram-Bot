package com.offlineai.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineai.app.data.ModelInfo
import com.offlineai.app.ui.ChatViewModel
import com.offlineai.app.ui.ComputeBackend
import com.offlineai.app.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var recentModels by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // Copy to app storage for reliable access
        val name = FileUtils.getFileName(context, uri)
        if (!name.lowercase().endsWith(".gguf")) {
            // still allow, but warn
        }
        val dest = File(context.filesDir, "models/$name")
        dest.parentFile?.mkdirs()
        if (FileUtils.copyUriToFile(context, uri, dest)) {
            val info = ModelInfo(
                path = dest.absolutePath,
                name = name,
                sizeBytes = dest.length()
            )
            recentModels = (listOf(info) + recentModels).distinctBy { it.path }.take(20)
            vm.selectModel(info)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pilih Model GGUF", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pilih file .gguf dari storage. Model akan langsung dimuat setelah dipilih.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Pilih dari Storage")
        }

        Spacer(Modifier.height(16.dp))

        // Backend selector
        Text("Backend Komputasi", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.backend == ComputeBackend.AUTO,
                onClick = { vm.setBackend(ComputeBackend.AUTO) },
                label = { Text("Otomatis") }
            )
            FilterChip(
                selected = state.backend == ComputeBackend.CPU,
                onClick = { vm.setBackend(ComputeBackend.CPU) },
                label = { Text("CPU") }
            )
            FilterChip(
                selected = state.backend == ComputeBackend.GPU,
                onClick = { vm.setBackend(ComputeBackend.GPU) },
                label = { Text("GPU") }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (state.isModelLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Memuat model...")
        }

        state.currentModel?.let { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Model aktif: ${m.name}", style = MaterialTheme.typography.titleSmall)
                    Text("Ukuran: ${m.sizeMb} MB · Estimasi RAM: ~${m.estimatedRamMb} MB")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Model terbaru", style = MaterialTheme.typography.titleMedium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recentModels) { model ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.selectModel(model) }
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Memory, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(model.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${model.sizeMb} MB", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
