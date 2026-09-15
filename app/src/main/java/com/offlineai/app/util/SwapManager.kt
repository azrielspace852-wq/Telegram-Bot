package com.offlineai.app.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile

/**
 * Experimental swap-file helper for low-RAM devices.
 *
 * On stock (non-rooted) Android, true swapon() is not available to apps.
 * This class creates a large sparse / preallocated file that can be used
 * as a "virtual swap" indicator and for future native engines that support
 * mmap / disk-backed memory.
 *
 * Max size enforced: 7 GB.
 * Requires enough free storage.
 */
object SwapManager {

    const val MAX_SWAP_MB = 7L * 1024  // 7 GB
    private const val SWAP_DIR_NAME = "axion_swap"
    private const val SWAP_FILE_NAME = "swapfile.dat"

    data class SwapInfo(
        val exists: Boolean,
        val path: String?,
        val sizeMb: Long,
        val freeStorageMb: Long
    )

    fun getInfo(context: Context): SwapInfo {
        val dir = getSwapDir(context)
        val file = File(dir, SWAP_FILE_NAME)
        val free = getAvailableStorageMb()
        return if (file.exists() && file.length() > 0) {
            SwapInfo(true, file.absolutePath, file.length() / (1024 * 1024), free)
        } else {
            SwapInfo(false, null, 0, free)
        }
    }

    fun getAvailableStorageMb(): Long {
        return try {
            val path = Environment.getDataDirectory().path
            val stat = StatFs(path)
            stat.availableBytes / (1024 * 1024)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Create a swap file of the requested size (MB).
     * Size is clamped to [1, MAX_SWAP_MB] and available storage minus 500 MB safety.
     * Returns Result with path on success.
     */
    fun createSwap(context: Context, sizeMb: Long): Result<String> {
        return try {
            val free = getAvailableStorageMb()
            val safeMax = (free - 500).coerceAtLeast(0)
            val target = sizeMb.coerceIn(1, MAX_SWAP_MB).coerceAtMost(safeMax)
            if (target < 1) {
                return Result.failure(IllegalStateException(
                    "Ruang penyimpanan tidak cukup. Tersedia ~${free} MB (butuh minimal ~1 GB free)."
                ))
            }

            val dir = getSwapDir(context)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, SWAP_FILE_NAME)

            // Remove old if present
            if (file.exists()) file.delete()

            // Pre-allocate with RandomAccessFile (may take time for large sizes)
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(target * 1024 * 1024)
            }

            if (!file.exists() || file.length() < target * 1024 * 1024 / 2) {
                file.delete()
                return Result.failure(IllegalStateException("Gagal membuat file swap (ukuran tidak cocok)."))
            }

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteSwap(context: Context): Boolean {
        return try {
            val dir = getSwapDir(context)
            val file = File(dir, SWAP_FILE_NAME)
            if (file.exists()) file.delete() else true
            // also clean empty dir
            dir.listFiles()?.isEmpty()?.let { if (it) dir.delete() }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getSwapDir(context: Context): File {
        // Prefer external files dir if available (larger), else internal
        val external = context.getExternalFilesDir(null)
        return if (external != null) {
            File(external, SWAP_DIR_NAME)
        } else {
            File(context.filesDir, SWAP_DIR_NAME)
        }
    }
}
