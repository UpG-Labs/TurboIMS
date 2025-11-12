package io.github.vvb2060.ims

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    private const val PREF_NAME = "locale_config"
    private const val KEY_LANGUAGE = "language"

    fun setLocale(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()

        updateResources(context, languageCode)
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // 如果用户已经手动设置过语言，使用用户设置
        if (prefs.contains(KEY_LANGUAGE)) {
            return prefs.getString(KEY_LANGUAGE, "zh")!!
        }

        // 否则根据系统语言自动判断
        val systemLang = Locale.getDefault().getLanguage()
        // 如果系统语言是中文（包括简体、繁体等），使用中文，否则使用英文
        return if (systemLang.startsWith("zh")) "zh" else "en"
    }

    fun updateResources(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val resources = context.getResources()
        val config = Configuration(resources.getConfiguration())
        config.setLocale(locale)
        context.createConfigurationContext(config)
        resources.updateConfiguration(config, resources.getDisplayMetrics())
    }

    fun toggleLanguage(context: Context): String {
        val currentLang = getLanguage(context)
        val newLang = if (currentLang == "zh") "en" else "zh"
        setLocale(context, newLang)
        return newLang
    }
}
