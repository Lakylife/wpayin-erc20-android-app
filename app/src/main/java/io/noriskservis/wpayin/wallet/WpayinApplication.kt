package io.noriskservis.wpayin.wallet

import android.app.Application
import io.noriskservis.wpayin.wallet.core.AppContainer

class WpayinApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
