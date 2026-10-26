package io.noriskservis.wpayin.wallet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class Base58CodecTest {
    @Test
    fun `decodes leading zeroes and Solana addresses`() {
        assertEquals(32, Base58Codec.decode("11111111111111111111111111111111").size)
        assertTrue(Base58Codec.isSolanaAddress("EN2sCsJ1WDV8UFqsiTXHcUPUxQ4juE71eCknHYYMifkd"))
        assertFalse(Base58Codec.isSolanaAddress("not-a-solana-address"))
    }

    @Test
    fun `rejects ambiguous base58 characters`() {
        assertThrows(IllegalArgumentException::class.java) { Base58Codec.decode("0OIl") }
    }
}
