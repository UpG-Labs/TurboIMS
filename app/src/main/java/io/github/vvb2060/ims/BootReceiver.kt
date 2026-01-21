package io.github.vvb2060.ims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.FeatureValueType
import io.github.vvb2060.ims.privileged.ImsModifier
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
                val savedConfig = loadConfiguration(context, sim.subId)
                if (savedConfig != null) {
                    Log.i(TAG, "Applying saved configuration for SIM ${sim.subId} (${sim.displayName})")
                    applyConfiguration(context, sim.subId, savedConfig)
                } else {
                    Log.d(TAG, "No saved configuration found for SIM ${sim.subId} (${sim.displayName})")
                }
            }
            
            Log.i(TAG, "Finished applying saved configurations on boot")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying saved configurations on boot", e)
        }
    }

    /**
     * Loads saved configuration for a specific SIM card.
     */
    private fun loadConfiguration(context: Context, subId: Int): Map<Feature, FeatureValue>? {
        val prefs = context.getSharedPreferences("sim_config_$subId", Context.MODE_PRIVATE)
        if (prefs.all.isEmpty()) return null

        val map = linkedMapOf<Feature, FeatureValue>()
        Feature.entries.forEach { feature ->
            if (prefs.contains(feature.name)) {
                when (feature.valueType) {
                    FeatureValueType.BOOLEAN -> {
                        val data = prefs.getBoolean(feature.name, feature.defaultValue as Boolean)
                        map[feature] = FeatureValue(data, feature.valueType)
                    }
                    FeatureValueType.STRING -> {
                        val data = prefs.getString(feature.name, feature.defaultValue as String) ?: ""
                        map[feature] = FeatureValue(data, feature.valueType)
                    }
                }
            } else {
                map[feature] = FeatureValue(feature.defaultValue, feature.valueType)
            }
        }
        return map
    }

    /**
     * Applies a configuration to a specific SIM card.
     */
    private suspend fun applyConfiguration(
        context: Context,
        subId: Int,
        config: Map<Feature, FeatureValue>
    ) {
        try {
            val carrierName = config[Feature.CARRIER_NAME]?.data as String?
            val countryISO = config[Feature.COUNTRY_ISO]?.data as String?
            val imsUserAgent = config[Feature.IMS_USER_AGENT]?.data as String?
            val enableVoLTE = (config[Feature.VOLTE]?.data ?: true) as Boolean
            val enableVoWiFi = (config[Feature.VOWIFI]?.data ?: true) as Boolean
            val enableVT = (config[Feature.VT]?.data ?: true) as Boolean
            val enableVoNR = (config[Feature.VONR]?.data ?: true) as Boolean
            val enableCrossSIM = (config[Feature.CROSS_SIM]?.data ?: true) as Boolean
            val enableUT = (config[Feature.UT]?.data ?: true) as Boolean
            val enable5GNR = (config[Feature.FIVE_G_NR]?.data ?: true) as Boolean
            val enable5GNROnlySA = (config[Feature.FIVE_G_NR_ONLY_SA]?.data ?: false) as Boolean
            val enable5GThreshold = (config[Feature.FIVE_G_THRESHOLDS]?.data ?: true) as Boolean
            val enableShow4GForLTE = (config[Feature.SHOW_4G_FOR_LTE]?.data ?: false) as Boolean

            val bundle = ImsModifier.buildBundle(
                carrierName,
                countryISO,
                imsUserAgent,
                enableVoLTE,
                enableVoWiFi,
                enableVT,
                enableVoNR,
                enableCrossSIM,
                enableUT,
                enable5GNR,
                enable5GNROnlySA,
                enable5GThreshold,
                enableShow4GForLTE
            )
            bundle.putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, subId)

            val resultMsg = ShizukuProvider.overrideImsConfig(context, bundle)
            if (resultMsg == null) {
                Log.i(TAG, "Successfully applied configuration for SIM $subId")
            } else {
                Log.e(TAG, "Failed to apply configuration for SIM $subId: $resultMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception applying configuration for SIM $subId", e)
        }
    }
}
