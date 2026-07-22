package io.noriskservis.wpayin.wallet.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LifiServiceTest {
    @Test
    fun parsesAndValidatesQuoteTransaction() {
        val response = JSONObject(
            """
            {
              "tool": "lifi",
              "toolDetails": { "name": "LI.FI" },
              "estimate": {
                "toAmount": "2500000",
                "toAmountMin": "2487500",
                "executionDuration": 42,
                "approvalAddress": "0x1111111111111111111111111111111111111111"
              },
              "transactionRequest": {
                "chainId": 1,
                "to": "0x2222222222222222222222222222222222222222",
                "data": "0x12345678",
                "value": "1000000000000000"
              }
            }
            """.trimIndent(),
        )

        val quote = LifiService().parseQuote(response, expectedFromChainId = 1, toDecimals = 6)

        assertEquals("2.5", quote.toAmount)
        assertEquals("2.4875", quote.minimumToAmount)
        assertEquals("LI.FI", quote.provider)
        assertEquals(42, quote.estimatedDurationSeconds)
        assertEquals("0xde0b6b3a7640000", quote.transactionValueHex)
    }

    @Test
    fun ignoresZeroApprovalAddressForNativeAssets() {
        val response = JSONObject(
            """
            {
              "tool": "lifi",
              "estimate": {
                "toAmount": "1",
                "toAmountMin": "1",
                "approvalAddress": "${LifiService.NATIVE_TOKEN_ADDRESS}"
              },
              "transactionRequest": {
                "chainId": "8453",
                "to": "0x2222222222222222222222222222222222222222",
                "data": "0x12345678",
                "value": "0x0"
              }
            }
            """.trimIndent(),
        )

        val quote = LifiService().parseQuote(response, expectedFromChainId = 8453, toDecimals = 18)

        assertNull(quote.approvalAddress)
        assertEquals("0.000000000000000001", quote.toAmount)
    }
}
