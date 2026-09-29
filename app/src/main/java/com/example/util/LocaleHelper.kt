package com.example.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun updateLocale(context: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        return context.createConfigurationContext(config)
    }

    fun applyLocale(activity: Activity, lang: String) {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        activity.recreate()
    }
}
