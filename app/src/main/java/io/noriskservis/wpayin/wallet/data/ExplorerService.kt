package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.WalletAccount
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class ExplorerService(
    private val client: HttpJsonClient = HttpJsonClient(),
) {
    suspend fun transactions(account: WalletAccount): List<WalletTransaction> = when {
        account.blockchain == Blockchain.BITCOIN -> bitcoinTransactions(account)
        account.blockchain == Blockchain.SOLANA -> solanaTransactions(account)
        account.blockchain.isEvm -> evmTransactions(account)
        else -> emptyList()
    }

    private suspend fun evmTransactions(account: WalletAccount): List<WalletTransaction> {
        val key = BuildConfig.ETHERSCAN_API_KEY.trim()
        val primary = if (key.isNotEmpty()) {
            runCatching { explorerTransactions(account, etherscanUrl(account, key)) }.getOrNull()
        } else {
            null
        }
        val combined = primary ?: account.blockchain.blockscoutApiUrl()?.let { baseUrl ->
            runCatching { explorerTransactions(account, baseUrl) }.getOrNull()
        }.orEmpty()

        return combined
            .distinctBy { "${it.hash.lowercase()}|${it.symbol.uppercase()}|${it.type}" }
            .sortedByDescending(WalletTransaction::timestamp)
    }

    private suspend fun explorerTransactions(
        account: WalletAccount,
        baseUrl: String,
    ): List<WalletTransaction> {
        val normal = fetchExplorerAction(account, baseUrl, "txlist")
        val tokenTransfers = fetchExplorerAction(account, baseUrl, "tokentx")
        return normal + tokenTransfers
    }

    private suspend fun fetchExplorerAction(
        account: WalletAccount,
        baseUrl: String,
        action: String,
    ): List<WalletTransaction> {
        val separator = if (baseUrl.contains('?')) "&" else "?"
        val response = client.get(
            "$baseUrl${separator}module=account&action=$action&address=${account.address}" +
                "&page=1&offset=50&sort=desc",
        )
        val entries = response.optJSONArray("result")
        if (entries == null) {
            val message = response.optString("message") + " " + response.optString("result")
            if (message.lowercase(Locale.US).contains("no transactions")) return emptyList()
            error("Explorer did not return transaction data")
        }

        return buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val transaction = if (action == "tokentx") {
                    mapTokenTransaction(account, item)
                } else {
                    mapNormalTransaction(account, item)
                }
                if (transaction != null) add(transaction)
            }
        }
    }

    private fun mapNormalTransaction(
        account: WalletAccount,
        entry: JSONObject,
    ): WalletTransaction? {
        val hash = entry.optString("hash").takeIf(String::isNotBlank) ?: return null
        val from = entry.optString("from")
        val to = entry.optString("to").ifBlank { entry.optString("contractAddress") }
        val outgoing = from.equals(account.address, ignoreCase = true)
        return WalletTransaction(
            hash = hash,
            blockchain = account.blockchain,
            type = classifyNormalTransaction(account.blockchain, outgoing, to, entry.optString("input")),
            status = explorerStatus(entry),
            amount = decimalAmount(entry.optString("value"), account.blockchain.decimals),
            symbol = account.blockchain.symbol,
            timestamp = timestamp(entry.optString("timeStamp")),
            from = from,
            to = to,
            fee = gasFee(entry, account.blockchain.decimals),
            blockNumber = entry.optString("blockNumber").takeIf(String::isNotBlank),
        )
    }

    private fun mapTokenTransaction(
        account: WalletAccount,
        entry: JSONObject,
    ): WalletTransaction? {
        val hash = entry.optString("hash").takeIf(String::isNotBlank) ?: return null
        val from = entry.optString("from")
        val outgoing = from.equals(account.address, ignoreCase = true)
        return WalletTransaction(
            hash = hash,
            blockchain = account.blockchain,
            type = if (outgoing) TransactionType.SEND else TransactionType.RECEIVE,
            status = explorerStatus(entry),
            amount = decimalAmount(entry.optString("value"), entry.optString("tokenDecimal").toIntOrNull() ?: 18),
            symbol = entry.optString("tokenSymbol").ifBlank { entry.optString("tokenName", "Token") },
            timestamp = timestamp(entry.optString("timeStamp")),
            from = from,
            to = entry.optString("to"),
            fee = gasFee(entry, account.blockchain.decimals),
            blockNumber = entry.optString("blockNumber").takeIf(String::isNotBlank),
        )
    }

    private suspend fun bitcoinTransactions(account: WalletAccount): List<WalletTransaction> {
        var lastError: Throwable? = null
        account.blockchain.rpcUrls.forEach { baseUrl ->
            try {
                val entries = client.getArray("$baseUrl/address/${account.address}/txs")
                return buildList {
                    for (index in 0 until entries.length()) {
                        entries.optJSONObject(index)?.let { entry ->
                            mapBitcoinTransaction(account, entry)?.let(::add)
                        }
                    }
                }.sortedByDescending(WalletTransaction::timestamp)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No Bitcoin explorer endpoint configured")
    }

    private fun mapBitcoinTransaction(
        account: WalletAccount,
        entry: JSONObject,
    ): WalletTransaction? {
        val hash = entry.optString("txid").takeIf(String::isNotBlank) ?: return null
        val inputs = entry.optJSONArray("vin") ?: JSONArray()
        val outputs = entry.optJSONArray("vout") ?: JSONArray()
        var inputValue = 0L
        var outputValue = 0L
        var walletInput = 0L
        var walletOutput = 0L
        var from = ""
        var externalRecipient = ""

        for (index in 0 until inputs.length()) {
            val previousOutput = inputs.optJSONObject(index)?.optJSONObject("prevout") ?: continue
            val address = previousOutput.optString("scriptpubkey_address")
            val value = previousOutput.optLong("value")
            inputValue += value
            if (from.isEmpty()) from = address
            if (address == account.address) walletInput += value
        }
        for (index in 0 until outputs.length()) {
            val output = outputs.optJSONObject(index) ?: continue
            val address = output.optString("scriptpubkey_address")
            val value = output.optLong("value")
            outputValue += value
            if (address == account.address) {
                walletOutput += value
            } else if (externalRecipient.isEmpty()) {
                externalRecipient = address
            }
        }

        val outgoing = walletInput > 0
        val feeSatoshis = (inputValue - outputValue).coerceAtLeast(0)
        val amountSatoshis = if (outgoing) {
            (walletInput - walletOutput - feeSatoshis).coerceAtLeast(0)
        } else {
            walletOutput
        }
        val status = entry.optJSONObject("status")
        val confirmed = status?.optBoolean("confirmed") == true
        val blockTime = status?.optLong("block_time")?.takeIf { it > 0 }
        return WalletTransaction(
            hash = hash,
            blockchain = Blockchain.BITCOIN,
            type = if (outgoing) TransactionType.SEND else TransactionType.RECEIVE,
            status = if (confirmed) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
            amount = BigDecimal.valueOf(amountSatoshis).movePointLeft(Blockchain.BITCOIN.decimals).toDouble(),
            symbol = Blockchain.BITCOIN.symbol,
            timestamp = blockTime?.let(Instant::ofEpochSecond) ?: Instant.now(),
            from = from,
            to = if (outgoing) externalRecipient else account.address,
            fee = BigDecimal.valueOf(feeSatoshis).movePointLeft(Blockchain.BITCOIN.decimals).toDouble(),
            blockNumber = status?.optLong("block_height")?.takeIf { it > 0 }?.toString(),
        )
    }

    private suspend fun solanaTransactions(account: WalletAccount): List<WalletTransaction> {
        var lastError: Throwable? = null
        account.blockchain.rpcUrls.forEach { rpcUrl ->
            try {
                val options = JSONObject().put("limit", 15).put("commitment", "confirmed")
                val signaturesResponse = client.post(
                    rpcUrl,
                    jsonRpc("getSignaturesForAddress", JSONArray().put(account.address).put(options)),
                )
                if (signaturesResponse.has("error")) error("Solana history request failed")
                val signatures = signaturesResponse.getJSONArray("result")
                return buildList {
                    for (index in 0 until signatures.length()) {
                        val summary = signatures.optJSONObject(index) ?: continue
                        val signature = summary.optString("signature")
                        if (signature.isBlank()) continue
                        val transaction = runCatching {
                            val config = JSONObject()
                                .put("encoding", "jsonParsed")
                                .put("commitment", "confirmed")
                                .put("maxSupportedTransactionVersion", 0)
                            val detail = client.post(
                                rpcUrl,
                                jsonRpc("getTransaction", JSONArray().put(signature).put(config)),
                            ).optJSONObject("result")
                            detail?.let { mapSolanaTransaction(account, signature, summary, it) }
                        }.getOrNull()
                        if (transaction != null) add(transaction)
                    }
                }.sortedByDescending(WalletTransaction::timestamp)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No Solana RPC endpoint configured")
    }

    private fun mapSolanaTransaction(
        account: WalletAccount,
        signature: String,
        summary: JSONObject,
        detail: JSONObject,
    ): WalletTransaction {
        val meta = detail.getJSONObject("meta")
        val message = detail.getJSONObject("transaction").getJSONObject("message")
        val instructions = message.optJSONArray("instructions") ?: JSONArray()
        var from = account.address
        var to = account.address
        var lamports: Long? = null

        for (index in 0 until instructions.length()) {
            val parsed = instructions.optJSONObject(index)?.optJSONObject("parsed") ?: continue
            if (parsed.optString("type") != "transfer") continue
            val info = parsed.optJSONObject("info") ?: continue
            val source = info.optString("source")
            val destination = info.optString("destination")
            if (source == account.address || destination == account.address) {
                from = source
                to = destination
                lamports = info.optLong("lamports").takeIf { it > 0 }
                break
            }
        }

        var outgoing = from == account.address
        if (lamports == null) {
            val accountKeys = message.optJSONArray("accountKeys") ?: JSONArray()
            var accountIndex = -1
            for (index in 0 until accountKeys.length()) {
                val key = accountKeys.optJSONObject(index)?.optString("pubkey")
                    ?: accountKeys.optString(index)
                if (key == account.address) {
                    accountIndex = index
                    break
                }
            }
            if (accountIndex >= 0) {
                val before = meta.optJSONArray("preBalances")?.optLong(accountIndex) ?: 0L
                val after = meta.optJSONArray("postBalances")?.optLong(accountIndex) ?: before
                val delta = after - before
                val fee = meta.optLong("fee")
                lamports = if (delta < 0) (-delta - fee).coerceAtLeast(0) else delta
                outgoing = delta < 0
                if (!outgoing) from = ""
            }
        }

        val failed = !meta.isNull("err")
        val blockTime = detail.optLong("blockTime").takeIf { it > 0 }
            ?: summary.optLong("blockTime").takeIf { it > 0 }
        return WalletTransaction(
            hash = signature,
            blockchain = Blockchain.SOLANA,
            type = if (outgoing) TransactionType.SEND else TransactionType.RECEIVE,
            status = if (failed) TransactionStatus.FAILED else TransactionStatus.CONFIRMED,
            amount = BigDecimal.valueOf(lamports ?: 0).movePointLeft(Blockchain.SOLANA.decimals).toDouble(),
            symbol = "SOL",
            timestamp = blockTime?.let(Instant::ofEpochSecond) ?: Instant.EPOCH,
            from = from,
            to = to,
            fee = BigDecimal.valueOf(meta.optLong("fee")).movePointLeft(Blockchain.SOLANA.decimals).toDouble(),
            blockNumber = detail.optLong("slot").takeIf { it > 0 }?.toString(),
        )
    }

    private fun etherscanUrl(account: WalletAccount, apiKey: String): String =
        "https://api.etherscan.io/v2/api?chainid=${requireNotNull(account.blockchain.chainId)}&apikey=$apiKey"

    private fun Blockchain.blockscoutApiUrl(): String? = when (this) {
        Blockchain.ETHEREUM -> "https://eth.blockscout.com/api"
        Blockchain.POLYGON -> "https://polygon.blockscout.com/api"
        Blockchain.ARBITRUM -> "https://arbitrum.blockscout.com/api"
        Blockchain.OPTIMISM -> "https://optimism.blockscout.com/api"
        Blockchain.BASE -> "https://base.blockscout.com/api"
        else -> null
    }

    private fun decimalAmount(value: String, decimals: Int): Double =
        value.toBigDecimalOrNull()?.movePointLeft(decimals)?.toDouble() ?: 0.0

    private fun gasFee(entry: JSONObject, decimals: Int): Double? {
        val gasUsed = entry.optString("gasUsed").toBigDecimalOrNull() ?: return null
        val gasPrice = entry.optString("gasPrice").toBigDecimalOrNull() ?: return null
        return gasUsed.multiply(gasPrice).movePointLeft(decimals).toDouble()
    }

    private fun explorerStatus(entry: JSONObject): TransactionStatus = when {
        entry.optString("txreceipt_status") == "1" -> TransactionStatus.CONFIRMED
        entry.optString("txreceipt_status") == "0" -> TransactionStatus.FAILED
        entry.optString("isError") == "0" -> TransactionStatus.CONFIRMED
        entry.optString("isError") == "1" -> TransactionStatus.FAILED
        else -> TransactionStatus.PENDING
    }

    private fun timestamp(value: String): Instant =
        value.toLongOrNull()?.let(Instant::ofEpochSecond) ?: Instant.EPOCH

    private fun classifyNormalTransaction(
        blockchain: Blockchain,
        outgoing: Boolean,
        destination: String,
        input: String,
    ): TransactionType {
        if (!outgoing) return TransactionType.RECEIVE
        val selector = input.lowercase().take(10)
        if (selector in setOf("0x7ff36ab5", "0x18cbafe5", "0x38ed1739")) return TransactionType.SWAP
        if (destination.equals("0x1231deb6f5749ef6ce6943a275a1d3e7486f4eae", ignoreCase = true)) {
            return TransactionType.BRIDGE
        }
        val wrappedNative = mapOf(
            Blockchain.ETHEREUM to "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
            Blockchain.BNB_CHAIN to "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c",
            Blockchain.POLYGON to "0x0d500b1d8e8ef31e21c99d1db9a6444d3adf1270",
            Blockchain.ARBITRUM to "0x82af49447d8a07e3bd95bd0d56f35241523fbab1",
            Blockchain.OPTIMISM to "0x4200000000000000000000000000000000000006",
            Blockchain.BASE to "0x4200000000000000000000000000000000000006",
        )
        if (selector in setOf("0xd0e30db0", "0x2e1a7d4d") &&
            destination.equals(wrappedNative[blockchain], ignoreCase = true)
        ) {
            return TransactionType.SWAP
        }
        return TransactionType.SEND
    }

    private fun jsonRpc(method: String, params: JSONArray): JSONObject = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", 1)
        .put("method", method)
        .put("params", params)
}
