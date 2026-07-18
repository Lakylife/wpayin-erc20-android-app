package io.noriskservis.wpayin.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.noriskservis.wpayin.wallet.data.WalletRepository
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.TransferDraft
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.KnownToken
import io.noriskservis.wpayin.wallet.model.WalletTransaction
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
    private var backgroundedAtNanos: Long? = null

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

    fun setCurrency(code: String) = repository.setCurrency(code)

    fun clearError() = repository.clearError()

    fun unlockSession() {
        backgroundedAtNanos = null
        mutableSessionUnlocked.value = true
    }

    fun lockSession() {
        mutableSessionUnlocked.value = false
    }

    fun onAppBackgrounded() {
        backgroundedAtNanos = System.nanoTime()
    }

    fun onAppForegrounded() {
        val backgroundedAt = backgroundedAtNanos ?: return
        backgroundedAtNanos = null
        if (System.nanoTime() - backgroundedAt >= AUTO_LOCK_AFTER_NANOS) lockSession()
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

    companion object {
        private const val AUTO_LOCK_AFTER_NANOS = 5L * 60L * 1_000_000_000L
    }
}
