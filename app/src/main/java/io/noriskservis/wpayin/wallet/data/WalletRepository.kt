package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.core.WalletEngine
import io.noriskservis.wpayin.wallet.model.AppSettings
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.FiatCurrency
import io.noriskservis.wpayin.wallet.model.KnownTokenCatalog
import io.noriskservis.wpayin.wallet.model.KnownToken
import io.noriskservis.wpayin.wallet.model.P2POffer
import io.noriskservis.wpayin.wallet.model.WalletAccount
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.model.WalletSnapshot
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.model.SavedAddress
import io.noriskservis.wpayin.wallet.model.ExchangeQuote
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransferDraft
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.AutoLockDuration
import io.noriskservis.wpayin.wallet.model.AppColorTheme
import io.noriskservis.wpayin.wallet.model.AssetListStyle
import io.noriskservis.wpayin.wallet.model.AUTOMATIC_TIME_ZONE_ID
import io.noriskservis.wpayin.wallet.model.ExportedPrivateKey
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WalletState(
    val isInitializing: Boolean = true,
    val isRefreshing: Boolean = false,
    val isRefreshingHistory: Boolean = false,
    val isSending: Boolean = false,
    val isProductionWalletEnabled: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val fiatRates: Map<String, Double> = emptyMap(),
    val priceChanges24h: Map<String, Double> = emptyMap(),
    val wallet: WalletSnapshot? = null,
    val transactions: List<WalletTransaction> = emptyList(),
    val customTokens: List<KnownToken> = emptyList(),
    val savedAddresses: List<SavedAddress> = emptyList(),
    val errorMessage: String? = null,
) {
    fun formatFiat(valueUsd: Double): String =
        FiatCurrency.formatUsdValue(valueUsd, settings.currencyCode, fiatRates)
}

