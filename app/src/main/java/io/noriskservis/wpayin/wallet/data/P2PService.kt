package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.P2POffer
import java.math.BigInteger
import java.time.Instant

/**
 * Buyer-side plan for filling an AirSwap OrderERC20 offer. The offer signature
 * itself is enforced by the contract; the exact fill is dry-run via eth_call
 * before anything is signed, so an invalid offer costs the buyer nothing.
 */
data class P2PAcceptPlan(
    val offer: P2POffer,
    val blockchain: Blockchain,
    val contract: String,
    val takerAddress: String,
    val platformFeeUnits: BigInteger,
    val needsApproval: Boolean,
    val needsApprovalReset: Boolean,
    val estimatedMaxFeeNative: Double,
    val fillCallDataHex: String,
    // Non-zero when part of the payment is auto-wrapped from the native coin (1:1).
    val wrapShortfallUnits: BigInteger = BigInteger.ZERO,
    val requestedAt: Instant = Instant.now(),
)

object P2PService {
    /** AirSwap SwapERC20 v4.3 — same address on every supported chain (no Optimism deployment). */
    private const val SWAP_ERC20_CONTRACT = "0xd82e10b9a4107939e55fcca9b53a9ede6cf2fc46"

    val supportedBlockchains: Set<Blockchain> = setOf(
        Blockchain.ETHEREUM,
        Blockchain.BNB_CHAIN,
        Blockchain.POLYGON,
        Blockchain.ARBITRUM,
        Blockchain.BASE,
        Blockchain.AVALANCHE,
    )

    fun contractFor(blockchain: Blockchain): String? =
        SWAP_ERC20_CONTRACT.takeIf { blockchain in supportedBlockchains }

    // keccak-derived selectors, identical to the iOS P2PTradeService constants.
    private const val SELECTOR_SWAP_ANY_SENDER = "0x3eb1af24"
    private const val SELECTOR_NONCE_USED = "0x1647795e"
    private const val SELECTOR_PROTOCOL_FEE = "0xb0e21e8a"
    private const val SELECTOR_DEPOSIT = "0xd0e30db0"

    /** Canonical wrapped-native tokens; paying with these auto-wraps the shortfall 1:1. */
    private val wrappedNative: Map<Blockchain, Pair<String, String>> = mapOf(
        Blockchain.ETHEREUM to ("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2" to "WETH"),
        Blockchain.ARBITRUM to ("0x82af49447d8a07e3bd95bd0d56f35241523fbab1" to "WETH"),
        Blockchain.BASE to ("0x4200000000000000000000000000000000000006" to "WETH"),
        Blockchain.POLYGON to ("0x0d500b1d8e8ef31e21c99d1db9a6444d3adf1270" to "WMATIC"),
        Blockchain.BNB_CHAIN to ("0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c" to "WBNB"),
        Blockchain.AVALANCHE to ("0xb31f66aa3c1e785363f0875a1b74e27b85fd66c7" to "WAVAX"),
    )

    fun isWrappedNative(blockchain: Blockchain, tokenAddress: String): Boolean =
        wrappedNative[blockchain]?.first.equals(tokenAddress, ignoreCase = true)

    /** The wrapped-native coin as a token descriptor (address, symbol); 18 decimals everywhere. */
    fun wrappedNativeToken(blockchain: Blockchain): Pair<String, String>? = wrappedNative[blockchain]

    fun depositCallData(): String = SELECTOR_DEPOSIT

    // EIP-712 hashes — must exactly match SwapERC20.sol (name SWAP_ERC20, version 4.3);
    // values copied from the audited iOS P2PTradeService.
    private const val DOMAIN_TYPE_HASH = "8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f"
    private const val DOMAIN_NAME_HASH = "53be2722d46649832d0712cbda538f9399a2de2a00cf45739b4874b2169e004c"
    private const val DOMAIN_VERSION_HASH = "1a4a38b58898b90facc89350d9c72c929053b43bc088c91ec178f60fc1f34678"
    private const val ORDER_TYPE_HASH = "c0c0b55c946f3a31595c7c56c2fcfb240731b0e424d96f05eea86f1997ebf0d8"

    /** keccak256(0x1901 ‖ domainSeparator ‖ structHash) per EIP-712. */
    fun orderDigest(
        keccak256: (ByteArray) -> ByteArray,
        chainId: Long,
        contract: String,
        nonce: BigInteger,
        expiry: Long,
        signerWallet: String,
        signerToken: String,
        signerAmount: BigInteger,
        protocolFeeBps: Int,
        senderToken: String,
        senderAmount: BigInteger,
    ): ByteArray {
        val domain = (DOMAIN_TYPE_HASH + DOMAIN_NAME_HASH + DOMAIN_VERSION_HASH +
            uintWord(BigInteger.valueOf(chainId)) + addressWord(contract)).hexToBytes()
        val domainSeparator = keccak256(domain)

        val order = (ORDER_TYPE_HASH +
            uintWord(nonce) +
            uintWord(BigInteger.valueOf(expiry)) +
            addressWord(signerWallet) +
            addressWord(signerToken) +
            uintWord(signerAmount) +
            uintWord(BigInteger.valueOf(protocolFeeBps.toLong())) +
            addressWord("0x0000000000000000000000000000000000000000") + // senderWallet: anyone may fill
            addressWord(senderToken) +
            uintWord(senderAmount)).hexToBytes()
        val structHash = keccak256(order)

        return keccak256(byteArrayOf(0x19, 0x01) + domainSeparator + structHash)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex payload" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun protocolFeeCallData(): String = SELECTOR_PROTOCOL_FEE

    fun nonceUsedCallData(signerWallet: String, nonce: BigInteger): String =
        SELECTOR_NONCE_USED + addressWord(signerWallet) + uintWord(nonce)

    /**
     * swapAnySender(recipient, nonce, expiry, signerWallet, signerToken,
     * signerAmount, senderToken, senderAmount, v, r, s)
     */
    fun fillCallData(offer: P2POffer, recipient: String): String = buildString {
        append(SELECTOR_SWAP_ANY_SENDER)
        append(addressWord(recipient))
        append(uintWord(offer.nonce))
        append(uintWord(BigInteger.valueOf(offer.expiry)))
        append(addressWord(offer.signerWallet))
        append(addressWord(offer.signerToken))
        append(uintWord(offer.signerAmount))
        append(addressWord(offer.senderToken))
        append(uintWord(offer.senderAmount))
        append(uintWord(BigInteger.valueOf(offer.sigV.toLong())))
        append(signatureWord(offer.sigR))
        append(signatureWord(offer.sigS))
    }

    /** Seller must keep amount + AirSwap protocol fee funded and approved. */
    fun requiredMakerUnits(offer: P2POffer): BigInteger =
        offer.signerAmount + offer.signerAmount
            .multiply(BigInteger.valueOf(offer.protocolFeeBps.toLong()))
            .divide(BigInteger.valueOf(10_000))

    private fun addressWord(address: String): String =
        address.removePrefix("0x").lowercase().padStart(64, '0')

    private fun uintWord(value: BigInteger): String {
        require(value.signum() >= 0 && value.bitLength() <= 256) { "Value exceeds uint256" }
        return value.toString(16).padStart(64, '0')
    }

    private fun signatureWord(word: String): String =
        word.removePrefix("0x").lowercase().padStart(64, '0')
}
