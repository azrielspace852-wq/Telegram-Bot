package com.offlineai.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlineai.app.data.AppPreferences
import com.offlineai.app.data.Attachment
import com.offlineai.app.data.ChatMessage
import com.offlineai.app.data.MessageRole
import com.offlineai.app.data.ModelInfo
import com.offlineai.app.data.ThemeMode
import com.offlineai.app.inference.InferenceEngine
import com.offlineai.app.inference.StubInferenceEngine
import com.offlineai.app.server.LocalAIServerService
import com.offlineai.app.util.FileUtils
import com.offlineai.app.util.HardwareInfo
import com.offlineai.app.util.SwapManager
import com.offlineai.app.util.TtsHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val deviceStats: HardwareInfo.DeviceStats? = null,
    val thinkingEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val swapInfo: SwapManager.SwapInfo? = null,
    val isCreatingSwap: Boolean = false
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val engine: InferenceEngine = StubInferenceEngine()
    private val tts = TtsHelper(app)
    private val prefs = AppPreferences(app)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var genJob: Job? = null

    init {
        refreshHardware()
        refreshSwap()
        LocalAIServerService.engine = engine
        // Restore persisted model + settings
        viewModelScope.launch {
            val path = prefs.selectedModelPath.first()
            val name = prefs.selectedModelName.first()
            val size = prefs.selectedModelSize.first()
            val thinking = prefs.thinkingEnabled.first()
            val backendStr = prefs.backend.first()
            val theme = prefs.themeMode.first()

            _uiState.update {
                it.copy(
                    thinkingEnabled = thinking,
                    themeMode = theme,
                    backend = when (backendStr) {
                        "CPU" -> ComputeBackend.CPU
                        "GPU" -> ComputeBackend.GPU
                        else -> ComputeBackend.AUTO
                    }
                )
            }

            if (path != null && File(path).exists()) {
                val info = ModelInfo(
                    path = path,
                    name = name ?: File(path).name,
                    sizeBytes = size ?: File(path).length()
                )
                // Auto-enable thinking if model supports it
                if (info.supportsReasoning && !thinking) {
                    prefs.setThinkingEnabled(true)
                    _uiState.update { it.copy(thinkingEnabled = true) }
                }
                selectModel(info, force = true, persist = false)
            }
        }
    }

    fun refreshHardware() {
        val stats = HardwareInfo.getStats(getApplication())
        _uiState.update { it.copy(deviceStats = stats) }
    }

    fun refreshSwap() {
        val info = SwapManager.getInfo(getApplication())
        _uiState.update { it.copy(swapInfo = info) }
    }

    fun selectModel(model: ModelInfo, force: Boolean = false, persist: Boolean = true) {
        viewModelScope.launch {
            val stats = _uiState.value.deviceStats ?: HardwareInfo.getStats(getApplication())
            val swap = _uiState.value.swapInfo ?: SwapManager.getInfo(getApplication())
            // Consider swap as additional "memory" for estimation when present
            val effectiveRam = stats.availableRamMb + if (swap.exists) swap.sizeMb else 0L
            val (ok, msg) = HardwareInfo.canLoadModel(
                stats.copy(availableRamMb = effectiveRam),
                model.sizeMb,
                force
            )
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
                // Auto thinking if supported
                val enableThinking = model.supportsReasoning
                if (enableThinking) {
                    prefs.setThinkingEnabled(true)
                }
                _uiState.update {
                    it.copy(
                        currentModel = model,
                        isModelLoading = false,
                        thinkingEnabled = enableThinking || it.thinkingEnabled && model.supportsReasoning,
                        messages = emptyList()
                    )
                }
                if (persist) {
                    prefs.setSelectedModel(model.path, model.name, model.sizeBytes)
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
        viewModelScope.launch { prefs.setBackend(backend.name) }
        _uiState.value.currentModel?.let { selectModel(it, force = true) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun setThinkingEnabled(enabled: Boolean) {
        val model = _uiState.value.currentModel
        if (enabled && model != null && !model.supportsReasoning) {
            _uiState.update { it.copy(error = "Model ini tidak mendukung mode thinking/reasoning") }
            return
        }
        viewModelScope.launch {
            prefs.setThinkingEnabled(enabled)
            _uiState.update { it.copy(thinkingEnabled = enabled, error = null) }
        }
    }

    fun createSwap(sizeMb: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSwap = true, error = null) }
            val result = SwapManager.createSwap(getApplication(), sizeMb)
            if (result.isSuccess) {
                prefs.setSwap(result.getOrNull(), sizeMb.coerceAtMost(SwapManager.MAX_SWAP_MB))
                refreshSwap()
                refreshHardware()
            } else {
                _uiState.update {
                    it.copy(error = result.exceptionOrNull()?.message ?: "Gagal membuat swap")
                }
            }
            _uiState.update { it.copy(isCreatingSwap = false) }
        }
    }

    fun deleteSwap() {
        viewModelScope.launch {
            SwapManager.deleteSwap(getApplication())
            prefs.setSwap(null, 0)
            refreshSwap()
            refreshHardware()
        }
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
        val thinkingPrefix = if (_uiState.value.thinkingEnabled) {
            "[Thinking mode aktif – model akan menampilkan penalaran jika didukung]\n\n"
        } else ""
        val fullContent = buildString {
            append(thinkingPrefix)
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

    fun generateZipFromText(files: Map<String, String>): java.io.File {
        return FileUtils.createZip(getApplication(), files)
    }

    override fun onCleared() {
        tts.shutdown()
        engine.unloadModel()
        super.onCleared()
    }
}
