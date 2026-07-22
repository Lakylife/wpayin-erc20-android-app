package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.P2POffer
import java.math.BigInteger
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class P2PServiceTest {

    private fun samplePayload(mutate: (JSONObject) -> Unit = {}): String {
        val json = JSONObject()
            .put("version", 1)
            .put("chainId", 8453)
            .put("nonce", "123456789")
            .put("expiry", 4_102_444_800.0)
            .put("signerWallet", "0x1111111111111111111111111111111111111111")
            .put("signerToken", "0x2222222222222222222222222222222222222222")
            .put("signerAmount", "1000000")
            .put("signerSymbol", "USDC")
            .put("signerDecimals", 6)
            .put("senderToken", "0x3333333333333333333333333333333333333333")
            .put("senderAmount", "500000000000000000")
            .put("senderSymbol", "WETH")
            .put("senderDecimals", 18)
            .put("protocolFeeBps", 5)
            .put("sigV", 27)
            .put("sigR", "0x" + "ab".repeat(32))
            .put("sigS", "0x" + "cd".repeat(32))
        mutate(json)
        return P2POffer.PAYLOAD_PREFIX +
            Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `parses an iOS-format payload`() {
        val offer = P2POffer.parse(samplePayload())
        assertEquals(8453L, offer.chainId)
        assertEquals(Blockchain.BASE, offer.blockchain)
        assertEquals(BigInteger.valueOf(123_456_789), offer.nonce)
        assertEquals("usdc", offer.signerSymbol.lowercase())
        assertEquals(BigInteger.valueOf(1_000_000), offer.signerAmount)
        assertEquals(0, offer.senderAmountDecimal.compareTo(java.math.BigDecimal("0.5")))
        assertTrue(!offer.isExpired)
    }

    @Test
    fun `rejects payloads without the prefix`() {
        assertThrows(IllegalArgumentException::class.java) {
            P2POffer.parse("not-an-offer")
        }
    }

    @Test
    fun `rejects a zero amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            P2POffer.parse(samplePayload { it.put("signerAmount", "0") })
        }
    }

    @Test
    fun `rejects an invalid signature v`() {
        assertThrows(IllegalArgumentException::class.java) {
            P2POffer.parse(samplePayload { it.put("sigV", 5) })
        }
    }

    @Test
    fun `fill calldata encodes eleven words after the selector`() {
        val offer = P2POffer.parse(samplePayload())
        val data = P2PService.fillCallData(offer, "0x4444444444444444444444444444444444444444")
        assertTrue(data.startsWith("0x3eb1af24"))
        assertEquals(10 + 11 * 64, data.length)
        // recipient word
        assertEquals(
            "4444444444444444444444444444444444444444".padStart(64, '0'),
            data.substring(10, 74),
        )
        // signature r/s are the final two words
        assertEquals("ab".repeat(32), data.substring(data.length - 128, data.length - 64))
        assertEquals("cd".repeat(32), data.substring(data.length - 64))
    }

    @Test
    fun `nonceUsed calldata matches the AirSwap selector`() {
        val data = P2PService.nonceUsedCallData(
            "0x1111111111111111111111111111111111111111",
            BigInteger.ONE,
        )
        assertTrue(data.startsWith("0x1647795e"))
        assertEquals(10 + 2 * 64, data.length)
    }

    @Test
    fun `maker requirement includes the protocol fee`() {
        val offer = P2POffer.parse(samplePayload())
        // 1_000_000 + 5 bps = 1_000_500
        assertEquals(BigInteger.valueOf(1_000_500), P2PService.requiredMakerUnits(offer))
    }

    @Test
    fun `p2p contract is available on supported chains only`() {
        assertTrue(P2PService.contractFor(Blockchain.BASE) != null)
        assertTrue(P2PService.contractFor(Blockchain.OPTIMISM) == null)
        assertTrue(P2PService.contractFor(Blockchain.BITCOIN) == null)
    }
}
