package io.noriskservis.wpayin.wallet.core

import com.google.protobuf.ByteString
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.SignedBitcoinTransaction
import java.math.BigInteger
import wallet.core.java.AnySigner
import wallet.core.jni.CoinType
import wallet.core.jni.Curve
import wallet.core.jni.HDWallet
import wallet.core.jni.Mnemonic
import wallet.core.jni.PrivateKey
import wallet.core.jni.proto.Ethereum
import wallet.core.jni.proto.Solana
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.AnyAddress
import wallet.core.jni.BitcoinScript

object WalletEngineProvider {
    fun create(): WalletEngine = TrustWalletCoreEngine()
}

private class TrustWalletCoreEngine : WalletEngine {
    override val isProductionReady = true

    override fun generateMnemonic(): String = HDWallet(128, "").mnemonic()

    override fun validateMnemonic(mnemonic: String): Boolean = Mnemonic.isValid(mnemonic)

    override fun deriveAddresses(mnemonic: String): Map<Blockchain, String> {
        require(validateMnemonic(mnemonic)) { "Invalid recovery phrase" }
        val wallet = HDWallet(mnemonic, "")
        return linkedMapOf(
            Blockchain.BITCOIN to wallet.getAddressForCoin(CoinType.BITCOIN),
            Blockchain.ETHEREUM to wallet.getAddressForCoin(CoinType.ETHEREUM),
            Blockchain.SOLANA to wallet.getAddressForCoin(CoinType.SOLANA),
            Blockchain.BNB_CHAIN to wallet.getAddressForCoin(CoinType.SMARTCHAIN),
            Blockchain.POLYGON to wallet.getAddressForCoin(CoinType.POLYGON),
            Blockchain.ARBITRUM to wallet.getAddressForCoin(CoinType.ARBITRUM),
            Blockchain.OPTIMISM to wallet.getAddressForCoin(CoinType.OPTIMISM),
            Blockchain.AVALANCHE to wallet.getAddressForCoin(CoinType.AVALANCHECCHAIN),
            Blockchain.BASE to wallet.getAddressForCoin(CoinType.BASE),
        )
    }

    override fun validatePrivateKey(privateKey: String): Boolean {
        val bytes = privateKey.hexToBytesOrNull() ?: return false
        return bytes.size == 32 && PrivateKey.isValid(bytes, Curve.SECP256K1)
    }

    override fun deriveEvmAddress(privateKey: String): String {
        val bytes = privateKey.hexToBytesOrNull()
        require(bytes != null && PrivateKey.isValid(bytes, Curve.SECP256K1)) {
            "Invalid private key"
        }
        return CoinType.ETHEREUM.deriveAddress(PrivateKey(bytes))
    }

    override fun validateAddress(blockchain: Blockchain, address: String): Boolean = runCatching {
        AnyAddress(address, blockchain.walletCoreCoinType())
        true
    }.getOrDefault(false)

