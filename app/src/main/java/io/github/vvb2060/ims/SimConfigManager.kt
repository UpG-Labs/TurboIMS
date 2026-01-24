package io.github.vvb2060.ims

import android.content.Context
import android.util.Log
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.FeatureValueType
import io.github.vvb2060.ims.privileged.ImsModifier

/**
 * Utility class for managing SIM card configurations.
 * Handles loading, saving, and applying configurations to SIM cards.
 */
object SimConfigManager {
    private const val TAG = "SimConfigManager"

    /**
     * Loads saved configuration for a specific SIM card.
     * Returns null if no saved configuration exists.
     */
    fun loadConfiguration(context: Context, subId: Int): Map<Feature, FeatureValue>? {
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
     * Returns null on success, or an error message string on failure.
     */
    suspend fun applyConfiguration(
        context: Context,
        subId: Int,
        config: Map<Feature, FeatureValue>
    ): String? {
        return try {
            // For "all SIMs" option (subId == -1), don't set carrier-specific fields
            val carrierName = if (subId == -1) null else config[Feature.CARRIER_NAME]?.data as String?
            val countryISO = if (subId == -1) null else config[Feature.COUNTRY_ISO]?.data as String?
            val imsUserAgent = if (subId == -1) null else config[Feature.IMS_USER_AGENT]?.data as String?
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

            ShizukuProvider.overrideImsConfig(context, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Exception applying configuration for SIM $subId", e)
            e.message ?: "Unknown error"
        }
    }

    /**
     * Saves a configuration for a specific SIM card.
     */
    fun saveConfiguration(context: Context, subId: Int, map: Map<Feature, FeatureValue>) {
        context.getSharedPreferences("sim_config_$subId", Context.MODE_PRIVATE).edit().apply {
            clear() // Clear old configuration
            map.forEach { (feature, value) ->
                when (value.valueType) {
                    FeatureValueType.BOOLEAN -> putBoolean(feature.name, value.data as Boolean)
                    FeatureValueType.STRING -> putString(feature.name, value.data as String)
                }
            }
        }.apply()
    }
}
