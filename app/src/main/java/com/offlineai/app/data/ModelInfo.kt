package com.offlineai.app.data

import java.io.File

enum class ModelFormat {
    GGUF, ONNX, PYTORCH, SAFETENSORS, BIN, UNKNOWN;

    companion object {
        fun fromFileName(name: String): ModelFormat {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".gguf") -> GGUF
                lower.endsWith(".onnx") -> ONNX
                lower.endsWith(".pt") || lower.endsWith(".pth") -> PYTORCH
                lower.endsWith(".safetensors") -> SAFETENSORS
                lower.endsWith(".bin") -> BIN
                else -> UNKNOWN
            }
        }
    }
}

data class ModelInfo(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val format: ModelFormat = ModelFormat.fromFileName(name),
    /** Rough estimate: GGUF Q4 ~1.2–1.8×, others higher */
    val estimatedRamMb: Long = when (ModelFormat.fromFileName(name)) {
        ModelFormat.GGUF -> sizeBytes / (1024 * 1024) * 18 / 10
        else -> sizeBytes / (1024 * 1024) * 25 / 10
    },
    /** Heuristic: models with "r1", "reason", "think", "o1", "qwq" often support reasoning */
    val supportsReasoning: Boolean = name.lowercase().let { n ->
        listOf("r1", "reason", "think", "o1", "qwq", "deepseek-r", "sky-t1").any { n.contains(it) }
    }
) {
    val file: File get() = File(path)
    val sizeMb: Long get() = sizeBytes / (1024 * 1024)

    companion object {
        val SUPPORTED_EXTENSIONS = listOf(
            "gguf", "onnx", "pt", "pth", "safetensors", "bin", "ggml"
        )

        fun isSupportedFileName(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in SUPPORTED_EXTENSIONS
        }
    }
}