class WalletRepository(
    private val secureStore: SecureWalletStore,
    private val settingsStore: SettingsStore,
    private val walletEngine: WalletEngine,
    private val rpcService: RpcService,
    private val priceService: PriceService,
    private val explorerService: ExplorerService,
    private val customTokenStore: CustomTokenStore,
    private val savedAddressStore: SavedAddressStore,
    private val lifiService: LifiService,
    private val p2pMarketService: P2PMarketService = P2PMarketService(),
) {
    // Engine state is filled in initialize(): touching the engine here would
    // load the Wallet Core native library on the caller's (main) thread.
    private val mutableState = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = mutableState.asStateFlow()

    suspend fun initialize() {
        val settings = withContext(Dispatchers.IO) { settingsStore.load() }
        val customTokens = withContext(Dispatchers.IO) { customTokenStore.load() }
        val savedAddresses = withContext(Dispatchers.IO) { savedAddressStore.load() }
        val fiatRates = withContext(Dispatchers.IO) { settingsStore.loadFiatRates() }
        val productionReady = withContext(Dispatchers.Default) { walletEngine.isProductionReady }
        val wallet = runCatching {
            withContext(Dispatchers.IO) { secureStore.load() }?.let { buildSnapshot(it) }
        }.onFailure { error ->
            mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
        }.getOrNull()

        mutableState.update {
            it.copy(
                isInitializing = false,
                isProductionWalletEnabled = productionReady,
                settings = settings,
                fiatRates = fiatRates,
                wallet = wallet,
                customTokens = customTokens,
                savedAddresses = savedAddresses,
            )
        }

        if (wallet != null) refresh()
    }

    suspend fun refreshFiatRates() {
        val fresh = runCatching {
            priceService.usdFiatRates(FiatCurrency.entries.map(FiatCurrency::code))
        }.getOrNull() ?: return
        settingsStore.saveFiatRates(fresh)
        mutableState.update { it.copy(fiatRates = it.fiatRates + fresh) }
    }

    fun completeOnboarding() {
        settingsStore.setOnboardingComplete(true)
        mutableState.update {
            it.copy(settings = it.settings.copy(onboardingComplete = true))
        }
    }

    suspend fun generateRecoveryPhrase(): Result<String> = runCatching {
        withContext(Dispatchers.Default) { walletEngine.generateMnemonic() }
    }

    suspend fun createWallet(mnemonic: String): Result<Unit> = runCatching {
        val normalized = normalizeMnemonic(mnemonic)
        check(withContext(Dispatchers.Default) { walletEngine.validateMnemonic(normalized) }) {
            "Invalid recovery phrase"
        }
        val secret = SecureWalletStore.WalletSecret(WalletKind.MNEMONIC, normalized)
        val snapshot = buildSnapshot(secret)
        secureStore.save(secret)
        mutableState.update { it.copy(wallet = snapshot, errorMessage = null) }
        refresh()
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    suspend fun importPrivateKey(privateKey: String): Result<Unit> = runCatching {
        val normalized = normalizePrivateKey(privateKey)
        check(withContext(Dispatchers.Default) { walletEngine.validatePrivateKey(normalized) }) {
            "Invalid private key"
        }
        val secret = SecureWalletStore.WalletSecret(WalletKind.PRIVATE_KEY, normalized)
        val snapshot = buildSnapshot(secret)
        secureStore.save(secret)
        mutableState.update { it.copy(wallet = snapshot, errorMessage = null) }
        refresh()
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    suspend fun refresh() {
        supervisorScope {
            listOf(
                async { refreshBalances() },
                async { refreshHistory() },
                async { refreshFiatRates() },
            ).awaitAll()
        }
    }

    suspend fun refreshBalances() {
        val current = mutableState.value.wallet ?: return
        if (mutableState.value.isRefreshing) return
        mutableState.update { it.copy(isRefreshing = true, errorMessage = null) }

        val result = runCatching {
            val activeAccounts = current.accounts.filter {
                it.blockchain in mutableState.value.settings.enabledNetworks
            }
            fun tokenKey(blockchain: Blockchain, contractAddress: String): String =
                "${blockchain.name}:${contractAddress.lowercase()}"

            val explicitlyTrackedTokens = activeAccounts.associate { account ->
                account.blockchain to tokensForBlockchain(account.blockchain)
            }
            val locallyTrackedContracts = explicitlyTrackedTokens.values
                .flatten()
                .mapTo(mutableSetOf()) { tokenKey(it.blockchain, it.contractAddress) }
            val indexedTokenBalances = supervisorScope {
                activeAccounts
                    .filter { it.blockchain.isEvm }
                    .map { account ->
                        async {
                            runCatching { explorerService.tokenBalances(account) }
                                .getOrDefault(emptyList())
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
            val indexedByContract = indexedTokenBalances.associateBy {
                tokenKey(it.token.blockchain, it.token.contractAddress)
            }
            // An explorer-discovered contract is admitted automatically only
            // when the explorer has a positive market quote for that exact
            // contract. Unpriced airdrops stay hidden; users can still add an
            // unpriced token deliberately through Add Token.
            val tokensByBlockchain = activeAccounts.associate { account ->
                val marketVerified = indexedTokenBalances
                    .filter {
                        it.token.blockchain == account.blockchain &&
                            it.priceUsd.isFinite() &&
                            it.priceUsd > 0.0
                    }
                    .map(IndexedTokenBalance::token)
                account.blockchain to
                    (explicitlyTrackedTokens[account.blockchain].orEmpty() + marketVerified)
                        .distinctBy { it.contractAddress.lowercase() }
            }
            val knownTokens = tokensByBlockchain.values.flatten()
            val priceIds = activeAccounts.map { it.blockchain.coinGeckoId } +
                knownTokens.map { it.coinGeckoId }
            val marketQuotes = runCatching { priceService.usdMarketQuotesForIds(priceIds) }
                .getOrDefault(emptyMap())
            val prices = marketQuotes.mapValues { it.value.price }
            val failures = AtomicInteger(0)
            val previousAssets = current.assets.associateBy(Asset::id)
            val assets = supervisorScope {
                val nativeAssets = activeAccounts.map { account ->
                    async {
                        val assetId = "${account.blockchain.name}:native"
                        val previous = previousAssets[assetId]
                        val balance = runCatching {
                            rpcService.nativeBalance(account.blockchain, account.address)
                        }.getOrElse {
                            failures.incrementAndGet()
                            previous?.balance ?: 0.0
                        }
                        Asset(
                            id = assetId,
                            name = account.blockchain.displayName,
                            symbol = account.blockchain.symbol,
                            blockchain = account.blockchain,
                            balance = balance,
                            priceUsd = prices[account.blockchain.coinGeckoId] ?: previous?.priceUsd ?: 0.0,
                        )
                    }
                }
                val tokenAssets = activeAccounts.flatMap { account ->
                    tokensByBlockchain[account.blockchain].orEmpty().map { token ->
                        async {
                            val assetId = "${account.blockchain.name}:${token.contractAddress.lowercase()}"
                            val previous = previousAssets[assetId]
                            val indexed = indexedByContract[
                                tokenKey(account.blockchain, token.contractAddress)
                            ]
                            val balance = if (assetId in locallyTrackedContracts) {
                                runCatching {
                                    rpcService.erc20Balance(token, account.address)
                                }.getOrElse {
                                    failures.incrementAndGet()
                                    indexed?.balance ?: previous?.balance ?: 0.0
                                }
                            } else {
                                indexed?.balance ?: previous?.balance ?: 0.0
                            }
                            Asset(
                                id = assetId,
                                name = token.name,
                                symbol = token.symbol,
                                blockchain = account.blockchain,
                                balance = balance,
                                priceUsd = prices[token.coinGeckoId]
                                    ?: indexed?.priceUsd?.takeIf { it > 0.0 }
                                    ?: previous?.priceUsd
                                    ?: 0.0,
                                isNative = false,
                                contractAddress = token.contractAddress,
                                decimals = token.decimals,
                                coinGeckoId = token.coinGeckoId,
                            )
                        }
                    }
                }
                (nativeAssets + tokenAssets).awaitAll()
            }
            Triple(
                current.copy(
                assets = assets.sortedWith(
                    compareByDescending(Asset::valueUsd)
                        .thenByDescending(Asset::isNative)
                        .thenBy(Asset::symbol),
                ),
                lastUpdated = Instant.now(),
                ),
                failures.get(),
                marketQuotes.mapNotNull { (id, quote) ->
                    quote.change24hPercent?.let { id to it }
                }.toMap(),
            )
        }

        mutableState.update { state ->
            result.fold(
                onSuccess = { (snapshot, failures, priceChanges24h) ->
                    state.copy(
                        isRefreshing = false,
                        wallet = snapshot,
                        priceChanges24h = priceChanges24h,
                        errorMessage = if (failures > 0) {
                            "$failures asset balance${if (failures == 1) "" else "s"} could not be refreshed"
                        } else {
                            null
                        },
                    )
                },
                onFailure = { error ->
                    state.copy(isRefreshing = false, errorMessage = error.userFacingMessage())
                },
            )
        }
    }

    suspend fun refreshHistory() {
        val current = mutableState.value.wallet ?: return
        if (mutableState.value.isRefreshingHistory) return
        mutableState.update { it.copy(isRefreshingHistory = true) }

        val results = supervisorScope {
            current.accounts.filter {
                it.blockchain in mutableState.value.settings.enabledNetworks
            }.map { account ->
                async { account.blockchain to runCatching { explorerService.transactions(account) } }
            }.awaitAll()
        }
        val successfulBlockchains = results.mapNotNull { (blockchain, result) ->
            blockchain.takeIf { result.isSuccess }
        }.toSet()
        val freshTransactions = results.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
        val freshHashes = freshTransactions.map { it.hash.lowercase() }.toSet()
        val retainedTransactions = mutableState.value.transactions.filter { transaction ->
            transaction.blockchain !in successfulBlockchains ||
                (transaction.status == io.noriskservis.wpayin.wallet.model.TransactionStatus.PENDING &&
                    transaction.hash.lowercase() !in freshHashes)
        }
        val transactions = (freshTransactions + retainedTransactions)
            .distinctBy { "${it.hash.lowercase()}|${it.symbol.uppercase()}|${it.type}" }
            .sortedByDescending(WalletTransaction::timestamp)

        mutableState.update {
            it.copy(
                isRefreshingHistory = false,
                transactions = transactions,
            )
        }
    }

    suspend fun prepareEvmTransfer(
        assetId: String,
        recipient: String,
        amount: String,
    ): Result<EvmTransferDraft> = runCatching {
        check(walletEngine.isProductionReady) { "Sending is unavailable in the preview build" }
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        val asset = wallet.assets.firstOrNull { it.id == assetId } ?: error("Selected asset is unavailable")
        check(asset.blockchain.isEvm) { "This send flow currently supports EVM assets" }
        val account = wallet.account(asset.blockchain) ?: error("Wallet account is unavailable")
        val token = asset.contractAddress?.let { contract ->
            tokensForBlockchain(asset.blockchain).firstOrNull {
                it.contractAddress.equals(contract, ignoreCase = true)
            } ?: error("Unknown token contract cannot be signed yet")
        }
        val draft = rpcService.planEvmTransfer(
            blockchain = asset.blockchain,
            from = account.address,
            recipient = recipient.trim(),
            amount = amount,
            token = token,
        )

        val requestedAmount = amount.trim().toBigDecimal()
        val assetBalance = BigDecimal.valueOf(asset.balance)
        check(requestedAmount <= assetBalance) { "Insufficient ${asset.symbol} balance" }
        val nativeAsset = wallet.assets.firstOrNull { it.blockchain == asset.blockchain && it.isNative }
            ?: error("Native fee asset is unavailable")
        val maximumFee = draft.maximumFeeWei.toBigDecimal().movePointLeft(asset.blockchain.decimals)
        if (asset.isNative) {
            check(requestedAmount.add(maximumFee) <= assetBalance) {
                "Insufficient ${asset.symbol} balance after the maximum network fee"
            }
        } else {
            check(maximumFee <= BigDecimal.valueOf(nativeAsset.balance)) {
                "Insufficient ${nativeAsset.symbol} for the maximum network fee"
            }
        }
        draft
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    suspend fun prepareTransfer(
        assetId: String,
        recipient: String,
        amount: String,
    ): Result<TransferDraft> {
        val asset = mutableState.value.wallet?.assets?.firstOrNull { it.id == assetId }
            ?: return Result.failure(IllegalStateException("Selected asset is unavailable"))
        return when {
            asset.blockchain.isEvm -> prepareEvmTransfer(assetId, recipient, amount)
            asset.blockchain == Blockchain.SOLANA && asset.isNative -> prepareSolanaTransfer(asset, recipient, amount)
            asset.blockchain == Blockchain.BITCOIN && asset.isNative -> prepareBitcoinTransfer(asset, recipient, amount)
            else -> Result.failure(IllegalStateException("Sending this asset is not implemented yet"))
        }
    }

    private suspend fun prepareSolanaTransfer(
        asset: Asset,
        recipient: String,
        amount: String,
    ): Result<SolanaTransferDraft> = runCatching {
        check(walletEngine.isProductionReady) { "Sending is unavailable in the preview build" }
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        check(wallet.kind == WalletKind.MNEMONIC) { "A mnemonic wallet is required for Solana sends" }
        val account = wallet.account(Blockchain.SOLANA) ?: error("Solana account is unavailable")
        val draft = rpcService.planSolanaTransfer(account.address, recipient.trim(), amount)
        val total = amount.trim().toBigDecimal().add(BigDecimal.valueOf(draft.maximumNetworkFee))
        check(total <= BigDecimal.valueOf(asset.balance)) { "Insufficient SOL after the estimated network fee" }
        draft
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    private suspend fun prepareBitcoinTransfer(
        asset: Asset,
        recipient: String,
        amount: String,
    ): Result<BitcoinTransferDraft> = runCatching {
        check(walletEngine.isProductionReady) { "Sending is unavailable in the preview build" }
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        check(wallet.kind == WalletKind.MNEMONIC) { "A mnemonic wallet is required for Bitcoin sends" }
        check(walletEngine.validateAddress(Blockchain.BITCOIN, recipient.trim())) { "Invalid Bitcoin recipient address" }
        val account = wallet.account(Blockchain.BITCOIN) ?: error("Bitcoin account is unavailable")
        val draft = rpcService.planBitcoinTransfer(account.address, recipient.trim(), amount)
        val total = amount.trim().toBigDecimal().add(BigDecimal.valueOf(draft.maximumNetworkFee))
        check(total <= BigDecimal.valueOf(asset.balance)) { "Insufficient confirmed BTC after the network fee" }
        draft
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    suspend fun sendEvmTransfer(reviewedDraft: EvmTransferDraft): Result<WalletTransaction> {
        if (mutableState.value.isSending) return Result.failure(IllegalStateException("A transaction is already being sent"))
        mutableState.update { it.copy(isSending = true, errorMessage = null) }

        val result = runCatching {
            check(walletEngine.isProductionReady) { "Sending is unavailable in the preview build" }
            check(Duration.between(reviewedDraft.plannedAt, Instant.now()).toMinutes() < 5) {
                "Transaction review expired; estimate the network fee again"
            }
            val wallet = mutableState.value.wallet ?: error("No active wallet")
            val account = wallet.account(reviewedDraft.blockchain) ?: error("Wallet account is unavailable")
            check(account.address.equals(reviewedDraft.from, ignoreCase = true)) { "Sender account changed" }
            val token = reviewedDraft.contractAddress?.let { contract ->
                tokensForBlockchain(reviewedDraft.blockchain).firstOrNull {
                    it.contractAddress.equals(contract, ignoreCase = true)
                } ?: error("Unknown token contract cannot be signed yet")
            }

            // Refresh nonce and gas immediately before signing. A material fee
            // increase requires a new explicit review instead of silent consent.
            val currentDraft = rpcService.planEvmTransfer(
                blockchain = reviewedDraft.blockchain,
                from = reviewedDraft.from,
                recipient = reviewedDraft.to,
                amount = reviewedDraft.amount,
                token = token,
            )
            val acceptedMaximum = reviewedDraft.maximumFeeWei.multiply(java.math.BigInteger.valueOf(125))
                .divide(java.math.BigInteger.valueOf(100))
            check(currentDraft.maximumFeeWei <= acceptedMaximum) {
                "Network fee increased by more than 25%; review the transaction again"
            }

            val secret = secureStore.load() ?: error("Wallet credentials are unavailable")
            val signed = withContext(Dispatchers.Default) {
                walletEngine.signEvmTransaction(secret.kind, secret.value, currentDraft)
            }
            val hash = rpcService.broadcastRawTransaction(currentDraft.blockchain, signed)
            val transaction = WalletTransaction(
                hash = hash,
                blockchain = currentDraft.blockchain,
                type = io.noriskservis.wpayin.wallet.model.TransactionType.SEND,
                status = io.noriskservis.wpayin.wallet.model.TransactionStatus.PENDING,
                amount = currentDraft.amount.toDouble(),
                symbol = currentDraft.symbol,
                timestamp = Instant.now(),
                from = currentDraft.from,
                to = currentDraft.to,
                fee = currentDraft.maximumFeeNative,
            )
            applyOptimisticEvmSend(currentDraft)
            mutableState.update { state ->
                state.copy(
                    transactions = listOf(transaction) + state.transactions.filterNot {
                        it.hash.equals(hash, ignoreCase = true)
                    },
                )
            }
            sendPlatformFeeBestEffort(currentDraft, secret)
            transaction
        }

        mutableState.update { state ->
            result.fold(
                onSuccess = { state.copy(isSending = false, errorMessage = null) },
                onFailure = { state.copy(isSending = false, errorMessage = it.userFacingMessage()) },
            )
        }
        return result
    }

    suspend fun sendTransfer(reviewedDraft: TransferDraft): Result<WalletTransaction> = when (reviewedDraft) {
        is EvmTransferDraft -> sendEvmTransfer(reviewedDraft)
        is SolanaTransferDraft -> sendSolanaTransfer(reviewedDraft)
        is BitcoinTransferDraft -> sendBitcoinTransfer(reviewedDraft)
    }

    private suspend fun sendSolanaTransfer(reviewedDraft: SolanaTransferDraft): Result<WalletTransaction> {
        if (mutableState.value.isSending) return Result.failure(IllegalStateException("A transaction is already being sent"))
        mutableState.update { it.copy(isSending = true, errorMessage = null) }
        val result = runCatching {
            check(walletEngine.isProductionReady) { "Sending is unavailable in the preview build" }
            check(Duration.between(reviewedDraft.plannedAt, Instant.now()).toMinutes() < 2) {
                "Solana transaction review expired; refresh the blockhash"
            }
            val wallet = mutableState.value.wallet ?: error("No active wallet")
            val account = wallet.account(Blockchain.SOLANA) ?: error("Solana account is unavailable")
            check(account.address == reviewedDraft.from) { "Sender account changed" }
            val currentDraft = rpcService.planSolanaTransfer(
                from = reviewedDraft.from,
                recipient = reviewedDraft.to,
                amount = reviewedDraft.amount,
            )
            val secret = secureStore.load() ?: error("Wallet credentials are unavailable")
            val signed = withContext(Dispatchers.Default) {
                walletEngine.signSolanaTransaction(secret.kind, secret.value, currentDraft)
            }
            val signature = rpcService.broadcastSolanaTransaction(signed)
            val transaction = WalletTransaction(
                hash = signature,
                blockchain = Blockchain.SOLANA,
                type = io.noriskservis.wpayin.wallet.model.TransactionType.SEND,
                status = io.noriskservis.wpayin.wallet.model.TransactionStatus.PENDING,
                amount = currentDraft.amount.toDouble(),
                symbol = "SOL",
                timestamp = Instant.now(),
                from = currentDraft.from,
                to = currentDraft.to,
                fee = currentDraft.maximumNetworkFee,
            )
            mutableState.update { state ->
                val currentWallet = state.wallet
                val updatedWallet = currentWallet?.copy(
                    assets = currentWallet.assets.map { asset ->
                        if (asset.blockchain == Blockchain.SOLANA && asset.isNative) {
                            asset.copy(
                                balance = (asset.balance - transaction.amount - currentDraft.maximumNetworkFee)
                                    .coerceAtLeast(0.0),
                            )
                        } else {
                            asset
                        }
                    },
                    lastUpdated = Instant.now(),
                )
                state.copy(
                    wallet = updatedWallet,
                    transactions = listOf(transaction) + state.transactions.filterNot { it.hash == signature },
                )
            }
            transaction
        }
        mutableState.update { state ->
            result.fold(
                onSuccess = { state.copy(isSending = false, errorMessage = null) },
                onFailure = { state.copy(isSending = false, errorMessage = it.userFacingMessage()) },
            )
        }
        return result
    }

    private suspend fun sendBitcoinTransfer(reviewedDraft: BitcoinTransferDraft): Result<WalletTransaction> {
        if (mutableState.value.isSending) return Result.failure(IllegalStateException("A transaction is already being sent"))
        mutableState.update { it.copy(isSending = true, errorMessage = null) }
        val result = runCatching {
            check(walletEngine.isProductionReady) { "Sending is unavailable in the preview build" }
            check(Duration.between(reviewedDraft.plannedAt, Instant.now()).toMinutes() < 5) {
                "Bitcoin transaction review expired; refresh UTXOs and fee rate"
            }
            val wallet = mutableState.value.wallet ?: error("No active wallet")
            val account = wallet.account(Blockchain.BITCOIN) ?: error("Bitcoin account is unavailable")
            check(account.address == reviewedDraft.from) { "Sender account changed" }
            val currentDraft = rpcService.planBitcoinTransfer(
                from = reviewedDraft.from,
                recipient = reviewedDraft.to,
                amount = reviewedDraft.amount,
            )
            val acceptedMaximumFeeValue = reviewedDraft.feeSatoshis.toBigInteger()
                .multiply(java.math.BigInteger.valueOf(125))
                .divide(java.math.BigInteger.valueOf(100))
            check(acceptedMaximumFeeValue.signum() >= 0 && acceptedMaximumFeeValue.bitLength() <= 63) {
                "Reviewed Bitcoin fee is outside the supported range"
            }
            val acceptedMaximumFee = acceptedMaximumFeeValue.toLong()
            check(currentDraft.feeSatoshis <= acceptedMaximumFee) {
                "Bitcoin network fee increased by more than 25%; review the transaction again"
            }
            val secret = secureStore.load() ?: error("Wallet credentials are unavailable")
            val signed = withContext(Dispatchers.Default) {
                walletEngine.signBitcoinTransaction(secret.kind, secret.value, currentDraft)
            }
            check(signed.feeSatoshis <= acceptedMaximumFee) {
                "Wallet Core fee exceeds the reviewed maximum"
            }
            val txid = rpcService.broadcastBitcoinTransaction(signed.rawHex)
            val feeBtc = signed.feeSatoshis.toBigDecimal().movePointLeft(Blockchain.BITCOIN.decimals).toDouble()
            val transaction = WalletTransaction(
                hash = txid,
                blockchain = Blockchain.BITCOIN,
                type = io.noriskservis.wpayin.wallet.model.TransactionType.SEND,
                status = io.noriskservis.wpayin.wallet.model.TransactionStatus.PENDING,
                amount = currentDraft.amount.toDouble(),
                symbol = "BTC",
                timestamp = Instant.now(),
                from = currentDraft.from,
                to = currentDraft.to,
                fee = feeBtc,
            )
            mutableState.update { state ->
                val currentWallet = state.wallet
                val updatedWallet = currentWallet?.copy(
                    assets = currentWallet.assets.map { asset ->
                        if (asset.blockchain == Blockchain.BITCOIN && asset.isNative) {
                            asset.copy(balance = (asset.balance - transaction.amount - feeBtc).coerceAtLeast(0.0))
                        } else {
                            asset
                        }
                    },
                    lastUpdated = Instant.now(),
                )
                state.copy(
                    wallet = updatedWallet,
                    transactions = listOf(transaction) + state.transactions.filterNot { it.hash == txid },
                )
            }
            transaction
        }
        mutableState.update { state ->
            result.fold(
                onSuccess = { state.copy(isSending = false, errorMessage = null) },
                onFailure = { state.copy(isSending = false, errorMessage = it.userFacingMessage()) },
            )
        }
        return result
    }

    /**
     * Broadcasts the disclosed application-development fee (25 bps of the sent
     * amount) right after the main transaction, matching iOS. The main send is
     * already on-chain, so a fee failure must never surface as a failed send.
     */
    private suspend fun sendPlatformFeeBestEffort(
        mainDraft: EvmTransferDraft,
        secret: SecureWalletStore.WalletSecret,
    ) {
        val recipient = BuildConfig.PLATFORM_FEE_RECIPIENT.trim()
        if (BuildConfig.PLATFORM_FEE_BPS <= 0 || !RpcService.isEvmAddress(recipient)) return
        val feeAtomic = mainDraft.amountAtomic
            .multiply(java.math.BigInteger.valueOf(BuildConfig.PLATFORM_FEE_BPS.toLong()))
            .divide(java.math.BigInteger.valueOf(10_000))
        if (feeAtomic.signum() <= 0) return

        runCatching {
            val token = mainDraft.contractAddress
            val feeDraft = EvmTransferDraft(
                blockchain = mainDraft.blockchain,
                from = mainDraft.from,
                to = recipient,
                symbol = mainDraft.symbol,
                amount = feeAtomic.toBigDecimal()
                    .movePointLeft(decimalsForDraft(mainDraft))
                    .toPlainString(),
                amountAtomic = feeAtomic,
                contractAddress = token,
                transactionTo = token ?: recipient,
                valueHex = if (token == null) "0x${feeAtomic.toString(16)}" else "0x0",
                dataHex = token?.let { RpcService.erc20TransferData(recipient, feeAtomic) } ?: "0x",
                nonce = mainDraft.nonce.add(java.math.BigInteger.ONE),
                gasLimit = java.math.BigInteger.valueOf(if (token == null) 21_000 else 65_000),
                gasPriceWei = mainDraft.gasPriceWei,
            )
            val signed = withContext(Dispatchers.Default) {
                walletEngine.signEvmTransaction(secret.kind, secret.value, feeDraft)
            }
            rpcService.broadcastRawTransaction(mainDraft.blockchain, signed)
        }
    }

    private fun decimalsForDraft(draft: EvmTransferDraft): Int {
        val token = draft.contractAddress ?: return draft.blockchain.decimals
        return tokensForBlockchain(draft.blockchain).firstOrNull {
            it.contractAddress.equals(token, ignoreCase = true)
        }?.decimals ?: draft.blockchain.decimals
    }

    fun revealWalletSecret(): Result<SecureWalletStore.WalletSecret> = runCatching {
        secureStore.load() ?: error("Wallet credentials are unavailable")
    }

    fun revealPrivateKeys(): Result<List<ExportedPrivateKey>> = runCatching {
        val secret = secureStore.load() ?: error("Wallet credentials are unavailable")
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        walletEngine.exportPrivateKeys(
            walletKind = secret.kind,
            walletSecret = secret.value,
            blockchains = wallet.accounts.map(WalletAccount::blockchain).toSet(),
        ).map { (blockchain, privateKey) ->
            ExportedPrivateKey(
                blockchain = blockchain,
                address = wallet.account(blockchain)?.address.orEmpty(),
                privateKey = privateKey,
            )
        }
    }

    private fun applyOptimisticEvmSend(draft: EvmTransferDraft) {
        mutableState.update { state ->
            val wallet = state.wallet ?: return@update state
            val amount = draft.amount.toDouble()
            val fee = draft.maximumFeeNative
            val assets = wallet.assets.map { asset ->
                val isSentAsset = asset.blockchain == draft.blockchain && if (draft.contractAddress == null) {
                    asset.isNative
                } else {
                    asset.contractAddress.equals(draft.contractAddress, ignoreCase = true)
                }
                val debit = when {
                    isSentAsset && asset.isNative -> amount + fee
                    isSentAsset -> amount
                    asset.blockchain == draft.blockchain && asset.isNative -> fee
                    else -> 0.0
                }
                if (debit == 0.0) asset else asset.copy(balance = (asset.balance - debit).coerceAtLeast(0.0))
            }
            state.copy(wallet = wallet.copy(assets = assets, lastUpdated = Instant.now()))
        }
    }

    suspend fun addCustomToken(
        blockchain: Blockchain,
        contractAddress: String,
    ): Result<KnownToken> = runCatching {
        check(blockchain.isEvm) { "Custom tokens currently require an EVM network" }
        val token = rpcService.erc20Metadata(blockchain, contractAddress.trim())
        val existing = tokensForBlockchain(blockchain).firstOrNull {
            it.contractAddress.equals(token.contractAddress, ignoreCase = true)
        }
        check(existing == null) { "This token is already in Your Assets" }
        val updated = mutableState.value.customTokens + token
        customTokenStore.save(updated)
        mutableState.update { it.copy(customTokens = updated, errorMessage = null) }
        refreshBalances()
        token
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    suspend fun prepareExchange(
        fromAssetId: String,
        toAssetId: String,
        amount: String,
    ): Result<ExchangeQuote> = runCatching {
        check(walletEngine.isProductionReady) { "Swap and Bridge are unavailable in the preview build" }
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        val fromAsset = wallet.assets.firstOrNull { it.id == fromAssetId }
            ?: error("Source asset is unavailable")
        val toAsset = wallet.assets.firstOrNull { it.id == toAssetId }
            ?: error("Destination asset is unavailable")
        check(fromAsset.id != toAsset.id) { "Select a different destination asset" }
        check(fromAsset.blockchain in mutableState.value.settings.enabledNetworks &&
            toAsset.blockchain in mutableState.value.settings.enabledNetworks
        ) { "Enable both networks in Manage Networks" }
        if (fromAsset.blockchain == Blockchain.BITCOIN) {
            return@runCatching prepareBitcoinExchange(wallet, fromAsset, toAsset, amount)
        }
        check(fromAsset.blockchain.isEvm) { "Swap and Bridge currently require an EVM or Bitcoin source" }
        val nonEvmDestination = !toAsset.blockchain.isEvm
        if (nonEvmDestination) {
            // LI.FI routes EVM → native BTC/SOL (receive-only); the transactionRequest
            // stays a normal EVM transaction, so the signing path is unchanged.
            check(
                toAsset.isNative &&
                    (toAsset.blockchain == Blockchain.BITCOIN || toAsset.blockchain == Blockchain.SOLANA),
            ) { "Bridge destinations support EVM networks plus native BTC and SOL" }
        }

        val fromAccount = wallet.account(fromAsset.blockchain) ?: error("Source account is unavailable")
        val toAccount = wallet.account(toAsset.blockchain) ?: error("Destination account is unavailable")
        val fromAmountAtomic = RpcService.atomicAmount(amount, fromAsset.decimals)
        check(amount.trim().toBigDecimal() <= BigDecimal.valueOf(fromAsset.balance)) {
            "Insufficient ${fromAsset.symbol} balance"
        }

        val lifi = lifiService.quote(
            fromAsset = fromAsset,
            toAsset = toAsset,
            fromAddress = fromAccount.address,
            toAddress = toAccount.address,
            fromAmountAtomic = fromAmountAtomic,
        )
        val requestValue = lifi.transactionValueHex.hexQuantity()
        if (fromAsset.isNative) {
            check(requestValue <= fromAmountAtomic) { "Quote requests more native value than reviewed" }
        } else {
            check(requestValue == java.math.BigInteger.ZERO) { "Token quote requests unexpected native value" }
        }

        val callDraft = rpcService.planEvmContractCall(
            blockchain = fromAsset.blockchain,
            from = fromAccount.address,
            transactionTo = lifi.transactionTo,
            dataHex = lifi.transactionDataHex,
            valueHex = lifi.transactionValueHex,
            symbol = fromAsset.symbol,
            amount = amount.trim(),
            amountAtomic = fromAmountAtomic,
            assetContractAddress = fromAsset.contractAddress,
            recipient = toAccount.address,
        )

        val approvalDrafts = if (!fromAsset.isNative && lifi.approvalAddress != null) {
            val tokenContract = requireNotNull(fromAsset.contractAddress)
            val allowance = rpcService.erc20Allowance(
                fromAsset.blockchain,
                tokenContract,
                fromAccount.address,
                lifi.approvalAddress,
            )
            if (allowance >= fromAmountAtomic) {
                emptyList()
            } else {
                buildList {
                    if (allowance.signum() > 0) {
                        add(
                            rpcService.planErc20Approval(
                                fromAsset.blockchain,
                                fromAccount.address,
                                tokenContract,
                                lifi.approvalAddress,
                                java.math.BigInteger.ZERO,
                                fromAsset.symbol,
                            ),
                        )
                    }
                    add(
                        rpcService.planErc20Approval(
                            fromAsset.blockchain,
                            fromAccount.address,
                            tokenContract,
                            lifi.approvalAddress,
                            fromAmountAtomic,
                            fromAsset.symbol,
                        ),
                    )
                }
            }
        } else {
            emptyList()
        }

        val maximumFee = callDraft.maximumFeeWei +
            approvalDrafts.fold(java.math.BigInteger.ZERO) { total, draft -> total + draft.maximumFeeWei }
        val nativeAsset = wallet.assets.firstOrNull {
            it.blockchain == fromAsset.blockchain && it.isNative
        } ?: error("Native fee asset is unavailable")
        val feeNative = maximumFee.toBigDecimal().movePointLeft(fromAsset.blockchain.decimals)
        if (fromAsset.isNative) {
            check(amount.trim().toBigDecimal().add(feeNative) <= BigDecimal.valueOf(fromAsset.balance)) {
                "Insufficient ${fromAsset.symbol} after maximum network fees"
            }
        } else {
            check(feeNative <= BigDecimal.valueOf(nativeAsset.balance)) {
                "Insufficient ${nativeAsset.symbol} for maximum network fees"
            }
        }

        ExchangeQuote(
            fromAssetId = fromAsset.id,
            toAssetId = toAsset.id,
            fromBlockchain = fromAsset.blockchain,
            toBlockchain = toAsset.blockchain,
            fromAddress = fromAccount.address,
            toAddress = toAccount.address,
            fromAmount = amount.trim(),
            fromAmountAtomic = fromAmountAtomic,
            toAmount = lifi.toAmount,
            minimumToAmount = lifi.minimumToAmount,
            provider = lifi.provider,
            estimatedDurationSeconds = lifi.estimatedDurationSeconds,
            approvalAddress = lifi.approvalAddress,
            transactionTo = lifi.transactionTo,
            transactionDataHex = lifi.transactionDataHex,
            transactionValueHex = lifi.transactionValueHex,
            callDraft = callDraft,
            approvalDrafts = approvalDrafts,
        )
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    /**
     * Bitcoin as bridge source, mirroring iOS: LI.FI returns a deposit address in
     * `transactionRequest.to` and the exact sats in `transactionRequest.value`;
     * the spend itself is a plain Bitcoin transaction through the existing signer.
     */
    private suspend fun prepareBitcoinExchange(
        wallet: WalletSnapshot,
        fromAsset: Asset,
        toAsset: Asset,
        amount: String,
    ): ExchangeQuote {
        check(fromAsset.isNative) { "Only native BTC can be bridged" }
        check(wallet.kind == WalletKind.MNEMONIC) { "A mnemonic wallet is required for Bitcoin bridging" }
        check(toAsset.blockchain.isEvm || (toAsset.blockchain == Blockchain.SOLANA && toAsset.isNative)) {
            "Bitcoin can bridge to EVM networks or native SOL"
        }
        val fromAccount = wallet.account(Blockchain.BITCOIN) ?: error("Bitcoin account is unavailable")
        val toAccount = wallet.account(toAsset.blockchain) ?: error("Destination account is unavailable")
        val fromAmountAtomic = RpcService.atomicAmount(amount, fromAsset.decimals)
        check(amount.trim().toBigDecimal() <= BigDecimal.valueOf(fromAsset.balance)) {
            "Insufficient BTC balance"
        }

        val lifi = lifiService.quote(
            fromAsset = fromAsset,
            toAsset = toAsset,
            fromAddress = fromAccount.address,
            toAddress = toAccount.address,
            fromAmountAtomic = fromAmountAtomic,
        )
        val depositSats = lifi.transactionValueHex.hexQuantity()
        check(depositSats.signum() > 0 && depositSats <= fromAmountAtomic) {
            "Quote requests more BTC than reviewed"
        }
        check(walletEngine.validateAddress(Blockchain.BITCOIN, lifi.transactionTo)) {
            "Quote returned an invalid Bitcoin deposit address"
        }
        val depositAmount = depositSats.toBigDecimal()
            .movePointLeft(Blockchain.BITCOIN.decimals)
            .toPlainString()
        val bitcoinDraft = rpcService.planBitcoinTransfer(fromAccount.address, lifi.transactionTo, depositAmount)
        val total = depositAmount.toBigDecimal().add(BigDecimal.valueOf(bitcoinDraft.maximumNetworkFee))
        check(total <= BigDecimal.valueOf(fromAsset.balance)) { "Insufficient BTC after the network fee" }

        return ExchangeQuote(
            fromAssetId = fromAsset.id,
            toAssetId = toAsset.id,
            fromBlockchain = Blockchain.BITCOIN,
            toBlockchain = toAsset.blockchain,
            fromAddress = fromAccount.address,
            toAddress = toAccount.address,
            fromAmount = depositAmount,
            fromAmountAtomic = depositSats,
            toAmount = lifi.toAmount,
            minimumToAmount = lifi.minimumToAmount,
            provider = lifi.provider,
            estimatedDurationSeconds = lifi.estimatedDurationSeconds,
            approvalAddress = null,
            transactionTo = lifi.transactionTo,
            transactionDataHex = lifi.transactionDataHex,
            transactionValueHex = lifi.transactionValueHex,
            callDraft = null,
            bitcoinDraft = bitcoinDraft,
        )
    }

    private suspend fun executeBitcoinExchange(
        reviewed: ExchangeQuote,
        reviewedDraft: BitcoinTransferDraft,
        secret: SecureWalletStore.WalletSecret,
    ): WalletTransaction {
        val currentDraft = rpcService.planBitcoinTransfer(
            from = reviewed.fromAddress,
            recipient = reviewed.transactionTo,
            amount = reviewed.fromAmount,
        )
        val acceptedMaximumFeeValue = reviewedDraft.feeSatoshis.toBigInteger()
            .multiply(java.math.BigInteger.valueOf(125))
            .divide(java.math.BigInteger.valueOf(100))
        check(acceptedMaximumFeeValue.signum() >= 0 && acceptedMaximumFeeValue.bitLength() <= 63) {
            "Reviewed Bitcoin fee is outside the supported range"
        }
        val acceptedMaximumFee = acceptedMaximumFeeValue.toLong()
        check(currentDraft.feeSatoshis <= acceptedMaximumFee) {
            "Bitcoin network fee increased by more than 25%; request a fresh quote"
        }
        val signed = withContext(Dispatchers.Default) {
            walletEngine.signBitcoinTransaction(secret.kind, secret.value, currentDraft)
        }
        check(signed.feeSatoshis <= acceptedMaximumFee) { "Wallet Core fee exceeds the reviewed maximum" }
        val txid = rpcService.broadcastBitcoinTransaction(signed.rawHex)
        val feeBtc = signed.feeSatoshis.toBigDecimal().movePointLeft(Blockchain.BITCOIN.decimals).toDouble()
        val transaction = WalletTransaction(
            hash = txid,
            blockchain = Blockchain.BITCOIN,
            type = reviewed.type,
            status = TransactionStatus.PENDING,
            amount = currentDraft.amount.toDouble(),
            symbol = "BTC",
            timestamp = Instant.now(),
            from = reviewed.fromAddress,
            to = reviewed.toAddress,
            fee = feeBtc,
        )
        mutableState.update { state ->
            val currentWallet = state.wallet
            val updatedWallet = currentWallet?.copy(
                assets = currentWallet.assets.map { asset ->
                    if (asset.blockchain == Blockchain.BITCOIN && asset.isNative) {
                        asset.copy(balance = (asset.balance - transaction.amount - feeBtc).coerceAtLeast(0.0))
                    } else {
                        asset
                    }
                },
                lastUpdated = Instant.now(),
            )
            state.copy(
                wallet = updatedWallet,
                transactions = listOf(transaction) + state.transactions.filterNot { it.hash == txid },
            )
        }
        return transaction
    }

    suspend fun executeExchange(reviewed: ExchangeQuote): Result<WalletTransaction> {
        if (mutableState.value.isSending) {
            return Result.failure(IllegalStateException("A transaction is already being sent"))
        }
        mutableState.update { it.copy(isSending = true, errorMessage = null) }
        val result = runCatching {
            check(walletEngine.isProductionReady) { "Swap and Bridge are unavailable in the preview build" }
            check(Duration.between(reviewed.requestedAt, Instant.now()).toMinutes() < 3) {
                "Quote expired; request a fresh quote"
            }
            val wallet = mutableState.value.wallet ?: error("No active wallet")
            val fromAsset = wallet.assets.firstOrNull { it.id == reviewed.fromAssetId }
                ?: error("Source asset changed")
            val toAsset = wallet.assets.firstOrNull { it.id == reviewed.toAssetId }
                ?: error("Destination asset changed")
            val fromAccount = wallet.account(reviewed.fromBlockchain) ?: error("Source account is unavailable")
            val toAccount = wallet.account(reviewed.toBlockchain) ?: error("Destination account is unavailable")
            check(fromAccount.address.equals(reviewed.fromAddress, ignoreCase = true) &&
                toAccount.address.equals(reviewed.toAddress, ignoreCase = true)
            ) { "Wallet account changed" }
            check(fromAsset.blockchain == reviewed.fromBlockchain &&
                toAsset.blockchain == reviewed.toBlockchain
            ) { "Reviewed networks changed" }
            check(RpcService.atomicAmount(reviewed.fromAmount, fromAsset.decimals) == reviewed.fromAmountAtomic) {
                "Reviewed amount changed"
            }

            val secret = secureStore.load() ?: error("Wallet credentials are unavailable")
            reviewed.bitcoinDraft?.let { reviewedBitcoin ->
                return@runCatching executeBitcoinExchange(reviewed, reviewedBitcoin, secret)
            }
            val reviewedCall = reviewed.callDraft ?: error("Reviewed quote is incomplete")
            var actualFeeWei = java.math.BigInteger.ZERO
            if (!fromAsset.isNative && reviewed.approvalAddress != null) {
                val tokenContract = requireNotNull(fromAsset.contractAddress)
                var allowance = rpcService.erc20Allowance(
                    reviewed.fromBlockchain,
                    tokenContract,
                    fromAccount.address,
                    reviewed.approvalAddress,
                )
                val approvals = buildList {
                    if (allowance < reviewed.fromAmountAtomic) {
                        if (allowance.signum() > 0) add(java.math.BigInteger.ZERO)
                        add(reviewed.fromAmountAtomic)
                    }
                }
                approvals.forEachIndexed { index, approvalAmount ->
                    val approval = rpcService.planErc20Approval(
                        reviewed.fromBlockchain,
                        fromAccount.address,
                        tokenContract,
                        reviewed.approvalAddress,
                        approvalAmount,
                        fromAsset.symbol,
                    )
                    val reviewedApproval = reviewed.approvalDrafts.getOrNull(index)
                        ?: error("Token approval requirements changed; request a fresh quote")
                    check(approval.maximumFeeWei <= reviewedApproval.maximumFeeWei.percent(125)) {
                        "Approval fee increased by more than 25%; request a fresh quote"
                    }
                    val signedApproval = withContext(Dispatchers.Default) {
                        walletEngine.signEvmTransaction(secret.kind, secret.value, approval)
                    }
                    val approvalHash = rpcService.broadcastRawTransaction(reviewed.fromBlockchain, signedApproval)
                    rpcService.waitForSuccessfulEvmReceipt(reviewed.fromBlockchain, approvalHash)
                    actualFeeWei += approval.maximumFeeWei
                    allowance = approvalAmount
                }
            }

            val callDraft = rpcService.planEvmContractCall(
                blockchain = reviewed.fromBlockchain,
                from = reviewed.fromAddress,
                transactionTo = reviewed.transactionTo,
                dataHex = reviewed.transactionDataHex,
                valueHex = reviewed.transactionValueHex,
                symbol = fromAsset.symbol,
                amount = reviewed.fromAmount,
                amountAtomic = reviewed.fromAmountAtomic,
                assetContractAddress = fromAsset.contractAddress,
                recipient = reviewed.toAddress,
            )
            check(callDraft.maximumFeeWei <= reviewedCall.maximumFeeWei.percent(125)) {
                "Network fee increased by more than 25%; request a fresh quote"
            }
            val signed = withContext(Dispatchers.Default) {
                walletEngine.signEvmTransaction(secret.kind, secret.value, callDraft)
            }
            val hash = rpcService.broadcastRawTransaction(reviewed.fromBlockchain, signed)
            sendPlatformFeeBestEffort(callDraft, secret)
            actualFeeWei += callDraft.maximumFeeWei
            val maximumFeeNative = actualFeeWei.toBigDecimal()
                .movePointLeft(reviewed.fromBlockchain.decimals)
                .toDouble()
            val transaction = WalletTransaction(
                hash = hash,
                blockchain = reviewed.fromBlockchain,
                type = reviewed.type,
                status = TransactionStatus.PENDING,
                amount = reviewed.fromAmount.toDouble(),
                symbol = fromAsset.symbol,
                timestamp = Instant.now(),
                from = reviewed.fromAddress,
                to = reviewed.toAddress,
                fee = maximumFeeNative,
            )
            mutableState.update { state ->
                val currentWallet = state.wallet
                val updatedWallet = currentWallet?.copy(
                    assets = currentWallet.assets.map { asset ->
                        val sourceAsset = asset.id == fromAsset.id
                        val feeAsset = asset.blockchain == reviewed.fromBlockchain && asset.isNative
                        val debit = when {
                            sourceAsset && asset.isNative -> transaction.amount + maximumFeeNative
                            sourceAsset -> transaction.amount
                            feeAsset -> maximumFeeNative
                            else -> 0.0
                        }
                        if (debit == 0.0) asset else asset.copy(
                            balance = (asset.balance - debit).coerceAtLeast(0.0),
                        )
                    },
                    lastUpdated = Instant.now(),
                )
                state.copy(
                    wallet = updatedWallet,
                    transactions = listOf(transaction) + state.transactions.filterNot {
                        it.hash.equals(hash, ignoreCase = true)
                    },
                )
            }
            transaction
        }
        mutableState.update { state ->
            result.fold(
                onSuccess = { state.copy(isSending = false, errorMessage = null) },
                onFailure = { state.copy(isSending = false, errorMessage = it.userFacingMessage()) },
            )
        }
        return result
    }

    val isP2PBoardConfigured: Boolean
        get() = p2pMarketService.isConfigured

    /** Public offer-board listings, with the user's own offers filtered out. */
    suspend fun loadP2PListings(): Result<List<P2PListing>> = runCatching {
        val ownAddresses = mutableState.value.wallet?.accounts
            ?.map { it.address.lowercase() }
            ?.toSet()
            .orEmpty()
        p2pMarketService.fetchListings().filterNot {
            it.offer.signerWallet.lowercase() in ownAddresses
        }
    }

    /**
     * Validate a pasted/scanned P2P offer from the buyer's side. The contract
     * enforces the order signature; anything doubtful is caught by the exact
     * eth_call dry-run before any gas is spent, mirroring the iOS accept flow.
     */
    suspend fun prepareP2PAccept(payload: String): Result<P2PAcceptPlan> = runCatching {
        check(walletEngine.isProductionReady) { "P2P is unavailable in the preview build" }
        val offer = P2POffer.parse(payload)
        val blockchain = offer.blockchain ?: error("This offer uses an unsupported network")
        val contract = P2PService.contractFor(blockchain)
            ?: error("P2P trading is not available on ${blockchain.displayName}")
        check(blockchain in mutableState.value.settings.enabledNetworks) {
            "Enable ${blockchain.displayName} in Manage Networks"
        }
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        val taker = wallet.account(blockchain)?.address ?: error("Wallet account is unavailable")
        check(!taker.equals(offer.signerWallet, ignoreCase = true)) { "This is your own offer" }
        check(!offer.isExpired) { "The offer has expired" }

        val liveFee = rpcService.ethCall(blockchain, contract, P2PService.protocolFeeCallData()).hexQuantity()
        check(liveFee == java.math.BigInteger.valueOf(offer.protocolFeeBps.toLong())) {
            "The offer no longer matches the live AirSwap protocol fee"
        }
        val nonceUsed = rpcService
            .ethCall(blockchain, contract, P2PService.nonceUsedCallData(offer.signerWallet, offer.nonce))
            .hexQuantity()
        check(nonceUsed.signum() == 0) { "The offer was already filled or cancelled" }

        val requiredMaker = P2PService.requiredMakerUnits(offer)
        val makerBalance = rpcService
            .ethCall(blockchain, offer.signerToken, RpcService.erc20BalanceOfData(offer.signerWallet))
            .hexQuantity()
        val makerAllowance = rpcService.erc20Allowance(blockchain, offer.signerToken, offer.signerWallet, contract)
        check(makerBalance >= requiredMaker && makerAllowance >= requiredMaker) {
            "The seller can no longer cover this offer"
        }

        val platformFeeUnits = platformFeeUnits(offer.senderAmount)
        val requiredTaker = offer.senderAmount + platformFeeUnits
        val takerBalance = rpcService
            .ethCall(blockchain, offer.senderToken, RpcService.erc20BalanceOfData(taker))
            .hexQuantity()
        // Paying with the wrapped native coin? The shortfall auto-wraps 1:1.
        var wrapShortfallUnits = java.math.BigInteger.ZERO
        if (takerBalance < requiredTaker) {
            check(P2PService.isWrappedNative(blockchain, offer.senderToken)) {
                "Insufficient ${offer.senderSymbol} balance to pay this offer"
            }
            wrapShortfallUnits = requiredTaker.subtract(takerBalance)
        }

        val allowance = rpcService.erc20Allowance(blockchain, offer.senderToken, taker, contract)
        val needsApproval = allowance < offer.senderAmount
        val needsApprovalReset = needsApproval && allowance.signum() > 0

        val fillCallData = P2PService.fillCallData(offer, taker)
        if (!needsApproval && wrapShortfallUnits.signum() == 0) {
            runCatching {
                rpcService.ethCall(blockchain, contract, fillCallData, from = taker)
            }.getOrElse { error("The trade would fail on-chain; ask the seller for a fresh offer") }
        }

        val gasPrice = rpcService.gasPriceWei(blockchain)
        val approvalCount = (if (needsApproval) 1 else 0) + (if (needsApprovalReset) 1 else 0)
        val wrapCount = if (wrapShortfallUnits.signum() > 0) 1 else 0
        val maxFeeWei = gasPrice.multiply(
            java.math.BigInteger.valueOf(300_000L + 80_000L * approvalCount + 60_000L * wrapCount),
        )
        val estimatedMaxFeeNative = maxFeeWei.toBigDecimal().movePointLeft(blockchain.decimals).toDouble()
        val wrapShortfallNative = wrapShortfallUnits.toBigDecimal()
            .movePointLeft(blockchain.decimals)
            .toDouble()
        val nativeBalance = wallet.assets
            .firstOrNull { it.blockchain == blockchain && it.isNative }?.balance ?: 0.0
        check(nativeBalance >= estimatedMaxFeeNative + wrapShortfallNative) {
            "Insufficient ${blockchain.symbol} for the network fee"
        }

        P2PAcceptPlan(
            offer = offer,
            blockchain = blockchain,
            contract = contract,
            takerAddress = taker,
            platformFeeUnits = platformFeeUnits,
            needsApproval = needsApproval,
            needsApprovalReset = needsApprovalReset,
            estimatedMaxFeeNative = estimatedMaxFeeNative,
            fillCallDataHex = fillCallData,
            wrapShortfallUnits = wrapShortfallUnits,
        )
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    suspend fun executeP2PAccept(plan: P2PAcceptPlan): Result<WalletTransaction> {
        if (mutableState.value.isSending) {
            return Result.failure(IllegalStateException("A transaction is already being sent"))
        }
        mutableState.update { it.copy(isSending = true, errorMessage = null) }
        val result = runCatching {
            check(walletEngine.isProductionReady) { "P2P is unavailable in the preview build" }
            check(Duration.between(plan.requestedAt, Instant.now()).toMinutes() < 5) {
                "Offer review expired; validate the offer again"
            }
            val offer = plan.offer
            check(!offer.isExpired) { "The offer has expired" }
            val nonceUsed = rpcService
                .ethCall(plan.blockchain, plan.contract, P2PService.nonceUsedCallData(offer.signerWallet, offer.nonce))
                .hexQuantity()
            check(nonceUsed.signum() == 0) { "The offer was already filled or cancelled" }
            val secret = secureStore.load() ?: error("Wallet credentials are unavailable")

            // Wrap the native shortfall 1:1 first when paying with the wrapped native coin.
            wrapNativeShortfall(
                blockchain = plan.blockchain,
                wrappedAddress = offer.senderToken,
                wrappedSymbol = offer.senderSymbol,
                owner = plan.takerAddress,
                requiredUnits = offer.senderAmount + plan.platformFeeUnits,
                secret = secret,
            )

            var allowance = rpcService.erc20Allowance(plan.blockchain, offer.senderToken, plan.takerAddress, plan.contract)
            if (allowance < offer.senderAmount) {
                val approvals = buildList {
                    if (allowance.signum() > 0) add(java.math.BigInteger.ZERO)
                    add(offer.senderAmount)
                }
                approvals.forEach { approvalAmount ->
                    val approval = rpcService.planErc20Approval(
                        plan.blockchain,
                        plan.takerAddress,
                        offer.senderToken,
                        plan.contract,
                        approvalAmount,
                        offer.senderSymbol,
                    )
                    val signedApproval = withContext(Dispatchers.Default) {
                        walletEngine.signEvmTransaction(secret.kind, secret.value, approval)
                    }
                    val approvalHash = rpcService.broadcastRawTransaction(plan.blockchain, signedApproval)
                    rpcService.waitForSuccessfulEvmReceipt(plan.blockchain, approvalHash)
                    allowance = approvalAmount
                }
            }

            // Dry-run the exact fill against mined state — a revert costs nothing.
            runCatching {
                rpcService.ethCall(plan.blockchain, plan.contract, plan.fillCallDataHex, from = plan.takerAddress)
            }.getOrElse { error("The trade would fail on-chain; ask the seller for a fresh offer") }

            val callDraft = rpcService.planEvmContractCall(
                blockchain = plan.blockchain,
                from = plan.takerAddress,
                transactionTo = plan.contract,
                dataHex = plan.fillCallDataHex,
                valueHex = "0x0",
                symbol = offer.senderSymbol,
                amount = offer.senderAmountDecimal.toPlainString(),
                amountAtomic = offer.senderAmount,
                assetContractAddress = offer.senderToken,
                recipient = offer.signerWallet,
            )
            val signed = withContext(Dispatchers.Default) {
                walletEngine.signEvmTransaction(secret.kind, secret.value, callDraft)
            }
            val hash = rpcService.broadcastRawTransaction(plan.blockchain, signed)
            sendPlatformFeeBestEffort(callDraft, secret)
            val transaction = WalletTransaction(
                hash = hash,
                blockchain = plan.blockchain,
                type = TransactionType.SWAP,
                status = TransactionStatus.PENDING,
                amount = offer.senderAmountDecimal.toDouble(),
                symbol = offer.senderSymbol,
                timestamp = Instant.now(),
                from = plan.takerAddress,
                to = offer.signerWallet,
                fee = callDraft.maximumFeeNative,
            )
            mutableState.update { state ->
                state.copy(
                    transactions = listOf(transaction) + state.transactions.filterNot {
                        it.hash.equals(hash, ignoreCase = true)
                    },
                )
            }
            transaction
        }
        mutableState.update { state ->
            result.fold(
                onSuccess = { state.copy(isSending = false, errorMessage = null) },
                onFailure = { state.copy(isSending = false, errorMessage = it.userFacingMessage()) },
            )
        }
        return result
    }

    /**
     * When the balance of a wrapped-native token is short, wrap the missing
     * amount 1:1 via deposit() and wait for the receipt (no-op when covered,
     * throws when the token is not a wrapped native).
     */
    private suspend fun wrapNativeShortfall(
        blockchain: Blockchain,
        wrappedAddress: String,
        wrappedSymbol: String,
        owner: String,
        requiredUnits: java.math.BigInteger,
        secret: SecureWalletStore.WalletSecret,
    ) {
        val tokenBalance = rpcService
            .ethCall(blockchain, wrappedAddress, RpcService.erc20BalanceOfData(owner))
            .hexQuantity()
        if (tokenBalance >= requiredUnits) return
        check(P2PService.isWrappedNative(blockchain, wrappedAddress)) {
            "Insufficient $wrappedSymbol balance"
        }
        val shortfall = requiredUnits.subtract(tokenBalance)
        val wrapDraft = rpcService.planEvmContractCall(
            blockchain = blockchain,
            from = owner,
            transactionTo = wrappedAddress,
            dataHex = P2PService.depositCallData(),
            valueHex = "0x${shortfall.toString(16)}",
            symbol = wrappedSymbol,
            amount = shortfall.toBigDecimal().movePointLeft(blockchain.decimals).toPlainString(),
            amountAtomic = shortfall,
            assetContractAddress = null,
            recipient = owner,
        )
        val signedWrap = withContext(Dispatchers.Default) {
            walletEngine.signEvmTransaction(secret.kind, secret.value, wrapDraft)
        }
        val wrapHash = rpcService.broadcastRawTransaction(blockchain, signedWrap)
        rpcService.waitForSuccessfulEvmReceipt(blockchain, wrapHash)
    }

    /**
     * Create and sign a sell offer (AirSwap OrderERC20, senderWallet = 0 so
     * anyone may fill). Mirrors the iOS creation flow: live protocol fee is
     * part of the signed hash, native coins sell as their wrapped form, and
     * the swap contract gets an allowance for amount + fee.
     */
    suspend fun createP2POffer(
        sellAssetId: String,
        receiveTokenAddress: String,
        receiveTokenSymbol: String,
        receiveTokenDecimals: Int,
        sellAmount: String,
        receiveAmount: String,
        validForSeconds: Long,
    ): Result<String> = runCatching {
        check(walletEngine.isProductionReady) { "P2P is unavailable in the preview build" }
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        val sellAsset = wallet.assets.firstOrNull { it.id == sellAssetId }
            ?: error("Selected asset is unavailable")
        val blockchain = sellAsset.blockchain
        val contract = P2PService.contractFor(blockchain)
            ?: error("P2P trading is not available on ${blockchain.displayName}")
        val chainId = requireNotNull(blockchain.chainId)
        val signerWallet = wallet.account(blockchain)?.address ?: error("Wallet account is unavailable")

        // Native coins are sold as their canonical wrapped ERC-20 (1:1).
        val (sellTokenAddress, sellSymbol, sellDecimals) = if (sellAsset.isNative) {
            val wrapped = P2PService.wrappedNativeToken(blockchain)
                ?: error("Only ERC-20 tokens can be traded on ${blockchain.displayName}")
            Triple(wrapped.first, wrapped.second, 18)
        } else {
            Triple(
                requireNotNull(sellAsset.contractAddress),
                sellAsset.symbol,
                sellAsset.decimals,
            )
        }
        check(!receiveTokenAddress.equals(sellTokenAddress, ignoreCase = true)) {
            "Select a different token to receive"
        }
        check(RpcService.isEvmAddress(receiveTokenAddress)) { "Invalid receive token" }

        // The protocol fee is part of the signed order hash — read the live value.
        val protocolFeeBps = rpcService
            .ethCall(blockchain, contract, P2PService.protocolFeeCallData())
            .hexQuantity()
            .toInt()
        check(protocolFeeBps in 0..1_000) { "AirSwap returned an unexpected protocol fee" }

        val signerUnits = RpcService.atomicAmount(sellAmount, sellDecimals)
        val senderUnits = RpcService.atomicAmount(receiveAmount, receiveTokenDecimals)
        val requiredUnits = signerUnits + signerUnits
            .multiply(java.math.BigInteger.valueOf(protocolFeeBps.toLong()))
            .divide(java.math.BigInteger.valueOf(10_000))

        val secret = secureStore.load() ?: error("Wallet credentials are unavailable")
        if (sellAsset.isNative) {
            wrapNativeShortfall(blockchain, sellTokenAddress, sellSymbol, signerWallet, requiredUnits, secret)
        }
        val sellerBalance = rpcService
            .ethCall(blockchain, sellTokenAddress, RpcService.erc20BalanceOfData(signerWallet))
            .hexQuantity()
        check(sellerBalance >= requiredUnits) { "Insufficient $sellSymbol to cover the offer and protocol fee" }

        var allowance = rpcService.erc20Allowance(blockchain, sellTokenAddress, signerWallet, contract)
        if (allowance < requiredUnits) {
            val approvals = buildList {
                if (allowance.signum() > 0) add(java.math.BigInteger.ZERO)
                add(requiredUnits)
            }
            approvals.forEach { approvalAmount ->
                val approval = rpcService.planErc20Approval(
                    blockchain,
                    signerWallet,
                    sellTokenAddress,
                    contract,
                    approvalAmount,
                    sellSymbol,
                )
                val signedApproval = withContext(Dispatchers.Default) {
                    walletEngine.signEvmTransaction(secret.kind, secret.value, approval)
                }
                val approvalHash = rpcService.broadcastRawTransaction(blockchain, signedApproval)
                rpcService.waitForSuccessfulEvmReceipt(blockchain, approvalHash)
                allowance = approvalAmount
            }
        }

        val nonce = java.math.BigInteger(64, java.security.SecureRandom()).max(java.math.BigInteger.ONE)
        val expiry = Instant.now().epochSecond + validForSeconds
        val digest = P2PService.orderDigest(
            keccak256 = walletEngine::keccak256,
            chainId = chainId,
            contract = contract,
            nonce = nonce,
            expiry = expiry,
            signerWallet = signerWallet,
            signerToken = sellTokenAddress,
            signerAmount = signerUnits,
            protocolFeeBps = protocolFeeBps,
            senderToken = receiveTokenAddress,
            senderAmount = senderUnits,
        )
        val signature = withContext(Dispatchers.Default) {
            walletEngine.signEvmDigest(secret.kind, secret.value, digest)
        }
        val offer = P2POffer(
            version = 1,
            chainId = chainId,
            nonce = nonce,
            expiry = expiry,
            signerWallet = signerWallet.lowercase(),
            signerToken = sellTokenAddress.lowercase(),
            signerAmount = signerUnits,
            signerSymbol = sellSymbol,
            signerDecimals = sellDecimals,
            senderToken = receiveTokenAddress.lowercase(),
            senderAmount = senderUnits,
            senderSymbol = receiveTokenSymbol,
            senderDecimals = receiveTokenDecimals,
            protocolFeeBps = protocolFeeBps,
            sigV = (signature[64].toInt() and 0xff) + 27,
            sigR = "0x" + signature.copyOfRange(0, 32).toHexString(),
            sigS = "0x" + signature.copyOfRange(32, 64).toHexString(),
        )
        offer.encodePayload()
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun platformFeeUnits(amountAtomic: java.math.BigInteger): java.math.BigInteger {
        val recipient = BuildConfig.PLATFORM_FEE_RECIPIENT.trim()
        if (BuildConfig.PLATFORM_FEE_BPS <= 0 || !RpcService.isEvmAddress(recipient)) {
            return java.math.BigInteger.ZERO
        }
        return amountAtomic
            .multiply(java.math.BigInteger.valueOf(BuildConfig.PLATFORM_FEE_BPS.toLong()))
            .divide(java.math.BigInteger.valueOf(10_000))
    }

    fun removeCustomToken(token: KnownToken) {
        val updated = mutableState.value.customTokens.filterNot {
            it.blockchain == token.blockchain &&
                it.contractAddress.equals(token.contractAddress, ignoreCase = true)
        }
        customTokenStore.save(updated)
        mutableState.update { state ->
            val wallet = state.wallet?.let { currentWallet ->
                currentWallet.copy(
                    assets = currentWallet.assets.filterNot { asset ->
                        asset.blockchain == token.blockchain &&
                            asset.contractAddress.equals(token.contractAddress, ignoreCase = true)
                    },
                    lastUpdated = Instant.now(),
                )
            }
            state.copy(customTokens = updated, wallet = wallet, errorMessage = null)
        }
    }

    private fun tokensForBlockchain(blockchain: Blockchain): List<KnownToken> =
        (KnownTokenCatalog.forBlockchain(blockchain) + mutableState.value.customTokens.filter {
            it.blockchain == blockchain
        }).distinctBy { it.contractAddress.lowercase() }

    fun setBalancesHidden(hidden: Boolean) {
        settingsStore.setHideBalances(hidden)
        mutableState.update { it.copy(settings = it.settings.copy(hideBalances = hidden)) }
    }

    fun setAutoLockDuration(duration: AutoLockDuration) {
        settingsStore.setAutoLockDuration(duration)
        mutableState.update { it.copy(settings = it.settings.copy(autoLockDuration = duration)) }
    }

    fun setCurrency(code: String) {
        settingsStore.setCurrency(code)
        mutableState.update { it.copy(settings = it.settings.copy(currencyCode = code)) }
    }

    fun setFavorite(symbol: String, favorite: Boolean) {
        val normalized = symbol.uppercase()
        val current = mutableState.value.settings.favoriteSymbols
        val updated = if (favorite) current + normalized else current - normalized
        settingsStore.setFavoriteSymbols(updated)
        mutableState.update { it.copy(settings = it.settings.copy(favoriteSymbols = updated)) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settingsStore.setNotificationsEnabled(enabled)
        mutableState.update { it.copy(settings = it.settings.copy(notificationsEnabled = enabled)) }
    }

    fun setTimeZone(timeZoneId: String) {
        require(
            timeZoneId == AUTOMATIC_TIME_ZONE_ID || timeZoneId in ZoneId.getAvailableZoneIds(),
        ) { "Unknown time zone" }
        settingsStore.setTimeZone(timeZoneId)
        mutableState.update { it.copy(settings = it.settings.copy(timeZoneId = timeZoneId)) }
    }

    fun setColorTheme(theme: AppColorTheme) {
        settingsStore.setColorTheme(theme)
        mutableState.update { it.copy(settings = it.settings.copy(colorTheme = theme)) }
    }

    fun setAssetListStyle(style: AssetListStyle) {
        settingsStore.setAssetListStyle(style)
        mutableState.update { it.copy(settings = it.settings.copy(assetListStyle = style)) }
    }

    suspend fun setNetworkEnabled(blockchain: Blockchain, enabled: Boolean): Result<Unit> = runCatching {
        val wallet = mutableState.value.wallet ?: error("No active wallet")
        check(wallet.account(blockchain) != null) {
            "${blockchain.displayName} is not available for this wallet type"
        }
        val current = mutableState.value.settings.enabledNetworks
        val updated = if (enabled) current + blockchain else current - blockchain
        check(updated.isNotEmpty()) { "At least one network must remain active" }
        settingsStore.setEnabledNetworks(updated)
        mutableState.update { state ->
            val trimmedWallet = state.wallet?.copy(
                assets = state.wallet.assets.filter { it.blockchain in updated },
                lastUpdated = Instant.now(),
            )
            state.copy(
                settings = state.settings.copy(enabledNetworks = updated),
                wallet = trimmedWallet,
                errorMessage = null,
            )
        }
        refresh()
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    fun addSavedAddress(name: String, address: String): Result<SavedAddress> = runCatching {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        require(trimmedName.isNotBlank()) { "Enter a name for this address" }
        require(trimmedAddress.isNotBlank()) { "Address cannot be empty" }
        check(mutableState.value.savedAddresses.none {
            it.address.equals(trimmedAddress, ignoreCase = true)
        }) { "This address is already saved" }
        val saved = SavedAddress(name = trimmedName, address = trimmedAddress)
        val updated = mutableState.value.savedAddresses + saved
        savedAddressStore.save(updated)
        mutableState.update { it.copy(savedAddresses = updated, errorMessage = null) }
        saved
    }.onFailure { error ->
        mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
    }

    fun removeSavedAddress(id: String) {
        val updated = mutableState.value.savedAddresses.filterNot { it.id == id }
        savedAddressStore.save(updated)
        mutableState.update { it.copy(savedAddresses = updated, errorMessage = null) }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun deleteWallet() {
        secureStore.clear()
        customTokenStore.clear()
        savedAddressStore.clear()
        mutableState.update {
            it.copy(
                wallet = null,
                transactions = emptyList(),
                customTokens = emptyList(),
                savedAddresses = emptyList(),
                errorMessage = null,
            )
        }
    }

    private suspend fun buildSnapshot(secret: SecureWalletStore.WalletSecret): WalletSnapshot {
        val addresses = withContext(Dispatchers.Default) {
            when (secret.kind) {
                WalletKind.MNEMONIC -> walletEngine.deriveAddresses(secret.value)
                WalletKind.PRIVATE_KEY -> {
                    val evmAddress = walletEngine.deriveEvmAddress(secret.value)
                    Blockchain.entries
                        .filter(Blockchain::isEvm)
                        .associateWith { evmAddress }
                }
            }
        }
        check(addresses.isNotEmpty()) { "No wallet accounts could be derived" }
        return WalletSnapshot(
            kind = secret.kind,
            accounts = addresses.map { (blockchain, address) -> WalletAccount(blockchain, address) },
        )
    }

    private fun normalizeMnemonic(value: String): String =
        value.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    private fun normalizePrivateKey(value: String): String =
        value.trim().removePrefix("0x").lowercase()

    private fun Throwable.userFacingMessage(): String =
        message?.takeIf(String::isNotBlank) ?: "The wallet operation failed"

    private fun String.hexQuantity(): java.math.BigInteger {
        val normalized = removePrefix("0x").ifEmpty { "0" }
        require(normalized.all { it.digitToIntOrNull(16) != null }) { "Invalid EVM value" }
        return java.math.BigInteger(normalized, 16)
    }

    private fun java.math.BigInteger.percent(value: Long): java.math.BigInteger =
        multiply(java.math.BigInteger.valueOf(value)).divide(java.math.BigInteger.valueOf(100))
}
