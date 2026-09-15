package com.offlineai.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.app.data.Attachment
import com.offlineai.app.data.ChatMessage
import com.offlineai.app.data.MessageRole
import com.offlineai.app.data.ModelInfo
import com.offlineai.app.inference.InferenceEngine
import com.offlineai.app.inference.StubInferenceEngine
import com.offlineai.app.server.LocalAIServerService
import com.offlineai.app.util.FileUtils
import com.offlineai.app.util.HardwareInfo
import com.offlineai.app.util.TtsHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ComputeBackend { AUTO, CPU, GPU }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentModel: ModelInfo? = null,
    val isModelLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val error: String? = null,
    val warning: String? = null,
    val backend: ComputeBackend = ComputeBackend.AUTO,
    val serverRunning: Boolean = false,
    val serverPort: Int = 8080,
    val deviceStats: HardwareInfo.DeviceStats? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val engine: InferenceEngine = StubInferenceEngine() // TODO: replace with real engine
    private val tts = TtsHelper(app)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var genJob: Job? = null

    init {
        refreshHardware()
        LocalAIServerService.engine = engine
    }

    fun refreshHardware() {
        val stats = HardwareInfo.getStats(getApplication())
        _uiState.update { it.copy(deviceStats = stats) }
    }

    fun selectModel(model: ModelInfo, force: Boolean = false) {
        viewModelScope.launch {
            val stats = _uiState.value.deviceStats ?: HardwareInfo.getStats(getApplication())
            val (ok, msg) = HardwareInfo.canLoadModel(stats, model.sizeMb, force)
            if (!ok) {
                _uiState.update { it.copy(warning = msg) }
                return@launch
            }
            _uiState.update { it.copy(isModelLoading = true, error = null, warning = null) }
            val nGpu = when (_uiState.value.backend) {
                ComputeBackend.CPU -> 0
                ComputeBackend.GPU -> -1
                ComputeBackend.AUTO -> if (stats.hasVulkan) -1 else 0
            }
            val result = engine.loadModel(
                path = model.path,
                nThreads = stats.cpuCores.coerceAtMost(8),
                nGpuLayers = nGpu
            )
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        currentModel = model,
                        isModelLoading = false,
                        messages = emptyList() // reset chat on new model
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Gagal memuat model"
                    )
                }
            }
        }
    }

    fun clearWarning() {
        _uiState.update { it.copy(warning = null) }
    }

    fun setBackend(backend: ComputeBackend) {
        _uiState.update { it.copy(backend = backend) }
        // reload model if already loaded
        _uiState.value.currentModel?.let { selectModel(it, force = true) }
    }

    fun addAttachment(uri: Uri) {
        viewModelScope.launch {
            val att = FileUtils.processAttachment(getApplication(), uri)
            _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + att) }
        }
    }

    fun removeAttachment(att: Attachment) {
        _uiState.update {
            it.copy(pendingAttachments = it.pendingAttachments.filter { a -> a.uri != att.uri })
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && _uiState.value.pendingAttachments.isEmpty()) return
        if (!engine.isLoaded) {
            _uiState.update { it.copy(error = "Pilih model terlebih dahulu") }
            return
        }

        val atts = _uiState.value.pendingAttachments
        val fullContent = buildString {
            append(text)
            atts.forEach { a ->
                append("\n\n[File: ${a.name}]")
                a.extractedText?.let { append("\n$it") }
                if (a.isImage) append("\n[Gambar terlampir – model vision diperlukan]")
                if (a.isAudio) append("\n[Audio terlampir – model audio diperlukan]")
                if (a.isVideo) append("\n[Video terlampir – model multimodal diperlukan]")
            }
        }

        val userMsg = ChatMessage(role = MessageRole.USER, content = fullContent, attachments = atts)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                pendingAttachments = emptyList(),
                isGenerating = true,
                error = null
            )
        }

        genJob?.cancel()
        genJob = viewModelScope.launch {
            val assistantId = java.util.UUID.randomUUID().toString()
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(
                        id = assistantId,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        isStreaming = true
                    )
                )
            }

            val sb = StringBuilder()
            try {
                engine.generate(_uiState.value.messages.filter { !it.isStreaming || it.id == assistantId })
                    .collect { token ->
                        sb.append(token)
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { m ->
                                    if (m.id == assistantId) m.copy(content = sb.toString()) else m
                                }
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = state.messages.map { m ->
                            if (m.id == assistantId) m.copy(isStreaming = false) else m
                        }
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        engine.stopGeneration()
        genJob?.cancel()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun speak(text: String) {
        tts.speak(text)
    }

    fun stopSpeak() {
        tts.stop()
    }

    fun toggleServer(start: Boolean, port: Int = 8080) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, LocalAIServerService::class.java).apply {
            putExtra(LocalAIServerService.EXTRA_PORT, port)
            putExtra(LocalAIServerService.EXTRA_HOST, "0.0.0.0")
        }
        if (start) {
            ctx.startForegroundService(intent)
            _uiState.update { it.copy(serverRunning = true, serverPort = port) }
        } else {
            ctx.stopService(intent)
            _uiState.update { it.copy(serverRunning = false) }
        }
    }

    /** AI can request ZIP generation – helper for tool-like behaviour */
    fun generateZipFromText(files: Map<String, String>): java.io.File {
        return FileUtils.createZip(getApplication(), files)
    }

    override fun onCleared() {
        tts.shutdown()
        engine.unloadModel()
        super.onCleared()
    }
}
