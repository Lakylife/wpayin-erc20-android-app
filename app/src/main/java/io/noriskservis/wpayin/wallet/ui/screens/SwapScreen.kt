package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.ExchangeQuote
import io.noriskservis.wpayin.wallet.model.isTrustedWalletAsset
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.InkRaised
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceLight
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Violet
import java.util.Locale

@Composable
fun SwapScreen(
    state: WalletState,
    initialPayAssetId: String? = null,
    onPrepare: (String, String, String, (Result<ExchangeQuote>) -> Unit) -> Unit = { _, _, _, done ->
        done(Result.failure(IllegalStateException("Quotes are unavailable")))
    },
    onAuthenticate: ((Result<Unit>) -> Unit) -> Unit = { done ->
        done(Result.failure(IllegalStateException("Authentication is unavailable")))
    },
    onExecute: (ExchangeQuote, (Result<WalletTransaction>) -> Unit) -> Unit = { _, done ->
        done(Result.failure(IllegalStateException("Exchange is unavailable")))
    },
) {
    // Defense in depth: Swap accepts bundled/user-added contracts plus assets
    // whose exact explorer contract has a positive market quote.
    val walletAssets = state.wallet?.assets.orEmpty().filter {
        it.isTrustedWalletAsset(state.customTokens)
    }
    val assets = walletAssets.filter { it.blockchain.isEvm }
    // Native BTC/SOL are valid bridge destinations, and native BTC also a source, matching iOS.
    val nonEvmDestinations = walletAssets.filter {
        it.isNative &&
            (it.blockchain == Blockchain.BITCOIN || it.blockchain == Blockchain.SOLANA) &&
            state.wallet?.account(it.blockchain) != null
    }
    val launchPayAssetId = remember { initialPayAssetId }
    val requestedPayAsset = walletAssets.firstOrNull { it.id == launchPayAssetId }
        ?.takeIf { it.blockchain.isEvm || (it.blockchain == Blockchain.BITCOIN && it.isNative) }
    var swapMode by remember { mutableStateOf(requestedPayAsset?.blockchain?.isEvm ?: true) }
    val payCandidates = if (swapMode) {
        assets
    } else {
        assets + nonEvmDestinations.filter { it.blockchain == Blockchain.BITCOIN }
    }
    val sourceNetworks = payCandidates.map(Asset::blockchain).distinct()
    var sourceNetwork by remember(sourceNetworks, swapMode) {
        mutableStateOf(
            requestedPayAsset?.blockchain?.takeIf { it in sourceNetworks }
                ?: sourceNetworks.firstOrNull()
                ?: Blockchain.ETHEREUM,
        )
    }
    val destinationNetworks = if (swapMode) {
        listOf(sourceNetwork)
    } else {
        (assets + nonEvmDestinations)
            .map(Asset::blockchain)
            .distinct()
            .filter { it != sourceNetwork }
    }
    var destinationNetwork by remember { mutableStateOf<Blockchain?>(null) }
    val sourceCandidates = payCandidates.filter { it.blockchain == sourceNetwork }
    var payAsset by remember(sourceCandidates, sourceNetwork) {
        mutableStateOf(
            sourceCandidates.firstOrNull { it.id == requestedPayAsset?.id }
                ?: sourceCandidates.firstOrNull { it.balance > 0.0 }
                ?: sourceCandidates.firstOrNull(),
        )
    }
    var receiveAsset by remember { mutableStateOf<Asset?>(null) }
    var amount by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<ExchangeQuote?>(null) }
    var showReview by remember { mutableStateOf(false) }
    var showPayPicker by remember { mutableStateOf(false) }
    var showReceivePicker by remember { mutableStateOf(false) }
    var showSourceNetworkPicker by remember { mutableStateOf(false) }
    var showDestinationNetworkPicker by remember { mutableStateOf(false) }
    var isQuoting by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf<WalletTransaction?>(null) }

    val destinations = payAsset?.let { source ->
        val candidates = if (swapMode) assets else assets + nonEvmDestinations
        candidates.filter {
            it.id != source.id &&
                it.blockchain == if (swapMode) sourceNetwork else destinationNetwork
        }
    }.orEmpty()

    LaunchedEffect(sourceNetwork, swapMode, sourceCandidates.map(Asset::id)) {
        if (payAsset?.id !in sourceCandidates.map(Asset::id)) {
            payAsset = sourceCandidates.firstOrNull { it.balance > 0.0 } ?: sourceCandidates.firstOrNull()
        }
        destinationNetwork = if (swapMode) {
            sourceNetwork
        } else {
            destinationNetwork?.takeIf { it in destinationNetworks } ?: destinationNetworks.firstOrNull()
        }
    }

    LaunchedEffect(payAsset?.id, destinationNetwork, swapMode, assets.map(Asset::id)) {
        if (receiveAsset?.id !in destinations.map(Asset::id)) {
            receiveAsset = destinations.firstOrNull()
        }
        quote = null
        showReview = false
        localError = null
    }

    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val validAmount = payAsset != null && amountValue > 0.0 && amountValue <= payAsset!!.balance
    val canQuote = validAmount && receiveAsset != null && !isQuoting && !state.isSending

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11162A), Ink, Ink))),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SwapHeader(swapMode = swapMode)
            SwapModePicker(swapMode = swapMode, onModeChange = {
                swapMode = it
                amount = ""
                quote = null
            })

            if (assets.isEmpty()) {
                Text(
                    stringResource(R.string.swap_no_evm),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    ExchangeNetworkSelectors(
                        swapMode = swapMode,
                        sourceNetwork = sourceNetwork,
                        destinationNetwork = destinationNetwork,
                        onSelectSource = { showSourceNetworkPicker = true },
                        onSelectDestination = { showDestinationNetworkPicker = true },
                    )
                    Box {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExchangeAssetCard(
                                label = stringResource(R.string.swap_you_pay),
                                asset = payAsset,
                                amount = amount,
                                valueUsd = amountValue * (payAsset?.priceUsd ?: 0.0),
                                fiat = state::formatFiat,
                                editable = true,
                                onAmountChange = {
                                    if (it.isEmpty() || it.matches(Regex("\\d*\\.?\\d*"))) {
                                        amount = it
                                        quote = null
                                        localError = null
                                    }
                                },
                                onSelectAsset = { showPayPicker = true },
                                onMax = {
                                    amount = payAsset?.balance?.toEditableAmount().orEmpty()
                                    quote = null
                                },
                            )
                            ExchangeAssetCard(
                                label = stringResource(R.string.swap_you_receive),
                                asset = receiveAsset,
                                amount = quote?.toAmount.orEmpty(),
                                valueUsd = (quote?.toAmount?.toDoubleOrNull() ?: 0.0) *
                                    (receiveAsset?.priceUsd ?: 0.0),
                                fiat = state::formatFiat,
                                editable = false,
                                onAmountChange = {},
                                onSelectAsset = { showReceivePicker = true },
                                onMax = {},
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                                .background(InkRaised, CircleShape)
                                .border(1.5.dp, Indigo, CircleShape)
                                .clickable(enabled = receiveAsset != null) {
                                    val oldPay = payAsset
                                    val oldReceive = receiveAsset
                                    if (oldPay != null && oldReceive != null) {
                                        if (swapMode || oldReceive.id in payCandidates.map(Asset::id)) {
                                            sourceNetwork = oldReceive.blockchain
                                            destinationNetwork = oldPay.blockchain
                                            payAsset = oldReceive
                                            receiveAsset = oldPay
                                            amount = ""
                                            quote = null
                                        }
                                    }
                                },
                        ) {
                            Icon(
                                Icons.Rounded.SwapVert,
                                contentDescription = stringResource(R.string.swap_reverse_assets),
                                tint = Indigo,
                            )
                        }
                    }

                    quote?.let { ExchangeDetailsCard(it, payAsset, receiveAsset) }
                    localError?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
                    Spacer(Modifier.height(4.dp))
                }

                val routeFailedMessage = stringResource(R.string.swap_unable_route)
                PrimaryButton(
                    title = stringResource(
                        when {
                            isQuoting -> R.string.swap_finding_route
                            quote != null -> if (swapMode) R.string.swap_review_swap else R.string.swap_review_bridge
                            else -> if (swapMode) R.string.swap_get_swap_quote else R.string.swap_get_bridge_quote
                        },
                    ),
                    enabled = canQuote || (quote != null && !state.isSending),
                    onClick = {
                        val source = payAsset ?: return@PrimaryButton
                        val destination = receiveAsset ?: return@PrimaryButton
                        val currentQuote = quote
                        if (currentQuote != null) {
                            showReview = true
                        } else {
                            isQuoting = true
                            localError = null
                            onPrepare(source.id, destination.id, amount) { result ->
                                isQuoting = false
                                result.fold(
                                    onSuccess = {
                                        quote = it
                                        showReview = true
                                    },
                                    onFailure = {
                                        localError = it.message ?: routeFailedMessage
                                    },
                                )
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                )
            }
        }
    }

    if (showPayPicker) {
        ExchangeAssetPicker(
            title = stringResource(R.string.swap_you_pay),
            assets = sourceCandidates,
            selected = payAsset,
            onDismiss = { showPayPicker = false },
            onSelect = {
                payAsset = it
                amount = ""
                quote = null
                showPayPicker = false
            },
        )
    }
    if (showReceivePicker) {
        ExchangeAssetPicker(
            title = stringResource(if (swapMode) R.string.swap_to else R.string.swap_bridge_to),
            assets = destinations,
            selected = receiveAsset,
            onDismiss = { showReceivePicker = false },
            onSelect = {
                receiveAsset = it
                quote = null
                showReceivePicker = false
            },
        )
    }
    if (showSourceNetworkPicker) {
        ExchangeNetworkPicker(
            title = stringResource(R.string.swap_select_source_network),
            networks = sourceNetworks,
            selected = sourceNetwork,
            onDismiss = { showSourceNetworkPicker = false },
            onSelect = {
                sourceNetwork = it
                if (swapMode) destinationNetwork = it
                amount = ""
                quote = null
                showSourceNetworkPicker = false
            },
        )
    }
    if (showDestinationNetworkPicker && !swapMode) {
        ExchangeNetworkPicker(
            title = stringResource(R.string.swap_select_destination_network),
            networks = destinationNetworks,
            selected = destinationNetwork,
            onDismiss = { showDestinationNetworkPicker = false },
            onSelect = {
                destinationNetwork = it
                quote = null
                showDestinationNetworkPicker = false
            },
        )
    }

    val reviewedQuote = quote
    if (showReview && reviewedQuote != null) {
        AlertDialog(
            onDismissRequest = { if (!state.isSending) showReview = false },
            title = {
                Text(
                    stringResource(
                        if (reviewedQuote.fromBlockchain == reviewedQuote.toBlockchain) {
                            R.string.swap_review_swap
                        } else {
                            R.string.swap_review_bridge
                        },
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    ReviewExchangeLine(
                        stringResource(R.string.swap_you_pay),
                        "${reviewedQuote.fromAmount} ${payAsset?.symbol.orEmpty()}",
                    )
                    ReviewExchangeLine(
                        stringResource(R.string.swap_you_receive),
                        "${reviewedQuote.toAmount} ${receiveAsset?.symbol.orEmpty()}",
                    )
                    ReviewExchangeLine(
                        stringResource(R.string.swap_minimum_received),
                        "${reviewedQuote.minimumToAmount} ${receiveAsset?.symbol.orEmpty()}",
                    )
                    ReviewExchangeLine(
                        stringResource(R.string.swap_route),
                        "${reviewedQuote.fromBlockchain.displayName} → ${reviewedQuote.toBlockchain.displayName}",
                    )
                    ReviewExchangeLine(stringResource(R.string.swap_provider), reviewedQuote.provider)
                    if (!reviewedQuote.toBlockchain.isEvm) {
                        ReviewExchangeLine(
                            stringResource(R.string.swap_receiving_address),
                            reviewedQuote.toAddress.let {
                                if (it.length <= 16) it else "${it.take(7)}…${it.takeLast(6)}"
                            },
                        )
                    }
                    ReviewExchangeLine(
                        stringResource(R.string.swap_max_network_fee),
                        "${reviewedQuote.maximumNetworkFee.displayAmount()} ${reviewedQuote.fromBlockchain.symbol}",
                    )
                    if (reviewedQuote.approvalDrafts.isNotEmpty()) {
                        ReviewExchangeLine(
                            stringResource(R.string.swap_token_approval),
                            stringResource(R.string.swap_approvals_count, reviewedQuote.approvalDrafts.size),
                        )
                    }
                    if (reviewedQuote.estimatedDurationSeconds > 0) {
                        ReviewExchangeLine(
                            stringResource(R.string.swap_estimated_time),
                            reviewedQuote.estimatedDurationSeconds.durationLabel(),
                        )
                    }
                    Text(
                        stringResource(R.string.swap_fee_note),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    localError?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp) }
                }
            },
            dismissButton = {
                TextButton(enabled = !state.isSending, onClick = { showReview = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                val transactionFailedMessage = stringResource(R.string.send_transaction_failed)
                val authFailedMessage = stringResource(R.string.auth_failed)
                TextButton(
                    enabled = !state.isSending,
                    onClick = {
                        localError = null
                        onAuthenticate { authentication ->
                            authentication.fold(
                                onSuccess = {
                                    onExecute(reviewedQuote) { result ->
                                        result.fold(
                                            onSuccess = {
                                                submitted = it
                                                showReview = false
                                                quote = null
                                                amount = ""
                                            },
                                            onFailure = {
                                                localError = it.message ?: transactionFailedMessage
                                                showReview = false
                                                quote = null
                                            },
                                        )
                                    }
                                },
                                onFailure = { localError = it.message ?: authFailedMessage },
                            )
                        }
                    },
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(stringResource(R.string.swap_confirm_sign))
                    }
                }
            },
        )
    }

    submitted?.let { transaction ->
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { submitted = null },
            title = {
                Text(
                    stringResource(
                        if (transaction.type.name == "BRIDGE") {
                            R.string.swap_bridge_submitted
                        } else {
                            R.string.swap_submitted
                        },
                    ),
                    color = Success,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.swap_accepted_by, transaction.blockchain.displayName))
                    Text(
                        transaction.hash,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { uriHandler.openUri(transaction.explorerUrl) }) {
                    Text(stringResource(R.string.common_view_on_explorer))
                }
            },
            confirmButton = {
                TextButton(onClick = { submitted = null }) { Text(stringResource(R.string.common_done)) }
            },
        )
    }
}

