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
                        setNegativeButtonText(activity.getString(R.string.common_cancel))
                    }
                }
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
