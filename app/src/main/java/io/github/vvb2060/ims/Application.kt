package io.github.vvb2060.ims

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class Application : Application() {
    companion object {
        const val BOOT_CONFIG_CHANNEL_ID = "boot_config_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onTerminate() {
        super.onTerminate()
        LogcatRepository.stopAndClear()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.boot_config_channel_name)
            val descriptionText = getString(R.string.boot_config_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(BOOT_CONFIG_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}