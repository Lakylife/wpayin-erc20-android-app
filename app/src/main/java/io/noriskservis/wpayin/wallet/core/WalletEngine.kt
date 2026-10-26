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

    /** Exports per-network private keys only after the UI has authenticated the user. */
    fun exportPrivateKeys(
        walletKind: WalletKind,
        walletSecret: String,
        blockchains: Set<Blockchain>,
    ): Map<Blockchain, String>

    fun validateAddress(blockchain: Blockchain, address: String): Boolean

    /** keccak-256 (EVM variant), used for EIP-712 hashing. */
    fun keccak256(data: ByteArray): ByteArray

    /**
     * Signs a 32-byte digest with the wallet's EVM secp256k1 key.
     * Returns 65 bytes: r (32) ‖ s (32) ‖ recovery id (0/1).
     */
    fun signEvmDigest(
        walletKind: WalletKind,
        walletSecret: String,
        digest: ByteArray,
    ): ByteArray

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
