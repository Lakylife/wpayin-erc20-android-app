package io.noriskservis.wpayin.wallet.model

enum class Blockchain(
    val displayName: String,
    val symbol: String,
    val decimals: Int,
    val chainId: Long?,
    val rpcUrls: List<String>,
    val explorerUrl: String,
    val coinGeckoId: String,
) {
    BITCOIN(
        displayName = "Bitcoin",
        symbol = "BTC",
        decimals = 8,
        chainId = null,
        rpcUrls = listOf("https://blockstream.info/api", "https://mempool.space/api"),
        explorerUrl = "https://blockstream.info",
        coinGeckoId = "bitcoin",
    ),
    ETHEREUM(
        displayName = "Ethereum",
        symbol = "ETH",
        decimals = 18,
        chainId = 1,
        rpcUrls = listOf(
            "https://ethereum-rpc.publicnode.com",
            "https://cloudflare-eth.com",
        ),
        explorerUrl = "https://etherscan.io",
        coinGeckoId = "ethereum",
    ),
    SOLANA(
        displayName = "Solana",
        symbol = "SOL",
        decimals = 9,
        chainId = null,
        rpcUrls = listOf(
            "https://api.mainnet-beta.solana.com",
            "https://solana-rpc.publicnode.com",
        ),
        explorerUrl = "https://explorer.solana.com",
        coinGeckoId = "solana",
    ),
    BNB_CHAIN(
        displayName = "BNB Chain",
        symbol = "BNB",
        decimals = 18,
        chainId = 56,
        rpcUrls = listOf(
            "https://bsc-dataseed.binance.org",
            "https://bsc-rpc.publicnode.com",
        ),
        explorerUrl = "https://bscscan.com",
        coinGeckoId = "binancecoin",
    ),
    POLYGON(
        displayName = "Polygon",
        symbol = "POL",
        decimals = 18,
        chainId = 137,
        rpcUrls = listOf("https://polygon-bor-rpc.publicnode.com"),
        explorerUrl = "https://polygonscan.com",
        coinGeckoId = "matic-network",
    ),
    ARBITRUM(
        displayName = "Arbitrum",
        symbol = "ETH",
        decimals = 18,
        chainId = 42161,
        rpcUrls = listOf("https://arb1.arbitrum.io/rpc", "https://arbitrum-one-rpc.publicnode.com"),
        explorerUrl = "https://arbiscan.io",
        coinGeckoId = "ethereum",
    ),
    OPTIMISM(
        displayName = "Optimism",
        symbol = "ETH",
        decimals = 18,
        chainId = 10,
        rpcUrls = listOf("https://mainnet.optimism.io", "https://optimism-rpc.publicnode.com"),
        explorerUrl = "https://optimistic.etherscan.io",
        coinGeckoId = "ethereum",
    ),
    AVALANCHE(
        displayName = "Avalanche C-Chain",
        symbol = "AVAX",
        decimals = 18,
        chainId = 43114,
        rpcUrls = listOf(
            "https://api.avax.network/ext/bc/C/rpc",
            "https://avalanche-c-chain-rpc.publicnode.com",
        ),
        explorerUrl = "https://snowtrace.io",
        coinGeckoId = "avalanche-2",
    ),
    BASE(
        displayName = "Base",
        symbol = "ETH",
        decimals = 18,
        chainId = 8453,
        rpcUrls = listOf("https://mainnet.base.org", "https://base-rpc.publicnode.com"),
        explorerUrl = "https://basescan.org",
        coinGeckoId = "ethereum",
    );

    val isEvm: Boolean
        get() = chainId != null

    fun addressExplorerUrl(address: String): String = "$explorerUrl/address/$address"

    fun transactionExplorerUrl(hash: String): String = "$explorerUrl/tx/$hash"
}
