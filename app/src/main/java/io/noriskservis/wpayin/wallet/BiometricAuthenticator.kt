package io.noriskservis.wpayin.wallet

import android.os.Build
import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthenticator(
    private val activity: FragmentActivity,
) {
    private var credentialResult: ((Result<Unit>) -> Unit)? = null
    private val credentialLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = credentialResult
        credentialResult = null
        callback?.invoke(
            if (result.resultCode == Activity.RESULT_OK) Result.success(Unit)
            else Result.failure(IllegalStateException(activity.getString(R.string.auth_failed))),
        )
    }

    @Suppress("DEPRECATION")
    private fun authenticateWithDeviceCredential(
        title: String,
        subtitle: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (credentialResult != null) return
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val intent = if (keyguard.isDeviceSecure) {
            keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
        } else null
        if (intent == null) {
            onResult(Result.failure(IllegalStateException(
                activity.getString(R.string.auth_secure_authentication_unavailable),
            )))
            return
        }
        credentialResult = onResult
        try {
            credentialLauncher.launch(intent)
        } catch (error: Exception) {
            credentialResult = null
            onResult(Result.failure(error))
        }
    }

    fun authenticateForSigning(onResult: (Result<Unit>) -> Unit) {
        authenticate(
            title = activity.getString(R.string.auth_authorize_transaction),
            subtitle = activity.getString(R.string.auth_sign_subtitle),
            onResult = onResult,
        )
    }

    fun authenticateForUnlock(onResult: (Result<Unit>) -> Unit) {
        authenticate(
            title = activity.getString(R.string.auth_unlock_title),
            subtitle = activity.getString(R.string.auth_unlock_subtitle),
            onResult = onResult,
        )
    }

    private fun authenticate(
        title: String,
        subtitle: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        // Strong biometrics + credential is unsupported on Android 8–10.
        // Use the system lock-screen challenge without weakening biometric strength.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            authenticateWithDeviceCredential(title, subtitle, onResult)
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val availability = BiometricManager.from(activity).canAuthenticate(authenticators)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(Result.failure(IllegalStateException(availability.userFacingMessage())))
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(Result.success(Unit))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(Result.failure(IllegalStateException(errString.toString())))
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    private fun Int.userFacingMessage(): String = when (this) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> activity.getString(R.string.auth_no_biometrics_enrolled)
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> activity.getString(R.string.auth_no_biometric_hardware)
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> activity.getString(R.string.auth_biometrics_unavailable)
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            activity.getString(R.string.auth_security_update_required)
        else -> activity.getString(R.string.auth_secure_authentication_unavailable)
    }
}
