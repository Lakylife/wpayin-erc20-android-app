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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.TransactionStatus
import io.noriskservis.wpayin.wallet.model.TransactionType
import io.noriskservis.wpayin.wallet.model.WalletTransaction
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
import java.util.Locale

private enum class ActivityFilter(val label: String) {
    ALL("All"), SENT("Sent"), RECEIVED("Received"), SWAPPED("Swapped")
}

@Composable
fun ActivityScreen(state: WalletState, onRefresh: () -> Unit) {
    var query by remember { mutableStateOf(TextFieldValue()) }
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }
    var selectedNetwork by remember { mutableStateOf<Blockchain?>(null) }
    val availableNetworks = state.transactions
        .map(WalletTransaction::blockchain)
        .distinct()
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
        transaction.timestamp.atZone(ZoneId.systemDefault()).toLocalDate()
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
                Text("Activity", style = MaterialTheme.typography.headlineLarge)
                if (state.isRefreshingHistory) {
                    Text("Updating on-chain history…", color = TextSecondary)
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
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Indigo)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search transactions…") },
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
                        filter.label,
                        color = if (selected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (availableNetworks.size > 1) {
            ActivityNetworkTabs(
                selectedNetwork = selectedNetwork,
                networks = availableNetworks,
                onSelected = { selectedNetwork = it },
            )
        }

        if (transactions.isEmpty()) {
            EmptyState(
                title = if (state.transactions.isEmpty()) "No on-chain history yet" else "No matching transactions",
                message = if (state.transactions.isEmpty()) {
                    "Bitcoin, Solana, and supported EVM activity will appear after explorers index it."
                } else {
                    "Try another search or transaction filter."
                },
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
                        TransactionRow(transaction)
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
                    "All Networks",
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
                Text("Overview", color = Color.White.copy(alpha = 0.72f), fontSize = 14.sp)
                Text("Transactions", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(transactions.size.toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(17.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.16f)))
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth()) {
            OverviewMetric("↑", sent, "Sent", Color(0xFFFF7A70), Modifier.weight(1f))
            OverviewMetric("↓", received, "Received", Success, Modifier.weight(1f))
            OverviewMetric("⇄", swapped, "Swapped", Color.White, Modifier.weight(1f))
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
private fun TransactionRow(transaction: WalletTransaction) {
    val uriHandler = LocalUriHandler.current
    val outgoing = transaction.type == TransactionType.SEND || transaction.type == TransactionType.BRIDGE
    val icon = when (transaction.type) {
        TransactionType.RECEIVE -> Icons.AutoMirrored.Rounded.CallReceived
        TransactionType.SWAP -> Icons.Rounded.SwapHoriz
        else -> Icons.AutoMirrored.Rounded.CallMade
    }
    val title = when (transaction.type) {
        TransactionType.SEND -> "Sent"
        TransactionType.RECEIVE -> "Received"
        TransactionType.SWAP -> "Swapped"
        TransactionType.BRIDGE -> "Bridged"
    }
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
            .clickable { uriHandler.openUri(transaction.explorerUrl) }
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
                transaction.timestamp.atZone(ZoneId.systemDefault()).format(transactionDateFormatter),
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
                transaction.status.name.lowercase().replaceFirstChar(Char::uppercase),
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

private val transactionDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
private val groupDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

private fun Double.displayAmount(): String = when {
    this == 0.0 -> "0"
    this < 0.000001 -> "<0.000001"
    else -> "%,.6f".format(Locale.US, this).trimEnd('0').trimEnd('.')
}
