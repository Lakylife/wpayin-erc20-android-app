package io.noriskservis.wpayin.wallet.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetTrustPolicyTest {
    @Test
    fun acceptsNativeAndBundledAssets() {
        val bundled = KnownTokenCatalog.tokens.first()

        assertTrue(
            Asset(
                name = "Ethereum",
                symbol = "ETH",
                blockchain = Blockchain.ETHEREUM,
                balance = 1.0,
                priceUsd = 1.0,
            ).isTrustedWalletAsset(emptyList()),
        )
        assertTrue(assetFor(bundled).isTrustedWalletAsset(emptyList()))
    }

    @Test
    fun rejectsUnsolicitedTokenButAllowsExactUserAddedContract() {
        val unsolicited = KnownToken(
            blockchain = Blockchain.ETHEREUM,
            symbol = "PEPE",
            name = "Unsolicited Token",
            contractAddress = "0x1111111111111111111111111111111111111111",
            decimals = 18,
            coinGeckoId = "",
        )

        assertFalse(assetFor(unsolicited).isTrustedWalletAsset(emptyList()))
        assertTrue(assetFor(unsolicited).isTrustedWalletAsset(listOf(unsolicited)))
    }

    @Test
    fun allowsMarketQuotedExplorerTokenButRejectsUnpricedAirdrop() {
        val discovered = KnownToken(
            blockchain = Blockchain.BASE,
            symbol = "REAL",
            name = "Market Quoted Token",
            contractAddress = "0x2222222222222222222222222222222222222222",
            decimals = 18,
            coinGeckoId = "",
        )

        assertFalse(assetFor(discovered).isTrustedWalletAsset(emptyList()))
        assertTrue(
            assetFor(discovered)
                .copy(priceUsd = 0.025)
                .isTrustedWalletAsset(emptyList()),
        )
    }

    @Test
    fun contractTrustDoesNotCrossNetworks() {
        val ethereumToken = KnownTokenCatalog.tokens.first { it.blockchain == Blockchain.ETHEREUM }
        val wrongNetworkAsset = assetFor(ethereumToken).copy(blockchain = Blockchain.POLYGON)

        assertFalse(wrongNetworkAsset.isTrustedWalletAsset(emptyList()))
    }

    private fun assetFor(token: KnownToken) = Asset(
        id = "${token.blockchain.name}:${token.contractAddress}",
        name = token.name,
        symbol = token.symbol,
        blockchain = token.blockchain,
        balance = 10_000_000_000.0,
        priceUsd = 0.0,
        isNative = false,
        contractAddress = token.contractAddress,
        decimals = token.decimals,
        coinGeckoId = token.coinGeckoId,
    )
}
