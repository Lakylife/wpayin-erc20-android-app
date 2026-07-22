package io.noriskservis.wpayin.wallet.core

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.SignedBitcoinTransaction
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.WalletKind

/**
 * Defers the real engine (and its ~95 MB Trust Wallet Core native library)
 * until first use so Application/Activity startup never blocks the main
 * thread. AppContainer warms it up from a background thread right away.
 */
class LazyWalletEngine(factory: () -> WalletEngine) : WalletEngine {
    private val delegate: WalletEngine by lazy(factory)

    /** Forces the native library to load; call from a background thread. */
    fun warmUp() {
        delegate.isProductionReady
    }

    override val isProductionReady: Boolean get() = delegate.isProductionReady

    override fun generateMnemonic(): String = delegate.generateMnemonic()

    override fun validateMnemonic(mnemonic: String): Boolean = delegate.validateMnemonic(mnemonic)

    override fun deriveAddresses(mnemonic: String): Map<Blockchain, String> =
        delegate.deriveAddresses(mnemonic)

    override fun validatePrivateKey(privateKey: String): Boolean =
        delegate.validatePrivateKey(privateKey)

    override fun deriveEvmAddress(privateKey: String): String = delegate.deriveEvmAddress(privateKey)

    override fun exportPrivateKeys(
        walletKind: WalletKind,
        walletSecret: String,
        blockchains: Set<Blockchain>,
    ): Map<Blockchain, String> = delegate.exportPrivateKeys(walletKind, walletSecret, blockchains)

    override fun validateAddress(blockchain: Blockchain, address: String): Boolean =
        delegate.validateAddress(blockchain, address)

    override fun keccak256(data: ByteArray): ByteArray = delegate.keccak256(data)

    override fun signEvmDigest(
        walletKind: WalletKind,
        walletSecret: String,
        digest: ByteArray,
    ): ByteArray = delegate.signEvmDigest(walletKind, walletSecret, digest)

    override fun signEvmTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: EvmTransferDraft,
    ): String = delegate.signEvmTransaction(walletKind, walletSecret, draft)

    override fun signSolanaTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: SolanaTransferDraft,
    ): String = delegate.signSolanaTransaction(walletKind, walletSecret, draft)

    override fun signBitcoinTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: BitcoinTransferDraft,
    ): SignedBitcoinTransaction = delegate.signBitcoinTransaction(walletKind, walletSecret, draft)
}
