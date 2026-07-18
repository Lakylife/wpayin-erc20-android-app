package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.core.WalletEngine
import io.noriskservis.wpayin.wallet.model.AppSettings
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.KnownTokenCatalog
import io.noriskservis.wpayin.wallet.model.KnownToken
import io.noriskservis.wpayin.wallet.model.WalletAccount
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.model.WalletSnapshot
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.model.TransferDraft
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import java.time.Instant
import java.time.Duration
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
    val wallet: WalletSnapshot? = null,
    val transactions: List<WalletTransaction> = emptyList(),
    val customTokens: List<KnownToken> = emptyList(),
    val errorMessage: String? = null,
)

class WalletRepository(
    private val secureStore: SecureWalletStore,
    private val settingsStore: SettingsStore,
    private val walletEngine: WalletEngine,
    private val rpcService: RpcService,
    private val priceService: PriceService,
    private val explorerService: ExplorerService,
    private val customTokenStore: CustomTokenStore,
) {
    private val mutableState = MutableStateFlow(
        WalletState(isProductionWalletEnabled = walletEngine.isProductionReady),
    )
    val state: StateFlow<WalletState> = mutableState.asStateFlow()

    suspend fun initialize() {
        val settings = settingsStore.load()
        val customTokens = customTokenStore.load()
        val wallet = runCatching {
            secureStore.load()?.let { buildSnapshot(it) }
        }.onFailure { error ->
            mutableState.update { it.copy(errorMessage = error.userFacingMessage()) }
        }.getOrNull()

        mutableState.update {
            it.copy(
                isInitializing = false,
                settings = settings,
                wallet = wallet,
                customTokens = customTokens,
            )
        }

        if (wallet != null) refresh()
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
            ).awaitAll()
        }
    }

    suspend fun refreshBalances() {
        val current = mutableState.value.wallet ?: return
        if (mutableState.value.isRefreshing) return
        mutableState.update { it.copy(isRefreshing = true, errorMessage = null) }

        val result = runCatching {
            val knownTokens = current.accounts.flatMap { tokensForBlockchain(it.blockchain) }
            val priceIds = current.accounts.map { it.blockchain.coinGeckoId } +
                knownTokens.map { it.coinGeckoId }
            val prices = runCatching { priceService.usdPricesForIds(priceIds) }
                .getOrDefault(emptyMap())
            val failures = AtomicInteger(0)
            val previousAssets = current.assets.associateBy(Asset::id)
            val assets = supervisorScope {
                val nativeAssets = current.accounts.map { account ->
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
                val tokenAssets = current.accounts.flatMap { account ->
                    tokensForBlockchain(account.blockchain).map { token ->
                        async {
                            val assetId = "${account.blockchain.name}:${token.contractAddress.lowercase()}"
                            val previous = previousAssets[assetId]
                            val balance = runCatching {
                                rpcService.erc20Balance(token, account.address)
                            }.getOrElse {
                                failures.incrementAndGet()
                                previous?.balance ?: 0.0
                            }
                            Asset(
                                id = assetId,
                                name = token.name,
                                symbol = token.symbol,
                                blockchain = account.blockchain,
                                balance = balance,
                                priceUsd = prices[token.coinGeckoId] ?: previous?.priceUsd ?: 0.0,
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
            current.copy(
                assets = assets.sortedWith(
                    compareByDescending(Asset::valueUsd)
                        .thenByDescending(Asset::isNative)
                        .thenBy(Asset::symbol),
                ),
                lastUpdated = Instant.now(),
            ) to failures.get()
        }

        mutableState.update { state ->
            result.fold(
                onSuccess = { (snapshot, failures) ->
                    state.copy(
                        isRefreshing = false,
                        wallet = snapshot,
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
            current.accounts.map { account ->
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

    fun setCurrency(code: String) {
        settingsStore.setCurrency(code)
        mutableState.update { it.copy(settings = it.settings.copy(currencyCode = code)) }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun deleteWallet() {
        secureStore.clear()
        customTokenStore.clear()
        mutableState.update {
            it.copy(
                wallet = null,
                transactions = emptyList(),
                customTokens = emptyList(),
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
}
