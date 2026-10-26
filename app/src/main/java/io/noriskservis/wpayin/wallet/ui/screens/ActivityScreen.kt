package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.model.resolvedZoneId
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.EmptyState
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class ActivityFilter(val label: Int) {
    ALL(R.string.activity_filter_all),
    SENT(R.string.activity_filter_sent),
    RECEIVED(R.string.activity_filter_received),
    SWAPPED(R.string.activity_filter_swapped),
}

@Composable
fun ActivityScreen(state: WalletState, onRefresh: () -> Unit) {
    val zoneId = state.settings.resolvedZoneId()
    val locale = LocalConfiguration.current.locales[0]
    val groupDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }
    var selectedTransaction by remember { mutableStateOf<WalletTransaction?>(null) }
    selectedTransaction?.let { transaction ->
        TransactionDetailScreen(
            transaction = transaction,
            state = state,
            onBack = { selectedTransaction = null },
        )
        return
    }
    var query by remember { mutableStateOf(TextFieldValue()) }
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }
    var selectedNetwork by remember { mutableStateOf<Blockchain?>(null) }
    val availableNetworks = state.settings.enabledNetworks
        .filter { state.wallet?.account(it) != null }
        .sortedBy(Blockchain::ordinal)
    val normalizedQuery = query.text.trim().lowercase()
    val transactions = state.transactions.filter { transaction ->
        val networkMatches = selectedNetwork == null || transaction.blockchain == selectedNetwork
        val filterMatches = when (selectedFilter) {
            ActivityFilter.ALL -> true
            ActivityFilter.SENT -> transaction.type == TransactionType.SEND || transaction.type == TransactionType.BRIDGE
            ActivityFilter.RECEIVED -> transaction.type == TransactionType.RECEIVE
            ActivityFilter.SWAPPED -> transaction.type == TransactionType.SWAP
        }
        val queryMatches = normalizedQuery.isEmpty() || listOf(
            transaction.hash,
            transaction.from,
            transaction.to,
            transaction.symbol,
            transaction.blockchain.displayName,
        ).any { it.lowercase().contains(normalizedQuery) }
        networkMatches && filterMatches && queryMatches
    }
    val transactionGroups = transactions.groupBy { transaction ->
        transaction.timestamp.atZone(zoneId).toLocalDate()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11162A), Ink, Ink)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.common_activity), style = MaterialTheme.typography.headlineLarge)
                if (state.isRefreshingHistory) {
                    Text(stringResource(R.string.activity_updating), color = TextSecondary)
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(Surface, CircleShape)
                    .border(1.dp, SurfaceBorder, CircleShape)
                    .clickable(enabled = !state.isRefreshingHistory, onClick = onRefresh),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.activity_refresh), tint = Indigo)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.activity_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            shape = LargeShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Indigo,
                unfocusedBorderColor = SurfaceBorder,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
            ),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            ActivityFilter.entries.forEach { filter ->
                val selected = filter == selectedFilter
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(if (selected) Indigo.copy(alpha = 0.2f) else Surface, CircleShape)
                        .border(1.dp, if (selected) Indigo else SurfaceBorder, CircleShape)
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                ) {
                    Icon(
                        imageVector = when (filter) {
                            ActivityFilter.ALL -> Icons.Rounded.Layers
                            ActivityFilter.SENT -> Icons.AutoMirrored.Rounded.CallMade
                            ActivityFilter.RECEIVED -> Icons.AutoMirrored.Rounded.CallReceived
                            ActivityFilter.SWAPPED -> Icons.Rounded.SwapHoriz
                        },
                        contentDescription = null,
                        tint = if (selected) Color.White else TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(filter.label),
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (availableNetworks.isNotEmpty()) {
            ActivityNetworkTabs(
                selectedNetwork = selectedNetwork,
                networks = availableNetworks,
                onSelected = { selectedNetwork = it },
            )
        }

        if (transactions.isEmpty()) {
            EmptyState(
                title = stringResource(
                    if (state.transactions.isEmpty()) R.string.activity_empty_title else R.string.activity_no_match_title,
                ),
                message = stringResource(
                    if (state.transactions.isEmpty()) {
                        R.string.activity_empty_message
                    } else {
                        R.string.activity_no_match_message
                    },
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    ActivityOverviewCard(state.transactions)
                    Spacer(Modifier.height(12.dp))
                }
                transactionGroups.forEach { (date, group) ->
                    item(key = "date:$date") {
                        Text(
                            date.format(groupDateFormatter),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(
                        items = group,
                        key = { "${it.blockchain.name}:${it.hash}:${it.symbol}:${it.type}" },
                    ) { transaction ->
                        TransactionRow(transaction, zoneId) { selectedTransaction = it }
                    }
                }
            }
        }

        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ActivityNetworkTabs(
    selectedNetwork: Blockchain?,
    networks: List<Blockchain>,
    onSelected: (Blockchain?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        val allSelected = selectedNetwork == null
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .animateContentSize()
                .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
                .background(if (allSelected) Indigo.copy(alpha = 0.22f) else Color.Transparent, CircleShape)
                .border(1.dp, if (allSelected) Indigo.copy(alpha = 0.65f) else Color.Transparent, CircleShape)
                .clickable { onSelected(null) }
                .padding(horizontal = if (allSelected) 13.dp else 8.dp),
        ) {
            Icon(
                Icons.Rounded.GridView,
                contentDescription = null,
                tint = if (allSelected) Color.White else TextSecondary,
                modifier = Modifier.size(17.dp),
            )
            if (allSelected) {
                Text(
                    stringResource(R.string.activity_all_networks),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        networks.forEach { network ->
            val selected = selectedNetwork == network
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .animateContentSize()
                    .defaultMinSize(minWidth = 38.dp, minHeight = 38.dp)
                    .background(if (selected) Indigo.copy(alpha = 0.22f) else Color.Transparent, CircleShape)
                    .border(1.dp, if (selected) Indigo.copy(alpha = 0.65f) else Color.Transparent, CircleShape)
                    .clickable { onSelected(network) }
                    .padding(horizontal = if (selected) 13.dp else 8.dp),
            ) {
                CryptoNetworkIcon(network, 22)
                if (selected) {
                    Text(
                        network.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityOverviewCard(transactions: List<WalletTransaction>) {
    val sent = transactions.count { it.type == TransactionType.SEND || it.type == TransactionType.BRIDGE }
    val received = transactions.count { it.type == TransactionType.RECEIVE }
    val swapped = transactions.count { it.type == TransactionType.SWAP }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Indigo.copy(alpha = 0.88f), Color(0xFF1A2038))),
                LargeShape,
            )
            .border(1.dp, Indigo.copy(alpha = 0.42f), LargeShape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    stringResource(R.string.activity_overview),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                )
                Text(stringResource(R.string.activity_transactions), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(transactions.size.toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(17.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.16f)))
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth()) {
            OverviewMetric("↑", sent, stringResource(R.string.activity_sent), Color(0xFFFF7A70), Modifier.weight(1f))
            OverviewMetric("↓", received, stringResource(R.string.activity_received), Success, Modifier.weight(1f))
            OverviewMetric("⇄", swapped, stringResource(R.string.activity_swapped), Color.White, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewMetric(
    icon: String,
    count: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(icon, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun TransactionRow(
    transaction: WalletTransaction,
    zoneId: ZoneId,
    onClick: (WalletTransaction) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val transactionDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }
    val outgoing = transaction.type == TransactionType.SEND || transaction.type == TransactionType.BRIDGE
    val icon = when (transaction.type) {
        TransactionType.RECEIVE -> Icons.AutoMirrored.Rounded.CallReceived
        TransactionType.SWAP -> Icons.Rounded.SwapHoriz
        else -> Icons.AutoMirrored.Rounded.CallMade
    }
    val title = stringResource(
        when (transaction.type) {
            TransactionType.SEND -> R.string.activity_sent
            TransactionType.RECEIVE -> R.string.activity_received
            TransactionType.SWAP -> R.string.activity_swapped
            TransactionType.BRIDGE -> R.string.activity_bridged
        },
    )
    val amountColor = when {
        transaction.status == TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
        outgoing -> Color.White
        else -> Success
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .clickable { onClick(transaction) }
            .padding(15.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(Indigo.copy(alpha = 0.16f), CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = Indigo)
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    transaction.blockchain.displayName,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                transaction.timestamp.atZone(zoneId).format(transactionDateFormatter),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (outgoing) "−" else "+"}${transaction.amount.displayAmount()} ${transaction.symbol}",
                color = amountColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    when (transaction.status) {
                        TransactionStatus.CONFIRMED -> R.string.activity_status_confirmed
                        TransactionStatus.PENDING -> R.string.activity_status_pending
                        TransactionStatus.FAILED -> R.string.activity_status_failed
                    },
                ),
                color = when (transaction.status) {
                    TransactionStatus.CONFIRMED -> Success
                    TransactionStatus.PENDING -> TextSecondary
                    TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun Double.displayAmount(): String = when {
    this == 0.0 -> "0"
    this < 0.000001 -> "<0.000001"
    else -> "%,.6f".format(Locale.US, this).trimEnd('0').trimEnd('.')
}

/** In-app transaction detail, mirroring the iOS TransactionDetailView layout. */
@Composable
private fun TransactionDetailScreen(
    transaction: WalletTransaction,
    state: WalletState,
    onBack: () -> Unit,
) {
    val zoneId = state.settings.resolvedZoneId()
    val uriHandler = LocalUriHandler.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT).withLocale(locale)
    }
    val outgoing = transaction.type == TransactionType.SEND || transaction.type == TransactionType.BRIDGE
    val typeColor = when (transaction.type) {
        TransactionType.RECEIVE -> Success
        TransactionType.SEND -> Color(0xFFFF7A70)
        TransactionType.SWAP -> Indigo
        TransactionType.BRIDGE -> io.noriskservis.wpayin.wallet.ui.theme.Violet
    }
    val assetPrice = state.wallet?.assets
        ?.firstOrNull { it.blockchain == transaction.blockchain && it.symbol.equals(transaction.symbol, true) }
        ?.priceUsd
    val nativePrice = state.wallet?.assets
        ?.firstOrNull { it.blockchain == transaction.blockchain && it.isNative }
        ?.priceUsd
    var copiedField by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11162A), Ink, Ink)))
            .statusBarsPadding(),
    ) {
        io.noriskservis.wpayin.wallet.ui.components.ScreenHeader(
            title = stringResource(R.string.activity_transaction),
            onBack = onBack,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp,
            ),
            modifier = Modifier.weight(1f),
        ) {
            // Hero card — type, signed amount, fiat value, status pill.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(typeColor.copy(alpha = 0.55f), Color(0xFF1A2038), Surface),
                            ),
                            LargeShape,
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.12f), LargeShape)
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        CryptoNetworkIcon(transaction.blockchain, 56)
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        when (transaction.status) {
                                            TransactionStatus.CONFIRMED -> Success
                                            TransactionStatus.PENDING -> Color(0xFFFBBF24)
                                            TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
                                        },
                                        CircleShape,
                                    ),
                            )
                            Text(
                                stringResource(
                                    when (transaction.status) {
                                        TransactionStatus.CONFIRMED -> R.string.activity_status_confirmed
                                        TransactionStatus.PENDING -> R.string.activity_status_pending
                                        TransactionStatus.FAILED -> R.string.activity_status_failed
                                    },
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 7.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(
                            when (transaction.type) {
                                TransactionType.SEND -> R.string.activity_sent
                                TransactionType.RECEIVE -> R.string.activity_received
                                TransactionType.SWAP -> R.string.activity_swapped
                                TransactionType.BRIDGE -> R.string.activity_bridged
                            },
                        ),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        "${if (outgoing) "−" else "+"}${transaction.amount.displayAmount()} ${transaction.symbol}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        assetPrice?.let { state.formatFiat(transaction.amount * it) } ?: "—",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.activity_details),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Information card — date, network, gas fee, block.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, LargeShape)
                        .border(1.dp, SurfaceBorder, LargeShape),
                ) {
                    TransactionInfoRow(
                        stringResource(R.string.activity_date),
                        transaction.timestamp.atZone(zoneId).format(dateFormatter),
                    )
                    TransactionInfoDivider()
                    TransactionInfoRow(
                        stringResource(R.string.send_review_network),
                        transaction.blockchain.displayName,
                    )
                    transaction.fee?.takeIf { it > 0 }?.let { fee ->
                        TransactionInfoDivider()
                        TransactionInfoRow(
                            stringResource(R.string.activity_gas_fee),
                            "${fee.displayAmount()} ${transaction.blockchain.symbol}" +
                                (nativePrice?.let { "\n" + state.formatFiat(fee * it) } ?: ""),
                        )
                    }
                    transaction.blockNumber?.let { block ->
                        TransactionInfoDivider()
                        TransactionInfoRow(stringResource(R.string.activity_block), block)
                    }
                }
            }

            // Copyable hash / from / to cards.
            listOf(
                Triple(R.string.activity_hash, transaction.hash, "hash"),
                Triple(R.string.activity_from, transaction.from, "from"),
                Triple(R.string.activity_to, transaction.to, "to"),
            ).forEach { (titleRes, value, key) ->
                if (value.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface, LargeShape)
                                .border(1.dp, SurfaceBorder, LargeShape)
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(titleRes),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    if (copiedField == key) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.backup_copy),
                                    tint = if (copiedField == key) Success else Indigo,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            clipboard.setText(
                                                androidx.compose.ui.text.AnnotatedString(value),
                                            )
                                            copiedField = key
                                        },
                                )
                            }
                            Text(
                                value,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.common_view_on_explorer),
                    color = Indigo,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Indigo.copy(alpha = 0.10f), LargeShape)
                        .border(1.dp, Indigo.copy(alpha = 0.35f), LargeShape)
                        .clickable { uriHandler.openUri(transaction.explorerUrl) }
                        .padding(vertical = 15.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TransactionInfoRow(title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun TransactionInfoDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(SurfaceBorder),
    )
}
