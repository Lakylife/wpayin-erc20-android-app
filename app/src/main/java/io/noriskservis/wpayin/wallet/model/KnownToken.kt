package io.noriskservis.wpayin.wallet.model

data class KnownToken(
    val blockchain: Blockchain,
    val symbol: String,
    val name: String,
    val contractAddress: String,
    val decimals: Int,
    val coinGeckoId: String,
)

object KnownTokenCatalog {
    val tokens: List<KnownToken> = listOf(
        token(Blockchain.ETHEREUM, "USDT", "Tether USD", "0xdac17f958d2ee523a2206206994597c13d831ec7", 6, "tether"),
        token(Blockchain.ETHEREUM, "USDC", "USD Coin", "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", 6, "usd-coin"),
        token(Blockchain.ETHEREUM, "WETH", "Wrapped Ether", "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", 18, "weth"),
        token(Blockchain.ETHEREUM, "WBTC", "Wrapped Bitcoin", "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599", 8, "wrapped-bitcoin"),

        token(Blockchain.ARBITRUM, "USDT", "Tether USD", "0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9", 6, "tether"),
        token(Blockchain.ARBITRUM, "USDC", "USD Coin", "0xaf88d065e77c8cc2239327c5edb3a432268e5831", 6, "usd-coin"),
        token(Blockchain.ARBITRUM, "WETH", "Wrapped Ether", "0x82af49447d8a07e3bd95bd0d56f35241523fbab1", 18, "weth"),
        token(Blockchain.ARBITRUM, "WBTC", "Wrapped Bitcoin", "0x2f2a2543b76a4166549f7aab2e75bef0aefc5b0f", 8, "wrapped-bitcoin"),

        token(Blockchain.OPTIMISM, "USDT", "Tether USD", "0x94b008aa00579c1307b0ef2c499ad98a8ce58e58", 6, "tether"),
        token(Blockchain.OPTIMISM, "USDC", "USD Coin", "0x0b2c639c533813f4aa9d7837caf62653d097ff85", 6, "usd-coin"),
        token(Blockchain.OPTIMISM, "WETH", "Wrapped Ether", "0x4200000000000000000000000000000000000006", 18, "weth"),
        token(Blockchain.OPTIMISM, "WBTC", "Wrapped Bitcoin", "0x68f180fcce6836688e9084f035309e29bf0a2095", 8, "wrapped-bitcoin"),

        token(Blockchain.BASE, "USDT", "Tether USD", "0xfde4c96c8593536e31f229ea8f37b2ada2699bb2", 6, "tether"),
        token(Blockchain.BASE, "USDC", "USD Coin", "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", 6, "usd-coin"),
        token(Blockchain.BASE, "WETH", "Wrapped Ether", "0x4200000000000000000000000000000000000006", 18, "weth"),
        token(Blockchain.BASE, "cbBTC", "Coinbase Wrapped BTC", "0xcbb7c0000ab88b473b1f5afd9ef808440eed33bf", 8, "coinbase-wrapped-btc"),

        token(Blockchain.POLYGON, "USDT", "Tether USD", "0xc2132d05d31c914a87c6611c10748aeb04b58e8f", 6, "tether"),
        token(Blockchain.POLYGON, "USDC", "USD Coin", "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359", 6, "usd-coin"),
        token(Blockchain.POLYGON, "WETH", "Wrapped Ether", "0x7ceb23fd6bc0add59e62ac25578270cff1b9f619", 18, "weth"),
        token(Blockchain.POLYGON, "WBTC", "Wrapped Bitcoin", "0x1bfd67037b42cf73acf2047067bd4f2c47d9bfd6", 8, "wrapped-bitcoin"),

        token(Blockchain.BNB_CHAIN, "USDT", "Tether USD", "0x55d398326f99059ff775485246999027b3197955", 18, "tether"),
        token(Blockchain.BNB_CHAIN, "USDC", "USD Coin", "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d", 18, "usd-coin"),
        token(Blockchain.BNB_CHAIN, "BTCB", "Bitcoin BEP20", "0x7130d2a12b9bcbfae4f2634d864a1ee1ce3ead9c", 18, "bitcoin-bep2"),

        token(Blockchain.AVALANCHE, "USDT", "Tether USD", "0x9702230a8ea53601f5cd2dc00fdbc13d4df4a8c7", 6, "tether"),
        token(Blockchain.AVALANCHE, "USDC", "USD Coin", "0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e", 6, "usd-coin"),
        token(Blockchain.AVALANCHE, "WETH", "Wrapped Ether", "0x49d5c2bdffac6ce2bfdb6640f4f80f226bc10bab", 18, "weth"),
        token(Blockchain.AVALANCHE, "BTC.b", "Bitcoin (Avalanche)", "0x152b9d0fdc40c096757f570a51e494bd4b943e50", 8, "bitcoin-avalanche-bridged-btc-b"),
    )

    fun forBlockchain(blockchain: Blockchain): List<KnownToken> =
        tokens.filter { it.blockchain == blockchain }

    private fun token(
        blockchain: Blockchain,
        symbol: String,
        name: String,
        address: String,
        decimals: Int,
        coinGeckoId: String,
    ) = KnownToken(blockchain, symbol, name, address, decimals, coinGeckoId)
}
