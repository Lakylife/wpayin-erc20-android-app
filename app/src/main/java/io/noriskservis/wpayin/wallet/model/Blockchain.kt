package io.noriskservis.wpayin.wallet.model

enum class Blockchain(
    open val displayName: String,
    open val symbol: String,
    val decimals: Int,
    open val chainId: Long?,
    open val rpcUrls: List<String>,
    open val explorerUrl: String,
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
            "https://solana.publicnode.com",
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
    ),

    /**
     * One slot that follows the Quintium network chosen in Settings → Quintium
     * (testnet tQU by default, mainnet QU from 1 January 2027). Quintium has
     * no CoinGecko listing yet, so it carries no fiat price.
     */
    QUINTIUM(
        displayName = "Quintium",
        symbol = "QU",
        decimals = QuintiumNetwork.DECIMALS,
        chainId = QuintiumNetwork.MAINNET.chainId,
        rpcUrls = emptyList(),
        explorerUrl = "",
        coinGeckoId = "",
    ) {
        override val displayName: String get() = QuintiumConfig.network.displayName
        override val symbol: String get() = QuintiumConfig.network.symbol
        override val chainId: Long get() = QuintiumConfig.network.chainId
        override val rpcUrls: List<String>
            get() = QuintiumConfig.rpcUrl.takeIf(QuintiumConfig::isValidUrl)?.let(::listOf).orEmpty()

        // The node serves the Quintium Explorer with /tx/… and /address/… pages,
        // so the node URL stands in until a separate explorer is set.
        override val explorerUrl: String
            get() = QuintiumConfig.explorerUrl.ifEmpty { QuintiumConfig.rpcUrl }
    };

    val isEvm: Boolean
        get() = chainId != null

    fun addressExplorerUrl(address: String): String = "$explorerUrl/address/$address"

    fun transactionExplorerUrl(hash: String): String = "$explorerUrl/tx/$hash"
}
