package io.noriskservis.wpayin.wallet.model

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class TransferReviewTest {
    private fun draft(amount: String, gas: Long = 21_000) = EvmTransferDraft(
        blockchain = Blockchain.ETHEREUM, from = "0x" + "1".repeat(40),
        to = "0x" + "2".repeat(40), symbol = "ETH", amount = amount,
        amountAtomic = amount.toBigDecimal().movePointRight(18).toBigIntegerExact(),
        contractAddress = null, transactionTo = "0x" + "2".repeat(40),
        valueHex = "0x0", dataHex = "0x", nonce = BigInteger.ZERO,
        gasLimit = BigInteger.valueOf(gas), gasPriceWei = BigInteger.valueOf(1_000_000_000),
    )

    @Test fun reviewIncludesApplicationFeeAndBothNetworkFees() {
        val transfer = draft("1").copy(platformFeeTransfer = draft("0.0025"))
        assertEquals("1.0025".toBigDecimal(), transfer.totalAssetAmount)
        assertEquals(BigInteger("42000000000000"), transfer.totalMaximumFeeWei)
        assertEquals(0.000042, transfer.maximumNetworkFee, 0.000000000001)
        // This balance covers the transfer and its first gas fee, but not the fee transfer.
        val balance = "1.000021".toBigDecimal()
        val debit = transfer.totalAssetAmount + transfer.totalMaximumFeeWei.toBigDecimal().movePointLeft(18)
        assertTrue(debit > balance)
    }

    @Test fun tokenReviewKeepsNativeGasSeparateFromTokenDebit() {
        val fee = draft("0.025", 65_000).copy(symbol = "USDC", contractAddress = "0x" + "3".repeat(40))
        val transfer = draft("10", 80_000).copy(symbol = "USDC", contractAddress = fee.contractAddress, platformFeeTransfer = fee)
        assertEquals("10.025".toBigDecimal(), transfer.totalAssetAmount)
        assertEquals(0.000145, transfer.maximumNetworkFee, 0.000000000001)
    }

    @Test fun noApplicationFeeDoesNotAddGasOrTokenDebit() {
        val transfer = draft("1")
        assertEquals("1".toBigDecimal(), transfer.totalAssetAmount)
        assertEquals(transfer.maximumFeeWei, transfer.totalMaximumFeeWei)
    }

    @Test fun snapshotUpdatesKeepSessionButReimportGetsANewSession() {
        val original = WalletSnapshot(kind = WalletKind.MNEMONIC, accounts = emptyList())
        assertEquals(original.sessionId, original.copy(name = "Renamed wallet").sessionId)
        val reimported = WalletSnapshot(kind = original.kind, accounts = original.accounts)
        assertNotEquals(original.sessionId, reimported.sessionId)
    }
}
