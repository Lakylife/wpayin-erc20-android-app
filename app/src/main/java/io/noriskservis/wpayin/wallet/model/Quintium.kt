package io.noriskservis.wpayin.wallet.model

import java.net.URI
import java.time.Instant

/**
 * Quintium (QU) is the project's own EVM-compatible Proof-of-Work chain.
 * The values mirror `packages/node/src/params.js` of the Quintium node
 * (release 2.11.1) and the iOS app. Changing chain IDs or decimals there is a
 * hard fork, so they are constants here as well.
 */
enum class QuintiumNetwork(
    val storageValue: String,
    val chainId: Long,
    val symbol: String,
    val displayName: String,
    val defaultRpcPort: Int,
) {
    MAINNET("mainnet", 52401, "QU", "Quintium", 8545),
    TESTNET("testnet", 52402, "tQU", "Quintium Testnet", 8546);

    val isTestnet: Boolean
        get() = this == TESTNET

    /** Mainnet genesis is planned for 1 January 2027 00:00 UTC; the testnet already runs. */
    fun isLaunched(now: Instant = Instant.now()): Boolean =
        this == TESTNET || !now.isBefore(MAINNET_GENESIS)

    companion object {
        val MAINNET_GENESIS: Instant = Instant.ofEpochSecond(1_798_761_600)
        const val DECIMALS = 18
        const val BLOCK_TIME_SECONDS = 15
        /** Ethereum keys and addresses (coin type 60), same as the Quintium desktop wallet. */
        const val DERIVATION_PATH = "m/44'/60'/0'/0"
        /** Node RPC with the transaction history of an address (`quintium_*` is its twin). */
        const val ADDRESS_TRANSACTIONS_METHOD = "aether_addressTxs"

        fun fromStorage(value: String?): QuintiumNetwork =
            entries.firstOrNull { it.storageValue == value } ?: TESTNET
    }
}

/**
 * The active Quintium network and the node it talks to. There is no public
 * Quintium RPC yet (no seed servers, no domain), so the node URL comes from
 * the user (Settings → Quintium) or from the QUINTIUM_* Gradle properties of a
 * release build. Loaded by SettingsStore at start-up.
 */
object QuintiumConfig {
    @Volatile
    var network: QuintiumNetwork = QuintiumNetwork.TESTNET
        private set

    @Volatile
    private var rpcByNetwork: Map<QuintiumNetwork, String> = emptyMap()

    @Volatile
    private var explorerByNetwork: Map<QuintiumNetwork, String> = emptyMap()

    val rpcUrl: String
        get() = rpcUrl(network)

    val explorerUrl: String
        get() = explorerUrl(network)

    val isConfigured: Boolean
        get() = isValidUrl(rpcUrl)

    fun rpcUrl(network: QuintiumNetwork): String = rpcByNetwork[network].orEmpty()

    fun explorerUrl(network: QuintiumNetwork): String = explorerByNetwork[network].orEmpty()

    fun apply(
        network: QuintiumNetwork,
        rpcUrls: Map<QuintiumNetwork, String>,
        explorerUrls: Map<QuintiumNetwork, String>,
    ) {
        rpcByNetwork = rpcUrls.mapValues { clean(it.value) }.filterValues(String::isNotEmpty)
        explorerByNetwork = explorerUrls.mapValues { clean(it.value) }.filterValues(String::isNotEmpty)
        this.network = network
    }

    /** Trims spaces and trailing slashes. */
    fun clean(value: String?): String = value.orEmpty().trim().trimEnd('/')

    fun isValidUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        val scheme = uri.scheme?.lowercase()
        (scheme == "https" || scheme == "http") && !uri.host.isNullOrEmpty()
    }.getOrDefault(false)
}