    override fun signEvmTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: EvmTransferDraft,
    ): String {
        require(draft.blockchain.isEvm) { "Only EVM transactions can be signed by this method" }
        val privateKeyBytes = when (walletKind) {
            WalletKind.MNEMONIC -> {
                require(validateMnemonic(walletSecret)) { "Invalid stored recovery phrase" }
                HDWallet(walletSecret, "").getKeyForCoin(draft.blockchain.walletCoreCoinType()).data()
            }
            WalletKind.PRIVATE_KEY -> walletSecret.hexToBytesOrNull()
                ?: error("Invalid stored private key")
        }

        try {
            val input = Ethereum.SigningInput.newBuilder().apply {
                privateKey = ByteString.copyFrom(privateKeyBytes)
                toAddress = draft.transactionTo
                chainId = draft.blockchain.chainId.asWalletCoreBytes()
                nonce = draft.nonce.asWalletCoreBytes()
                gasPrice = draft.gasPriceWei.asWalletCoreBytes()
                gasLimit = draft.gasLimit.asWalletCoreBytes()
                transaction = Ethereum.Transaction.newBuilder().apply {
                    if (draft.contractAddress == null) {
                        transfer = Ethereum.Transaction.Transfer.newBuilder().apply {
                            amount = draft.amountAtomic.asWalletCoreBytes()
                        }.build()
                    } else {
                        erc20Transfer = Ethereum.Transaction.ERC20Transfer.newBuilder().apply {
                            to = draft.to
                            amount = draft.amountAtomic.asWalletCoreBytes()
                        }.build()
                    }
                }.build()
            }.build()
            val output = AnySigner.sign(input, CoinType.ETHEREUM, Ethereum.SigningOutput.parser())
            check(!output.encoded.isEmpty) {
                output.errorMessage.ifBlank { "Wallet Core could not sign the transaction" }
            }
            return "0x" + output.encoded.toByteArray().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    override fun signSolanaTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: SolanaTransferDraft,
    ): String {
        require(walletKind == WalletKind.MNEMONIC) { "A mnemonic wallet is required for Solana" }
        require(validateMnemonic(walletSecret)) { "Invalid stored recovery phrase" }
        val privateKeyBytes = HDWallet(walletSecret, "").getKeyForCoin(CoinType.SOLANA).data()
        try {
            val transfer = Solana.Transfer.newBuilder().apply {
                recipient = draft.to
                value = draft.lamports
            }.build()
            val input = Solana.SigningInput.newBuilder().apply {
                transferTransaction = transfer
                recentBlockhash = draft.recentBlockhash
                privateKey = ByteString.copyFrom(privateKeyBytes)
            }.build()
            val output = AnySigner.sign(input, CoinType.SOLANA, Solana.SigningOutput.parser())
            check(output.encoded.isNotBlank()) { "Wallet Core could not sign the Solana transaction" }
            return output.encoded
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    override fun signBitcoinTransaction(
        walletKind: WalletKind,
        walletSecret: String,
        draft: BitcoinTransferDraft,
    ): SignedBitcoinTransaction {
        require(walletKind == WalletKind.MNEMONIC) { "A mnemonic wallet is required for Bitcoin" }
        require(validateMnemonic(walletSecret)) { "Invalid stored recovery phrase" }
        require(validateAddress(Blockchain.BITCOIN, draft.to)) { "Invalid Bitcoin recipient address" }
        val privateKeyBytes = HDWallet(walletSecret, "").getKeyForCoin(CoinType.BITCOIN).data()
        try {
            val lockScript = BitcoinScript.lockScriptForAddress(draft.from, CoinType.BITCOIN).data()
            check(lockScript.isNotEmpty()) { "Unable to create Bitcoin locking script" }
            val input = Bitcoin.SigningInput.newBuilder().apply {
                amount = draft.amountSatoshis
                hashType = BitcoinScript.hashTypeForCoin(CoinType.BITCOIN)
                toAddress = draft.to
                changeAddress = draft.from
                byteFee = draft.feeRateSatsPerVByte
                addPrivateKey(ByteString.copyFrom(privateKeyBytes))
                draft.selectedUtxos.forEach { source ->
                    val txid = source.txid.hexToBytesOrNull(expectedBytes = 32)
                        ?: error("Invalid Bitcoin UTXO transaction ID")
                    val outPoint = Bitcoin.OutPoint.newBuilder().apply {
                        hash = ByteString.copyFrom(txid.reversedArray())
                        index = source.outputIndex
                        sequence = -1
                    }.build()
                    addUtxo(
                        Bitcoin.UnspentTransaction.newBuilder().apply {
                            amount = source.valueSatoshis
                            this.outPoint = outPoint
                            script = ByteString.copyFrom(lockScript)
                        }.build(),
                    )
                }
            }
            val plan = AnySigner.plan(input.build(), CoinType.BITCOIN, Bitcoin.TransactionPlan.parser())
            check(plan.fee > 0 && plan.amount == draft.amountSatoshis) { "Wallet Core could not plan the Bitcoin transaction" }
            input.plan = plan
            val output = AnySigner.sign(input.build(), CoinType.BITCOIN, Bitcoin.SigningOutput.parser())
            check(!output.encoded.isEmpty) {
                output.errorMessage.ifBlank { "Wallet Core could not sign the Bitcoin transaction" }
            }
            return SignedBitcoinTransaction(
                rawHex = output.encoded.toByteArray().joinToString("") { "%02x".format(it.toInt() and 0xff) },
                feeSatoshis = plan.fee,
            )
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    private fun String.hexToBytesOrNull(): ByteArray? {
        val normalized = removePrefix("0x")
        if (normalized.length != 64 || normalized.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun String.hexToBytesOrNull(expectedBytes: Int): ByteArray? {
        if (length != expectedBytes * 2 || any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(expectedBytes) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun Blockchain.walletCoreCoinType(): CoinType = when (this) {
        Blockchain.ETHEREUM -> CoinType.ETHEREUM
        Blockchain.BNB_CHAIN -> CoinType.SMARTCHAIN
        Blockchain.POLYGON -> CoinType.POLYGON
        Blockchain.ARBITRUM -> CoinType.ARBITRUM
        Blockchain.OPTIMISM -> CoinType.OPTIMISM
        Blockchain.AVALANCHE -> CoinType.AVALANCHECCHAIN
        Blockchain.BASE -> CoinType.BASE
        Blockchain.BITCOIN -> CoinType.BITCOIN
        Blockchain.SOLANA -> CoinType.SOLANA
    }

    private fun Long?.asWalletCoreBytes(): ByteString =
        requireNotNull(this) { "Missing EVM chain ID" }.toBigInteger().asWalletCoreBytes()

    private fun BigInteger.asWalletCoreBytes(): ByteString {
        require(signum() >= 0) { "Wallet Core integers cannot be negative" }
        val signed = toByteArray()
        val unsigned = if (signed.size > 1 && signed.first() == 0.toByte()) signed.copyOfRange(1, signed.size) else signed
        return ByteString.copyFrom(unsigned)
    }

    companion object {
        init {
            System.loadLibrary("TrustWalletCore")
        }
    }
}
