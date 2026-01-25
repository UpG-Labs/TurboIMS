package io.github.vvb2060.ims

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * BootReceiver listens for device boot and Shizuku binder availability.
 * Once Shizuku is ready, it automatically applies saved configurations to all SIM cards.
 * 
 * Note: This receiver is triggered infrequently (only on boot), and the coroutine scope
 * is designed to outlive the receiver's onReceive() context. The static job reference
 * ensures we can cancel any previous attempts if multiple boot events occur.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val MAX_SHIZUKU_WAIT_TIME_MS = 60000L // 60 seconds
        private const val SHIZUKU_CHECK_INTERVAL_MS = 1000L // 1 second
        private const val BOOT_CONFIG_NOTIFICATION_ID = 1001
        
        // Static job to track ongoing configuration application
        // Synchronized to prevent race conditions from multiple boot events
        @Volatile
        private var applyJob: Job? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(TAG, "Boot completed, waiting for Shizuku to start")
                waitForShizukuAndApplyConfigs(context)
            }
        }
    }

    /**
     * Waits for Shizuku to become available and then applies saved configurations.
     * Uses a coroutine that outlives the receiver's onReceive() lifecycle.
     */
    private fun waitForShizukuAndApplyConfigs(context: Context) {
        // Use application context to ensure validity beyond receiver lifecycle
        val appContext = context.applicationContext
        
        // Synchronized cancellation and creation to prevent race conditions
        synchronized(BootReceiver::class.java) {
            applyJob?.cancel()
            
            applyJob = CoroutineScope(Dispatchers.IO).launch {
                var elapsedTime = 0L
                
                // Wait for Shizuku to be ready
                while (elapsedTime < MAX_SHIZUKU_WAIT_TIME_MS) {
                    if (Shizuku.pingBinder() && 
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "Shizuku is ready after ${elapsedTime}ms")
                        applyAllSavedConfigurations(appContext)
                        return@launch
                    }
                    
                    delay(SHIZUKU_CHECK_INTERVAL_MS)
                    elapsedTime += SHIZUKU_CHECK_INTERVAL_MS
                }
                
                Log.w(TAG, "Timeout waiting for Shizuku to start after ${MAX_SHIZUKU_WAIT_TIME_MS}ms")
            }
        }
    }

    /**
     * Applies saved configurations to all SIM cards that have saved preferences.
     */
    private suspend fun applyAllSavedConfigurations(context: Context) {
        var successCount = 0
        var failureCount = 0
        
        try {
            // Read all available SIM cards
            val simList = ShizukuProvider.readSimInfoList(context)
            Log.i(TAG, "Found ${simList.size} SIM cards")
            
            if (simList.isEmpty()) {
                Log.w(TAG, "No SIM cards found, skipping configuration application")
                return
            }
            
            // Apply saved configuration for each SIM card that has one
            for (sim in simList) {
                val savedConfig = SimConfigManager.loadConfiguration(context, sim.subId)
                if (savedConfig != null) {
                    Log.i(TAG, "Applying saved configuration for SIM ${sim.subId} (${sim.displayName})")
                    val resultMsg = SimConfigManager.applyConfiguration(context, sim.subId, savedConfig)
                    if (resultMsg == null) {
                        Log.i(TAG, "Successfully applied configuration for SIM ${sim.subId}")
                        successCount++
                    } else {
                        Log.e(TAG, "Failed to apply configuration for SIM ${sim.subId}: $resultMsg")
                        failureCount++
                    }
                } else {
                    Log.d(TAG, "No saved configuration found for SIM ${sim.subId} (${sim.displayName})")
                }
            }
            
            Log.i(TAG, "Finished applying saved configurations on boot")
            
            // Show notification
            if (successCount > 0 || failureCount > 0) {
                showNotification(context, successCount, failureCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying saved configurations on boot", e)
            showErrorNotification(context, e.message ?: "Unknown error")
        }
    }
    
    /**
     * Shows a notification with configuration results.
     */
    private fun showNotification(context: Context, successCount: Int, failureCount: Int) {
        val message = when {
            failureCount == 0 && successCount > 0 -> 
                context.getString(R.string.config_success_message)
            failureCount > 0 && successCount == 0 -> 
                context.getString(R.string.config_all_failed)
            else -> 
                context.getString(R.string.config_mixed_result, successCount, failureCount)
        }
        
        val notification = NotificationCompat.Builder(context, Application.BOOT_CONFIG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.boot_config_notification_title))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(BOOT_CONFIG_NOTIFICATION_ID, notification)
    }
    
    /**
     * Shows an error notification.
     */
    private fun showErrorNotification(context: Context, error: String) {
        val message = context.getString(R.string.config_failed, error)
        
        val notification = NotificationCompat.Builder(context, Application.BOOT_CONFIG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.boot_config_notification_title))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(BOOT_CONFIG_NOTIFICATION_ID, notification)
    }
}
