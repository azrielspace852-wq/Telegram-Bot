package com.offlineai.app.inference

import android.content.Context
import com.offlineai.app.data.ChatMessage
import com.offlineai.app.data.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Prebuilt llama.cpp engine using:
 *   implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
 *
 * This makes inference fully active without needing to build native code yourself.
 */
class LlamaCppInferenceEngine(
    private val context: Context
) : InferenceEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventFlow = MutableSharedFlow<Any>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // We keep the helper as Any? so the project still compiles even if the
    // dependency is temporarily missing. Once the dependency is present the
    // real type is used at runtime.
    private var llamaHelper: Any? = null
    private var _modelPath: String? = null
    @Volatile private var stopRequested = false

    override val isLoaded: Boolean get() = _modelPath != null
    override val modelPath: String? get() = _modelPath

    override suspend fun loadModel(
        path: String,
        nThreads: Int,
        nGpuLayers: Int,
        contextSize: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Create LlamaHelper via reflection-free path when possible,
            // but keep it resilient.
            val helperClass = Class.forName("org.nehuatl.llamacpp.LlamaHelper")
            val ctor = helperClass.constructors.first {
                it.parameterTypes.size >= 2
            }
            // LlamaHelper(ContentResolver, CoroutineScope, MutableSharedFlow)
            val helper = ctor.newInstance(
                context.contentResolver,
                scope,
                eventFlow
            )
            llamaHelper = helper

            // load(path: String, contextLength: Int, ...)
            val loadMethod = helperClass.methods.first { it.name == "load" }
            // Call the simplest overload we can find
            when (loadMethod.parameterCount) {
                2 -> loadMethod.invoke(helper, path, contextSize)
                3 -> loadMethod.invoke(helper, path, contextSize, null)
                else -> loadMethod.invoke(helper, path)
            }

            _modelPath = path
            Result.success(Unit)
        } catch (e: ClassNotFoundException) {
            Result.failure(
                IllegalStateException(
                    "Library io.github.ljcamargo:llamacpp-kotlin not found. " +
                    "Make sure the dependency is added in app/build.gradle.kts",
                    e
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unloadModel() {
        try {
            llamaHelper?.let { helper ->
                // try common cleanup names
                listOf("unload", "release", "close", "destroy").forEach { name ->
                    try {
                        helper.javaClass.getMethod(name).invoke(helper)
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        llamaHelper = null
        _modelPath = null
    }

    override fun generate(
        messages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): Flow<String> = flow {
        stopRequested = false
        val helper = llamaHelper
            ?: throw IllegalStateException("Model not loaded")

        val lastUser = messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val system = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.content

        // Build a simple prompt (library usually applies chat template)
        val prompt = buildString {
            if (!system.isNullOrBlank()) {
                appendLine("System: $system")
                appendLine()
            }
            // include a bit of history for better context
            messages.takeLast(6).forEach { msg ->
                when (msg.role) {
                    MessageRole.USER -> appendLine("User: ${msg.content}")
                    MessageRole.ASSISTANT -> appendLine("Assistant: ${msg.content}")
                    else -> {}
                }
            }
            if (!lastUser.isBlank() && messages.lastOrNull()?.role != MessageRole.USER) {
                appendLine("User: $lastUser")
            }
            append("Assistant:")
        }

        try {
            val predictMethod = helper.javaClass.methods.first { it.name == "predict" }
            // predict(prompt: String) or with extra args
            if (predictMethod.parameterCount >= 1) {
                predictMethod.invoke(helper, prompt)
            }

            // Collect from the shared event flow
            // Events are typically: Ongoing(word), Done, Error
            eventFlow.collect { event ->
                if (stopRequested) return@collect
                val className = event.javaClass.simpleName
                when {
                    className.contains("Ongoing", ignoreCase = true) -> {
                        // try to extract the token/word field
                        val word = try {
                            event.javaClass.getDeclaredField("word").apply { isAccessible = true }.get(event)?.toString()
                                ?: event.javaClass.getDeclaredField("token").apply { isAccessible = true }.get(event)?.toString()
                                ?: event.toString()
                        } catch (_: Exception) {
                            event.toString()
                        }
                        emit(word)
                    }
                    className.contains("Done", ignoreCase = true) -> {
                        return@collect
                    }
                    className.contains("Error", ignoreCase = true) -> {
                        emit("\n[Error: $event]")
                        return@collect
                    }
                }
            }
        } catch (e: Exception) {
            emit("\n[Generation error: ${e.message}]")
        }
    }

    override fun stopGeneration() {
        stopRequested = true
        try {
            llamaHelper?.javaClass?.methods
                ?.firstOrNull { it.name.contains("stop", ignoreCase = true) }
                ?.invoke(llamaHelper)
        } catch (_: Exception) { }
    }

    fun destroy() {
        unloadModel()
        scope.cancel()
    }
}
