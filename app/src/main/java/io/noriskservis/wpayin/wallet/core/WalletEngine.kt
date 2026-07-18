package io.noriskservis.wpayin.wallet.core

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.SignedBitcoinTransaction

/** Isolates Wallet Core JNI from UI, storage, and networking code. */
interface WalletEngine {
    val isProductionReady: Boolean

    fun generateMnemonic(): String

    fun validateMnemonic(mnemonic: String): Boolean

    fun deriveAddresses(mnemonic: String): Map<Blockchain, String>

    fun validatePrivateKey(privateKey: String): Boolean

    fun deriveEvmAddress(privateKey: String): String

    fun validateAddress(blockchain: Blockchain, address: String): Boolean

    /** Returns a 0x-prefixed raw signed EVM transaction. */
    fun signEvmTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: EvmTransferDraft,
    ): String

    /** Returns Wallet Core's Base58-encoded signed Solana transaction. */
    fun signSolanaTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: SolanaTransferDraft,
    ): String

    fun signBitcoinTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: BitcoinTransferDraft,
    ): SignedBitcoinTransaction
}
