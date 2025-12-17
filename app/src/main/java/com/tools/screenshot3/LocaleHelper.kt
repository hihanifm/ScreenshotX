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
            else -> "English"
        }
    }
    
    fun getAvailableLanguages(): List<String> {
        return listOf(LANGUAGE_ENGLISH, LANGUAGE_KOREAN)
    }
}

