package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.KnownToken
import io.noriskservis.wpayin.wallet.model.Base58Codec
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinUtxo
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64
import kotlinx.coroutines.delay

class RpcService(
    private val client: HttpJsonClient = HttpJsonClient(),
) {
    suspend fun nativeBalance(blockchain: Blockchain, address: String): Double = when (blockchain) {
        Blockchain.BITCOIN -> bitcoinBalance(blockchain, address)
        Blockchain.SOLANA -> solanaBalance(blockchain, address)
        else -> evmBalance(blockchain, address)
    }

    suspend fun erc20Balance(token: KnownToken, ownerAddress: String): Double {
        require(token.blockchain.isEvm) { "ERC-20 balance requires an EVM network" }
        require(isEvmAddress(ownerAddress)) { "Invalid EVM wallet address" }
        require(isEvmAddress(token.contractAddress)) { "Invalid token contract address" }

        val result = withRpcFailover(token.blockchain) { rpcUrl ->
            val call = JSONObject()
                .put("to", token.contractAddress.lowercase())
                .put("data", erc20BalanceOfData(ownerAddress))
            client.post(
                rpcUrl,
                jsonRpc("eth_call", JSONArray().put(call).put("latest")),
            ).requiredResultString()
        }
        return hexQuantity(result).toBigDecimal().movePointLeft(token.decimals).toDouble()
    }

    suspend fun gasPriceWei(blockchain: Blockchain): BigInteger {
        require(blockchain.isEvm) { "Gas price requires an EVM network" }
        val result = withRpcFailover(blockchain) { rpcUrl ->
            client.post(rpcUrl, jsonRpc("eth_gasPrice", JSONArray())).requiredResultString()
        }
        return hexQuantity(result)
    }

    /** Generic read-only contract call; optional `from` lets callers dry-run a fill. */
    suspend fun ethCall(
        blockchain: Blockchain,
        to: String,
        dataHex: String,
        from: String? = null,
    ): String {
        require(blockchain.isEvm && isEvmAddress(to)) { "Invalid contract call target" }
        require(dataHex.startsWith("0x")) { "Invalid contract call data" }
        if (from != null) require(isEvmAddress(from)) { "Invalid contract call sender" }
        return withRpcFailover(blockchain) { rpcUrl ->
            val call = JSONObject().put("to", to.lowercase()).put("data", dataHex)
            if (from != null) call.put("from", from)
            client.post(
                rpcUrl,
                jsonRpc("eth_call", JSONArray().put(call).put("latest")),
            ).requiredResultString()
        }
    }

    suspend fun erc20Metadata(blockchain: Blockchain, contractAddress: String): KnownToken {
        require(blockchain.isEvm) { "Token contracts require an EVM network" }
        require(isEvmAddress(contractAddress)) { "Invalid token contract address" }
        val normalizedAddress = contractAddress.lowercase()
        val (name, symbol, decimals) = withRpcFailover(blockchain) { rpcUrl ->
            val code = client.post(
                rpcUrl,
                jsonRpc("eth_getCode", JSONArray().put(normalizedAddress).put("latest")),
            ).requiredResultString()
            check(code.removePrefix("0x").any { it != '0' }) { "No smart contract exists at this address" }

            suspend fun call(selector: String): String {
                val request = JSONObject().put("to", normalizedAddress).put("data", selector)
                return client.post(
                    rpcUrl,
                    jsonRpc("eth_call", JSONArray().put(request).put("latest")),
                ).requiredResultString()
            }

            val name = decodeAbiString(call("0x06fdde03"))
            val symbol = decodeAbiString(call("0x95d89b41"))
            val rawDecimals = hexQuantity(call("0x313ce567"))
            require(rawDecimals.bitLength() <= 31) { "Token returned invalid decimals" }
            val decimals = rawDecimals.toInt()
            Triple(name, symbol, decimals)
        }
        require(name.isNotBlank() && name.length <= 80) { "Token returned an invalid name" }
        require(symbol.isNotBlank() && symbol.length <= 20) { "Token returned an invalid symbol" }
        require(decimals in 0..36) { "Token decimals are outside the supported range" }
        return KnownToken(
            blockchain = blockchain,
            symbol = symbol,
            name = name,
            contractAddress = normalizedAddress,
            decimals = decimals,
            coinGeckoId = "",
        )
    }

    /**
     * Builds and estimates an unsigned EVM transfer. Signing and broadcasting
     * are deliberately kept outside this read-only planner.
     */
    suspend fun planEvmTransfer(
        blockchain: Blockchain,
        from: String,
        recipient: String,
        amount: String,
        token: KnownToken? = null,
    ): EvmTransferDraft {
        require(blockchain.isEvm) { "Transfers can only be planned for EVM networks" }
        require(isEvmAddress(from)) { "Invalid sender address" }
        require(isEvmAddress(recipient)) { "Invalid recipient address" }
        require(!recipient.equals(ZERO_EVM_ADDRESS, ignoreCase = true)) { "The zero address cannot receive a wallet transfer" }
        require(token == null || token.blockchain == blockchain) { "Token network does not match transfer network" }

        val decimals = token?.decimals ?: blockchain.decimals
        val atomicAmount = atomicAmount(amount, decimals)
        val transactionTo = token?.contractAddress ?: recipient
        val valueHex = if (token == null) atomicAmount.toHexQuantity() else "0x0"
        val dataHex = token?.let { erc20TransferData(recipient, atomicAmount) } ?: "0x"

        val (nonce, gasPrice, gasLimit) = withRpcFailover(blockchain) { rpcUrl ->
            val nonce = client.post(
                rpcUrl,
                jsonRpc("eth_getTransactionCount", JSONArray().put(from).put("pending")),
            ).requiredResultString().let(::hexQuantity)
            val gasPrice = client.post(
                rpcUrl,
                jsonRpc("eth_gasPrice", JSONArray()),
            ).requiredResultString().let(::hexQuantity)
            val transaction = JSONObject()
                .put("from", from)
                .put("to", transactionTo)
                .put("value", valueHex)
            if (dataHex != "0x") transaction.put("data", dataHex)
            val estimatedGas = client.post(
                rpcUrl,
                jsonRpc("eth_estimateGas", JSONArray().put(transaction)),
            ).requiredResultString().let(::hexQuantity)
            val gasLimit = estimatedGas.multiply(BigInteger.valueOf(120)).add(BigInteger.valueOf(99))
                .divide(BigInteger.valueOf(100))
            Triple(nonce, gasPrice, gasLimit)
        }

        return EvmTransferDraft(
            blockchain = blockchain,
            from = from,
            to = recipient,
            symbol = token?.symbol ?: blockchain.symbol,
            amount = amount.trim(),
            amountAtomic = atomicAmount,
            contractAddress = token?.contractAddress,
            transactionTo = transactionTo,
            valueHex = valueHex,
            dataHex = dataHex,
            nonce = nonce,
            gasLimit = gasLimit,
            gasPriceWei = gasPrice,
        )
    }

    suspend fun broadcastRawTransaction(blockchain: Blockchain, signedTransaction: String): String {
        require(blockchain.isEvm) { "Raw EVM transactions require an EVM network" }
        require(rawTransactionPattern.matches(signedTransaction)) { "Invalid signed transaction encoding" }
        val hash = withRpcFailover(blockchain) { rpcUrl ->
            client.post(
                rpcUrl,
                jsonRpc("eth_sendRawTransaction", JSONArray().put(signedTransaction)),
            ).requiredResultString()
        }
        check(transactionHashPattern.matches(hash)) { "RPC returned an invalid transaction hash" }
        return hash
    }

    suspend fun planEvmContractCall(
        blockchain: Blockchain,
        from: String,
        transactionTo: String,
        dataHex: String,
        valueHex: String,
        symbol: String,
        amount: String,
        amountAtomic: BigInteger,
        assetContractAddress: String?,
        recipient: String,
    ): EvmTransferDraft {
        require(blockchain.isEvm) { "Contract calls require an EVM network" }
        require(isEvmAddress(from)) { "Invalid sender address" }
        require(isEvmAddress(transactionTo)) { "Invalid contract address" }
        require(isEvmAddress(recipient)) { "Invalid destination address" }
        require(dataHexPattern.matches(dataHex) && dataHex.length <= MAX_CALL_DATA_HEX_LENGTH) {
            "Invalid contract call data"
        }
        val value = hexQuantity(valueHex)
        require(value.signum() >= 0 && value.bitLength() <= 256) { "Invalid contract call value" }
        require(amountAtomic.signum() >= 0 && amountAtomic.bitLength() <= 256) { "Invalid source amount" }
        if (assetContractAddress != null) require(isEvmAddress(assetContractAddress)) {
            "Invalid source token contract"
        }

        val normalizedValue = value.toHexQuantity()
        val (nonce, gasPrice, gasLimit) = withRpcFailover(blockchain) { rpcUrl ->
            val nonce = client.post(
                rpcUrl,
                jsonRpc("eth_getTransactionCount", JSONArray().put(from).put("pending")),
            ).requiredResultString().let(::hexQuantity)
            val gasPrice = client.post(
                rpcUrl,
                jsonRpc("eth_gasPrice", JSONArray()),
            ).requiredResultString().let(::hexQuantity)
            val transaction = JSONObject()
                .put("from", from)
                .put("to", transactionTo)
                .put("value", normalizedValue)
                .put("data", dataHex)
            val estimatedGas = client.post(
                rpcUrl,
                jsonRpc("eth_estimateGas", JSONArray().put(transaction)),
            ).requiredResultString().let(::hexQuantity)
            val bufferedGas = estimatedGas.multiply(BigInteger.valueOf(120)).add(BigInteger.valueOf(99))
                .divide(BigInteger.valueOf(100))
            Triple(nonce, gasPrice, bufferedGas)
        }

        return EvmTransferDraft(
            blockchain = blockchain,
            from = from,
            to = recipient,
            symbol = symbol,
            amount = amount,
            amountAtomic = amountAtomic,
            contractAddress = assetContractAddress,
            transactionTo = transactionTo,
            valueHex = normalizedValue,
            dataHex = dataHex,
            nonce = nonce,
            gasLimit = gasLimit,
            gasPriceWei = gasPrice,
            isGenericContractCall = true,
        )
    }

    suspend fun erc20Allowance(
        blockchain: Blockchain,
        tokenContract: String,
        owner: String,
        spender: String,
    ): BigInteger {
        require(blockchain.isEvm && isEvmAddress(tokenContract) && isEvmAddress(owner) && isEvmAddress(spender)) {
            "Invalid ERC-20 allowance request"
        }
        val result = withRpcFailover(blockchain) { rpcUrl ->
            val call = JSONObject()
                .put("to", tokenContract)
                .put("data", erc20AllowanceData(owner, spender))
            client.post(
                rpcUrl,
                jsonRpc("eth_call", JSONArray().put(call).put("latest")),
            ).requiredResultString()
        }
        return hexQuantity(result)
    }

    suspend fun planErc20Approval(
        blockchain: Blockchain,
        from: String,
        tokenContract: String,
        spender: String,
        amountAtomic: BigInteger,
        symbol: String,
    ): EvmTransferDraft = planEvmContractCall(
        blockchain = blockchain,
        from = from,
        transactionTo = tokenContract,
        dataHex = erc20ApproveData(spender, amountAtomic),
        valueHex = "0x0",
        symbol = symbol,
        amount = "0",
        amountAtomic = amountAtomic,
        assetContractAddress = null,
        recipient = spender,
    )

    suspend fun waitForSuccessfulEvmReceipt(
        blockchain: Blockchain,
        transactionHash: String,
        attempts: Int = 30,
    ) {
        require(transactionHashPattern.matches(transactionHash)) { "Invalid transaction hash" }
        repeat(attempts) {
            val receipt = withRpcFailover(blockchain) { rpcUrl ->
                val response = client.post(
                    rpcUrl,
                    jsonRpc("eth_getTransactionReceipt", JSONArray().put(transactionHash)),
                )
                if (response.has("error")) {
                    error(response.getJSONObject("error").optString("message", "Unable to check transaction"))
                }
                response.optJSONObject("result")
            }
            if (receipt != null) {
                val status = receipt.optString("status")
                check(status.equals("0x1", ignoreCase = true)) { "Token approval failed on-chain" }
                return
            }
            delay(2_000)
        }
        error("Token approval is still pending; try the swap again after it confirms")
    }

    suspend fun planSolanaTransfer(
        from: String,
        recipient: String,
        amount: String,
    ): SolanaTransferDraft {
        require(Base58Codec.isSolanaAddress(from)) { "Invalid Solana sender address" }
        require(Base58Codec.isSolanaAddress(recipient)) { "Invalid Solana recipient address" }
        val lamports = try {
            atomicAmount(amount, Blockchain.SOLANA.decimals).positiveLongValueExact()
        } catch (_: ArithmeticException) {
            error("SOL amount is too large")
        }
        val latest = withRpcFailover(Blockchain.SOLANA) { rpcUrl ->
            val options = JSONObject().put("commitment", "finalized")
            client.post(
                rpcUrl,
                jsonRpc("getLatestBlockhash", JSONArray().put(options)),
            ).let { response ->
                if (response.has("error")) {
                    error(response.getJSONObject("error").optString("message", "Unable to fetch Solana blockhash"))
                }
                response.getJSONObject("result").getJSONObject("value")
            }
        }
        val blockhash = latest.getString("blockhash")
        require(Base58Codec.isSolanaAddress(blockhash)) { "Solana RPC returned an invalid blockhash" }
        return SolanaTransferDraft(
            from = from,
            to = recipient,
            amount = amount.trim(),
            lamports = lamports,
            recentBlockhash = blockhash,
            lastValidBlockHeight = latest.getLong("lastValidBlockHeight"),
            feeLamports = SOLANA_BASE_SIGNATURE_FEE,
        )
    }

    suspend fun broadcastSolanaTransaction(signedBase58Transaction: String): String {
        val transactionBytes = Base58Codec.decode(signedBase58Transaction)
        require(transactionBytes.isNotEmpty() && transactionBytes.size <= 1_232) {
            "Invalid signed Solana transaction"
        }
        val encoded = Base64.getEncoder().encodeToString(transactionBytes)
        val signature = withRpcFailover(Blockchain.SOLANA) { rpcUrl ->
            val options = JSONObject()
                .put("encoding", "base64")
                .put("preflightCommitment", "confirmed")
                .put("maxRetries", 3)
            client.post(
                rpcUrl,
                jsonRpc("sendTransaction", JSONArray().put(encoded).put(options)),
            ).requiredResultString()
        }
        check(runCatching { Base58Codec.decode(signature).size == 64 }.getOrDefault(false)) {
            "Solana RPC returned an invalid transaction signature"
        }
        return signature
    }

    suspend fun planBitcoinTransfer(
        from: String,
        recipient: String,
        amount: String,
    ): BitcoinTransferDraft {
        require(from.isNotBlank() && recipient.isNotBlank()) { "Bitcoin addresses cannot be empty" }
        val amountSatoshis = try {
            atomicAmount(amount, Blockchain.BITCOIN.decimals).positiveLongValueExact()
        } catch (_: ArithmeticException) {
            error("BTC amount is too large")
        }
        require(amountSatoshis >= BITCOIN_DUST_SATOSHIS) { "Bitcoin amount is below the dust limit" }
        val feeRate = bitcoinFeeRate()
        val available = bitcoinUtxos(from).filter { it.valueSatoshis > 0 }
            .sortedByDescending(BitcoinUtxo::valueSatoshis)
        check(available.isNotEmpty()) { "No confirmed Bitcoin outputs are available to spend" }

        val selected = mutableListOf<BitcoinUtxo>()
        var selectedValue = 0L
        var estimatedFee = 0L
        for (utxo in available) {
            selected += utxo
            selectedValue = Math.addExact(selectedValue, utxo.valueSatoshis)
            estimatedFee = Math.multiplyExact(estimateBitcoinVirtualBytes(selected.size, 2).toLong(), feeRate)
            if (selectedValue >= Math.addExact(amountSatoshis, estimatedFee)) break
        }
        val requiredValue = try {
            Math.addExact(amountSatoshis, estimatedFee)
        } catch (_: ArithmeticException) {
            error("BTC amount and fee are too large")
        }
        check(selectedValue >= requiredValue) { "Insufficient confirmed BTC balance" }
        val change = selectedValue - requiredValue
        if (change in 1 until BITCOIN_DUST_SATOSHIS) {
            estimatedFee = Math.addExact(estimatedFee, change)
        }

        return BitcoinTransferDraft(
            from = from,
            to = recipient,
            amount = amount.trim(),
            amountSatoshis = amountSatoshis,
            feeRateSatsPerVByte = feeRate,
            feeSatoshis = estimatedFee,
            selectedUtxos = selected,
        )
    }

    suspend fun broadcastBitcoinTransaction(rawTransactionHex: String): String {
        require(rawTransactionHex.length >= 20 && rawTransactionHex.length % 2 == 0 &&
            rawTransactionHex.all { it.digitToIntOrNull(16) != null }
        ) { "Invalid signed Bitcoin transaction" }
        var lastError: Throwable? = null
        Blockchain.BITCOIN.rpcUrls.forEach { baseUrl ->
            try {
                val txid = client.postTextForText("$baseUrl/tx", rawTransactionHex).trim()
                check(txid.matches(Regex("^[0-9a-fA-F]{64}$"))) { "Bitcoin explorer returned an invalid txid" }
                return txid.lowercase()
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No Bitcoin broadcast endpoint configured")
    }

    private suspend fun bitcoinUtxos(address: String): List<BitcoinUtxo> {
        var lastError: Throwable? = null
        Blockchain.BITCOIN.rpcUrls.forEach { baseUrl ->
            try {
                val response = client.getArray("$baseUrl/address/$address/utxo")
                return buildList {
                    for (index in 0 until response.length()) {
                        val item = response.optJSONObject(index) ?: continue
                        if (item.optJSONObject("status")?.optBoolean("confirmed") != true) continue
                        val txid = item.optString("txid")
                        val outputIndex = item.optInt("vout", -1)
                        val value = item.optLong("value", -1)
                        if (txid.matches(Regex("^[0-9a-fA-F]{64}$")) && outputIndex >= 0 && value > 0) {
                            add(BitcoinUtxo(txid.lowercase(), outputIndex, value))
                        }
                    }
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Unable to fetch Bitcoin UTXOs")
    }

    private suspend fun bitcoinFeeRate(): Long {
        val response = client.get("https://mempool.space/api/v1/fees/recommended")
        return response.optLong("halfHourFee", 0).coerceIn(1, 1_000)
    }

    private suspend fun evmBalance(blockchain: Blockchain, address: String): Double {
        val result = withRpcFailover(blockchain) { rpcUrl ->
            client.post(
                rpcUrl,
                jsonRpc("eth_getBalance", JSONArray().put(address).put("latest")),
            ).requiredResultString()
        }
        val wei = result.removePrefix("0x").ifEmpty { "0" }
        return BigDecimal(BigInteger(wei, 16))
            .movePointLeft(blockchain.decimals)
            .toDouble()
    }

    private suspend fun solanaBalance(blockchain: Blockchain, address: String): Double {
        val lamports = withRpcFailover(blockchain) { rpcUrl ->
            client.post(
                rpcUrl,
                jsonRpc("getBalance", JSONArray().put(address)),
            ).getJSONObject("result").getLong("value")
        }
        return BigDecimal.valueOf(lamports).movePointLeft(blockchain.decimals).toDouble()
    }

    private suspend fun bitcoinBalance(blockchain: Blockchain, address: String): Double {
        var lastError: Throwable? = null
        blockchain.rpcUrls.forEach { baseUrl ->
            try {
                val response = client.get("$baseUrl/address/$address")
                val funded = response.getJSONObject("chain_stats").getLong("funded_txo_sum")
                val spent = response.getJSONObject("chain_stats").getLong("spent_txo_sum")
                val mempool = response.getJSONObject("mempool_stats")
                val mempoolFunded = mempool.getLong("funded_txo_sum")
                val mempoolSpent = mempool.getLong("spent_txo_sum")
                return BigDecimal.valueOf(funded - spent + mempoolFunded - mempoolSpent)
                    .movePointLeft(blockchain.decimals)
                    .toDouble()
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No Bitcoin endpoint configured")
    }

    private suspend fun <T> withRpcFailover(
        blockchain: Blockchain,
        block: suspend (String) -> T,
    ): T {
        var lastError: Throwable? = null
        blockchain.rpcUrls.forEach { url ->
            try {
                return block(url)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No RPC endpoint configured for ${blockchain.displayName}")
    }

    private fun jsonRpc(method: String, params: JSONArray): JSONObject = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", 1)
        .put("method", method)
        .put("params", params)

    private fun JSONObject.requiredResultString(): String {
        if (has("error")) error(getJSONObject("error").optString("message", "RPC request failed"))
        return getString("result")
    }

    companion object {
        private val evmAddressPattern = Regex("^0x[0-9a-fA-F]{40}$")
        private val rawTransactionPattern = Regex("^0x[0-9a-fA-F]{2,}$")
        private val transactionHashPattern = Regex("^0x[0-9a-fA-F]{64}$")
        private const val ZERO_EVM_ADDRESS = "0x0000000000000000000000000000000000000000"
        private const val SOLANA_BASE_SIGNATURE_FEE = 5_000L
        private const val BITCOIN_DUST_SATOSHIS = 546L

        fun isEvmAddress(value: String): Boolean = evmAddressPattern.matches(value.trim())

        fun erc20BalanceOfData(ownerAddress: String): String {
            require(isEvmAddress(ownerAddress)) { "Invalid EVM wallet address" }
            return "0x70a08231" + ownerAddress.trim().removePrefix("0x").lowercase().padStart(64, '0')
        }

        fun erc20TransferData(recipient: String, amountAtomic: BigInteger): String {
            require(isEvmAddress(recipient)) { "Invalid EVM recipient address" }
            require(amountAtomic.signum() >= 0) { "Transfer amount cannot be negative" }
            val addressWord = recipient.trim().removePrefix("0x").lowercase().padStart(64, '0')
            val amountWord = amountAtomic.toString(16).padStart(64, '0')
            require(amountWord.length <= 64) { "Transfer amount exceeds uint256" }
            return "0xa9059cbb$addressWord$amountWord"
        }

        fun erc20AllowanceData(owner: String, spender: String): String {
            require(isEvmAddress(owner) && isEvmAddress(spender)) { "Invalid ERC-20 allowance addresses" }
            val ownerWord = owner.trim().removePrefix("0x").lowercase().padStart(64, '0')
            val spenderWord = spender.trim().removePrefix("0x").lowercase().padStart(64, '0')
            return "0xdd62ed3e$ownerWord$spenderWord"
        }

        fun erc20ApproveData(spender: String, amountAtomic: BigInteger): String {
            require(isEvmAddress(spender)) { "Invalid ERC-20 spender address" }
            require(amountAtomic.signum() >= 0 && amountAtomic.bitLength() <= 256) {
                "Invalid ERC-20 approval amount"
            }
            val spenderWord = spender.trim().removePrefix("0x").lowercase().padStart(64, '0')
            val amountWord = amountAtomic.toString(16).padStart(64, '0')
            return "0x095ea7b3$spenderWord$amountWord"
        }

        fun atomicAmount(amount: String, decimals: Int): BigInteger {
            require(decimals in 0..255) { "Invalid token decimals" }
            val decimal = amount.trim().toBigDecimalOrNull() ?: error("Invalid transfer amount")
            require(decimal.signum() > 0) { "Transfer amount must be greater than zero" }
            val atomic = try {
                decimal.movePointRight(decimals).toBigIntegerExact()
            } catch (_: ArithmeticException) {
                error("Transfer amount has more than $decimals decimal places")
            }
            require(atomic.bitLength() <= 256) { "Transfer amount exceeds uint256" }
            return atomic
        }

        fun decodeAbiString(encoded: String): String {
            val hex = encoded.removePrefix("0x")
            if (hex.isEmpty()) return ""
            require(hex.length % 2 == 0 && hex.all { it.digitToIntOrNull(16) != null }) {
                "Contract returned malformed ABI data"
            }
            val bytes = ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            val payload = if (bytes.size >= 64) {
                val offset = BigInteger(1, bytes.copyOfRange(0, 32)).boundedAbiInt("offset")
                require(offset <= bytes.size - 32) { "Invalid ABI string offset" }
                val length = BigInteger(1, bytes.copyOfRange(offset, offset + 32)).boundedAbiInt("length")
                require(length in 0..256 && length <= bytes.size - offset - 32) { "Invalid ABI string length" }
                bytes.copyOfRange(offset + 32, offset + 32 + length)
            } else {
                bytes.take(32).takeWhile { it != 0.toByte() }.toByteArray()
            }
            return payload.toString(Charsets.UTF_8).trim().filter { !it.isISOControl() }
        }

        fun estimateBitcoinVirtualBytes(inputCount: Int, outputCount: Int): Int {
            require(inputCount > 0 && outputCount > 0) { "Bitcoin transaction needs inputs and outputs" }
            return 10 + inputCount * 68 + outputCount * 31
        }

        private fun hexQuantity(value: String): BigInteger {
            val normalized = value.removePrefix("0x").ifEmpty { "0" }
            return try {
                BigInteger(normalized, 16)
            } catch (error: NumberFormatException) {
                throw IllegalStateException("RPC returned an invalid hexadecimal quantity", error)
            }
        }

        private fun BigInteger.boundedAbiInt(field: String): Int {
            require(signum() >= 0 && bitLength() <= 31) { "Invalid ABI string $field" }
            return toInt()
        }

        private fun BigInteger.positiveLongValueExact(): Long {
            if (signum() < 0 || bitLength() > 63) throw ArithmeticException("Value does not fit in Long")
            return toLong()
        }

        private fun BigInteger.toHexQuantity(): String = "0x${toString(16)}"

        private val dataHexPattern = Regex("^0x(?:[0-9a-fA-F]{2})*$")
        private const val MAX_CALL_DATA_HEX_LENGTH = 262_146
    }
}
