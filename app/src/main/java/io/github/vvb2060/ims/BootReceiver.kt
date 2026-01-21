package io.github.vvb2060.ims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * BootReceiver listens for device boot and Shizuku binder availability.
 * Once Shizuku is ready, it automatically applies saved configurations to all SIM cards.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val MAX_SHIZUKU_WAIT_TIME_MS = 60000L // 60 seconds
        private const val SHIZUKU_CHECK_INTERVAL_MS = 1000L // 1 second
        
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
     */
    private fun waitForShizukuAndApplyConfigs(context: Context) {
        // Cancel any existing job
        applyJob?.cancel()
        
        applyJob = CoroutineScope(Dispatchers.IO).launch {
            var elapsedTime = 0L
            
            // Wait for Shizuku to be ready
            while (elapsedTime < MAX_SHIZUKU_WAIT_TIME_MS) {
                if (Shizuku.pingBinder() && 
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Shizuku is ready after ${elapsedTime}ms")
                    applyAllSavedConfigurations(context)
                    return@launch
                }
                
                delay(SHIZUKU_CHECK_INTERVAL_MS)
                elapsedTime += SHIZUKU_CHECK_INTERVAL_MS
            }
            
            Log.w(TAG, "Timeout waiting for Shizuku to start after ${MAX_SHIZUKU_WAIT_TIME_MS}ms")
        }
    }

    /**
     * Applies saved configurations to all SIM cards that have saved preferences.
     */
    private suspend fun applyAllSavedConfigurations(context: Context) {
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
                    } else {
                        Log.e(TAG, "Failed to apply configuration for SIM ${sim.subId}: $resultMsg")
                    }
                } else {
                    Log.d(TAG, "No saved configuration found for SIM ${sim.subId} (${sim.displayName})")
                }
            }
            
            Log.i(TAG, "Finished applying saved configurations on boot")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying saved configurations on boot", e)
        }
    }
}
