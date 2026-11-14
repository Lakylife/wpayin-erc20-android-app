package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.AutoLockDuration
import io.noriskservis.wpayin.wallet.model.AppColorTheme
import io.noriskservis.wpayin.wallet.model.AssetListStyle
import io.noriskservis.wpayin.wallet.model.ExportedPrivateKey
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.QuintiumNetwork
import io.noriskservis.wpayin.wallet.model.ExchangeQuote
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.WpayinLogo
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceLight
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Warning
import java.util.Locale

private enum class MainTab(val title: Int, val icon: ImageVector) {
    HOME(R.string.common_home, Icons.Rounded.Home),
    SWAP(R.string.common_swap, Icons.Rounded.SwapHoriz),
    ACTIVITY(R.string.common_activity, Icons.AutoMirrored.Rounded.ReceiptLong),
    SETTINGS(R.string.common_settings, Icons.Rounded.Settings),
}

@Composable
fun MainTabsScreen(
    state: WalletState,
    onRefresh: () -> Unit,
    onToggleBalances: (Boolean) -> Unit,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onAddToken: () -> Unit,
    onLockWallet: () -> Unit,
    onSetAutoLock: (AutoLockDuration) -> Unit = {},
    onDeleteWallet: () -> Unit,
    onNetworkEnabled: (Blockchain, Boolean) -> Unit = { _, _ -> },
    onSaveQuintium: (QuintiumNetwork, String, String, (Result<Unit>) -> Unit) -> Unit = { _, _, _, done ->
        done(Result.failure(IllegalStateException("Quinvium settings are unavailable")))
    },
    onPrepareExchange: (String, String, String, (Result<ExchangeQuote>) -> Unit) -> Unit = { _, _, _, done ->
        done(Result.failure(IllegalStateException("Quotes are unavailable")))
    },
    onAuthenticateExchange: ((Result<Unit>) -> Unit) -> Unit = { done ->
        done(Result.failure(IllegalStateException("Authentication is unavailable")))
    },
    onExecuteExchange: (ExchangeQuote, (Result<WalletTransaction>) -> Unit) -> Unit = { _, done ->
        done(Result.failure(IllegalStateException("Exchange is unavailable")))
    },
    onOpenP2P: () -> Unit = {},
    onOpenAllAssets: () -> Unit = {},
    onOpenAsset: (Asset) -> Unit = {},
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onSetCurrency: (String) -> Unit = {},
    onSetNotifications: (Boolean) -> Unit = {},
    onSetTimeZone: (String) -> Unit = {},
    onSetColorTheme: (AppColorTheme) -> Unit = {},
    onSetAssetListStyle: (AssetListStyle) -> Unit = {},
    onRevealSecret: ((Result<io.noriskservis.wpayin.wallet.data.SecureWalletStore.WalletSecret>) -> Unit) -> Unit = { done ->
        done(Result.failure(IllegalStateException("Backup is unavailable")))
    },
    onRevealPrivateKeys: ((Result<List<ExportedPrivateKey>>) -> Unit) -> Unit = { done ->
        done(Result.failure(IllegalStateException("Private keys are unavailable")))
    },
    initialSwapAssetId: String? = null,
    initialTab: Int = 0,
    onInitialNavigationConsumed: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(MainTab.entries.indices)) }
    var settingsDetailOpen by remember { mutableStateOf(false) }
    var requestedSwapAssetId by remember { mutableStateOf(initialSwapAssetId) }

    LaunchedEffect(initialTab, initialSwapAssetId) {
        when {
            initialSwapAssetId != null -> {
                requestedSwapAssetId = initialSwapAssetId
                selectedTab = MainTab.SWAP.ordinal
                settingsDetailOpen = false
                onInitialNavigationConsumed()
            }
            initialTab != MainTab.HOME.ordinal -> {
                selectedTab = initialTab.coerceIn(MainTab.entries.indices)
                settingsDetailOpen = false
                onInitialNavigationConsumed()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (MainTab.entries[selectedTab]) {
                MainTab.HOME -> HomeScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onToggleBalances = onToggleBalances,
                    onReceive = onReceive,
                    onSend = onSend,
                    onAddToken = onAddToken,
                    onOpenAllAssets = onOpenAllAssets,
                    onOpenAsset = onOpenAsset,
                    onToggleFavorite = onToggleFavorite,
                    onOpenP2P = onOpenP2P,
                    onOpenSwap = { selectedTab = MainTab.SWAP.ordinal },
                    onOpenSettings = { selectedTab = MainTab.SETTINGS.ordinal },
                )
                MainTab.SWAP -> SwapScreen(
                    state = state,
                    initialPayAssetId = requestedSwapAssetId,
                    onPrepare = onPrepareExchange,
                    onAuthenticate = onAuthenticateExchange,
                    onExecute = onExecuteExchange,
                )
                MainTab.ACTIVITY -> ActivityScreen(state, onRefresh)
                MainTab.SETTINGS -> SettingsScreen(
                    state = state,
                    onLockWallet = onLockWallet,
                    onSetAutoLock = onSetAutoLock,
                    onDeleteWallet = onDeleteWallet,
                    onNetworkEnabled = onNetworkEnabled,
                    onSaveQuintium = onSaveQuintium,
                    onSetCurrency = onSetCurrency,
                    onSetNotifications = onSetNotifications,
                    onSetTimeZone = onSetTimeZone,
                    onSetColorTheme = onSetColorTheme,
                    onSetAssetListStyle = onSetAssetListStyle,
                    onRevealSecret = onRevealSecret,
                    onRevealPrivateKeys = onRevealPrivateKeys,
                    onNestedScreenChanged = { settingsDetailOpen = it },
                )
            }
        }
        if (MainTab.entries[selectedTab] != MainTab.SETTINGS || !settingsDetailOpen) {
            BottomNavigation(
                selected = selectedTab,
                onSelected = {
                    settingsDetailOpen = false
                    selectedTab = it
                },
            )
        }
    }
}

