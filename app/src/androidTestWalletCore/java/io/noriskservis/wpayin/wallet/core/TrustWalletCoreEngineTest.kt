package io.noriskservis.wpayin.wallet.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.noriskservis.wpayin.wallet.model.Base58Codec
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinUtxo
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.WalletKind
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrustWalletCoreEngineTest {
    private val mnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun signsOfficialWalletCoreLegacyEthereumVector() {
        val signed = WalletEngineProvider.create().signEvmTransaction(
            walletKind = WalletKind.PRIVATE_KEY,
            walletSecret = "46".repeat(32),
            draft = EvmTransferDraft(
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

        assertEquals(
            "0xf86c098504a817c800825208943535353535353535353535353535353535353535" +
                "880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71" +
                "ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc6421" +
                "4b297fb1966a3b6d83",
            signed,
        )
    }

    @Test
    fun signsNativeSolanaTransfer() {
        val engine = WalletEngineProvider.create()
        val sender = engine.deriveAddresses(mnemonic).getValue(Blockchain.SOLANA)
        val signed = engine.signSolanaTransaction(
            walletKind = WalletKind.MNEMONIC,
            walletSecret = mnemonic,
            draft = SolanaTransferDraft(
                from = sender,
                to = sender,
                amount = "0.000000042",
                lamports = 42,
                recentBlockhash = "11111111111111111111111111111111",
                lastValidBlockHeight = 1,
                feeLamports = 5_000,
            ),
        )

        val bytes = Base58Codec.decode(signed)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size <= 1_232)
    }

    @Test
    fun signsNativeSegwitBitcoinTransfer() {
        val engine = WalletEngineProvider.create()
        val sender = engine.deriveAddresses(mnemonic).getValue(Blockchain.BITCOIN)
        assertTrue(sender.startsWith("bc1q"))
        val signed = engine.signBitcoinTransaction(
            walletKind = WalletKind.MNEMONIC,
            walletSecret = mnemonic,
            draft = BitcoinTransferDraft(
                from = sender,
                to = sender,
                amount = "0.00001",
                amountSatoshis = 1_000,
                feeRateSatsPerVByte = 1,
                feeSatoshis = 300,
                selectedUtxos = listOf(
                    BitcoinUtxo(
                        txid = "11".repeat(32),
                        outputIndex = 0,
                        valueSatoshis = 10_000,
                    ),
                ),
            ),
        )

        assertTrue(signed.rawHex.length > 100)
        assertTrue(signed.rawHex.all { it.digitToIntOrNull(16) != null })
        assertTrue(signed.feeSatoshis in 1..300)
    }
}
