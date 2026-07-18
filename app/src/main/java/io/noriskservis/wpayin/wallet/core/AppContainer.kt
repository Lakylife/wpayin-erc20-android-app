package io.noriskservis.wpayin.wallet.core

import android.content.Context
import io.noriskservis.wpayin.wallet.data.ExplorerService
import io.noriskservis.wpayin.wallet.data.CustomTokenStore
import io.noriskservis.wpayin.wallet.data.PriceService
import io.noriskservis.wpayin.wallet.data.RpcService
import io.noriskservis.wpayin.wallet.data.SecureWalletStore
import io.noriskservis.wpayin.wallet.data.SettingsStore
import io.noriskservis.wpayin.wallet.data.WalletRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val secureStore = SecureWalletStore(appContext)
    private val settingsStore = SettingsStore(appContext)
    private val rpcService = RpcService()
    private val priceService = PriceService()
    private val explorerService = ExplorerService()
    private val customTokenStore = CustomTokenStore(appContext)
    private val walletEngine = WalletEngineProvider.create()

    val walletRepository = WalletRepository(
        secureStore = secureStore,
        settingsStore = settingsStore,
        walletEngine = walletEngine,
        rpcService = rpcService,
        priceService = priceService,
        explorerService = explorerService,
        customTokenStore = customTokenStore,
    )
}
