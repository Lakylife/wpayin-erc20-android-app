package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.model.WalletSnapshot
import io.noriskservis.wpayin.wallet.model.WalletKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WalletSessionTest {
    @Test fun delayedBalanceResponseCannotRestoreDeletedWallet() = runTest {
        val wallet = WalletSnapshot(kind = WalletKind.MNEMONIC, accounts = emptyList())
        val state = MutableStateFlow(WalletState(wallet = wallet, isRefreshing = true))
        val response = CompletableDeferred<WalletSnapshot>()
        val job = launch {
            val refreshed = response.await()
            state.update { latest -> latest.forSession(wallet) { it.copy(wallet = refreshed) } }
        }
        state.value = state.value.copy(wallet = null, isRefreshing = false)
        response.complete(wallet.copy(name = "Old response"))
        job.join()
        assertNull(state.value.wallet)
        assertFalse(state.value.isRefreshing)
    }

    @Test fun reimportOfSameAccountsRejectsOldResponseAndError() {
        val old = WalletSnapshot(kind = WalletKind.MNEMONIC, accounts = emptyList())
        val replacement = WalletSnapshot(kind = old.kind, accounts = old.accounts)
        val state = WalletState(wallet = replacement, isRefreshing = true)
        val result = state.forSession(old) {
            it.copy(wallet = old, isRefreshing = false, errorMessage = "Old network error")
        }
        assertSame(state, result)
        assertEquals(replacement.sessionId, result.wallet?.sessionId)
        assertNull(result.errorMessage)
        assertTrue(result.isRefreshing)
    }

    @Test fun currentSessionAcceptsRefresh() {
        val wallet = WalletSnapshot(kind = WalletKind.MNEMONIC, accounts = emptyList())
        val state = WalletState(wallet = wallet, isRefreshing = true)
        val updated = wallet.copy(name = "Refreshed")
        val result = state.forSession(wallet) { it.copy(wallet = updated, isRefreshing = false) }
        assertEquals("Refreshed", result.wallet?.name)
        assertFalse(result.isRefreshing)
    }
}
