package io.noriskservis.wpayin.wallet.data

import android.content.Context
import io.noriskservis.wpayin.wallet.model.AppSettings
import io.noriskservis.wpayin.wallet.model.AppColorTheme
import io.noriskservis.wpayin.wallet.model.AssetListStyle
import io.noriskservis.wpayin.wallet.model.AutoLockDuration
import io.noriskservis.wpayin.wallet.model.AUTOMATIC_TIME_ZONE_ID
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.FiatCurrency

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("wpayin_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        onboardingComplete = preferences.getBoolean(KEY_ONBOARDING, false),
        currencyCode = preferences.getString(KEY_CURRENCY, "USD") ?: "USD",
        languageTag = preferences.getString(KEY_LANGUAGE, "system") ?: "system",
        hideBalances = preferences.getBoolean(KEY_HIDE_BALANCES, false),
        autoLockDuration = AutoLockDuration.fromStorage(
            preferences.getString(KEY_AUTO_LOCK_DURATION, null),
        ),
        enabledNetworks = preferences.getStringSet(KEY_ENABLED_NETWORKS, null)
            ?.mapNotNull { runCatching { Blockchain.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeIf(Set<Blockchain>::isNotEmpty)
            ?: Blockchain.entries.toSet(),
        favoriteSymbols = preferences.getStringSet(KEY_FAVORITE_SYMBOLS, emptySet())
            .orEmpty()
            .map(String::uppercase)
            .toSet(),
        notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false),
        timeZoneId = preferences.getString(KEY_TIME_ZONE, AUTOMATIC_TIME_ZONE_ID)
            ?: AUTOMATIC_TIME_ZONE_ID,
        colorTheme = AppColorTheme.fromStorage(preferences.getString(KEY_COLOR_THEME, null)),
        assetListStyle = AssetListStyle.fromStorage(preferences.getString(KEY_ASSET_LIST_STYLE, null)),
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

    fun setAutoLockDuration(duration: AutoLockDuration) {
        preferences.edit().putString(KEY_AUTO_LOCK_DURATION, duration.storageValue).apply()
    }

    fun setEnabledNetworks(networks: Set<Blockchain>) {
        require(networks.isNotEmpty()) { "At least one network must remain active" }
        preferences.edit().putStringSet(KEY_ENABLED_NETWORKS, networks.map(Blockchain::name).toSet()).apply()
    }

    fun setFavoriteSymbols(symbols: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_FAVORITE_SYMBOLS, symbols.map(String::uppercase).toSet())
            .apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun setTimeZone(timeZoneId: String) {
        preferences.edit().putString(KEY_TIME_ZONE, timeZoneId).apply()
    }

    fun setColorTheme(theme: AppColorTheme) {
        preferences.edit().putString(KEY_COLOR_THEME, theme.storageValue).apply()
    }

    fun setAssetListStyle(style: AssetListStyle) {
        preferences.edit().putString(KEY_ASSET_LIST_STYLE, style.storageValue).apply()
    }

    fun loadFiatRates(): Map<String, Double> =
        FiatCurrency.entries.associate { currency ->
            val cached = preferences.getFloat("$KEY_FIAT_RATE_PREFIX${currency.code}", 0f).toDouble()
            currency.code to if (cached > 0) cached else currency.fallbackUsdRate
        }

    fun saveFiatRates(rates: Map<String, Double>) {
        val editor = preferences.edit()
        rates.forEach { (code, rate) ->
            if (rate > 0) editor.putFloat("$KEY_FIAT_RATE_PREFIX$code", rate.toFloat())
        }
        editor.apply()
    }

    companion object {
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_HIDE_BALANCES = "hide_balances"
        private const val KEY_AUTO_LOCK_DURATION = "auto_lock_duration"
        private const val KEY_ENABLED_NETWORKS = "enabled_networks"
        private const val KEY_FAVORITE_SYMBOLS = "favorite_symbols"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_TIME_ZONE = "time_zone"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_ASSET_LIST_STYLE = "asset_list_style"
        private const val KEY_FIAT_RATE_PREFIX = "fiat_rate_usd_"
    }
}
