package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.model.resolvedZoneId
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Error
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceLight
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Warning
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private data class AssetBalancePoint(
    val timestamp: Instant,
    val balance: Double,
)

@Composable
fun AssetDetailScreen(
    state: WalletState,
    asset: Asset,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSend: () -> Unit,
    onReceive: () -> Unit,
    onSwap: () -> Unit = {},
    onBack: () -> Unit,
) {
    val wallet = state.wallet ?: return
    val matchingAssets = wallet.assets
        .filter { it.symbol.equals(asset.symbol, ignoreCase = true) }
        .sortedByDescending(Asset::valueUsd)
        .ifEmpty { listOf(asset) }
    val matchingTransactions = state.transactions
        .filter { it.symbol.equals(asset.symbol, ignoreCase = true) }
        .sortedByDescending(WalletTransaction::timestamp)
    val totalBalance = matchingAssets.sumOf(Asset::balance)
    val totalValue = matchingAssets.sumOf(Asset::valueUsd)
    val marketChange = state.priceChanges24h[asset.coinGeckoId]
    val hidden = state.settings.hideBalances

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AssetDetailHeader(
                symbol = asset.symbol,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onBack = onBack,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(26.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                AssetOverview(
                    state = state,
                    asset = asset,
                    networkCount = matchingAssets.size,
                    totalBalance = totalBalance,
                    totalValue = totalValue,
                    marketChange = marketChange,
                    hidden = hidden,
                )
                AssetQuickActions(
                    asset = asset,
                    onReceive = onReceive,
                    onSend = onSend,
                    onSwap = onSwap,
                )
                AssetBalanceChart(
                    state = state,
                    symbol = asset.symbol,
                    currentBalance = totalBalance,
                    transactions = matchingTransactions,
                    hidden = hidden,
                )
                if (matchingAssets.size > 1) {
                    AssetNetworkDistribution(
                        state = state,
                        assets = matchingAssets,
                        hidden = hidden,
                    )
                }
                AssetTransactionHistory(
                    transactions = matchingTransactions,
                    hidden = hidden,
                    zoneId = state.settings.resolvedZoneId(),
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun AssetDetailHeader(
    symbol: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        CircularHeaderButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.common_close),
            tint = Color.White,
            onClick = onBack,
        )
        Text(
            symbol,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        CircularHeaderButton(
            icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.assets_remove_favorite else R.string.assets_add_favorite,
            ),
            tint = if (isFavorite) Warning else Indigo,
            onClick = onToggleFavorite,
        )
    }
}

@Composable
private fun CircularHeaderButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(38.dp)
            .background(SurfaceLight, CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
    )
}

@Composable
private fun AssetOverview(
    state: WalletState,
    asset: Asset,
    networkCount: Int,
    totalBalance: Double,
    totalValue: Double,
    marketChange: Double?,
    hidden: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Indigo.copy(alpha = 0.09f),
                shape = RoundedCornerShape(26.dp),
            )
            .border(1.dp, Indigo.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CryptoTokenIcon(asset = asset, size = 58)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(asset.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.asset_detail_network_count, networkCount),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            marketChange?.let { change ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            (if (change >= 0) Success else Error).copy(alpha = 0.11f),
                            RoundedCornerShape(18.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        "${if (change >= 0) "↗ +" else "↘ "}${String.format(Locale.US, "%.2f", change)}%",
                        color = if (change >= 0) Success else Error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " 24h",
                        color = if (change >= 0) Success else Error,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.asset_detail_total_balance),
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 22.dp),
        )
        Text(
            if (hidden) "••••" else state.formatFiat(totalValue),
            fontSize = 36.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (hidden) "•••• ${asset.symbol}" else "${totalBalance.detailAmount()} ${asset.symbol.uppercase()}",
            color = TextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun AssetQuickActions(
    asset: Asset,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onSwap: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssetAction(
            title = stringResource(R.string.common_receive),
            icon = Icons.AutoMirrored.Rounded.CallReceived,
            modifier = Modifier.weight(1f),
            onClick = onReceive,
        )
        AssetAction(
            title = stringResource(R.string.common_send),
            icon = Icons.AutoMirrored.Rounded.CallMade,
            modifier = Modifier.weight(1f),
            onClick = onSend,
        )
        val canExchangeFromAsset = asset.blockchain.isEvm ||
            (asset.blockchain == io.noriskservis.wpayin.wallet.model.Blockchain.BITCOIN && asset.isNative)
        AssetAction(
            title = stringResource(
                if (asset.blockchain.isEvm) R.string.common_swap else R.string.common_bridge,
            ),
            icon = if (asset.blockchain.isEvm) Icons.Rounded.SwapHoriz else Icons.Rounded.Link,
            modifier = Modifier.weight(1f),
            enabled = canExchangeFromAsset,
            onClick = onSwap,
        )
    }
}

