package io.noriskservis.wpayin.wallet.model

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.Base64
import org.json.JSONObject

/**
 * A signed, shareable P2P trade offer (AirSwap OrderERC20 with senderWallet = 0),
 * wire-compatible with the payloads the iOS app creates ("WPAYIN-P2P:" + base64 JSON).
 */
data class P2POffer(
    val version: Int,
    val chainId: Long,
    val nonce: BigInteger,
    val expiry: Long,
    val signerWallet: String,
    val signerToken: String,
    val signerAmount: BigInteger,
    val signerSymbol: String,
    val signerDecimals: Int,
    val senderToken: String,
    val senderAmount: BigInteger,
    val senderSymbol: String,
    val senderDecimals: Int,
    val protocolFeeBps: Int,
    val sigV: Int,
    val sigR: String,
    val sigS: String,
) {
    val blockchain: Blockchain?
        get() = Blockchain.entries.firstOrNull { it.chainId == chainId }

    val isExpired: Boolean
        get() = Instant.now().epochSecond >= expiry

    val signerAmountDecimal: BigDecimal
        get() = signerAmount.toBigDecimal().movePointLeft(signerDecimals)

    val senderAmountDecimal: BigDecimal
        get() = senderAmount.toBigDecimal().movePointLeft(senderDecimals)

    /** Encodes the offer as the shareable payload the iOS app understands. */
    fun encodePayload(): String {
        val json = JSONObject()
            .put("version", version)
            .put("chainId", chainId)
            .put("nonce", nonce.toString())
            .put("expiry", expiry)
            .put("signerWallet", signerWallet)
            .put("signerToken", signerToken)
            .put("signerAmount", signerAmount.toString())
            .put("signerSymbol", signerSymbol)
            .put("signerDecimals", signerDecimals)
            .put("senderToken", senderToken)
            .put("senderAmount", senderAmount.toString())
            .put("senderSymbol", senderSymbol)
            .put("senderDecimals", senderDecimals)
            .put("protocolFeeBps", protocolFeeBps)
            .put("sigV", sigV)
            .put("sigR", sigR)
            .put("sigS", sigS)
        return PAYLOAD_PREFIX +
            Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val PAYLOAD_PREFIX = "WPAYIN-P2P:"

        fun parse(payload: String): P2POffer {
            val trimmed = payload.trim()
            require(trimmed.startsWith(PAYLOAD_PREFIX)) { "This is not a Wpayin P2P offer" }
            val json = runCatching {
                JSONObject(String(Base64.getDecoder().decode(trimmed.removePrefix(PAYLOAD_PREFIX)), Charsets.UTF_8))
            }.getOrElse { error("The offer payload is damaged") }

            val offer = P2POffer(
                version = json.optInt("version", 1),
                chainId = json.getLong("chainId"),
                nonce = json.getString("nonce").toUint256("nonce"),
                expiry = json.getDouble("expiry").toLong(),
                signerWallet = json.getString("signerWallet").requireEvmAddress("seller wallet"),
                signerToken = json.getString("signerToken").requireEvmAddress("sold token"),
                signerAmount = json.getString("signerAmount").toUint256("sold amount"),
                signerSymbol = json.getString("signerSymbol").take(20),
                signerDecimals = json.getInt("signerDecimals"),
                senderToken = json.getString("senderToken").requireEvmAddress("payment token"),
                senderAmount = json.getString("senderAmount").toUint256("payment amount"),
                senderSymbol = json.getString("senderSymbol").take(20),
                senderDecimals = json.getInt("senderDecimals"),
                protocolFeeBps = json.getInt("protocolFeeBps"),
                sigV = json.getInt("sigV"),
                sigR = json.getString("sigR").requireSignatureWord("signature r"),
                sigS = json.getString("sigS").requireSignatureWord("signature s"),
            )
            require(offer.signerAmount.signum() > 0 && offer.senderAmount.signum() > 0) {
                "The offer amounts are invalid"
            }
            require(offer.signerDecimals in 0..36 && offer.senderDecimals in 0..36) {
                "The offer token decimals are invalid"
            }
            require(offer.protocolFeeBps in 0..1_000) { "The offer protocol fee is invalid" }
            require(offer.sigV == 27 || offer.sigV == 28) { "The offer signature is invalid" }
            return offer
        }

        private fun String.toUint256(label: String): BigInteger {
            val value = runCatching { BigInteger(trim()) }.getOrElse { error("The offer $label is invalid") }
            require(value.signum() >= 0 && value.bitLength() <= 256) { "The offer $label is invalid" }
            return value
        }

        private fun String.requireEvmAddress(label: String): String {
            require(Regex("^0x[0-9a-fA-F]{40}$").matches(trim())) { "The offer $label is invalid" }
            return trim().lowercase()
        }

        private fun String.requireSignatureWord(label: String): String {
            require(Regex("^0x[0-9a-fA-F]{1,64}$").matches(trim())) { "The offer $label is invalid" }
            return trim().lowercase()
        }
    }
}
