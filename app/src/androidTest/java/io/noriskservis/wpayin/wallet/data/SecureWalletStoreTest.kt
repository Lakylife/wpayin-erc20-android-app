package io.noriskservis.wpayin.wallet.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.noriskservis.wpayin.wallet.model.WalletKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureWalletStoreTest {
    private lateinit var store: SecureWalletStore

    @Before
    fun setUp() {
        store = SecureWalletStore(ApplicationProvider.getApplicationContext())
        store.clear()
    }

    @After
    fun tearDown() = store.clear()

    @Test
    fun encryptedSecretRoundTripsThroughAndroidKeystore() {
        val secret = SecureWalletStore.WalletSecret(WalletKind.MNEMONIC, "test-only-secret")
        store.save(secret)

        assertTrue(store.hasWallet())
        assertEquals(secret, store.load())
    }
}
