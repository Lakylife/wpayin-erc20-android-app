package io.noriskservis.wpayin.wallet.data

import android.content.Context
import io.noriskservis.wpayin.wallet.model.AppSettings

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("wpayin_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        onboardingComplete = preferences.getBoolean(KEY_ONBOARDING, false),
        currencyCode = preferences.getString(KEY_CURRENCY, "USD") ?: "USD",
        languageTag = preferences.getString(KEY_LANGUAGE, "system") ?: "system",
        hideBalances = preferences.getBoolean(KEY_HIDE_BALANCES, false),
    )

    fun setOnboardingComplete(complete: Boolean) {
        preferences.edit().putBoolean(KEY_ONBOARDING, complete).apply()
    }

    fun setCurrency(code: String) {
        preferences.edit().putString(KEY_CURRENCY, code).apply()
    }

    fun setLanguage(tag: String) {
        preferences.edit().putString(KEY_LANGUAGE, tag).apply()
    }

    fun setHideBalances(hidden: Boolean) {
        preferences.edit().putBoolean(KEY_HIDE_BALANCES, hidden).apply()
    }

    companion object {
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_HIDE_BALANCES = "hide_balances"
    }
}
