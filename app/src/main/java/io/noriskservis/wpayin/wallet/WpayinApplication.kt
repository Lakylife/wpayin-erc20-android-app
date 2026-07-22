package io.noriskservis.wpayin.wallet

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import io.noriskservis.wpayin.wallet.core.AppContainer

class WpayinApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            WALLET_ACTIVITY_CHANNEL_ID,
            getString(R.string.notifications_channel_wallet_activity),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notifications_channel_wallet_activity_description)
            enableVibration(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val WALLET_ACTIVITY_CHANNEL_ID = "wallet_activity"
    }
}
