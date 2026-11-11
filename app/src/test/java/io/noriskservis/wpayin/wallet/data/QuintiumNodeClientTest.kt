package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.WalletAccount
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class QuintiumNodeClientTest {
    private val me = "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f"

    @Test
    fun parsesAddressTransactionsPage() {
        val page = JSONObject(
            """
            {
              "total": 3,
              "items": [
                {
                  "hash": "0x${"a1".repeat(32)}",
                  "from": "0x4242424242424242424242424242424242424242",
                  "to": "$me",
                  "value": "0xde0b6b3a7640000",
                  "gasPrice": "0x3b9aca00",
                  "gasUsed": "0x5208",
                  "blockNumber": "0x10",
                  "timestamp": 1794400000,
                  "status": 1
                },
                {
                  "hash": "0x${"b2".repeat(32)}",
                  "from": "${me.uppercase().replace("0X", "0x")}",
                  "to": null,
                  "value": "0x0",
                  "blockNumber": "0x11",
                  "timestamp": 1794400015,
                  "status": "0x0"
                },
                { "from": "0x1" }
              ]
            }
            """.trimIndent(),
        )
        val transactions = parseQuintiumTransactions(WalletAccount(Blockchain.QUINTIUM, me), page)

        assertEquals(2, transactions.size)
        val received = transactions[0]
        assertEquals(TransactionType.RECEIVE, received.type)
        assertEquals(TransactionStatus.CONFIRMED, received.status)
        assertEquals(1.0, received.amount, 1e-12)
        assertEquals(0.000021, received.fee ?: 0.0, 1e-12)
        assertEquals("16", received.blockNumber)
        assertEquals(Instant.ofEpochSecond(1_794_400_000), received.timestamp)

        val failed = transactions[1]
        assertEquals(TransactionType.SEND, failed.type)
        assertEquals(TransactionStatus.FAILED, failed.status)
        assertEquals("", failed.to)
        assertEquals(null, failed.fee)
    }

    @Test
    fun readsHexNumbers() {
        assertEquals(52402L, QuintiumNodeClient.hexToLong("0xccb2"))
        assertEquals(0L, QuintiumNodeClient.hexToLong("0x"))
    }
}
