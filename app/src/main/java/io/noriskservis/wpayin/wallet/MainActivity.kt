package io.noriskservis.wpayin.wallet

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import io.noriskservis.wpayin.wallet.ui.MainViewModel
import io.noriskservis.wpayin.wallet.ui.WpayinWalletApp
import io.noriskservis.wpayin.wallet.ui.theme.WpayinTheme

class MainActivity : FragmentActivity() {
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
            WpayinTheme {
                val viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(repository))
                WpayinWalletApp(
                    viewModel = viewModel,
                    authenticateForSigning = biometricAuthenticator::authenticateForSigning,
                    authenticateForUnlock = biometricAuthenticator::authenticateForUnlock,
                )
            }
        }
    }
}
