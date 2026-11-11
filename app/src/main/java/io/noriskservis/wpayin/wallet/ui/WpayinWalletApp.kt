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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.ui.components.LoadingScreen
import io.noriskservis.wpayin.wallet.ui.screens.CreateWalletScreen
import io.noriskservis.wpayin.wallet.ui.screens.ImportWalletScreen
import io.noriskservis.wpayin.wallet.ui.screens.MainTabsScreen
import io.noriskservis.wpayin.wallet.ui.screens.OnboardingScreen
import io.noriskservis.wpayin.wallet.ui.screens.ReceiveScreen
import io.noriskservis.wpayin.wallet.ui.screens.SendScreen
import io.noriskservis.wpayin.wallet.ui.screens.AddTokenScreen
import io.noriskservis.wpayin.wallet.ui.screens.AllAssetsScreen
import io.noriskservis.wpayin.wallet.ui.screens.AssetDetailScreen
import io.noriskservis.wpayin.wallet.ui.screens.P2PScreen
import io.noriskservis.wpayin.wallet.ui.screens.WelcomeScreen
import io.noriskservis.wpayin.wallet.ui.screens.AppLockScreen

private enum class AppRoute {
    ROOT,
    CREATE,
    IMPORT,
    RECEIVE,
    SEND,
    ADD_TOKEN,
    ALL_ASSETS,
    ASSET_DETAIL,
    P2P,
}

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
    var selectedAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialSwapAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var rootTab by rememberSaveable { mutableStateOf(0) }

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
                ReceiveScreen(
                    wallet = wallet,
                    initialAssetId = selectedAssetId,
                    onBack = {
                        selectedAssetId = null
                        route = AppRoute.ROOT
                    },
                )
            }
        }
        AppRoute.SEND -> {
            val wallet = state.wallet
            if (wallet == null) {
                route = AppRoute.ROOT
            } else {
                SendScreen(
                    state = state,
                    initialAssetId = selectedAssetId,
                    onPrepare = viewModel::prepareTransfer,
                    onAuthenticate = authenticateForSigning,
                    onSend = viewModel::sendTransfer,
                    onAddSavedAddress = viewModel::addSavedAddress,
                    onRemoveSavedAddress = viewModel::removeSavedAddress,
                    onBack = {
                        selectedAssetId = null
                        route = AppRoute.ROOT
                    },
                    onFinished = {
                        selectedAssetId = null
                        route = AppRoute.ROOT
                    },
                )
            }
        }
        AppRoute.ADD_TOKEN -> AddTokenScreen(
            state = state,
            onAdd = viewModel::addCustomToken,
            onRemove = viewModel::removeCustomToken,
            onBack = { route = AppRoute.ROOT },
        )
        AppRoute.ALL_ASSETS -> AllAssetsScreen(
            state = state,
            onAddToken = { route = AppRoute.ADD_TOKEN },
            onOpenAsset = { asset ->
                selectedAssetId = asset.id
                route = AppRoute.ASSET_DETAIL
            },
            onToggleFavorite = viewModel::setFavorite,
            onBack = { route = AppRoute.ROOT },
        )
        AppRoute.ASSET_DETAIL -> {
            val asset = state.wallet?.assets?.firstOrNull { it.id == selectedAssetId }
            if (asset == null) {
                route = AppRoute.ROOT
            } else {
                AssetDetailScreen(
                    state = state,
                    asset = asset,
                    isFavorite = asset.symbol.uppercase() in state.settings.favoriteSymbols,
                    onToggleFavorite = {
                        viewModel.setFavorite(
                            asset.symbol,
                            asset.symbol.uppercase() !in state.settings.favoriteSymbols,
                        )
                    },
                    onSend = { route = AppRoute.SEND },
                    onReceive = { route = AppRoute.RECEIVE },
                    onSwap = {
                        initialSwapAssetId = asset.id
                        selectedAssetId = null
                        rootTab = 1
                        route = AppRoute.ROOT
                    },
                    onBack = { route = AppRoute.ROOT },
                )
            }
        }
        AppRoute.P2P -> P2PScreen(
            state = state,
            onBack = { route = AppRoute.ROOT },
            boardConfigured = viewModel.isP2PBoardConfigured,
            onLoadListings = viewModel::loadP2PListings,
            onPrepareAccept = viewModel::prepareP2PAccept,
            onAuthenticate = authenticateForSigning,
            onExecuteAccept = viewModel::executeP2PAccept,
            onCreateOffer = viewModel::createP2POffer,
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
                    onReceive = {
                        selectedAssetId = null
                        route = AppRoute.RECEIVE
                    },
                    onSend = {
                        selectedAssetId = null
                        route = AppRoute.SEND
                    },
                    onAddToken = { route = AppRoute.ADD_TOKEN },
                    onLockWallet = viewModel::lockSession,
                    onSetAutoLock = viewModel::setAutoLockDuration,
                    onDeleteWallet = viewModel::deleteWallet,
                    onNetworkEnabled = { blockchain, enabled ->
                        viewModel.setNetworkEnabled(blockchain, enabled)
                    },
                    onSaveQuintium = { network, rpcUrl, explorerUrl, done ->
                        viewModel.setQuintiumSettings(network, rpcUrl, explorerUrl, done)
                    },
                    onPrepareExchange = viewModel::prepareExchange,
                    onAuthenticateExchange = authenticateForSigning,
                    onExecuteExchange = viewModel::executeExchange,
                    onOpenP2P = { route = AppRoute.P2P },
                    onOpenAllAssets = { route = AppRoute.ALL_ASSETS },
                    onOpenAsset = { asset ->
                        selectedAssetId = asset.id
                        route = AppRoute.ASSET_DETAIL
                    },
                    onToggleFavorite = viewModel::setFavorite,
                    onSetCurrency = viewModel::setCurrency,
                    onSetNotifications = viewModel::setNotificationsEnabled,
                    onSetTimeZone = viewModel::setTimeZone,
                    onSetColorTheme = viewModel::setColorTheme,
                    onSetAssetListStyle = viewModel::setAssetListStyle,
                    initialSwapAssetId = initialSwapAssetId,
                    initialTab = rootTab,
                    onInitialNavigationConsumed = {
                        initialSwapAssetId = null
                        rootTab = 0
                    },
                    onRevealSecret = { done ->
                        authenticateForSigning { authentication ->
                            authentication.fold(
                                onSuccess = { done(viewModel.revealWalletSecret()) },
                                onFailure = { done(Result.failure(it)) },
                            )
                        }
                    },
                    onRevealPrivateKeys = { done ->
                        authenticateForSigning { authentication ->
                            authentication.fold(
                                onSuccess = { done(viewModel.revealPrivateKeys()) },
                                onFailure = { done(Result.failure(it)) },
                            )
                        }
                    },
                )
            }
        }
    }

    if (state.errorMessage != null && state.wallet == null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.app_wallet_operation_failed)) },
            text = { Text(state.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }
}
