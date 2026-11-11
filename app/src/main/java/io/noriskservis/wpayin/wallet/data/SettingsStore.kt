package io.noriskservis.wpayin.wallet.data

import android.content.Context
import io.noriskservis.wpayin.wallet.model.AppSettings
import io.noriskservis.wpayin.wallet.model.AppColorTheme
import io.noriskservis.wpayin.wallet.model.AssetListStyle
import io.noriskservis.wpayin.wallet.model.AutoLockDuration
import io.noriskservis.wpayin.wallet.model.AUTOMATIC_TIME_ZONE_ID
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.FiatCurrency
import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.model.QuintiumConfig
import io.noriskservis.wpayin.wallet.model.QuintiumNetwork

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("wpayin_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        loadQuintium()
        migrateQuintiumNetwork()
        return loadSettings()
    }

    private fun loadSettings(): AppSettings = AppSettings(
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

    /** Applies the saved Quintium network, node and explorer to [QuintiumConfig]. */
    fun loadQuintium() {
        val network = QuintiumNetwork.fromStorage(preferences.getString(KEY_QUINTIUM_NETWORK, null))
        val rpc = QuintiumNetwork.entries.associateWith { item ->
            preferences.getString(quintiumRpcKey(item), null)
                ?.let(QuintiumConfig::clean)
                ?.takeIf(String::isNotEmpty)
                ?: buildValue(if (item.isTestnet) BuildConfig.QUINTIUM_TESTNET_RPC_URL else BuildConfig.QUINTIUM_RPC_URL)
        }
        val explorer = QuintiumNetwork.entries.associateWith { item ->
            preferences.getString(quintiumExplorerKey(item), null)
                ?.let(QuintiumConfig::clean)
                ?.takeIf(String::isNotEmpty)
                ?: buildValue(
                    if (item.isTestnet) BuildConfig.QUINTIUM_TESTNET_EXPLORER_URL else BuildConfig.QUINTIUM_EXPLORER_URL,
                )
        }
        QuintiumConfig.apply(network, rpc, explorer)
    }

    fun saveQuintium(network: QuintiumNetwork, rpcUrl: String, explorerUrl: String) {
        val rpc = QuintiumConfig.clean(rpcUrl)
        val explorer = QuintiumConfig.clean(explorerUrl)
        require(rpc.isEmpty() || QuintiumConfig.isValidUrl(rpc)) {
            "Enter a valid node URL starting with https:// or http://"
        }
        require(explorer.isEmpty() || QuintiumConfig.isValidUrl(explorer)) {
            "Enter a valid explorer URL starting with https:// or http://"
        }
        preferences.edit().apply {
            putString(KEY_QUINTIUM_NETWORK, network.storageValue)
            if (rpc.isEmpty()) remove(quintiumRpcKey(network)) else putString(quintiumRpcKey(network), rpc)
            if (explorer.isEmpty()) {
                remove(quintiumExplorerKey(network))
            } else {
                putString(quintiumExplorerKey(network), explorer)
            }
        }.apply()
        loadQuintium()
    }

    /** Wallets set up before Quintium existed get it switched on once. */
    private fun migrateQuintiumNetwork() {
        if (preferences.getBoolean(KEY_QUINTIUM_ADDED, false)) return
        val saved = preferences.getStringSet(KEY_ENABLED_NETWORKS, null)
        preferences.edit().apply {
            if (saved != null && saved.isNotEmpty()) {
                putStringSet(KEY_ENABLED_NETWORKS, saved + Blockchain.QUINTIUM.name)
            }
            putBoolean(KEY_QUINTIUM_ADDED, true)
        }.apply()
    }

    private fun buildValue(value: String): String =
        QuintiumConfig.clean(value).takeIf(QuintiumConfig::isValidUrl).orEmpty()

    private fun quintiumRpcKey(network: QuintiumNetwork) = "$KEY_QUINTIUM_RPC_PREFIX${network.storageValue}"

    private fun quintiumExplorerKey(network: QuintiumNetwork) =
        "$KEY_QUINTIUM_EXPLORER_PREFIX${network.storageValue}"

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
        private const val KEY_QUINTIUM_NETWORK = "quintium_network"
        private const val KEY_QUINTIUM_RPC_PREFIX = "quintium_rpc_"
        private const val KEY_QUINTIUM_EXPLORER_PREFIX = "quintium_explorer_"
        private const val KEY_QUINTIUM_ADDED = "quintium_network_added"
    }
}
