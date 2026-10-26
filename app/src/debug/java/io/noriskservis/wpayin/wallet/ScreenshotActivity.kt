package io.noriskservis.wpayin.wallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.AppSettings
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.WalletAccount
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.model.WalletSnapshot
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.ui.screens.AppLockScreen
import io.noriskservis.wpayin.wallet.ui.screens.MainTabsScreen
import io.noriskservis.wpayin.wallet.ui.screens.OnboardingScreen
import io.noriskservis.wpayin.wallet.ui.screens.ReceiveScreen
import io.noriskservis.wpayin.wallet.ui.screens.SendScreen
import io.noriskservis.wpayin.wallet.ui.screens.ImportWalletScreen
import io.noriskservis.wpayin.wallet.ui.screens.WelcomeScreen
import io.noriskservis.wpayin.wallet.ui.screens.AllAssetsScreen
import io.noriskservis.wpayin.wallet.ui.screens.AssetDetailScreen
import io.noriskservis.wpayin.wallet.ui.screens.AboutScreen
import io.noriskservis.wpayin.wallet.ui.screens.HelpCenterScreen
import io.noriskservis.wpayin.wallet.ui.screens.P2PScreen
import io.noriskservis.wpayin.wallet.ui.components.LoadingScreen
import io.noriskservis.wpayin.wallet.ui.theme.WpayinTheme
import java.time.Instant

