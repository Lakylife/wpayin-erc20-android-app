package io.noriskservis.wpayin.wallet.core

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.SignedBitcoinTransaction

object WalletEngineProvider {
    fun create(): WalletEngine = PreviewWalletEngine
}

private object PreviewWalletEngine : WalletEngine {
    override val isProductionReady = false

    override fun generateMnemonic(): String = unavailable()

    override fun validateMnemonic(mnemonic: String): Boolean = false

    override fun deriveAddresses(mnemonic: String): Map<Blockchain, String> = unavailable()

    override fun validatePrivateKey(privateKey: String): Boolean = false

    override fun deriveEvmAddress(privateKey: String): String = unavailable()

    override fun exportPrivateKeys(
        walletKind: WalletKind,
        walletSecret: String,
        blockchains: Set<Blockchain>,
    ): Map<Blockchain, String> = unavailable()

    override fun validateAddress(blockchain: Blockchain, address: String): Boolean = false

    override fun keccak256(data: ByteArray): ByteArray = unavailable()

    override fun signEvmDigest(
        walletKind: WalletKind,
        walletSecret: String,
        digest: ByteArray,
    ): ByteArray = unavailable()

    override fun signEvmTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: EvmTransferDraft,
    ): String = unavailable()

    override fun signBitcoinTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: BitcoinTransferDraft,
    ): SignedBitcoinTransaction = unavailable()

    override fun signSolanaTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: SolanaTransferDraft,
    ): String = unavailable()

    private fun unavailable(): Nothing = throw WalletEngineUnavailableException()
}

class WalletEngineUnavailableException : IllegalStateException(
    "Wallet Core is disabled in the preview build. Select the walletCore build variant.",
)
