package io.noriskservis.wpayin.wallet.model

import java.time.Instant
import java.math.BigInteger
import java.util.UUID

enum class WalletKind { MNEMONIC, PRIVATE_KEY }

data class WalletAccount(
    val blockchain: Blockchain,
    val address: String,
)

data class Asset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val symbol: String,
    val blockchain: Blockchain,
    val balance: Double,
    val priceUsd: Double,
    val isNative: Boolean = true,
    val contractAddress: String? = null,
    val decimals: Int = blockchain.decimals,
    val coinGeckoId: String = blockchain.coinGeckoId,
) {
    val valueUsd: Double
        get() = balance * priceUsd
}

data class WalletSnapshot(
    val name: String = "Main Wallet",
    val kind: WalletKind,
    val accounts: List<WalletAccount>,
    val assets: List<Asset> = emptyList(),
    val lastUpdated: Instant? = null,
) {
    val totalValueUsd: Double
        get() = assets.sumOf(Asset::valueUsd)

    fun account(blockchain: Blockchain): WalletAccount? =
        accounts.firstOrNull { it.blockchain == blockchain }
}

enum class TransactionType { SEND, RECEIVE, SWAP, BRIDGE }

enum class TransactionStatus { PENDING, CONFIRMED, FAILED }

data class WalletTransaction(
    val hash: String,
    val blockchain: Blockchain,
    val type: TransactionType,
    val status: TransactionStatus,
    val amount: Double,
    val symbol: String,
    val timestamp: Instant,
    val from: String,
    val to: String,
    val fee: Double? = null,
    val blockNumber: String? = null,
    val explorerUrl: String = blockchain.transactionExplorerUrl(hash),
)

sealed interface TransferDraft {
    val blockchain: Blockchain
    val from: String
    val to: String
    val symbol: String
    val amount: String
    val maximumNetworkFee: Double
    val plannedAt: Instant
}

data class EvmTransferDraft(
    override val blockchain: Blockchain,
    override val from: String,
    override val to: String,
    override val symbol: String,
    override val amount: String,
    val amountAtomic: BigInteger,
    val contractAddress: String?,
    val transactionTo: String,
    val valueHex: String,
    val dataHex: String,
    val nonce: BigInteger,
    val gasLimit: BigInteger,
    val gasPriceWei: BigInteger,
    override val plannedAt: Instant = Instant.now(),
) : TransferDraft {
    val maximumFeeWei: BigInteger
        get() = gasLimit.multiply(gasPriceWei)

    val maximumFeeNative: Double
        get() = maximumFeeWei.toBigDecimal().movePointLeft(blockchain.decimals).toDouble()

    override val maximumNetworkFee: Double
        get() = maximumFeeNative
}

data class SolanaTransferDraft(
    override val blockchain: Blockchain = Blockchain.SOLANA,
    override val from: String,
    override val to: String,
    override val symbol: String = "SOL",
    override val amount: String,
    val lamports: Long,
    val recentBlockhash: String,
    val lastValidBlockHeight: Long,
    val feeLamports: Long,
    override val plannedAt: Instant = Instant.now(),
) : TransferDraft {
    override val maximumNetworkFee: Double
        get() = feeLamports.toBigDecimal().movePointLeft(Blockchain.SOLANA.decimals).toDouble()
}

data class BitcoinUtxo(
    val txid: String,
    val outputIndex: Int,
    val valueSatoshis: Long,
)

data class BitcoinTransferDraft(
    override val blockchain: Blockchain = Blockchain.BITCOIN,
    override val from: String,
    override val to: String,
    override val symbol: String = "BTC",
    override val amount: String,
    val amountSatoshis: Long,
    val feeRateSatsPerVByte: Long,
    val feeSatoshis: Long,
    val selectedUtxos: List<BitcoinUtxo>,
    override val plannedAt: Instant = Instant.now(),
) : TransferDraft {
    override val maximumNetworkFee: Double
        get() = feeSatoshis.toBigDecimal().movePointLeft(Blockchain.BITCOIN.decimals).toDouble()
}

data class SignedBitcoinTransaction(
    val rawHex: String,
    val feeSatoshis: Long,
)

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val currencyCode: String = "USD",
    val languageTag: String = "system",
    val hideBalances: Boolean = false,
)