/** Debug-only host for deterministic marketing and documentation screenshots. */
class ScreenshotActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val initialState = screenshotState()
        setContent {
            var state by remember { mutableStateOf(initialState) }
            WpayinTheme(appColorTheme = state.settings.colorTheme) {
                var activeScreen by remember {
                    mutableStateOf(intent.getStringExtra(EXTRA_SCREEN) ?: SCREEN_MAIN)
                }
                var initialTab by remember { mutableStateOf(intent.getIntExtra(EXTRA_TAB, 0)) }
                var initialSwapAssetId by remember {
                    mutableStateOf(
                        intent.getStringExtra(EXTRA_ASSET_ID).takeIf {
                            intent.getStringExtra(EXTRA_SCREEN) == SCREEN_MAIN &&
                                intent.getIntExtra(EXTRA_TAB, 0) == 1
                        },
                    )
                }
                var selectedAssetId by remember {
                    mutableStateOf(
                        intent.getStringExtra(EXTRA_ASSET_ID)
                            ?: state.wallet?.assets?.firstOrNull()?.id,
                    )
                }
                val setFavorite: (String, Boolean) -> Unit = { symbol, favorite ->
                    val normalized = symbol.uppercase()
                    val favorites = state.settings.favoriteSymbols
                    state = state.copy(
                        settings = state.settings.copy(
                            favoriteSymbols = if (favorite) {
                                favorites + normalized
                            } else {
                                favorites - normalized
                            },
                        ),
                    )
                }

                when (activeScreen) {
                    SCREEN_LOADING -> LoadingScreen()
                    SCREEN_ONBOARDING -> OnboardingScreen(onComplete = {})
                    SCREEN_WELCOME -> WelcomeScreen(
                        productionWalletEnabled = true,
                        onCreate = {},
                        onImport = {},
                    )
                    SCREEN_RECEIVE -> ReceiveScreen(
                        requireNotNull(state.wallet),
                        onBack = { activeScreen = SCREEN_MAIN },
                    )
                    SCREEN_SEND -> SendScreen(
                        state = state,
                        onAuthenticate = { it(Result.success(Unit)) },
                        onPrepare = { _, _, _, callback ->
                            callback(Result.failure(IllegalStateException("Screenshot preview")))
                        },
                        onSend = { _, callback ->
                            callback(Result.failure(IllegalStateException("Screenshot preview")))
                        },
                        onBack = { activeScreen = SCREEN_MAIN },
                        onFinished = { activeScreen = SCREEN_MAIN },
                    )
                    SCREEN_LOCK -> AppLockScreen(onUnlock = { callback -> callback(Result.success(Unit)) })
                    SCREEN_HELP -> HelpCenterScreen(onBack = {})
                    SCREEN_ABOUT -> AboutScreen(onBack = {})
                    SCREEN_IMPORT -> ImportWalletScreen(
                        onImportMnemonic = { _, callback -> callback(false) },
                        onImportPrivateKey = { _, callback -> callback(false) },
                        onBack = {},
                        onFinished = {},
                    )
                    SCREEN_ALL_ASSETS -> AllAssetsScreen(
                        state = state,
                        onAddToken = {},
                        onOpenAsset = {
                            selectedAssetId = it.id
                            activeScreen = SCREEN_ASSET_DETAIL
                        },
                        onToggleFavorite = setFavorite,
                        onBack = { activeScreen = SCREEN_MAIN },
                    )
                    SCREEN_ASSET_DETAIL -> {
                        val asset = state.wallet?.assets?.firstOrNull { it.id == selectedAssetId }
                        if (asset != null) {
                            AssetDetailScreen(
                                state = state,
                                asset = asset,
                                isFavorite = asset.symbol.uppercase() in state.settings.favoriteSymbols,
                                onToggleFavorite = {
                                    setFavorite(
                                        asset.symbol,
                                        asset.symbol.uppercase() !in state.settings.favoriteSymbols,
                                    )
                                },
                                onSend = { activeScreen = SCREEN_SEND },
                                onReceive = { activeScreen = SCREEN_RECEIVE },
                                onSwap = {
                                    initialSwapAssetId = asset.id
                                    initialTab = 1
                                    activeScreen = SCREEN_MAIN
                                },
                                onBack = { activeScreen = SCREEN_MAIN },
                            )
                        }
                    }
                    SCREEN_P2P -> P2PScreen(
                        state = state,
                        onBack = { activeScreen = SCREEN_MAIN },
                    )
                    else -> MainTabsScreen(
                        state = state,
                        onRefresh = {},
                        onToggleBalances = {
                            state = state.copy(settings = state.settings.copy(hideBalances = it))
                        },
                        onReceive = { activeScreen = SCREEN_RECEIVE },
                        onSend = { activeScreen = SCREEN_SEND },
                        onAddToken = {},
                        onLockWallet = {},
                        onSetAutoLock = {
                            state = state.copy(
                                settings = state.settings.copy(autoLockDuration = it),
                            )
                        },
                        onDeleteWallet = {},
                        onNetworkEnabled = { blockchain, enabled ->
                            val current = state.settings.enabledNetworks
                            state = state.copy(
                                settings = state.settings.copy(
                                    enabledNetworks = if (enabled) {
                                        current + blockchain
                                    } else {
                                        current - blockchain
                                    },
                                ),
                            )
                        },
                        onOpenP2P = { activeScreen = SCREEN_P2P },
                        onOpenAllAssets = { activeScreen = SCREEN_ALL_ASSETS },
                        onOpenAsset = {
                            selectedAssetId = it.id
                            activeScreen = SCREEN_ASSET_DETAIL
                        },
                        onToggleFavorite = setFavorite,
                        onSetCurrency = {
                            state = state.copy(settings = state.settings.copy(currencyCode = it))
                        },
                        onSetNotifications = {
                            state = state.copy(
                                settings = state.settings.copy(notificationsEnabled = it),
                            )
                        },
                        onSetTimeZone = {
                            state = state.copy(settings = state.settings.copy(timeZoneId = it))
                        },
                        onSetColorTheme = {
                            state = state.copy(settings = state.settings.copy(colorTheme = it))
                        },
                        onSetAssetListStyle = {
                            state = state.copy(settings = state.settings.copy(assetListStyle = it))
                        },
                        initialSwapAssetId = initialSwapAssetId,
                        initialTab = initialTab,
                        onInitialNavigationConsumed = {
                            initialSwapAssetId = null
                            initialTab = 0
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_TAB = "tab"
        const val EXTRA_ASSET_ID = "asset_id"
        const val SCREEN_LOADING = "loading"
        const val SCREEN_ONBOARDING = "onboarding"
        const val SCREEN_WELCOME = "welcome"
        const val SCREEN_RECEIVE = "receive"
        const val SCREEN_SEND = "send"
        const val SCREEN_LOCK = "lock"
        const val SCREEN_IMPORT = "import"
        const val SCREEN_MAIN = "main"
        const val SCREEN_ALL_ASSETS = "all_assets"
        const val SCREEN_ASSET_DETAIL = "asset_detail"
        const val SCREEN_P2P = "p2p"
        const val SCREEN_HELP = "help"
        const val SCREEN_ABOUT = "about"
    }
}

private fun screenshotState(): WalletState {
    val accounts = listOf(
        WalletAccount(Blockchain.BITCOIN, "bc1qumwjg8danv2vm29lp5swdux4r60ezptzz7ce85"),
        WalletAccount(Blockchain.ETHEREUM, "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"),
        WalletAccount(Blockchain.SOLANA, "4jpwTqt1qZoR7u6u639z2AngYFGN3nakvKhowcnRZDEC"),
        WalletAccount(Blockchain.BNB_CHAIN, "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"),
        WalletAccount(Blockchain.POLYGON, "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"),
        WalletAccount(Blockchain.ARBITRUM, "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"),
        WalletAccount(Blockchain.OPTIMISM, "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"),
        WalletAccount(Blockchain.AVALANCHE, "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"),
        WalletAccount(Blockchain.BASE, "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"),
    )
    val assets = listOf(
        Asset(
            id = "ETHEREUM:native",
            name = "Ethereum",
            symbol = "ETH",
            blockchain = Blockchain.ETHEREUM,
            balance = 2.4728,
            priceUsd = 3_385.42,
        ),
        Asset(
            id = "BITCOIN:native",
            name = "Bitcoin",
            symbol = "BTC",
            blockchain = Blockchain.BITCOIN,
            balance = 0.08426,
            priceUsd = 118_420.18,
        ),
        Asset(
            id = "SOLANA:native",
            name = "Solana",
            symbol = "SOL",
            blockchain = Blockchain.SOLANA,
            balance = 38.421,
            priceUsd = 181.64,
        ),
        Asset(
            id = "ETHEREUM:usdc",
            name = "USD Coin",
            symbol = "USDC",
            blockchain = Blockchain.ETHEREUM,
            balance = 4_280.55,
            priceUsd = 1.0,
            isNative = false,
            contractAddress = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            decimals = 6,
            coinGeckoId = "usd-coin",
        ),
        Asset(
            id = "BNB_CHAIN:native",
            name = "BNB Chain",
            symbol = "BNB",
            blockchain = Blockchain.BNB_CHAIN,
            balance = 6.315,
            priceUsd = 681.27,
        ),
        Asset(
            id = "ARBITRUM:native",
            name = "Arbitrum",
            symbol = "ETH",
            blockchain = Blockchain.ARBITRUM,
            balance = 0.782,
            priceUsd = 3_385.42,
        ),
        Asset(
            id = "BASE:native",
            name = "Base",
            symbol = "ETH",
            blockchain = Blockchain.BASE,
            balance = 0.194,
            priceUsd = 3_385.42,
        ),
        Asset(
            id = "BASE:usdc",
            name = "USD Coin",
            symbol = "USDC",
            blockchain = Blockchain.BASE,
            balance = 820.0,
            priceUsd = 1.0,
            isNative = false,
            contractAddress = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
            decimals = 6,
            coinGeckoId = "usd-coin",
        ),
    )
    val transactions = listOf(
        WalletTransaction(
            hash = "0x" + "a8".repeat(32),
            blockchain = Blockchain.ETHEREUM,
            type = TransactionType.RECEIVE,
            status = TransactionStatus.CONFIRMED,
            amount = 1.25,
            symbol = "ETH",
            timestamp = Instant.parse("2026-07-16T10:42:00Z"),
            from = "0x3535353535353535353535353535353535353535",
            to = accounts[1].address,
            fee = 0.00042,
        ),
        WalletTransaction(
            hash = "5n" + "P7".repeat(31),
            blockchain = Blockchain.SOLANA,
            type = TransactionType.SEND,
            status = TransactionStatus.CONFIRMED,
            amount = 4.2,
            symbol = "SOL",
            timestamp = Instant.parse("2026-07-15T16:18:00Z"),
            from = accounts[2].address,
            to = "EN2sCsJ1WDV8UFqsiTXHcUPUxQ4juE71eCknHYYMifkd",
            fee = 0.000005,
        ),
        WalletTransaction(
            hash = "7c".repeat(32),
            blockchain = Blockchain.BITCOIN,
            type = TransactionType.RECEIVE,
            status = TransactionStatus.CONFIRMED,
            amount = 0.0125,
            symbol = "BTC",
            timestamp = Instant.parse("2026-07-14T08:05:00Z"),
            from = "bc1q0exampleaddressforpreviewonly000000000",
            to = accounts[0].address,
            fee = 0.0000014,
        ),
    )
    return WalletState(
        isInitializing = false,
        isProductionWalletEnabled = false,
        settings = AppSettings(
            onboardingComplete = true,
            enabledNetworks = Blockchain.entries.toSet(),
        ),
        wallet = WalletSnapshot(
            name = "Main Wallet",
            kind = WalletKind.MNEMONIC,
            accounts = accounts,
            assets = assets,
            lastUpdated = Instant.parse("2026-07-16T10:45:00Z"),
        ),
        transactions = transactions,
    )
}
