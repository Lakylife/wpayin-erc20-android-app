package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.QuintiumConfig
import io.noriskservis.wpayin.wallet.model.QuintiumNetwork
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.WalletAccount
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Talks to a Quintium node directly over JSON-RPC. */
class QuintiumNodeClient(
    private val client: HttpJsonClient = HttpJsonClient(),
) {
    data class NodeStatus(val chainId: Long, val blockNumber: Long)

    class WrongChainException(val expected: Long, val actual: Long) :
        IllegalStateException("The node belongs to another network (chain ID $actual, expected $expected)")

    /** Calls eth_chainId and eth_blockNumber and checks that the chain ID matches the network. */
    suspend fun check(rpcUrl: String, network: QuintiumNetwork): NodeStatus {
        val url = QuintiumConfig.clean(rpcUrl)
        require(QuintiumConfig.isValidUrl(url)) { "Enter a valid node URL starting with https:// or http://" }
        val chainId = hexToLong(call(url, "eth_chainId", JSONArray()).toString())
        if (chainId != network.chainId) throw WrongChainException(network.chainId, chainId)
        val block = hexToLong(call(url, "eth_blockNumber", JSONArray()).toString())
        return NodeStatus(chainId, block)
    }

    /** Transaction history of an address from the node's own index (`aether_addressTxs`). */
    suspend fun transactions(account: WalletAccount, limit: Int = 50): List<WalletTransaction> {
        if (account.blockchain != Blockchain.QUINTIUM || !QuintiumConfig.isConfigured) return emptyList()
        val page = call(
            QuintiumConfig.rpcUrl,
            QuintiumNetwork.ADDRESS_TRANSACTIONS_METHOD,
            JSONArray().put(account.address).put(limit).put(0),
        ) as? JSONObject ?: error("The Quinvium node returned no transaction page")
        return parseQuintiumTransactions(account, page)
    }

    private suspend fun call(url: String, method: String, params: JSONArray): Any {
        val response = client.post(
            url,
            JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params),
        )
        response.optJSONObject("error")?.let { error(it.optString("message", "RPC request failed")) }
        check(response.has("result") && !response.isNull("result")) { "Empty answer from the node" }
        return response.get("result")
    }

    companion object {
        fun hexToLong(value: String): Long =
            value.removePrefix("0x").removePrefix("0X").ifEmpty { "0" }.toLongOrNull(16)
                ?: error("The node returned an invalid number")
    }
}

/** Maps one page of `aether_addressTxs` (`{ total, items: [...] }`) to wallet transactions. */
internal fun parseQuintiumTransactions(account: WalletAccount, page: JSONObject): List<WalletTransaction> {
    val items = page.optJSONArray("items") ?: return emptyList()
    val decimals = account.blockchain.decimals
    return buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val hash = item.optString("hash").takeIf(String::isNotBlank) ?: continue
            val from = item.optString("from")
            val to = if (item.isNull("to")) "" else item.optString("to")
            val outgoing = from.equals(account.address, ignoreCase = true)
            val gasUsed = quantity(item.opt("gasUsed"))
            val gasPrice = quantity(item.opt("gasPrice"))
            add(
                WalletTransaction(
                    hash = hash,
                    blockchain = account.blockchain,
                    type = if (outgoing) TransactionType.SEND else TransactionType.RECEIVE,
                    status = quintiumStatus(item.opt("status")),
                    amount = BigDecimal(quantity(item.opt("value")) ?: BigInteger.ZERO)
                        .movePointLeft(decimals)
                        .toDouble(),
                    symbol = account.blockchain.symbol,
                    timestamp = item.optLong("timestamp", 0L).takeIf { it > 0 }
                        ?.let(Instant::ofEpochSecond) ?: Instant.EPOCH,
                    from = from,
                    to = to,
                    fee = if (gasUsed != null && gasPrice != null) {
                        BigDecimal(gasUsed.multiply(gasPrice)).movePointLeft(decimals).toDouble()
                    } else {
                        null
                    },
                    blockNumber = quantity(item.opt("blockNumber"))?.toString(),
                ),
            )
        }
    }
}

/** Hex quantity ("0x1a"), decimal string or JSON number. */
private fun quantity(value: Any?): BigInteger? = when (value) {
    null, JSONObject.NULL -> null
    is Number -> BigInteger.valueOf(value.toLong())
    is String -> when {
        value.isBlank() -> null
        value.startsWith("0x") || value.startsWith("0X") ->
            value.substring(2).ifEmpty { "0" }.toBigIntegerOrNull(16)
        else -> value.toBigIntegerOrNull()
    }
    else -> null
}

/** Receipt status arrives as a number (1/0), a hex quantity or a bool depending on the node version. */
private fun quintiumStatus(value: Any?): TransactionStatus = when (value) {
    is Boolean -> if (value) TransactionStatus.CONFIRMED else TransactionStatus.FAILED
    null, JSONObject.NULL -> TransactionStatus.PENDING
    else -> when (quantity(value)?.toInt()) {
        1 -> TransactionStatus.CONFIRMED
        0 -> TransactionStatus.FAILED
        else -> TransactionStatus.PENDING
    }
}
