package io.noriskservis.wpayin.wallet.model

import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuintiumTest {
    @After
    fun reset() {
        QuintiumConfig.apply(QuintiumNetwork.TESTNET, emptyMap(), emptyMap())
    }

    @Test
    fun `network parameters match the Quintium node`() {
        assertEquals(52401L, QuintiumNetwork.MAINNET.chainId)
        assertEquals("QU", QuintiumNetwork.MAINNET.symbol)
        assertEquals(8545, QuintiumNetwork.MAINNET.defaultRpcPort)
        assertEquals(52402L, QuintiumNetwork.TESTNET.chainId)
        assertEquals("tQU", QuintiumNetwork.TESTNET.symbol)
        assertEquals(8546, QuintiumNetwork.TESTNET.defaultRpcPort)
        assertEquals(18, Blockchain.QUINTIUM.decimals)
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), QuintiumNetwork.MAINNET_GENESIS)
        assertFalse(QuintiumNetwork.MAINNET.isLaunched(Instant.parse("2026-12-31T23:59:59Z")))
        assertTrue(QuintiumNetwork.MAINNET.isLaunched(Instant.parse("2027-01-01T00:00:00Z")))
        assertTrue(QuintiumNetwork.TESTNET.isLaunched(Instant.parse("2026-11-11T00:00:00Z")))
    }

    @Test
    fun `slot defaults to testnet without a node`() {
        assertEquals(QuintiumNetwork.TESTNET, QuintiumNetwork.fromStorage(null))
        assertEquals("tQU", Blockchain.QUINTIUM.symbol)
        assertEquals(52402L, Blockchain.QUINTIUM.chainId)
        assertTrue(Blockchain.QUINTIUM.isEvm)
        assertTrue(Blockchain.QUINTIUM.rpcUrls.isEmpty())
        assertFalse(QuintiumConfig.isConfigured)
        assertEquals("", Blockchain.QUINTIUM.coinGeckoId)
    }

    @Test
    fun `slot follows the active network and node`() {
        QuintiumConfig.apply(
            QuintiumNetwork.MAINNET,
            mapOf(QuintiumNetwork.MAINNET to " https://node.example.org/ "),
            emptyMap(),
        )
        assertEquals("QU", Blockchain.QUINTIUM.symbol)
        assertEquals("Quintium", Blockchain.QUINTIUM.displayName)
        assertEquals(52401L, Blockchain.QUINTIUM.chainId)
        assertEquals(listOf("https://node.example.org"), Blockchain.QUINTIUM.rpcUrls)
        // Without a separate explorer the node's own explorer pages are used.
        assertEquals("https://node.example.org/tx/0xabc", Blockchain.QUINTIUM.transactionExplorerUrl("0xabc"))

        QuintiumConfig.apply(
            QuintiumNetwork.MAINNET,
            mapOf(QuintiumNetwork.MAINNET to "https://node.example.org"),
            mapOf(QuintiumNetwork.MAINNET to "https://explorer.example.org/"),
        )
        assertEquals(
            "https://explorer.example.org/address/0x1",
            Blockchain.QUINTIUM.addressExplorerUrl("0x1"),
        )
    }

    @Test
    fun `node URLs must be http or https with a host`() {
        assertTrue(QuintiumConfig.isValidUrl("https://rpc.example.com"))
        assertTrue(QuintiumConfig.isValidUrl("http://192.168.1.20:8546"))
        assertFalse(QuintiumConfig.isValidUrl(""))
        assertFalse(QuintiumConfig.isValidUrl("rpc.example.com"))
        assertFalse(QuintiumConfig.isValidUrl("ftp://rpc.example.com"))
    }
}
