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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.offlineai.app.R
import com.offlineai.app.data.ModelInfo
import com.offlineai.app.ui.ChatViewModel
import com.offlineai.app.ui.ComputeBackend
import com.offlineai.app.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(vm: ChatViewModel) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var recentModels by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }

    // Load existing models from app storage on first composition
    LaunchedEffect(Unit) {
        val modelsDir = File(context.filesDir, "models")
        if (modelsDir.exists()) {
            val list = modelsDir.listFiles()
                ?.filter { it.isFile && ModelInfo.isSupportedFileName(it.name) }
                ?.map {
                    ModelInfo(path = it.absolutePath, name = it.name, sizeBytes = it.length())
                }
                ?.sortedByDescending { File(it.path).lastModified() }
                ?: emptyList()
            recentModels = list
        }
        // Also restore current if present
        state.currentModel?.let { cur ->
            if (recentModels.none { it.path == cur.path }) {
                recentModels = listOf(cur) + recentModels
            }
        }
    }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = FileUtils.getFileName(context, uri)
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
        Text(stringResource(R.string.select_model), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.model_formats_hint),
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

        Text("Backend Komputasi", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.backend == ComputeBackend.AUTO,
                onClick = { vm.setBackend(ComputeBackend.AUTO) },
                label = { Text(stringResource(R.string.auto)) }
            )
            FilterChip(
                selected = state.backend == ComputeBackend.CPU,
                onClick = { vm.setBackend(ComputeBackend.CPU) },
                label = { Text(stringResource(R.string.cpu)) }
            )
            FilterChip(
                selected = state.backend == ComputeBackend.GPU,
                onClick = { vm.setBackend(ComputeBackend.GPU) },
                label = { Text(stringResource(R.string.gpu)) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Thinking mode
        Text(stringResource(R.string.thinking_mode), style = MaterialTheme.typography.titleMedium)
        val canThink = state.currentModel?.supportsReasoning == true
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = state.thinkingEnabled && canThink,
                onCheckedChange = { vm.setThinkingEnabled(it) },
                enabled = canThink
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (!canThink && state.currentModel != null)
                    stringResource(R.string.thinking_unsupported)
                else if (state.thinkingEnabled)
                    stringResource(R.string.thinking_on)
                else
                    stringResource(R.string.thinking_off)
            )
        }
        if (state.currentModel == null) {
            Text(
                "Pilih model terlebih dahulu. Mode thinking aktif otomatis jika nama model mengandung indikator reasoning (r1, think, o1, …).",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        if (state.isModelLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Memuat model…")
        }

        state.currentModel?.let { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Model aktif: ${m.name}", style = MaterialTheme.typography.titleSmall)
                    Text("Format: ${m.format} · Ukuran: ${m.sizeMb} MB · Estimasi RAM: ~${m.estimatedRamMb} MB")
                    if (m.supportsReasoning) {
                        Text("✓ Mendukung reasoning / thinking", color = MaterialTheme.colorScheme.primary)
                    }
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
                            Text(
                                "${model.sizeMb} MB · ${model.format}" +
                                        if (model.supportsReasoning) " · Reason" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
