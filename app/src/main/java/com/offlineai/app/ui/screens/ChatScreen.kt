package com.offlineai.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineai.app.data.MessageRole
import com.offlineai.app.ui.ChatViewModel
import com.offlineai.app.ui.ChatUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.addAttachment(it) }
    }

    // Auto scroll
    LaunchedEffect(state.messages.size, state.isGenerating) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // Warning dialog
    state.warning?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearWarning() },
            title = { Text("Peringatan Hardware") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = {
                    state.currentModel?.let { vm.selectModel(it, force = true) }
                    vm.clearWarning()
                }) { Text("Tetap Jalankan") }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearWarning() }) { Text("Batal") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        state.currentModel?.name ?: "Belum ada model",
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.deviceStats?.let {
                        Text(
                            "RAM: ${it.availableRamMb}/${it.totalRamMb} MB · CPU: ${it.cpuCores} cores",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            actions = {
                if (state.isGenerating) {
                    IconButton(onClick = { vm.stopGeneration() }) {
                        Icon(Icons.Default.Stop, "Stop")
                    }
                }
            }
        )

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    onSpeak = { vm.speak(msg.content) }
                )
            }
        }

        // Pending attachments
        if (state.pendingAttachments.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.pendingAttachments.forEach { att ->
                    AssistChip(
                        onClick = { vm.removeAttachment(att) },
                        label = { Text(att.name.take(20)) },
                        trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // Error
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // Input row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                filePicker.launch(arrayOf(
                    "text/*", "application/pdf", "application/json",
                    "application/zip", "image/*", "audio/*", "video/*",
                    "application/octet-stream"
                ))
            }) {
                Icon(Icons.Default.AttachFile, "Lampirkan")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ketik pesan...") },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    vm.sendMessage(input)
                    input = ""
                },
                enabled = !state.isGenerating && state.currentModel != null
            ) {
                Icon(Icons.Default.Send, "Kirim")
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: com.offlineai.app.data.ChatMessage,
    onSpeak: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content.ifBlank { if (message.isStreaming) "..." else "" },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!isUser && message.content.isNotBlank() && !message.isStreaming) {
                Row(Modifier.padding(top = 4.dp)) {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.VolumeUp, "Bacakan", Modifier.size(18.dp))
                    }
                }
            }
            if (message.attachments.isNotEmpty()) {
                message.attachments.forEach {
                    Text("📎 ${it.name}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
