package io.noriskservis.wpayin.wallet.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.noriskservis.wpayin.wallet.model.WalletKind
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts wallet material with a non-exportable AES key held by Android Keystore.
 * App backups and device transfer are disabled in AndroidManifest/data extraction rules.
 */
class SecureWalletStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    data class WalletSecret(val kind: WalletKind, val value: String)

    fun hasWallet(): Boolean =
        preferences.contains(KEY_CIPHERTEXT) && preferences.contains(KEY_IV)

    fun save(secret: WalletSecret) {
        require(secret.value.isNotBlank()) { "Wallet secret cannot be empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(secret.value.toByteArray(Charsets.UTF_8))

        preferences.edit()
            .putString(KEY_KIND, secret.kind.name)
            .putString(KEY_IV, cipher.iv.encodeBase64())
            .putString(KEY_CIPHERTEXT, encrypted.encodeBase64())
            .commit()
            .also { saved -> check(saved) { "Unable to persist encrypted wallet" } }
    }

    fun load(): WalletSecret? {
        val iv = preferences.getString(KEY_IV, null)?.decodeBase64() ?: return null
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)?.decodeBase64() ?: return null
        val kind = preferences.getString(KEY_KIND, null)
            ?.let { runCatching { WalletKind.valueOf(it) }.getOrNull() }
            ?: return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        return WalletSecret(kind, plaintext)
    }

    fun clear() {
        preferences.edit().clear().commit()
        val keyStore = keyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "wpayin_wallet_material_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val PREFERENCES_NAME = "wpayin_secure_wallet"
        private const val KEY_KIND = "wallet_kind"
        private const val KEY_IV = "wallet_iv"
        private const val KEY_CIPHERTEXT = "wallet_ciphertext"
    }
}
