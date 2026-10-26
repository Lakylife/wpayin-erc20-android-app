package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Warning
import java.util.Locale
import kotlinx.coroutines.launch

private sealed interface AssetListFilter {
    val key: String

    data object All : AssetListFilter {
        override val key = "all"
    }

    data object Favorites : AssetListFilter {
        override val key = "favorites"
    }

    data object Native : AssetListFilter {
        override val key = "native"
    }

    data class Network(val blockchain: Blockchain) : AssetListFilter {
        override val key = blockchain.name
    }
}

@Composable
fun AllAssetsScreen(
    state: WalletState,
    onAddToken: () -> Unit,
    onOpenAsset: (Asset) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val wallet = state.wallet ?: return
    val hidden = state.settings.hideBalances
    val allAssets = wallet.assets.sortedWith(
        compareByDescending<Asset> { it.isNative }
            .thenBy { it.symbol.uppercase() }
            .thenBy { it.blockchain.displayName },
    )
    val tokenNetworks = allAssets
        .filterNot(Asset::isNative)
        .map(Asset::blockchain)
        .distinct()
        .sortedBy(Blockchain::displayName)
    val filters = buildList {
        add(AssetListFilter.All)
        add(AssetListFilter.Favorites)
        if (allAssets.any(Asset::isNative)) add(AssetListFilter.Native)
        addAll(tokenNetworks.map(AssetListFilter::Network))
    }
    var selectedFilterKey by remember { mutableStateOf(AssetListFilter.All.key) }
    val filterListState = rememberLazyListState()
    val filterScrollScope = rememberCoroutineScope()
    val selectedFilter = filters.firstOrNull { it.key == selectedFilterKey } ?: AssetListFilter.All
    val assets = when (selectedFilter) {
        AssetListFilter.All -> allAssets
        AssetListFilter.Favorites -> allAssets.filter {
            it.symbol.uppercase() in state.settings.favoriteSymbols
        }
        AssetListFilter.Native -> allAssets.filter(Asset::isNative)
        is AssetListFilter.Network -> allAssets.filter {
            !it.isNative && it.blockchain == selectedFilter.blockchain
        }
    }

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AllAssetsHeader(onBack = onBack, onAddToken = onAddToken)

            LazyRow(
                state = filterListState,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                itemsIndexed(filters, key = { _, filter -> filter.key }) { index, filter ->
                    AssetFilterChip(
                        filter = filter,
                        selected = filter.key == selectedFilter.key,
                        onClick = {
                            selectedFilterKey = filter.key
                            filterScrollScope.launch {
                                filterListState.animateScrollToItem(index)
                            }
                        },
                    )
                }
            }

            if (assets.isEmpty()) {
                AssetEmptyState(favoritesOnly = selectedFilter == AssetListFilter.Favorites)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 22.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(assets, key = Asset::id) { asset ->
                        AllAssetsRow(
                            state = state,
                            asset = asset,
                            hidden = hidden,
                            isFavorite = asset.symbol.uppercase() in state.settings.favoriteSymbols,
                            onSelect = { onOpenAsset(asset) },
                            onFavorite = {
                                val symbol = asset.symbol.uppercase()
                                onToggleFavorite(symbol, symbol !in state.settings.favoriteSymbols)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllAssetsHeader(onBack: () -> Unit, onAddToken: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            stringResource(R.string.common_cancel),
            color = Indigo,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthForHeader().clickable(onClick = onBack).padding(vertical = 8.dp),
        )
        Text(
            stringResource(R.string.assets_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .widthForHeader()
                .height(40.dp)
                .clickable(onClick = onAddToken),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.assets_add_token),
                tint = Indigo,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(34.dp)
                    .background(Indigo.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, Indigo.copy(alpha = 0.28f), CircleShape)
                    .padding(8.dp),
            )
        }
    }
}

private fun Modifier.widthForHeader(): Modifier = size(width = 64.dp, height = 40.dp)

@Composable
private fun AssetFilterChip(
    filter: AssetListFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = when (filter) {
        AssetListFilter.All -> stringResource(R.string.assets_all)
        AssetListFilter.Favorites -> stringResource(R.string.assets_favorites)
        AssetListFilter.Native -> stringResource(R.string.assets_coins)
        is AssetListFilter.Network -> filter.blockchain.displayName
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .animateContentSize()
            .clip(CircleShape)
            .background(if (selected) Indigo.copy(alpha = 0.18f) else Surface)
            .border(
                1.dp,
                if (selected) Indigo.copy(alpha = 0.7f) else SurfaceBorder,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 11.dp else 9.dp, vertical = 8.dp),
    ) {
        when (filter) {
            AssetListFilter.All -> Icon(
                Icons.Rounded.GridView,
                contentDescription = title,
                modifier = Modifier.size(18.dp),
            )
            AssetListFilter.Favorites -> Icon(
                Icons.Rounded.Star,
                contentDescription = title,
                tint = if (selected) Warning else TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            AssetListFilter.Native -> Icon(
                Icons.Rounded.Paid,
                contentDescription = title,
                modifier = Modifier.size(18.dp),
            )
            is AssetListFilter.Network -> CryptoNetworkIcon(filter.blockchain, 18)
        }
        if (selected) {
            Text(
                title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AssetEmptyState(favoritesOnly: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(360.dp),
    ) {
        Text("▱", color = TextTertiary, fontSize = 48.sp)
        Text(
            stringResource(
                if (favoritesOnly) R.string.assets_no_favorites_title else R.string.assets_no_assets_title,
            ),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(
                if (favoritesOnly) R.string.assets_favorites_empty else R.string.assets_empty,
            ),
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AllAssetsRow(
    state: WalletState,
    asset: Asset,
    hidden: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(11.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clickable(onClick = onSelect),
        ) {
            CryptoTokenIcon(asset = asset, size = 34, showNetworkBadge = !asset.isNative)
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(asset.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        asset.name,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!asset.isNative) {
                        Text(
                            asset.blockchain.displayName,
                            color = Indigo,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .background(Indigo.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        if (hidden) "••••" else state.formatFiat(asset.priceUsd),
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                    state.priceChanges24h[asset.coinGeckoId]?.let { change ->
                        Text(
                            "${if (change >= 0) "+" else ""}${String.format(Locale.US, "%.2f", change)}%",
                            color = if (change >= 0) Color(0xFF3DDC97) else Color(0xFFFF6376),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    if (hidden) "•••• ${asset.symbol}" else "${asset.balance.displayAmount()} ${asset.symbol}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hidden) "••••" else state.formatFiat(asset.valueUsd),
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Icon(
            if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.assets_remove_favorite else R.string.assets_add_favorite,
            ),
            tint = if (isFavorite) Warning else TextSecondary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(30.dp)
                .clickable(onClick = onFavorite)
                .padding(6.dp),
        )
    }
}

private fun Double.displayAmount(): String = when {
    this == 0.0 -> "0.00"
    this < 0.0001 -> "<0.0001"
    else -> "%,.4f".format(Locale.US, this).trimEnd('0').trimEnd('.')
}
