package io.noriskservis.wpayin.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.noriskservis.wpayin.wallet.ui.components.LoadingScreen
import io.noriskservis.wpayin.wallet.ui.screens.CreateWalletScreen
import io.noriskservis.wpayin.wallet.ui.screens.ImportWalletScreen
import io.noriskservis.wpayin.wallet.ui.screens.MainTabsScreen
import io.noriskservis.wpayin.wallet.ui.screens.OnboardingScreen
import io.noriskservis.wpayin.wallet.ui.screens.ReceiveScreen
import io.noriskservis.wpayin.wallet.ui.screens.SendScreen
import io.noriskservis.wpayin.wallet.ui.screens.AddTokenScreen
import io.noriskservis.wpayin.wallet.ui.screens.WelcomeScreen
import io.noriskservis.wpayin.wallet.ui.screens.AppLockScreen

private enum class AppRoute { ROOT, CREATE, IMPORT, RECEIVE, SEND, ADD_TOKEN }

@Composable
fun WpayinWalletApp(
    viewModel: MainViewModel,
    authenticateForSigning: ((Result<Unit>) -> Unit) -> Unit,
    authenticateForUnlock: ((Result<Unit>) -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingMnemonic by viewModel.pendingMnemonic.collectAsStateWithLifecycle()
    val sessionUnlocked by viewModel.sessionUnlocked.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var route by rememberSaveable { mutableStateOf(AppRoute.ROOT) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.isInitializing) {
        LoadingScreen()
        return
    }

    if (!state.settings.onboardingComplete) {
        OnboardingScreen(onComplete = viewModel::completeOnboarding)
        return
    }

    if (state.wallet != null && !sessionUnlocked) {
        AppLockScreen(
            onUnlock = { onComplete ->
                authenticateForUnlock { result ->
                    result.onSuccess { viewModel.unlockSession() }
                    onComplete(result)
                }
            },
        )
        return
    }

    BackHandler(enabled = route != AppRoute.ROOT) {
        if (route == AppRoute.CREATE) viewModel.discardPendingMnemonic()
        route = AppRoute.ROOT
    }

    when (route) {
        AppRoute.CREATE -> CreateWalletScreen(
            mnemonic = pendingMnemonic,
            onGenerate = viewModel::generateRecoveryPhrase,
            onConfirm = viewModel::confirmGeneratedWallet,
            onBack = {
                viewModel.discardPendingMnemonic()
                route = AppRoute.ROOT
            },
            onFinished = { route = AppRoute.ROOT },
        )
        AppRoute.IMPORT -> ImportWalletScreen(
            onImportMnemonic = viewModel::importMnemonic,
            onImportPrivateKey = viewModel::importPrivateKey,
            onBack = { route = AppRoute.ROOT },
            onFinished = { route = AppRoute.ROOT },
        )
        AppRoute.RECEIVE -> {
            val wallet = state.wallet
            if (wallet == null) {
                route = AppRoute.ROOT
            } else {
                ReceiveScreen(wallet = wallet, onBack = { route = AppRoute.ROOT })
            }
        }
        AppRoute.SEND -> {
            val wallet = state.wallet
            if (wallet == null) {
                route = AppRoute.ROOT
            } else {
                SendScreen(
                    state = state,
                    onPrepare = viewModel::prepareTransfer,
                    onAuthenticate = authenticateForSigning,
                    onSend = viewModel::sendTransfer,
                    onBack = { route = AppRoute.ROOT },
                    onFinished = { route = AppRoute.ROOT },
                )
            }
        }
        AppRoute.ADD_TOKEN -> AddTokenScreen(
            state = state,
            onAdd = viewModel::addCustomToken,
            onRemove = viewModel::removeCustomToken,
            onBack = { route = AppRoute.ROOT },
        )
        AppRoute.ROOT -> {
            val wallet = state.wallet
            if (wallet == null) {
                WelcomeScreen(
                    productionWalletEnabled = state.isProductionWalletEnabled,
                    onCreate = { route = AppRoute.CREATE },
                    onImport = { route = AppRoute.IMPORT },
                )
            } else {
                MainTabsScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onToggleBalances = viewModel::setBalancesHidden,
                    onReceive = { route = AppRoute.RECEIVE },
                    onSend = { route = AppRoute.SEND },
                    onAddToken = { route = AppRoute.ADD_TOKEN },
                    onLockWallet = viewModel::lockSession,
                    onDeleteWallet = viewModel::deleteWallet,
                )
            }
        }
    }

    if (state.errorMessage != null && state.wallet == null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Wallet operation failed") },
            text = { Text(state.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            },
        )
    }
}
