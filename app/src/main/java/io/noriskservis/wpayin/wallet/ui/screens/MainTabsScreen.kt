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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.WpayinLogo
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceLight
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SWAP("Swap", Icons.Rounded.SwapHoriz),
    ACTIVITY("Activity", Icons.AutoMirrored.Rounded.ReceiptLong),
    SETTINGS("Settings", Icons.Rounded.Settings),
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
    onDeleteWallet: () -> Unit,
    initialTab: Int = 0,
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(MainTab.entries.indices)) }

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
                    onOpenSwap = { selectedTab = MainTab.SWAP.ordinal },
                    onOpenSettings = { selectedTab = MainTab.SETTINGS.ordinal },
                )
                MainTab.SWAP -> SwapScreen(state)
                MainTab.ACTIVITY -> ActivityScreen(state, onRefresh)
                MainTab.SETTINGS -> SettingsScreen(
                    state = state,
                    onToggleBalances = onToggleBalances,
                    onLockWallet = onLockWallet,
                    onDeleteWallet = onDeleteWallet,
                )
            }
        }
        BottomNavigation(
            selected = selectedTab,
            onSelected = { selectedTab = it },
        )
    }
}

@Composable
private fun HomeScreen(
    state: WalletState,
    onRefresh: () -> Unit,
    onToggleBalances: (Boolean) -> Unit,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onAddToken: () -> Unit,
    onOpenSwap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val wallet = requireNotNull(state.wallet)
    val primaryAccount = wallet.account(Blockchain.ETHEREUM) ?: wallet.accounts.first()
    val hidden = state.settings.hideBalances
    val balance = if (hidden) "••••••" else formatCurrency(wallet.totalValueUsd, state.settings.currencyCode)

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
                CircleIconButton(Icons.Rounded.Menu, "Menu", onClick = onOpenSettings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(SurfaceLight, RoundedCornerShape(30.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(30.dp))
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
                CircleIconButton(Icons.Rounded.QrCodeScanner, "Receive", onClick = onReceive)
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
                        "Total Balance",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    CircleIconButton(
                        if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (hidden) "Show balance" else "Hide balance",
                        size = 32,
                        iconSize = 18,
                    ) { onToggleBalances(!hidden) }
                }
                Text(
                    balance,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    if (state.isRefreshing) "Updating portfolio…" else "Portfolio is up to date",
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
                WalletAction("Send", Icons.Rounded.ArrowUpward, Modifier.weight(1f), onSend)
                WalletAction("Receive", Icons.Rounded.ArrowDownward, Modifier.weight(1f), onReceive)
                WalletAction("P2P", Icons.Rounded.PeopleAlt, Modifier.weight(1f)) { }
                WalletAction("Swap", Icons.Rounded.SwapHoriz, Modifier.weight(1f), onOpenSwap)
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
                listOf("Tokens", "DeFi", "NFTs").forEachIndexed { index, title ->
                    Text(
                        title,
                        color = if (index == 0) Indigo else TextTertiary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (index == 0) Indigo.copy(alpha = 0.16f) else Color.Transparent,
                                RoundedCornerShape(19.dp),
                            )
                            .padding(vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your Assets", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "View All  ›",
                    color = Indigo,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onAddToken)
                        .background(Indigo.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (wallet.assets.isEmpty()) {
            item {
                Text(
                    "Pull to refresh after the wallet has been created to load network balances.",
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(wallet.assets, key = Asset::id) { asset ->
                AssetRow(asset, hidden)
                Spacer(Modifier.height(10.dp))
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
}

@Composable
private fun AssetRow(asset: Asset, hidden: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        CryptoTokenIcon(asset = asset, size = 44, showNetworkBadge = true)
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(asset.symbol, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    asset.name,
                    color = TextTertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
            Text(
                if (hidden) "••••" else "$${"%,.2f".format(Locale.US, asset.priceUsd)}",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) "•••• ${asset.symbol}" else "${asset.balance.displayAmount()} ${asset.symbol}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (hidden) "••••" else "$${"%,.2f".format(Locale.US, asset.valueUsd)}",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
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
            tint = if (label.contains("balance")) TextSecondary else Color.White,
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
                    contentDescription = tab.title,
                    tint = if (selected == index) Indigo else TextTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    tab.title,
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

private fun formatCurrency(value: Double, currencyCode: String): String = runCatching {
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(currencyCode) }.format(value)
}.getOrElse { "$${"%,.2f".format(Locale.US, value)}" }
