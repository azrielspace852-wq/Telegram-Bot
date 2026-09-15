package com.offlineai.app.data

import java.io.File

data class ModelInfo(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val estimatedRamMb: Long = sizeBytes / (1024 * 1024) * 2, // rough estimate 2x file size
) {
    val file: File get() = File(path)
    val sizeMb: Long get() = sizeBytes / (1024 * 1024)
}
