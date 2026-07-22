package io.noriskservis.wpayin.wallet.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.P2PAcceptPlan
import io.noriskservis.wpayin.wallet.data.P2PListing
import io.noriskservis.wpayin.wallet.data.P2PService
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.KnownTokenCatalog
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import androidx.compose.ui.graphics.asImageBitmap
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceLight
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.Warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * P2P tab: non-custodial AirSwap trading. Accepting offers created on iOS works
 * end-to-end; creating offers needs EIP-712 signing support and comes later.
 */
private enum class P2PMode { BUY, SELL }

@Composable
fun P2PScreen(
    state: WalletState,
    onBack: () -> Unit,
    boardConfigured: Boolean = false,
    onLoadListings: ((Result<List<P2PListing>>) -> Unit) -> Unit = { done ->
        done(Result.success(emptyList()))
    },
    onPrepareAccept: (String, (Result<P2PAcceptPlan>) -> Unit) -> Unit = { _, done ->
        done(Result.failure(IllegalStateException("P2P is unavailable")))
    },
    onAuthenticate: ((Result<Unit>) -> Unit) -> Unit = { done ->
        done(Result.failure(IllegalStateException("Authentication is unavailable")))
    },
    onExecuteAccept: (P2PAcceptPlan, (Result<WalletTransaction>) -> Unit) -> Unit = { _, done ->
        done(Result.failure(IllegalStateException("P2P is unavailable")))
    },
    onCreateOffer: (String, String, String, Int, String, String, Long, (Result<String>) -> Unit) -> Unit =
        { _, _, _, _, _, _, _, done ->
            done(Result.failure(IllegalStateException("P2P is unavailable")))
        },
) {
    val context = LocalContext.current
    var payload by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<P2PAcceptPlan?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf<WalletTransaction?>(null) }
    var listings by remember { mutableStateOf<List<P2PListing>>(emptyList()) }
    var isLoadingBoard by remember { mutableStateOf(false) }
    var boardError by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(P2PMode.BUY) }

    // Create-offer form state
    val sellCandidates = state.wallet?.assets.orEmpty().filter {
        it.blockchain in P2PService.supportedBlockchains
    }
    var sellAsset by remember(sellCandidates) {
        mutableStateOf(sellCandidates.firstOrNull { it.balance > 0.0 } ?: sellCandidates.firstOrNull())
    }
    var sellAmount by remember { mutableStateOf("") }
    var receiveToken by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    var receiveAmount by remember { mutableStateOf("") }
    var validitySeconds by remember { mutableStateOf(86_400L) }
    var isCreating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var createdPayload by remember { mutableStateOf<String?>(null) }
    var showSellPicker by remember { mutableStateOf(false) }
    var showReceivePicker by remember { mutableStateOf(false) }

    val effectiveSellAddress = sellAsset?.let { asset ->
        if (asset.isNative) P2PService.wrappedNativeToken(asset.blockchain)?.first else asset.contractAddress
    }
    val receiveOptions: List<Triple<String, String, Int>> = sellAsset?.let { asset ->
        buildList {
            P2PService.wrappedNativeToken(asset.blockchain)?.let { add(Triple(it.first, it.second, 18)) }
            (KnownTokenCatalog.forBlockchain(asset.blockchain) +
                state.customTokens.filter { it.blockchain == asset.blockchain })
                .forEach { add(Triple(it.contractAddress, it.symbol, it.decimals)) }
        }
            .distinctBy { it.first.lowercase() }
            .filterNot { it.first.equals(effectiveSellAddress, ignoreCase = true) }
    }.orEmpty()

    LaunchedEffect(sellAsset?.id) {
        if (receiveToken?.first !in receiveOptions.map { it.first }) {
            receiveToken = receiveOptions.firstOrNull()
        }
        createError = null
    }

    fun refreshBoard() {
        if (!boardConfigured || isLoadingBoard) return
        isLoadingBoard = true
        boardError = null
        onLoadListings { result ->
            isLoadingBoard = false
            result.fold(
                onSuccess = { listings = it },
                onFailure = { boardError = it.message },
            )
        }
    }

    LaunchedEffect(boardConfigured) {
        if (boardConfigured) refreshBoard()
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let {
            payload = it.trim()
            plan = null
            localError = null
        }
    }

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = stringResource(R.string.p2p_exchange_title), onBack = onBack)
            P2PModePicker(
                mode = mode,
                onModeChange = { mode = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (mode == P2PMode.BUY && boardConfigured) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.p2p_board_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.p2p_board_refresh),
                                color = Indigo,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(Indigo.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isLoadingBoard) { refreshBoard() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    when {
                        boardError != null -> item {
                            Text(
                                boardError ?: stringResource(R.string.p2p_board_error),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                            )
                        }
                        listings.isEmpty() && !isLoadingBoard -> item {
                            Text(
                                stringResource(R.string.p2p_board_empty),
                                color = TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                        else -> items(listings.size) { index ->
                            val listing = listings[index]
                            P2PListingRow(listing) {
                                payload = listing.payload
                                plan = null
                                localError = null
                            }
                        }
                    }
                }

                if (mode == P2PMode.BUY) {
                    item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, LargeShape)
                            .border(1.dp, SurfaceBorder, LargeShape)
                            .padding(18.dp),
                    ) {
                        Text(
                            stringResource(R.string.p2p_accept_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.p2p_accept_hint),
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                        OutlinedTextField(
                            value = payload,
                            onValueChange = {
                                payload = it
                                plan = null
                                localError = null
                            },
                            placeholder = { Text(stringResource(R.string.p2p_offer_placeholder)) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(15.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Indigo,
                                unfocusedBorderColor = SurfaceBorder,
                                focusedContainerColor = SurfaceLight,
                                unfocusedContainerColor = SurfaceLight,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            P2PInputAction(
                                icon = Icons.Rounded.QrCodeScanner,
                                title = stringResource(R.string.send_scan_qr),
                                modifier = Modifier.weight(1f),
                            ) {
                                scanLauncher.launch(
                                    ScanOptions()
                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        .setPrompt("")
                                        .setBeepEnabled(false)
                                        .setOrientationLocked(true),
                                )
                            }
                            P2PInputAction(
                                icon = Icons.Rounded.ContentPaste,
                                title = stringResource(R.string.send_paste_address),
                                modifier = Modifier.weight(1f),
                            ) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let {
                                    payload = it.trim()
                                    plan = null
                                    localError = null
                                }
                            }
                        }
                        val invalidOfferMessage = stringResource(R.string.p2p_invalid_offer)
                        PrimaryButton(
                            title = stringResource(
                                if (isValidating) R.string.p2p_validating else R.string.p2p_validate,
                            ),
                            enabled = payload.isNotBlank() && !isValidating && !state.isSending,
                            onClick = {
                                isValidating = true
                                localError = null
                                plan = null
                                onPrepareAccept(payload) { result ->
                                    isValidating = false
                                    result.fold(
                                        onSuccess = { plan = it },
                                        onFailure = { localError = it.message ?: invalidOfferMessage },
                                    )
                                }
                            },
                        )
                        localError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                    }
                }

                if (mode == P2PMode.SELL && sellCandidates.isNotEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface, LargeShape)
                                .border(1.dp, SurfaceBorder, LargeShape)
                                .padding(18.dp),
                        ) {
                            Text(
                                stringResource(R.string.p2p_create_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            P2PFormRow(
                                label = stringResource(R.string.p2p_sell_asset),
                                value = sellAsset?.let { asset ->
                                    val symbol = if (asset.isNative) {
                                        P2PService.wrappedNativeToken(asset.blockchain)?.second ?: asset.symbol
                                    } else {
                                        asset.symbol
                                    }
                                    "$symbol • ${asset.blockchain.displayName}"
                                } ?: stringResource(R.string.p2p_select_asset),
                            ) { showSellPicker = true }
                            P2PAmountField(
                                value = sellAmount,
                                placeholder = stringResource(R.string.p2p_amount),
                                onChange = {
                                    sellAmount = it
                                    createError = null
                                },
                            )
                            P2PFormRow(
                                label = stringResource(R.string.p2p_receive_token),
                                value = receiveToken?.second ?: stringResource(R.string.p2p_select_token),
                            ) { showReceivePicker = true }
                            P2PAmountField(
                                value = receiveAmount,
                                placeholder = stringResource(R.string.p2p_amount),
                                onChange = {
                                    receiveAmount = it
                                    createError = null
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    3_600L to stringResource(R.string.p2p_validity_1h),
                                    86_400L to stringResource(R.string.p2p_validity_24h),
                                    604_800L to stringResource(R.string.p2p_validity_7d),
                                ).forEach { (seconds, label) ->
                                    val selected = validitySeconds == seconds
                                    Text(
                                        label,
                                        color = if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .background(
                                                if (selected) Indigo.copy(alpha = 0.22f) else SurfaceLight,
                                                CircleShape,
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) Indigo else SurfaceBorder,
                                                CircleShape,
                                            )
                                            .clickable { validitySeconds = seconds }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                            }
                            if (sellAsset?.isNative == true) {
                                Text(
                                    stringResource(R.string.p2p_native_sells_wrapped),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            val createFailedMessage = stringResource(R.string.p2p_create_failed)
                            PrimaryButton(
                                title = stringResource(
                                    if (isCreating) R.string.p2p_creating else R.string.p2p_create_and_sign,
                                ),
                                enabled = !isCreating && !state.isSending &&
                                    sellAsset != null && receiveToken != null &&
                                    (sellAmount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                                    (receiveAmount.toDoubleOrNull() ?: 0.0) > 0.0,
                                onClick = {
                                    val asset = sellAsset ?: return@PrimaryButton
                                    val token = receiveToken ?: return@PrimaryButton
                                    createError = null
                                    onAuthenticate { authentication ->
                                        authentication.fold(
                                            onSuccess = {
                                                isCreating = true
                                                onCreateOffer(
                                                    asset.id,
                                                    token.first,
                                                    token.second,
                                                    token.third,
                                                    sellAmount,
                                                    receiveAmount,
                                                    validitySeconds,
                                                ) { result ->
                                                    isCreating = false
                                                    result.fold(
                                                        onSuccess = {
                                                            createdPayload = it
                                                            sellAmount = ""
                                                            receiveAmount = ""
                                                        },
                                                        onFailure = {
                                                            createError = it.message ?: createFailedMessage
                                                        },
                                                    )
                                                }
                                            },
                                            onFailure = { createError = it.message },
                                        )
                                    }
                                },
                            )
                            createError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                        }
                    }
                }

                plan?.let { reviewedPlan ->
                    item {
                        P2POfferSummary(
                            plan = reviewedPlan,
                            isSending = state.isSending,
                            onAccept = {
                                localError = null
                                onAuthenticate { authentication ->
                                    authentication.fold(
                                        onSuccess = {
                                            onExecuteAccept(reviewedPlan) { result ->
                                                result.fold(
                                                    onSuccess = {
                                                        submitted = it
                                                        plan = null
                                                        payload = ""
                                                    },
                                                    onFailure = {
                                                        localError = it.message
                                                        plan = null
                                                    },
                                                )
                                            }
                                        },
                                        onFailure = { localError = it.message },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSellPicker) {
        AlertDialog(
            onDismissRequest = { showSellPicker = false },
            title = { Text(stringResource(R.string.p2p_select_asset)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sellCandidates.forEach { asset ->
                        Text(
                            "${asset.symbol} • ${asset.blockchain.displayName}",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (sellAsset?.id == asset.id) Indigo.copy(alpha = 0.12f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    sellAsset = asset
                                    showSellPicker = false
                                }
                                .padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSellPicker = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    if (showReceivePicker) {
        AlertDialog(
            onDismissRequest = { showReceivePicker = false },
            title = { Text(stringResource(R.string.p2p_select_token)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    receiveOptions.forEach { option ->
                        Text(
                            option.second,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (receiveToken?.first == option.first) Indigo.copy(alpha = 0.12f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    receiveToken = option
                                    showReceivePicker = false
                                }
                                .padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReceivePicker = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    createdPayload?.let { offerPayload ->
        var copied by remember(offerPayload) { mutableStateOf(false) }
        val qr = remember(offerPayload) { generateP2PQrCode(offerPayload, 640) }
        AlertDialog(
            onDismissRequest = { createdPayload = null },
            title = { Text(stringResource(R.string.p2p_offer_created), color = Success) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.p2p_offer_created_message), color = TextSecondary)
                    qr?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = stringResource(R.string.p2p_offer_created),
                            modifier = Modifier
                                .size(216.dp)
                                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                                .padding(10.dp),
                        )
                    }
                    Text(
                        offerPayload,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Wpayin P2P offer", offerPayload))
                        copied = true
                    },
                ) {
                    Text(stringResource(if (copied) R.string.p2p_offer_copied else R.string.p2p_copy_offer))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, offerPayload)
                                },
                                null,
                            ),
                        )
                    },
                ) { Text(stringResource(R.string.p2p_share_offer)) }
            },
        )
    }

    submitted?.let { transaction ->
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { submitted = null },
            title = { Text(stringResource(R.string.p2p_trade_submitted), color = Success) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.p2p_trade_submitted_message, transaction.blockchain.displayName))
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
private fun P2PModePicker(
    mode: P2PMode,
    onModeChange: (P2PMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, CircleShape)
            .border(1.dp, SurfaceBorder, CircleShape)
            .padding(4.dp),
    ) {
        listOf(
            P2PMode.BUY to stringResource(R.string.p2p_buy),
            P2PMode.SELL to stringResource(R.string.p2p_sell),
        ).forEach { (candidate, label) ->
            val selected = mode == candidate
            Text(
                label,
                color = if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) Indigo.copy(alpha = 0.9f) else androidx.compose.ui.graphics.Color.Transparent,
                        CircleShape,
                    )
                    .clickable { onModeChange(candidate) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun P2PListingRow(listing: P2PListing, onClick: () -> Unit) {
    val offer = listing.offer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    R.string.p2p_listing_buy,
                    offer.signerAmountDecimal.stripTrailingZeros().toPlainString(),
                    offer.signerSymbol,
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(
                    R.string.p2p_listing_for,
                    offer.senderAmountDecimal.stripTrailingZeros().toPlainString(),
                    offer.senderSymbol,
                ),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            offer.blockchain?.displayName.orEmpty(),
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .background(SurfaceLight, CircleShape)
                .border(1.dp, SurfaceBorder, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun P2POfferSummary(
    plan: P2PAcceptPlan,
    isSending: Boolean,
    onAccept: () -> Unit,
) {
    val offer = plan.offer
    val platformFee = plan.platformFeeUnits.toBigDecimal()
        .movePointLeft(offer.senderDecimals)
        .stripTrailingZeros()
        .toPlainString()
    val expiryText = remember(offer.expiry) {
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
            .format(Instant.ofEpochSecond(offer.expiry).atZone(ZoneId.systemDefault()))
    }
    val approvalCount = (if (plan.needsApproval) 1 else 0) + (if (plan.needsApprovalReset) 1 else 0)

    Column(
        verticalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, LargeShape)
            .border(1.dp, Indigo.copy(alpha = 0.4f), LargeShape)
            .padding(18.dp),
    ) {
        P2PSummaryLine(
            stringResource(R.string.p2p_you_receive),
            "${offer.signerAmountDecimal.stripTrailingZeros().toPlainString()} ${offer.signerSymbol}",
            valueColor = Success,
        )
        P2PSummaryLine(
            stringResource(R.string.p2p_you_pay),
            "${offer.senderAmountDecimal.stripTrailingZeros().toPlainString()} ${offer.senderSymbol}",
        )
        if (plan.platformFeeUnits.signum() > 0) {
            P2PSummaryLine(
                stringResource(R.string.p2p_platform_fee),
                "$platformFee ${offer.senderSymbol}",
            )
        }
        P2PSummaryLine(stringResource(R.string.p2p_network), plan.blockchain.displayName)
        P2PSummaryLine(stringResource(R.string.p2p_seller), offer.signerWallet.shortAddress())
        P2PSummaryLine(stringResource(R.string.p2p_expires), expiryText)
        if (plan.wrapShortfallUnits.signum() > 0) {
            P2PSummaryLine(
                stringResource(R.string.p2p_wrap_native, plan.blockchain.symbol),
                plan.wrapShortfallUnits.toBigDecimal()
                    .movePointLeft(offer.senderDecimals)
                    .stripTrailingZeros()
                    .toPlainString() + " ${offer.senderSymbol}",
            )
        }
        if (approvalCount > 0) {
            P2PSummaryLine(
                stringResource(R.string.p2p_approval_needed),
                stringResource(R.string.p2p_approval_needed_value, approvalCount),
            )
        }
        P2PSummaryLine(
            stringResource(R.string.p2p_estimated_fee),
            "${"%.6f".format(Locale.US, plan.estimatedMaxFeeNative).trimEnd('0').trimEnd('.')} ${plan.blockchain.symbol}",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Indigo.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Indigo, modifier = Modifier.size(17.dp))
            Text(
                stringResource(R.string.p2p_atomic_note),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        PrimaryButton(
            title = stringResource(if (isSending) R.string.p2p_accepting else R.string.p2p_accept_and_sign),
            enabled = !isSending,
            onClick = onAccept,
        )
    }
}

@Composable
private fun P2PSummaryLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun P2PInputAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(SurfaceLight, RoundedCornerShape(13.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(17.dp))
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun P2PFormRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceLight, RoundedCornerShape(13.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun P2PAmountField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (it.isEmpty() || it.matches(Regex("^[0-9]*([.,][0-9]*)?$"))) {
                onChange(it.replace(',', '.'))
            }
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
        ),
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Indigo,
            unfocusedBorderColor = SurfaceBorder,
            focusedContainerColor = SurfaceLight,
            unfocusedContainerColor = SurfaceLight,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun generateP2PQrCode(content: String, size: Int): android.graphics.Bitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        content,
        com.google.zxing.BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 0),
    )
    android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()

private fun String.shortAddress(): String = if (length <= 13) this else "${take(6)}…${takeLast(4)}"
