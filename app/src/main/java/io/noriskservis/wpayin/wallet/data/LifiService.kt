package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

data class LifiPreparedQuote(
    val toAmountAtomic: BigInteger,
    val minimumToAmountAtomic: BigInteger,
    val toAmount: String,
    val minimumToAmount: String,
    val provider: String,
    val estimatedDurationSeconds: Long,
    val approvalAddress: String?,
    val transactionTo: String,
    val transactionDataHex: String,
    val transactionValueHex: String,
)

class LifiService(
    private val client: HttpJsonClient = HttpJsonClient(),
) {
    suspend fun quote(
        fromAsset: Asset,
        toAsset: Asset,
        fromAddress: String,
        toAddress: String,
        fromAmountAtomic: BigInteger,
        slippage: Double = 0.005,
    ): LifiPreparedQuote {
        val evmSource = fromAsset.blockchain.isEvm
        val fromChainId = lifiChainId(fromAsset.blockchain)
        val toChainId = lifiChainId(toAsset.blockchain)
        if (evmSource) {
            require(RpcService.isEvmAddress(fromAddress)) { "Swap wallet address is invalid" }
        } else {
            require(fromAsset.blockchain == Blockchain.BITCOIN && fromAsset.isNative) {
                "Only native Bitcoin is supported as a non-EVM source"
            }
            require(fromAddress.isNotBlank()) { "Source address is required" }
        }
        if (toAsset.blockchain.isEvm) {
            require(RpcService.isEvmAddress(toAddress)) { "Swap wallet address is invalid" }
        } else {
            require(toAsset.isNative) { "Bridging to ${toAsset.blockchain.displayName} supports only the native coin" }
            require(toAddress.isNotBlank()) { "Receiving address is required for this destination" }
        }
        require(fromAmountAtomic.signum() > 0 && fromAmountAtomic.bitLength() <= 256) {
            "Swap amount is invalid"
        }
        require(slippage in 0.001..0.05) { "Slippage must be between 0.1% and 5%" }

        val parameters = linkedMapOf(
            "fromChain" to fromChainId.toString(),
            "toChain" to toChainId.toString(),
            "fromToken" to lifiTokenAddress(fromAsset),
            "toToken" to lifiTokenAddress(toAsset),
            "fromAddress" to fromAddress,
            "toAddress" to toAddress,
            "fromAmount" to fromAmountAtomic.toString(),
            "slippage" to slippage.toString(),
        )
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val response = client.get("$QUOTE_URL?$query")
        return parseQuote(response, fromChainId, toAsset.decimals, evmSource)
    }

    internal fun parseQuote(
        response: JSONObject,
        expectedFromChainId: Long,
        toDecimals: Int,
        evmSource: Boolean = true,
    ): LifiPreparedQuote {
        val estimate = response.getJSONObject("estimate")
        val request = response.getJSONObject("transactionRequest")
        val requestChainId = request.opt("chainId")?.toString()?.toLongOrNull()
        check(requestChainId == null || requestChainId == expectedFromChainId) {
            "Quote transaction is for a different network"
        }

        val toAmountAtomic = estimate.getString("toAmount").asPositiveUint256("quoted output")
        val minimumAtomic = estimate.optString("toAmountMin", estimate.getString("toAmount"))
            .asPositiveUint256("minimum output")
        check(minimumAtomic <= toAmountAtomic) { "Quote minimum output is invalid" }

        val transactionTo = request.getString("to")
        val transactionData = if (evmSource) request.getString("data") else request.optString("data", "0x")
        if (evmSource) {
            require(RpcService.isEvmAddress(transactionTo)) { "Quote returned an invalid contract address" }
            require(DATA_PATTERN.matches(transactionData) && transactionData.length in 10..MAX_DATA_HEX_LENGTH) {
                "Quote returned invalid transaction data"
            }
        } else {
            // Bitcoin source: `to` is the LI.FI deposit address, `value` the exact sats to send.
            require(transactionTo.isNotBlank()) { "Quote returned no deposit address" }
        }

        val valueHex = request.opt("value")?.toString().orEmpty().asHexQuantity()
        val approvalAddress = estimate.optString("approvalAddress")
            .takeIf { RpcService.isEvmAddress(it) && !it.equals(NATIVE_TOKEN_ADDRESS, ignoreCase = true) }
        val toolDetails = response.optJSONObject("toolDetails")
        val provider = toolDetails?.optString("name")?.takeIf(String::isNotBlank)
            ?: response.optString("tool").takeIf(String::isNotBlank)
            ?: "LI.FI"
        val duration = estimate.optLong("executionDuration", 0).coerceAtLeast(0)

        return LifiPreparedQuote(
            toAmountAtomic = toAmountAtomic,
            minimumToAmountAtomic = minimumAtomic,
            toAmount = toAmountAtomic.asDecimalString(toDecimals),
            minimumToAmount = minimumAtomic.asDecimalString(toDecimals),
            provider = provider,
            estimatedDurationSeconds = duration,
            approvalAddress = approvalAddress,
            // Base58 Bitcoin deposit addresses are case-sensitive; only EVM addresses normalize.
            transactionTo = if (evmSource) transactionTo.lowercase() else transactionTo.trim(),
            transactionDataHex = transactionData.lowercase(),
            transactionValueHex = valueHex,
        )
    }

    private fun String.asPositiveUint256(label: String): BigInteger {
        val value = runCatching { BigInteger(this) }.getOrElse { error("Quote returned an invalid $label") }
        require(value.signum() > 0 && value.bitLength() <= 256) { "Quote returned an invalid $label" }
        return value
    }

    private fun String.asHexQuantity(): String {
        if (isBlank()) return "0x0"
        val number = if (startsWith("0x", ignoreCase = true)) {
            val normalized = substring(2).ifEmpty { "0" }
            require(normalized.all { it.digitToIntOrNull(16) != null }) { "Quote returned an invalid value" }
            BigInteger(normalized, 16)
        } else {
            runCatching { BigInteger(this) }.getOrElse { error("Quote returned an invalid value") }
        }
        require(number.signum() >= 0 && number.bitLength() <= 256) { "Quote returned an invalid value" }
        return "0x${number.toString(16)}"
    }

    private fun BigInteger.asDecimalString(decimals: Int): String =
        BigDecimal(this).movePointLeft(decimals).stripTrailingZeros().toPlainString()

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    companion object {
        private const val QUOTE_URL = "https://li.quest/v1/quote"
        const val NATIVE_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"
        private val DATA_PATTERN = Regex("^0x(?:[0-9a-fA-F]{2})+$")
        private const val MAX_DATA_HEX_LENGTH = 262_146

        // LI.FI synthetic chain ids for non-EVM destinations (same values the iOS app uses).
        private const val LIFI_BITCOIN_CHAIN_ID = 20_000_000_000_001L
        private const val LIFI_SOLANA_CHAIN_ID = 1_151_111_081_099_710L
        private const val LIFI_BITCOIN_TOKEN = "bitcoin"
        private const val LIFI_SOLANA_NATIVE_MINT = "11111111111111111111111111111111"

        fun lifiChainId(blockchain: Blockchain): Long = blockchain.chainId ?: when (blockchain) {
            Blockchain.BITCOIN -> LIFI_BITCOIN_CHAIN_ID
            Blockchain.SOLANA -> LIFI_SOLANA_CHAIN_ID
            else -> error("${blockchain.displayName} is not supported by LI.FI routing")
        }

        fun lifiTokenAddress(asset: Asset): String = when {
            asset.blockchain == Blockchain.BITCOIN -> LIFI_BITCOIN_TOKEN
            asset.blockchain == Blockchain.SOLANA -> LIFI_SOLANA_NATIVE_MINT
            else -> asset.contractAddress ?: NATIVE_TOKEN_ADDRESS
        }
    }
}
