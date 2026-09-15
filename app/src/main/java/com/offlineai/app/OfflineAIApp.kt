package com.offlineai.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OfflineAIApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serverChannel = NotificationChannel(
                "local_ai_server",
                "AXION AI Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi saat device menjadi server AI lokal"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(serverChannel)
        }
    }
}
