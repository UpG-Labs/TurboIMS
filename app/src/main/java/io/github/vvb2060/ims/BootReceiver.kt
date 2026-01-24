package io.github.vvb2060.ims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
            
            // Show toast notification on main thread
            if (successCount > 0 || failureCount > 0) {
                showToast(context, successCount, failureCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying saved configurations on boot", e)
            showErrorToast(context, e.message ?: "Unknown error")
        }
    }
    
    /**
     * Shows a toast message on the main thread with configuration results.
     */
    private fun showToast(context: Context, successCount: Int, failureCount: Int) {
        Handler(Looper.getMainLooper()).post {
            val message = when {
                failureCount == 0 && successCount > 0 -> 
                    context.getString(R.string.config_success_message)
                failureCount > 0 && successCount == 0 -> 
                    context.getString(R.string.config_all_failed)
                else -> 
                    context.getString(R.string.config_mixed_result, successCount, failureCount)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Shows an error toast on the main thread.
     */
    private fun showErrorToast(context: Context, error: String) {
        Handler(Looper.getMainLooper()).post {
            val message = context.getString(R.string.config_failed, error)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
