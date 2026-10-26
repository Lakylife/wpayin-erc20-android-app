package io.noriskservis.wpayin.wallet.core

import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinUtxo
import java.math.BigInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewWalletEngineTest {
    @Test
    fun `preview engine cannot create real wallets`() {
        val engine = WalletEngineProvider.create()
        assertFalse(engine.isProductionReady)
        assertFalse(engine.validateMnemonic("not wallet material"))
        assertTrue(runCatching { engine.generateMnemonic() }.isFailure)
        assertTrue(
            runCatching {
                engine.signEvmTransaction(
                    WalletKind.PRIVATE_KEY,
                    "46".repeat(32),
                    EvmTransferDraft(
                        blockchain = Blockchain.ETHEREUM,
                        from = "0x9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f",
                        to = "0x3535353535353535353535353535353535353535",
                        symbol = "ETH",
                        amount = "1",
                        amountAtomic = BigInteger.TEN.pow(18),
                        contractAddress = null,
                        transactionTo = "0x3535353535353535353535353535353535353535",
                        valueHex = "0xde0b6b3a7640000",
                        dataHex = "0x",
                        nonce = BigInteger.valueOf(9),
                        gasLimit = BigInteger.valueOf(21_000),
                        gasPriceWei = BigInteger("20000000000"),
                    ),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                engine.signSolanaTransaction(
                    WalletKind.MNEMONIC,
                    "not wallet material",
                    SolanaTransferDraft(
                        from = "4jpwTqt1qZoR7u6u639z2AngYFGN3nakvKhowcnRZDEC",
                        to = "EN2sCsJ1WDV8UFqsiTXHcUPUxQ4juE71eCknHYYMifkd",
                        amount = "0.000000042",
                        lamports = 42,
                        recentBlockhash = "11111111111111111111111111111111",
                        lastValidBlockHeight = 1,
                        feeLamports = 5_000,
                    ),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                engine.signBitcoinTransaction(
                    WalletKind.MNEMONIC,
                    "not wallet material",
                    BitcoinTransferDraft(
                        from = "bc1qexample",
                        to = "bc1qrecipient",
                        amount = "0.00001",
                        amountSatoshis = 1_000,
                        feeRateSatsPerVByte = 2,
                        feeSatoshis = 280,
                        selectedUtxos = listOf(BitcoinUtxo("00".repeat(32), 0, 2_000)),
                    ),
                )
            }.isFailure,
        )
    }
}
