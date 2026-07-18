package io.noriskservis.wpayin.wallet.model

import java.math.BigInteger

object Base58Codec {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val base = BigInteger.valueOf(58)

    fun decode(value: String): ByteArray {
        require(value.isNotEmpty()) { "Base58 value cannot be empty" }
        var number = BigInteger.ZERO
        value.forEach { character ->
            val digit = ALPHABET.indexOf(character)
            require(digit >= 0) { "Invalid Base58 character" }
            number = number.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        val encoded = number.toByteArray().let { bytes ->
            if (bytes.size > 1 && bytes.first() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }
        val leadingZeros = value.takeWhile { it == '1' }.length
        if (number == BigInteger.ZERO) return ByteArray(leadingZeros)
        return ByteArray(leadingZeros) + encoded
    }

    fun isSolanaAddress(value: String): Boolean = runCatching { decode(value).size == 32 }.getOrDefault(false)
}