@Composable
private fun AssetAction(
    title: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
            .background(Surface, RoundedCornerShape(18.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) Indigo else TextTertiary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            title,
            color = if (enabled) Color.White else TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AssetBalanceChart(
    state: WalletState,
    symbol: String,
    currentBalance: Double,
    transactions: List<WalletTransaction>,
    hidden: Boolean,
) {
    val timeframes = listOf("1D", "1W", "1M", "3M", "1Y", "ALL")
    var selectedTimeframe by remember { mutableIntStateOf(0) }
    val cutoff = when (selectedTimeframe) {
        0 -> Instant.now().minus(Duration.ofDays(1))
        1 -> Instant.now().minus(Duration.ofDays(7))
        2 -> Instant.now().minus(Duration.ofDays(30))
        3 -> Instant.now().minus(Duration.ofDays(90))
        4 -> Instant.now().minus(Duration.ofDays(365))
        else -> Instant.EPOCH
    }
    val points = remember(currentBalance, transactions, selectedTimeframe) {
        reconstructBalancePoints(
            currentBalance = currentBalance,
            transactions = transactions.filter { it.timestamp >= cutoff },
            cutoff = cutoff,
        )
    }
    val first = points.firstOrNull()?.balance ?: currentBalance
    val last = points.lastOrNull()?.balance ?: currentBalance
    val change = last - first

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.asset_detail_balance_history),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (hidden) "••••" else "${if (change >= 0) "+" else ""}${change.detailAmount()} $symbol",
                    color = if (change >= 0) Success else Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                if (hidden) "••••" else state.formatFiat(currentBalance * (
                    state.wallet?.assets?.firstOrNull {
                        it.symbol.equals(symbol, ignoreCase = true)
                    }?.priceUsd ?: 0.0
                )),
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }

        val hasBalanceData = currentBalance > 0.0 ||
            points.any { it.balance != points.firstOrNull()?.balance }
        if (points.size > 1 && hasBalanceData) {
            BalanceLineChart(points = points, positive = change >= 0)
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            ) {
                Text(
                    stringResource(R.string.asset_detail_no_balance_history),
                    color = TextTertiary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            timeframes.forEachIndexed { index, timeframe ->
                Text(
                    timeframe,
                    color = if (selectedTimeframe == index) Color.White else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selectedTimeframe == index) Indigo.copy(alpha = 0.22f) else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { selectedTimeframe = index }
                        .padding(vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun BalanceLineChart(points: List<AssetBalancePoint>, positive: Boolean) {
    val lineColor = if (positive) Success else Error
    Canvas(
        modifier = Modifier.fillMaxWidth().height(170.dp).padding(top = 18.dp, bottom = 12.dp),
    ) {
        val min = points.minOf(AssetBalancePoint::balance)
        val max = points.maxOf(AssetBalancePoint::balance)
        val range = max(max - min, 0.00000001)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) 0f else size.width * index / (points.size - 1f)
            val y = size.height - ((point.balance - min) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(
            color = SurfaceBorder,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun reconstructBalancePoints(
    currentBalance: Double,
    transactions: List<WalletTransaction>,
    cutoff: Instant,
): List<AssetBalancePoint> {
    var balance = currentBalance
    val reversed = mutableListOf(AssetBalancePoint(Instant.now(), balance))
    transactions.sortedByDescending(WalletTransaction::timestamp).forEach { transaction ->
        if (transaction.status == TransactionStatus.FAILED) return@forEach
        when (transaction.type) {
            TransactionType.RECEIVE -> balance -= transaction.amount
            TransactionType.SEND, TransactionType.SWAP, TransactionType.BRIDGE -> balance += transaction.amount
        }
        reversed += AssetBalancePoint(transaction.timestamp.minusMillis(1), balance.coerceAtLeast(0.0))
    }
    reversed += AssetBalancePoint(cutoff, balance.coerceAtLeast(0.0))
    return reversed.distinctBy(AssetBalancePoint::timestamp).sortedBy(AssetBalancePoint::timestamp)
}

@Composable
private fun AssetNetworkDistribution(
    state: WalletState,
    assets: List<Asset>,
    hidden: Boolean,
) {
    val totalValue = assets.sumOf(Asset::valueUsd)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.asset_detail_distribution),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.asset_detail_network_count, assets.size),
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, LargeShape)
                .border(1.dp, SurfaceBorder, LargeShape)
                .padding(horizontal = 16.dp),
        ) {
            assets.forEachIndexed { index, networkAsset ->
                val share = if (totalValue > 0) networkAsset.valueUsd / totalValue else 0.0
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CryptoNetworkIcon(networkAsset.blockchain, 34)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                networkAsset.blockchain.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (hidden) {
                                    "••••"
                                } else {
                                    "${networkAsset.balance.detailAmount()} ${networkAsset.symbol}"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (hidden) "••••" else state.formatFiat(networkAsset.valueUsd),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${(share * 100).toInt()}%",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 46.dp, top = 10.dp)
                            .height(4.dp)
                            .background(SurfaceLight, CircleShape),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(share.toFloat().coerceIn(0.02f, 1f))
                                .height(4.dp)
                                .background(Indigo, CircleShape),
                        )
                    }
                }
                if (index < assets.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceBorder))
                }
            }
        }
    }
}

