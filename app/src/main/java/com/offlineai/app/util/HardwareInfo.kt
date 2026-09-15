package com.offlineai.app.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

object HardwareInfo {

    data class DeviceStats(
        val totalRamMb: Long,
        val availableRamMb: Long,
        val totalStorageMb: Long,
        val availableStorageMb: Long,
        val cpuCores: Int,
        val hasVulkan: Boolean,
        val deviceName: String
    )

    fun getStats(context: Context): DeviceStats {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem / (1024 * 1024)
        val availRam = memInfo.availMem / (1024 * 1024)

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = stat.totalBytes / (1024 * 1024)
        val availStorage = stat.availableBytes / (1024 * 1024)

        val hasVulkan = try {
            val pm = context.packageManager
            pm.hasSystemFeature("android.hardware.vulkan.level") ||
                    pm.hasSystemFeature("android.hardware.vulkan.version")
        } catch (_: Exception) {
            false
        }

        return DeviceStats(
            totalRamMb = totalRam,
            availableRamMb = availRam,
            totalStorageMb = totalStorage,
            availableStorageMb = availStorage,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            hasVulkan = hasVulkan,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    /** Rough check: model file size * 1.8 ~ 2.5 usually needed in RAM for Q4/Q5 */
    fun canLoadModel(stats: DeviceStats, modelSizeMb: Long, force: Boolean = false): Pair<Boolean, String?> {
        val estimatedNeed = (modelSizeMb * 2.2).toLong()
        if (estimatedNeed > stats.availableRamMb && !force) {
            return false to "Model ~${modelSizeMb}MB diperkirakan butuh ~${estimatedNeed}MB RAM. Tersedia hanya ${stats.availableRamMb}MB."
        }
        if (modelSizeMb > stats.availableStorageMb && !force) {
            return false to "Ruang penyimpanan tidak cukup untuk model ${modelSizeMb}MB."
        }
        return true to null
    }
}
