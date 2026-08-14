package com.nextservices.nextvision.utils

import android.content.Context
import android.content.res.Configuration
import com.nextservices.nextvision.BuildConfig
import java.util.Locale

object AppLanguageManager {
    private val preferredLanguageOrder = listOf(
        "en",
        "ar",
        "de",
        "es",
        "fr",
        "it",
        "pl",
    )

    fun wrap(context: Context): Context {
        val languageTag = getSelectedLanguage(context)

        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    fun getSelectedLanguage(context: Context): String {
        val storedLanguage = context
            .getSharedPreferences("${BuildConfig.APPLICATION_ID}.preferences", Context.MODE_PRIVATE)
            .getString("CURRENT_LANGUAGE", null)
            ?.takeIf { it in getAvailableLanguageTags(context) }

        return storedLanguage ?: "en"
    }

    private fun getAvailableLanguageTags(context: Context): List<String> {
        val discoveredLanguages = context.assets.locales
            .mapNotNull(::normalizeLanguageTag)
            .toMutableSet()

        // English is provided by the base `values/` resources even when no explicit locale folder exists.
        discoveredLanguages.add("en")

        return preferredLanguageOrder.filter { it in discoveredLanguages }
    }

    private fun normalizeLanguageTag(tag: String): String? {
        if (tag.isBlank()) return null

        val normalizedTag = when {
            tag.startsWith("b+") -> tag.removePrefix("b+").replace('+', '-')
            else -> tag.replace('_', '-')
        }

        return Locale.forLanguageTag(normalizedTag)
            .language
            .takeUnless { it.isBlank() || it == "und" }
    }

}