@Composable
private fun SwapHeader(swapMode: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Spacer(Modifier.size(44.dp))
        Text(
            stringResource(if (swapMode) R.string.common_swap else R.string.common_bridge),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(SurfaceLight, RoundedCornerShape(14.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp)),
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.swap_slippage),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SwapModePicker(swapMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(Surface, RoundedCornerShape(25.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(25.dp))
            .padding(4.dp),
    ) {
        SwapMode(stringResource(R.string.common_swap), Icons.Rounded.SwapVert, swapMode, Modifier.weight(1f)) {
            onModeChange(true)
        }
        SwapMode(stringResource(R.string.common_bridge), Icons.Rounded.Link, !swapMode, Modifier.weight(1f)) {
            onModeChange(false)
        }
    }
}

@Composable
private fun SwapMode(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(38.dp)
            .background(
                if (selected) Brush.horizontalGradient(listOf(Indigo, Violet))
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else TextSecondary, modifier = Modifier.size(18.dp))
        Text(
            title,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

@Composable
private fun ExchangeNetworkSelectors(
    swapMode: Boolean,
    sourceNetwork: Blockchain,
    destinationNetwork: Blockchain?,
    onSelectSource: () -> Unit,
    onSelectDestination: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            stringResource(R.string.swap_choose_network_first),
            color = TextSecondary,
            fontSize = 12.sp,
        )
        if (swapMode) {
            ExchangeNetworkButton(
                label = stringResource(R.string.swap_select_source_network),
                network = sourceNetwork,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSelectSource,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExchangeNetworkButton(
                    label = stringResource(R.string.swap_select_source_network),
                    network = sourceNetwork,
                    modifier = Modifier.weight(1f),
                    onClick = onSelectSource,
                )
                ExchangeNetworkButton(
                    label = stringResource(R.string.swap_select_destination_network),
                    network = destinationNetwork,
                    modifier = Modifier.weight(1f),
                    onClick = onSelectDestination,
                )
            }
        }
    }
}

@Composable
private fun ExchangeNetworkButton(
    label: String,
    network: Blockchain?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(SurfaceLight, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        network?.let { CryptoNetworkIcon(it, 30) }
        Column(Modifier.padding(start = if (network == null) 0.dp else 9.dp).weight(1f)) {
            Text(label, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
            Text(
                network?.displayName ?: stringResource(R.string.swap_select),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ExchangeNetworkPicker(
    title: String,
    networks: List<Blockchain>,
    selected: Blockchain?,
    onDismiss: () -> Unit,
    onSelect: (Blockchain) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                networks.forEach { network ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected == network) Indigo.copy(alpha = 0.12f) else Color.Transparent,
                                RoundedCornerShape(15.dp),
                            )
                            .clickable { onSelect(network) }
                            .padding(11.dp),
                    ) {
                        CryptoNetworkIcon(network, 38)
                        Text(
                            network.displayName,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 11.dp).weight(1f),
                        )
                        if (selected == network) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Success,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun ExchangeAssetCard(
    label: String,
    asset: Asset?,
    amount: String,
    valueUsd: Double,
    editable: Boolean,
    fiat: (Double) -> String = { "\$${"%,.2f".format(Locale.US, it)}" },
    onAmountChange: (String) -> Unit,
    onSelectAsset: () -> Unit,
    onMax: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Indigo.copy(alpha = if (editable) 0.11f else 0.07f), Surface, Surface)),
                LargeShape,
            )
            .border(1.dp, if (editable) Indigo.copy(alpha = 0.42f) else SurfaceBorder, LargeShape)
            .padding(18.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            if (editable) {
                BasicTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Indigo),
                    decorationBox = { inner ->
                        Box {
                            if (amount.isEmpty()) {
                                Text("0.0", color = TextTertiary, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    amount.ifEmpty { "0.0" },
                    color = if (amount.isEmpty()) TextTertiary else Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(SurfaceLight, RoundedCornerShape(24.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                    .clickable(onClick = onSelectAsset)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                asset?.let { CryptoTokenIcon(it, 30) }
                Text(
                    asset?.symbol ?: stringResource(R.string.swap_select),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = if (asset == null) 0.dp else 7.dp),
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(17.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 11.dp)) {
            Text(
                if (valueUsd > 0) "≈ ${fiat(valueUsd)}" else stringResource(R.string.swap_quote_from_lifi),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
                    R.string.common_balance_format,
                    asset?.balance?.displayAmount() ?: "—",
                    asset?.symbol.orEmpty(),
                ),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (editable) {
                Text(
                    stringResource(R.string.send_max),
                    color = Indigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .border(1.dp, Indigo.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                        .clickable(onClick = onMax)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun ExchangeDetailsCard(quote: ExchangeQuote, pay: Asset?, receive: Asset?) {
    val rate = if (pay != null && receive != null) {
        val output = quote.toAmount.toDoubleOrNull() ?: 0.0
        val input = quote.fromAmount.toDoubleOrNull() ?: 0.0
        if (input > 0) "1 ${pay.symbol} ≈ ${(output / input).displayAmount()} ${receive.symbol}" else "—"
    } else {
        "—"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .padding(horizontal = 16.dp),
    ) {
        ReviewExchangeLine(stringResource(R.string.swap_exchange_rate), rate, 48)
        DetailDivider()
        ReviewExchangeLine(
            stringResource(R.string.swap_minimum_received_cap),
            "${quote.minimumToAmount} ${receive?.symbol.orEmpty()}",
            48,
        )
        DetailDivider()
        ReviewExchangeLine(
            stringResource(R.string.swap_maximum_fee),
            "${quote.maximumNetworkFee.displayAmount()} ${quote.fromBlockchain.symbol}",
            48,
        )
        DetailDivider()
        ReviewExchangeLine(stringResource(R.string.swap_provider), quote.provider, 48)
    }
}

@Composable
private fun ExchangeAssetPicker(
    title: String,
    assets: List<Asset>,
    selected: Asset?,
    onDismiss: () -> Unit,
    onSelect: (Asset) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (assets.isEmpty()) {
                Text(stringResource(R.string.swap_no_compatible), color = TextSecondary)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    assets.sortedWith(
                        compareByDescending<Asset> { it.balance > 0 }
                            .thenByDescending(Asset::valueUsd)
                            .thenBy(Asset::symbol),
                    ).forEach { asset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected?.id == asset.id) Indigo.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(15.dp),
                                )
                                .clickable { onSelect(asset) }
                                .padding(11.dp),
                        ) {
                            CryptoTokenIcon(asset, 38, showNetworkBadge = true)
                            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                Text(
                                    asset.symbol,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(asset.blockchain.displayName, color = TextSecondary, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    asset.balance.displayAmount(),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                                if (selected?.id == asset.id) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Success, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun DetailDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceBorder))
}

@Composable
private fun ReviewExchangeLine(label: String, value: String, height: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height.dp) else Modifier),
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun Double.toEditableAmount(): String =
    "%.12f".format(Locale.US, this).trimEnd('0').trimEnd('.')

private fun Double.displayAmount(): String = when {
    !isFinite() -> "—"
    this == 0.0 -> "0"
    kotlin.math.abs(this) >= 1_000_000_000_000_000.0 ->
        "%.3e".format(Locale.US, this).replace("e+", "e")
    kotlin.math.abs(this) >= 1_000_000_000_000.0 -> compactAmount(1_000_000_000_000.0, "T")
    kotlin.math.abs(this) >= 1_000_000_000.0 -> compactAmount(1_000_000_000.0, "B")
    kotlin.math.abs(this) >= 1_000_000.0 -> compactAmount(1_000_000.0, "M")
    this < 0.000001 -> "<0.000001"
    else -> "%,.8f".format(Locale.US, this).trimEnd('0').trimEnd('.')
}

private fun Double.compactAmount(divisor: Double, suffix: String): String =
    "%.3f".format(Locale.US, this / divisor).trimEnd('0').trimEnd('.') + suffix

private fun Long.durationLabel(): String = when {
    this < 60 -> "${this}s"
    else -> "${this / 60} min"
}
