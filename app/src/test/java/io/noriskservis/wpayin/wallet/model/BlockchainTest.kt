package io.noriskservis.wpayin.wallet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockchainTest {
    @Test
    fun `supported main networks match iOS release`() {
        assertEquals(
            setOf(
                Blockchain.BITCOIN,
                Blockchain.ETHEREUM,
                Blockchain.SOLANA,
                Blockchain.BNB_CHAIN,
                Blockchain.POLYGON,
                Blockchain.ARBITRUM,
                Blockchain.OPTIMISM,
                Blockchain.AVALANCHE,
                Blockchain.BASE,
            ),
            Blockchain.entries.toSet(),
        )
    }

    @Test
    fun `EVM chain identifiers are present and unique`() {
        val identifiers = Blockchain.entries.filter(Blockchain::isEvm).mapNotNull(Blockchain::chainId)
        assertEquals(identifiers.size, identifiers.distinct().size)
        assertTrue(identifiers.all { it > 0 })
    }

    @Test
    fun `all production endpoints use TLS`() {
        assertTrue(Blockchain.entries.flatMap(Blockchain::rpcUrls).all { it.startsWith("https://") })
        assertTrue(Blockchain.entries.all { it.explorerUrl.startsWith("https://") })
    }
}
