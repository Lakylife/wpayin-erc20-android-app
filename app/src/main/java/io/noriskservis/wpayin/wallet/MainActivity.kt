package io.noriskservis.wpayin.wallet

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import io.noriskservis.wpayin.wallet.ui.MainViewModel
import io.noriskservis.wpayin.wallet.ui.WpayinWalletApp
import io.noriskservis.wpayin.wallet.ui.theme.WpayinTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val repository = (application as WpayinApplication).container.walletRepository
        val biometricAuthenticator = BiometricAuthenticator(this)
        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(repository))
            val state by viewModel.state.collectAsStateWithLifecycle()
            WpayinTheme(appColorTheme = state.settings.colorTheme) {
                WpayinWalletApp(
                    viewModel = viewModel,
                    authenticateForSigning = biometricAuthenticator::authenticateForSigning,
                    authenticateForUnlock = biometricAuthenticator::authenticateForUnlock,
                )
            }
        }
    }
}
