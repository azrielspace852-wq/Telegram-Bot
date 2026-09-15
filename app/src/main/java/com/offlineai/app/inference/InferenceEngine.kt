package com.offlineai.app.inference

import com.offlineai.app.data.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Interface for local LLM inference.
 * Implement this with real llama.cpp (JNI / kotlinllamacpp / official llama.android).
 *
 * Current implementation: StubEngine (for UI testing without native lib).
 */
interface InferenceEngine {
    val isLoaded: Boolean
    val modelPath: String?

    suspend fun loadModel(
        path: String,
        nThreads: Int = Runtime.getRuntime().availableProcessors(),
        nGpuLayers: Int = 0,          // 0 = CPU only, -1 = all layers to GPU if available
        contextSize: Int = 4096
    ): Result<Unit>

    fun unloadModel()

    /**
     * Stream tokens for the given conversation.
     * Attachments' extractedText should already be injected into the last user message.
     */
    fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): Flow<String>

    fun stopGeneration()
}

/**
 * Stub for development / when native library is not yet integrated.
 * Replace with real engine in production.
 */
class StubInferenceEngine : InferenceEngine {
    override var isLoaded: Boolean = false
        private set
    override var modelPath: String? = null
        private set

    private var stopRequested = false

    override suspend fun loadModel(
        path: String,
        nThreads: Int,
        nGpuLayers: Int,
        contextSize: Int
    ): Result<Unit> {
        // Simulate load delay
        kotlinx.coroutines.delay(800)
        modelPath = path
        isLoaded = true
        return Result.success(Unit)
    }

    override fun unloadModel() {
        isLoaded = false
        modelPath = null
    }

    override fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): Flow<String> = kotlinx.coroutines.flow.flow {
        stopRequested = false
        val lastUser = messages.lastOrNull { it.role == com.offlineai.app.data.MessageRole.USER }?.content ?: ""
        val reply = buildString {
            append("**[Stub Engine]** Model belum dihubungkan ke llama.cpp asli.\n\n")
            append("Pesan terakhir Anda:\n")
            append(lastUser.take(500))
            if (lastUser.length > 500) append("...")
            append("\n\n---\n")
            append("Untuk mengaktifkan inference sungguhan:\n")
            append("1. Tambahkan library llama.cpp (lihat README)\n")
            append("2. Ganti `StubInferenceEngine` dengan implementasi JNI / kotlinllamacpp\n")
            append("3. Build native dengan NDK\n")
        }
        // Simulate streaming
        val words = reply.split(" ")
        for (w in words) {
            if (stopRequested) break
            emit(w + " ")
            kotlinx.coroutines.delay(30)
        }
    }

    override fun stopGeneration() {
        stopRequested = true
    }
}
