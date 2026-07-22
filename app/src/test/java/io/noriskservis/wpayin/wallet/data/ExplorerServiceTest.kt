package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.Blockchain
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerServiceTest {
    @Test
    fun parsesPositiveErc20BalancesAndRejectsSpamAndNfts() {
        val entries = JSONArray(
            """
            [
              {
                "value": "1250000",
                "token": {
                  "name": "Example USD",
                  "symbol": "xUSD",
                  "type": "ERC-20",
                  "decimals": "6",
                  "exchange_rate": "1.01",
                  "address_hash": "0x1111111111111111111111111111111111111111"
                }
              },
              {
                "value": "10",
                "token": {
                  "name": "Spam",
                  "symbol": "SPAM",
                  "type": "ERC-20",
                  "reputation": "spam",
                  "decimals": "0",
                  "address_hash": "0x2222222222222222222222222222222222222222"
                }
              },
              {
                "value": "999999999999999999999999999999",
                "token": {
                  "name": "Fake Airdrop",
                  "symbol": "PEPE",
                  "type": "ERC-20",
                  "reputation": "scam",
                  "decimals": "0",
                  "address_hash": "0x4444444444444444444444444444444444444444"
                }
              },
              {
                "value": "1",
                "token": {
                  "name": "Collectible",
                  "symbol": "NFT",
                  "type": "ERC-721",
                  "decimals": "0",
                  "address_hash": "0x3333333333333333333333333333333333333333"
                }
              }
            ]
            """.trimIndent(),
        )

        val balances = parseBlockscoutTokenBalances(Blockchain.ETHEREUM, entries)

        assertEquals(1, balances.size)
        assertEquals("xUSD", balances.single().token.symbol)
        assertEquals(1.25, balances.single().balance, 0.0)
        assertEquals(1.01, balances.single().priceUsd, 0.0)
    }
}