private enum class HomeSection(val title: Int) {
    TOKENS(R.string.home_tokens),
    DEFI(R.string.home_defi),
    NFTS(R.string.home_nfts),
}

@Composable
private fun HomeScreen(
    state: WalletState,
    onRefresh: () -> Unit,
    onToggleBalances: (Boolean) -> Unit,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onAddToken: () -> Unit,
    onOpenAllAssets: () -> Unit,
    onOpenAsset: (Asset) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onOpenP2P: () -> Unit,
    onOpenSwap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val wallet = requireNotNull(state.wallet)
    val primaryAccount = wallet.accounts.firstOrNull {
        it.blockchain == Blockchain.ETHEREUM && it.blockchain in state.settings.enabledNetworks
    } ?: wallet.accounts.firstOrNull { it.blockchain in state.settings.enabledNetworks }
        ?: wallet.accounts.first()
    val hidden = state.settings.hideBalances
    var section by remember { mutableStateOf(HomeSection.TOKENS) }
    var showWalletOptions by remember { mutableStateOf(false) }
    var showWalletSelector by remember { mutableStateOf(false) }
    val balance = if (hidden) "••••••" else state.formatFiat(wallet.totalValueUsd)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11162A), Ink, Ink))),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 28.dp),
        ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                CircleIconButton(
                    Icons.Rounded.Menu,
                    stringResource(R.string.home_menu),
                    onClick = { showWalletOptions = true },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(SurfaceLight, RoundedCornerShape(30.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(30.dp))
                        .clickable { showWalletSelector = true }
                        .padding(horizontal = 14.dp),
                ) {
                    WpayinLogo(size = 30)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    ) {
                        Text(
                            wallet.name,
                            fontSize = 15.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            primaryAccount.address.shortAddress(),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                        )
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                CircleIconButton(Icons.Rounded.QrCodeScanner, stringResource(R.string.common_receive), onClick = onReceive)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                        listOf(SurfaceLight, Indigo.copy(alpha = 0.18f), Surface),
                        ),
                        RoundedCornerShape(26.dp),
                    )
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(26.dp))
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("◔", color = Indigo, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.home_total_balance),
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    CircleIconButton(
                        if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        stringResource(if (hidden) R.string.home_show_balance else R.string.home_hide_balance),
                        size = 32,
                        iconSize = 18,
                        isBalanceToggle = true,
                    ) { onToggleBalances(!hidden) }
                }
                Text(
                    balance,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    stringResource(
                        if (state.isRefreshing) R.string.home_updating_portfolio else R.string.home_portfolio_up_to_date,
                    ),
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp).clickable(enabled = !state.isRefreshing, onClick = onRefresh),
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                WalletAction(stringResource(R.string.common_send), Icons.Rounded.ArrowUpward, Modifier.weight(1f), onSend)
                WalletAction(stringResource(R.string.common_receive), Icons.Rounded.ArrowDownward, Modifier.weight(1f), onReceive)
                WalletAction(stringResource(R.string.common_p2p), Icons.Rounded.PeopleAlt, Modifier.weight(1f), onOpenP2P)
                WalletAction(stringResource(R.string.common_swap), Icons.Rounded.SwapHoriz, Modifier.weight(1f), onOpenSwap)
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(25.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(25.dp))
                    .padding(4.dp),
            ) {
                HomeSection.entries.forEach { candidate ->
                    val isSelected = section == candidate
                    Text(
                        stringResource(candidate.title),
                        color = if (isSelected) Indigo else TextTertiary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(19.dp))
                            .clickable { section = candidate }
                            .background(
                                if (isSelected) Indigo.copy(alpha = 0.16f) else Color.Transparent,
                                RoundedCornerShape(19.dp),
                            )
                            .padding(vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        when (section) {
            HomeSection.TOKENS -> {
                val groups = wallet.assets
                    .groupBy { it.symbol.uppercase() }
                    .map { (symbol, assets) -> AssetGroup(symbol, assets.sortedBy { it.blockchain.ordinal }) }
                    .sortedByDescending(AssetGroup::totalValueUsd)
                val favoriteGroups = groups.filter { it.symbol in state.settings.favoriteSymbols }
                val regularGroups = groups.filterNot { it.symbol in state.settings.favoriteSymbols }

                if (favoriteGroups.isNotEmpty()) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Warning,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                stringResource(R.string.assets_favorites),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    items(favoriteGroups, key = { "favorite:${it.symbol}" }) { group ->
                        ExpandableAssetCard(
                            group = group,
                            hidden = hidden,
                            compact = state.settings.assetListStyle == AssetListStyle.COMPACT,
                            fiat = state::formatFiat,
                            isFavorite = true,
                            onToggleFavorite = { onToggleFavorite(group.symbol, false) },
                            onOpenAsset = onOpenAsset,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.home_your_assets),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.home_view_all),
                            color = Indigo,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onOpenAllAssets)
                                .background(Indigo.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (wallet.assets.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.home_pull_to_refresh),
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(regularGroups, key = { "asset:${it.symbol}" }) { group ->
                        ExpandableAssetCard(
                            group = group,
                            hidden = hidden,
                            compact = state.settings.assetListStyle == AssetListStyle.COMPACT,
                            fiat = state::formatFiat,
                            isFavorite = false,
                            onToggleFavorite = { onToggleFavorite(group.symbol, true) },
                            onOpenAsset = onOpenAsset,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            HomeSection.DEFI -> item {
                HomeEmptySection(
                    title = stringResource(R.string.home_no_defi_title),
                    message = stringResource(R.string.home_no_defi_message),
                )
            }
            HomeSection.NFTS -> item {
                HomeEmptySection(
                    title = stringResource(R.string.home_no_nfts_title),
                    message = stringResource(R.string.home_no_nfts_message),
                )
            }
        }

        if (state.errorMessage != null) {
            item {
                Text(
                    state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        }
    }

    if (showWalletOptions) {
        WalletOptionsDialog(
            walletName = wallet.name,
            address = primaryAccount.address,
            onDismiss = { showWalletOptions = false },
            onAddToken = {
                showWalletOptions = false
                onAddToken()
            },
            onManageWallets = {
                showWalletOptions = false
                showWalletSelector = true
            },
        )
    }

    if (showWalletSelector) {
        WalletSelectorDialog(
            walletName = wallet.name,
            address = primaryAccount.address,
            totalValue = if (hidden) "••••" else state.formatFiat(wallet.totalValueUsd),
            onDismiss = { showWalletSelector = false },
            onOpenSettings = {
                showWalletSelector = false
                onOpenSettings()
            },
        )
    }
}

private data class AssetGroup(
    val symbol: String,
    val assets: List<Asset>,
) {
    val totalBalance: Double get() = assets.sumOf(Asset::balance)
    val totalValueUsd: Double get() = assets.sumOf(Asset::valueUsd)
}

@Composable
private fun HomeEmptySection(title: String, message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .padding(horizontal = 24.dp, vertical = 36.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ExpandableAssetCard(
    group: AssetGroup,
    hidden: Boolean,
    compact: Boolean,
    fiat: (Double) -> String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenAsset: (Asset) -> Unit,
) {
    var expanded by remember(group.symbol) { mutableStateOf(false) }
    val representative = group.assets.first()
    val hasNetworks = group.assets.size > 1

    Column(modifier = Modifier.fillMaxWidth()) {
        // Main token row — network mini-icons + chevron sit inline next to the
        // price on the second line, exactly like the iOS ExpandableTokenCard.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(if (compact) 14.dp else 20.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(if (compact) 14.dp else 20.dp))
                .clickable { onOpenAsset(representative) }
                .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
        ) {
            CryptoTokenIcon(asset = representative, size = if (compact) 36 else 44)
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.symbol, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Icon(
                        if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.assets_remove_favorite else R.string.assets_add_favorite,
                        ),
                        tint = if (isFavorite) Warning else TextSecondary,
                        modifier = Modifier
                            .padding(start = 7.dp)
                            .size(19.dp)
                            .clickable(onClick = onToggleFavorite),
                    )
                    Text(
                        representative.name,
                        color = TextTertiary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        if (hidden) "••••" else fiat(representative.priceUsd),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                    if (hasNetworks) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-4).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            group.assets.take(3).forEach { CryptoNetworkIcon(it.blockchain, 16) }
                            if (group.assets.size > 3) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(TextTertiary, CircleShape),
                                ) {
                                    Text(
                                        "+${group.assets.size - 3}",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Icon(
                                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    if (expanded) R.string.home_hide_networks else R.string.home_show_networks,
                                ),
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { expanded = !expanded }
                                    .padding(4.dp),
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (hidden) "•••• ${group.symbol}" else "${group.totalBalance.displayAmount()} ${group.symbol}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (hidden) "••••" else fiat(group.totalValueUsd),
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Expanded network rows live in their own card below the main one (iOS parity).
        if (expanded && hasNetworks) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LargeShape)
                    .background(Surface, LargeShape)
                    .border(1.dp, SurfaceBorder, LargeShape),
            ) {
                group.assets
                    .sortedBy { networkPriority(it.blockchain) }
                    .forEachIndexed { index, asset ->
                        AssetNetworkRow(asset, hidden, fiat) { onOpenAsset(asset) }
                        if (index != group.assets.lastIndex) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceBorder.copy(alpha = 0.5f)))
                        }
                    }
            }
        }
    }
}

private fun networkPriority(blockchain: Blockchain): Int = when (blockchain) {
    Blockchain.ETHEREUM -> 0
    Blockchain.ARBITRUM -> 1
    Blockchain.BASE -> 2
    Blockchain.OPTIMISM -> 3
    Blockchain.POLYGON -> 4
    Blockchain.BNB_CHAIN -> 5
    Blockchain.AVALANCHE -> 6
    else -> 999
}

@Composable
private fun AssetNetworkRow(
    asset: Asset,
    hidden: Boolean,
    fiat: (Double) -> String,
    onClick: () -> Unit,
) {
    val isMainNetwork = asset.blockchain == Blockchain.ETHEREUM
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isMainNetwork) SurfaceLight else Color.White.copy(alpha = 0.035f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (!isMainNetwork) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(width = 2.dp, height = 20.dp)
                    .background(TextTertiary.copy(alpha = 0.3f)),
            )
        }
        CryptoNetworkIcon(asset.blockchain, if (isMainNetwork) 32 else 28)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        ) {
            Text(
                asset.blockchain.displayName,
                fontSize = if (isMainNetwork) 15.sp else 14.sp,
                fontWeight = if (isMainNetwork) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (isMainNetwork) {
                Text(
                    stringResource(R.string.home_main_badge),
                    color = Indigo,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Indigo.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) "••••" else asset.balance.displayAmount(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (hidden) "••••" else fiat(asset.valueUsd),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun WalletOptionsDialog(
    walletName: String,
    address: String,
    onDismiss: () -> Unit,
    onAddToken: () -> Unit,
    onManageWallets: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_wallet_options)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceLight, LargeShape)
                        .border(1.dp, SurfaceBorder, LargeShape)
                        .padding(15.dp),
                ) {
                    Text(walletName, fontWeight = FontWeight.SemiBold)
                    Text(
                        address.shortAddress(),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                WalletDialogAction(
                    icon = Icons.Rounded.AddCircle,
                    title = stringResource(R.string.assets_add_token),
                    onClick = onAddToken,
                )
                WalletDialogAction(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = stringResource(R.string.home_manage_wallets),
                    onClick = onManageWallets,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun WalletSelectorDialog(
    walletName: String,
    address: String,
    totalValue: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_select_wallet)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceLight, LargeShape)
                        .border(1.dp, Indigo.copy(alpha = 0.38f), LargeShape)
                        .clickable(onClick = onDismiss)
                        .padding(16.dp),
                ) {
                    WpayinLogo(size = 42)
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(walletName, fontWeight = FontWeight.Bold)
                        Text(address.shortAddress(), color = TextSecondary, fontSize = 12.sp)
                        Text(totalValue, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Indigo)
                }
                Text(
                    stringResource(R.string.home_active_wallet),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                WalletDialogAction(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(R.string.common_settings),
                    onClick = onOpenSettings,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}

@Composable
private fun WalletDialogAction(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(22.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun WalletAction(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .background(Surface, CircleShape)
                .border(1.dp, SurfaceBorder, CircleShape),
        ) {
            Icon(icon, contentDescription = title, tint = Indigo, modifier = Modifier.size(22.dp))
        }
        Text(title, modifier = Modifier.padding(top = 9.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    label: String,
    size: Int = 44,
    iconSize: Int = 22,
    isBalanceToggle: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .background(SurfaceLight, CircleShape)
            .border(1.dp, SurfaceBorder, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isBalanceToggle) TextSecondary else Color.White,
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

@Composable
private fun BottomNavigation(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .background(Color(0xF20C0E17), RoundedCornerShape(32.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(32.dp))
            .padding(6.dp),
    ) {
        MainTab.entries.forEachIndexed { index, tab ->
            val title = stringResource(tab.title)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected == index) Indigo.copy(alpha = 0.18f) else Color.Transparent,
                        RoundedCornerShape(25.dp),
                    )
                    .clickable { onSelected(index) }
                    .padding(vertical = 7.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = title,
                    tint = if (selected == index) Indigo else TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    title,
                    color = if (selected == index) Color.White else TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun String.shortAddress(): String =
    if (length <= 13) this else "${take(6)}...${takeLast(4)}"

private fun Double.displayAmount(): String = when {
    this == 0.0 -> "0.00"
    this < 0.0001 -> "<0.0001"
    else -> "%,.4f".format(Locale.US, this)
}