@Composable
private fun AssetTransactionHistory(
    transactions: List<WalletTransaction>,
    hidden: Boolean,
    zoneId: ZoneId,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.asset_detail_recent_activity),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        if (transactions.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, LargeShape)
                    .border(1.dp, SurfaceBorder, LargeShape)
                    .padding(24.dp),
            ) {
                Text(
                    stringResource(R.string.asset_detail_no_transactions),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, LargeShape)
                    .border(1.dp, SurfaceBorder, LargeShape)
                    .padding(horizontal = 16.dp),
            ) {
                transactions.take(8).forEachIndexed { index, transaction ->
                    AssetTransactionRow(
                        transaction = transaction,
                        hidden = hidden,
                        zoneId = zoneId,
                    )
                    if (index < minOf(7, transactions.lastIndex)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceBorder))
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetTransactionRow(transaction: WalletTransaction, hidden: Boolean, zoneId: ZoneId) {
    val outgoing = transaction.type == TransactionType.SEND || transaction.type == TransactionType.BRIDGE
    val typeTitle = stringResource(
        when (transaction.type) {
            TransactionType.SEND -> R.string.activity_sent
            TransactionType.RECEIVE -> R.string.activity_received
            TransactionType.SWAP -> R.string.activity_swapped
            TransactionType.BRIDGE -> R.string.activity_bridged
        },
    )
    val formatter = remember(zoneId) {
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(zoneId)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(
                    (if (outgoing) Error else Success).copy(alpha = 0.12f),
                    CircleShape,
                ),
        ) {
            Text(if (outgoing) "↗" else "↙", color = if (outgoing) Error else Success, fontSize = 18.sp)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(typeTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${transaction.blockchain.displayName} • ${formatter.format(transaction.timestamp)}",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) {
                    "••••"
                } else {
                    "${if (outgoing) "−" else "+"}${transaction.amount.detailAmount()} ${transaction.symbol}"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    when (transaction.status) {
                        TransactionStatus.PENDING -> R.string.activity_status_pending
                        TransactionStatus.CONFIRMED -> R.string.activity_status_confirmed
                        TransactionStatus.FAILED -> R.string.activity_status_failed
                    },
                ),
                color = when (transaction.status) {
                    TransactionStatus.PENDING -> Warning
                    TransactionStatus.CONFIRMED -> Success
                    TransactionStatus.FAILED -> Error
                },
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

private fun Double.detailAmount(): String = when {
    this == 0.0 -> "0.00"
    this < 0.000001 -> "<0.000001"
    else -> "%,.6f".format(Locale.US, this).trimEnd('0').trimEnd('.')
}
