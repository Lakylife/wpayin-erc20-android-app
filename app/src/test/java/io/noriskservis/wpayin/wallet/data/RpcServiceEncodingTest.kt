package io.noriskservis.wpayin.wallet.data

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcServiceEncodingTest {
    private val sender = "0x1111111111111111111111111111111111111111"
    private val recipient = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"

    @Test
    fun `validates exact EVM address shape`() {
        assertTrue(RpcService.isEvmAddress(sender))
        assertTrue(RpcService.isEvmAddress(recipient.uppercase().replace("0X", "0x")))
        assertFalse(RpcService.isEvmAddress(sender.removePrefix("0x")))
        assertFalse(RpcService.isEvmAddress("0x1234"))
        assertFalse(RpcService.isEvmAddress("0xgg11111111111111111111111111111111111111"))
    }

    @Test
    fun `encodes ERC20 balanceOf calldata`() {
        assertEquals(
            "0x70a08231" + "0".repeat(24) + sender.removePrefix("0x"),
            RpcService.erc20BalanceOfData(sender),
        )
    }

    @Test
    fun `encodes ERC20 transfer calldata as two ABI words`() {
        val encoded = RpcService.erc20TransferData(recipient, BigInteger("1234567"))
        assertEquals(138, encoded.length)
        assertEquals("0xa9059cbb", encoded.take(10))
        assertEquals("0".repeat(24) + recipient.removePrefix("0x"), encoded.substring(10, 74))
        assertEquals(BigInteger("1234567").toString(16).padStart(64, '0'), encoded.takeLast(64))
    }

    @Test
    fun `converts decimal amount without floating point`() {
        assertEquals(BigInteger("1234567"), RpcService.atomicAmount("1.234567", 6))
        assertEquals(BigInteger("1"), RpcService.atomicAmount("0.000000000000000001", 18))
        assertThrows(IllegalStateException::class.java) { RpcService.atomicAmount("1.0000001", 6) }
        assertThrows(IllegalArgumentException::class.java) { RpcService.atomicAmount("0", 18) }
        assertThrows(IllegalArgumentException::class.java) {
            RpcService.atomicAmount(BigInteger.ONE.shiftLeft(256).toString(), 0)
        }
    }

    @Test
    fun `decodes dynamic and bytes32 token metadata`() {
        val dynamic = "0x" +
            BigInteger.valueOf(32).toString(16).padStart(64, '0') +
            BigInteger.valueOf(4).toString(16).padStart(64, '0') +
            "55534443".padEnd(64, '0')
        val bytes32 = "0x" + "55534454".padEnd(64, '0')

        assertEquals("USDC", RpcService.decodeAbiString(dynamic))
        assertEquals("USDT", RpcService.decodeAbiString(bytes32))
        assertThrows(IllegalArgumentException::class.java) { RpcService.decodeAbiString("0xzz") }
        val overflowingOffset = "0x" + "f".repeat(64) + "0".repeat(64)
        assertThrows(IllegalArgumentException::class.java) {
            RpcService.decodeAbiString(overflowingOffset)
        }
    }

    @Test
    fun `estimates native segwit transaction virtual bytes`() {
        assertEquals(140, RpcService.estimateBitcoinVirtualBytes(inputCount = 1, outputCount = 2))
        assertEquals(208, RpcService.estimateBitcoinVirtualBytes(inputCount = 2, outputCount = 2))
        assertThrows(IllegalArgumentException::class.java) {
            RpcService.estimateBitcoinVirtualBytes(inputCount = 0, outputCount = 2)
        }
    }
}
