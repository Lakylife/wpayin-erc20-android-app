package io.noriskservis.wpayin.wallet.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.noriskservis.wpayin.wallet.data.WalletRepository
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.TransferDraft
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.QuintiumNetwork
import io.noriskservis.wpayin.wallet.model.KnownToken
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.model.SavedAddress
import io.noriskservis.wpayin.wallet.model.ExchangeQuote
import io.noriskservis.wpayin.wallet.model.AutoLockDuration
import io.noriskservis.wpayin.wallet.model.AppColorTheme
import io.noriskservis.wpayin.wallet.model.AssetListStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: WalletRepository,
) : ViewModel() {
    val state: StateFlow<WalletState> = repository.state

    private val mutablePendingMnemonic = MutableStateFlow<String?>(null)
    val pendingMnemonic: StateFlow<String?> = mutablePendingMnemonic.asStateFlow()
    private val mutableSessionUnlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = mutableSessionUnlocked.asStateFlow()
    private var backgroundedAtElapsedRealtime: Long? = null

    init {
        viewModelScope.launch { repository.initialize() }
    }

    fun completeOnboarding() = repository.completeOnboarding()

    fun generateRecoveryPhrase(onComplete: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.generateRecoveryPhrase()
            result.onSuccess { mutablePendingMnemonic.value = it }
            onComplete(result)
        }
    }

    fun confirmGeneratedWallet(onComplete: (Boolean) -> Unit) {
        val mnemonic = mutablePendingMnemonic.value ?: return onComplete(false)
        viewModelScope.launch {
            val result = repository.createWallet(mnemonic)
            if (result.isSuccess) {
                mutablePendingMnemonic.value = null
                mutableSessionUnlocked.value = true
            }
            onComplete(result.isSuccess)
        }
    }

    fun discardPendingMnemonic() {
        mutablePendingMnemonic.value = null
    }

    fun importMnemonic(mnemonic: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.createWallet(mnemonic).isSuccess
            if (success) mutableSessionUnlocked.value = true
            onComplete(success)
        }
    }

    fun importPrivateKey(privateKey: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importPrivateKey(privateKey).isSuccess
            if (success) mutableSessionUnlocked.value = true
            onComplete(success)
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun prepareTransfer(
        assetId: String,
        recipient: String,
        amount: String,
        onComplete: (Result<TransferDraft>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.prepareTransfer(assetId, recipient, amount))
        }
    }

    fun sendTransfer(
        draft: TransferDraft,
        onComplete: (Result<WalletTransaction>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.sendTransfer(draft))
        }
    }

    fun prepareExchange(
        fromAssetId: String,
        toAssetId: String,
        amount: String,
        onComplete: (Result<ExchangeQuote>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.prepareExchange(fromAssetId, toAssetId, amount))
        }
    }

    fun executeExchange(
        quote: ExchangeQuote,
        onComplete: (Result<WalletTransaction>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.executeExchange(quote))
        }
    }

    fun addCustomToken(
        blockchain: Blockchain,
        contractAddress: String,
        onComplete: (Result<KnownToken>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.addCustomToken(blockchain, contractAddress))
        }
    }

    fun removeCustomToken(token: KnownToken) = repository.removeCustomToken(token)

    fun setBalancesHidden(hidden: Boolean) = repository.setBalancesHidden(hidden)

    fun setAutoLockDuration(duration: AutoLockDuration) = repository.setAutoLockDuration(duration)

    fun setCurrency(code: String) = repository.setCurrency(code)

    fun setFavorite(symbol: String, favorite: Boolean) = repository.setFavorite(symbol, favorite)

    fun setNotificationsEnabled(enabled: Boolean) = repository.setNotificationsEnabled(enabled)

    fun setTimeZone(timeZoneId: String) = repository.setTimeZone(timeZoneId)

    fun setColorTheme(theme: AppColorTheme) = repository.setColorTheme(theme)

    fun setAssetListStyle(style: AssetListStyle) = repository.setAssetListStyle(style)

    fun setNetworkEnabled(
        blockchain: Blockchain,
        enabled: Boolean,
        onComplete: (Result<Unit>) -> Unit = {},
    ) {
        viewModelScope.launch { onComplete(repository.setNetworkEnabled(blockchain, enabled)) }
    }

    fun setQuintiumSettings(
        network: QuintiumNetwork,
        rpcUrl: String,
        explorerUrl: String,
        onComplete: (Result<Unit>) -> Unit = {},
    ) {
        viewModelScope.launch { onComplete(repository.setQuintiumSettings(network, rpcUrl, explorerUrl)) }
    }

    fun revealWalletSecret() = repository.revealWalletSecret()

    fun revealPrivateKeys() = repository.revealPrivateKeys()

    val isP2PBoardConfigured: Boolean
        get() = repository.isP2PBoardConfigured

    fun loadP2PListings(
        onComplete: (Result<List<io.noriskservis.wpayin.wallet.data.P2PListing>>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.loadP2PListings())
        }
    }

    fun createP2POffer(
        sellAssetId: String,
        receiveTokenAddress: String,
        receiveTokenSymbol: String,
        receiveTokenDecimals: Int,
        sellAmount: String,
        receiveAmount: String,
        validForSeconds: Long,
        onComplete: (Result<String>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(
                repository.createP2POffer(
                    sellAssetId = sellAssetId,
                    receiveTokenAddress = receiveTokenAddress,
                    receiveTokenSymbol = receiveTokenSymbol,
                    receiveTokenDecimals = receiveTokenDecimals,
                    sellAmount = sellAmount,
                    receiveAmount = receiveAmount,
                    validForSeconds = validForSeconds,
                ),
            )
        }
    }

    fun prepareP2PAccept(
        payload: String,
        onComplete: (Result<io.noriskservis.wpayin.wallet.data.P2PAcceptPlan>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.prepareP2PAccept(payload))
        }
    }

    fun executeP2PAccept(
        plan: io.noriskservis.wpayin.wallet.data.P2PAcceptPlan,
        onComplete: (Result<WalletTransaction>) -> Unit,
    ) {
        viewModelScope.launch {
            onComplete(repository.executeP2PAccept(plan))
        }
    }

    fun addSavedAddress(name: String, address: String): Result<SavedAddress> =
        repository.addSavedAddress(name, address)

    fun removeSavedAddress(id: String) = repository.removeSavedAddress(id)

    fun clearError() = repository.clearError()

    fun unlockSession() {
        mutableSessionUnlocked.value = true
    }

    fun lockSession() {
        mutableSessionUnlocked.value = false
    }

    fun onAppBackgrounded() {
        backgroundedAtElapsedRealtime = SystemClock.elapsedRealtime()
        if (state.value.settings.autoLockDuration == AutoLockDuration.IMMEDIATELY) {
            lockSession()
        }
    }

    fun onAppForegrounded() {
        val backgroundedAt = backgroundedAtElapsedRealtime ?: return
        backgroundedAtElapsedRealtime = null
        val timeout = state.value.settings.autoLockDuration.timeoutMillis ?: return
        if (SystemClock.elapsedRealtime() - backgroundedAt >= timeout) {
            lockSession()
        }
    }

    fun deleteWallet() {
        repository.deleteWallet()
        mutableSessionUnlocked.value = false
    }

    class Factory(private val repository: WalletRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(repository) as T
        }
    }
}
