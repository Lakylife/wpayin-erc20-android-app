package io.noriskservis.wpayin.wallet.model

import io.noriskservis.wpayin.wallet.data.RpcService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownTokenCatalogTest {
    @Test
    fun `catalog only contains valid contracts on EVM networks`() {
        assertTrue(KnownTokenCatalog.tokens.isNotEmpty())
        assertTrue(KnownTokenCatalog.tokens.all { it.blockchain.isEvm })
        assertTrue(KnownTokenCatalog.tokens.all { RpcService.isEvmAddress(it.contractAddress) })
        assertTrue(KnownTokenCatalog.tokens.all { it.decimals in 0..36 })
    }

    @Test
    fun `token identity is unique per network and contract`() {
        val identities = KnownTokenCatalog.tokens.map { "${it.blockchain}:${it.contractAddress.lowercase()}" }
        assertEquals(identities.size, identities.distinct().size)
    }

    @Test
    fun `catalog mirrors seven iOS EVM allowlists`() {
        val networks = KnownTokenCatalog.tokens.map(KnownToken::blockchain).toSet()
        assertEquals(
            setOf(
                Blockchain.ETHEREUM,
                Blockchain.BNB_CHAIN,
                Blockchain.POLYGON,
                Blockchain.ARBITRUM,
                Blockchain.OPTIMISM,
                Blockchain.AVALANCHE,
                Blockchain.BASE,
            ),
            networks,
        )
    }
}
