package io.noriskservis.wpayin.wallet

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthenticator(
    private val activity: FragmentActivity,
) {
    fun authenticateForSigning(onResult: (Result<Unit>) -> Unit) {
        authenticate(
            title = "Authorize transaction",
            subtitle = "Confirm that you want to sign and broadcast this transfer",
            onResult = onResult,
        )
    }

    fun authenticateForUnlock(onResult: (Result<Unit>) -> Unit) {
        authenticate(
            title = "Unlock Wpayin Wallet",
            subtitle = "Authenticate to access your wallet",
            onResult = onResult,
        )
    }

    private fun authenticate(
        title: String,
        subtitle: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
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
                .apply {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        setNegativeButtonText("Cancel")
                    }
                }
                .build(),
        )
    }

    private fun Int.userFacingMessage(): String = when (this) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No secure biometric authentication is enrolled"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no supported biometric hardware"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric authentication is temporarily unavailable"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "A security update is required before signing"
        else -> "Secure authentication is unavailable"
    }
}
