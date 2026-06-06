package com.tools.screenshot3

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    
    private const val LANGUAGE_ENGLISH = "en"
    private const val LANGUAGE_KOREAN = "ko"
    private const val LANGUAGE_POLISH = "pl"
    private const val LANGUAGE_HINDI = "hi"
    private const val LANGUAGE_SPANISH = "es"
    private const val LANGUAGE_JAPANESE = "ja"
    private const val LANGUAGE_CHINESE = "zh"
    private const val LANGUAGE_FRENCH = "fr"
    private const val LANGUAGE_GERMAN = "de"
    private const val LANGUAGE_PORTUGUESE = "pt"
    private const val LANGUAGE_INDONESIAN = "id"
    private const val LANGUAGE_VIETNAMESE = "vi"
    private const val LANGUAGE_TURKISH = "tr"
    private const val LANGUAGE_RUSSIAN = "ru"
    private const val LANGUAGE_ITALIAN = "it"
    
    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
    }
    
    fun saveLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }
    
    fun setLocale(context: Context, language: String): Context {
        saveLanguage(context, language)
        return updateLocale(context, language)
    }
    
    fun updateLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
    
    fun getLanguageDisplayName(language: String): String {
        return when (language) {
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_KOREAN -> "한국어"
            LANGUAGE_POLISH -> "Polski"
            LANGUAGE_HINDI -> "हिन्दी"
            LANGUAGE_SPANISH -> "Español"
            LANGUAGE_JAPANESE -> "日本語"
            LANGUAGE_CHINESE -> "中文"
            LANGUAGE_FRENCH -> "Français"
            LANGUAGE_GERMAN -> "Deutsch"
            LANGUAGE_PORTUGUESE -> "Português"
            LANGUAGE_INDONESIAN -> "Bahasa Indonesia"
            LANGUAGE_VIETNAMESE -> "Tiếng Việt"
            LANGUAGE_TURKISH -> "Türkçe"
            LANGUAGE_RUSSIAN -> "Русский"
            LANGUAGE_ITALIAN -> "Italiano"
            else -> "English"
        }
    }
    
    fun getAvailableLanguages(): List<String> {
        return listOf(
            LANGUAGE_ENGLISH,
            LANGUAGE_KOREAN,
            LANGUAGE_POLISH,
            LANGUAGE_HINDI,
            LANGUAGE_SPANISH,
            LANGUAGE_JAPANESE,
            LANGUAGE_CHINESE,
            LANGUAGE_FRENCH,
            LANGUAGE_GERMAN,
            LANGUAGE_PORTUGUESE,
            LANGUAGE_INDONESIAN,
            LANGUAGE_VIETNAMESE,
            LANGUAGE_TURKISH,
            LANGUAGE_RUSSIAN,
            LANGUAGE_ITALIAN
        )
    }
}
