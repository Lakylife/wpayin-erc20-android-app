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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.BitcoinTransferDraft
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.EvmTransferDraft
import io.noriskservis.wpayin.wallet.model.SolanaTransferDraft
import io.noriskservis.wpayin.wallet.model.TransferDraft
import io.noriskservis.wpayin.wallet.model.WalletTransaction
import io.noriskservis.wpayin.wallet.model.SavedAddress
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
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
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.math.BigDecimal
import java.util.Locale

@Composable
fun SendScreen(
    state: WalletState,
    initialAssetId: String? = null,
    onPrepare: (String, String, String, (Result<TransferDraft>) -> Unit) -> Unit,
    onAuthenticate: ((Result<Unit>) -> Unit) -> Unit,
    onSend: (TransferDraft, (Result<WalletTransaction>) -> Unit) -> Unit,
    onAddSavedAddress: (String, String) -> Result<SavedAddress> = { _, _ ->
        Result.failure(IllegalStateException("Saved addresses are unavailable"))
    },
    onRemoveSavedAddress: (String) -> Unit = {},
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val wallet = requireNotNull(state.wallet)
    val assets = wallet.assets.filter {
        it.blockchain.isEvm || (it.blockchain in setOf(Blockchain.SOLANA, Blockchain.BITCOIN) && it.isNative)
    }
    val assetSymbols = assets.map { it.symbol.uppercase() }.distinct().sorted()
    val initialAsset = assets.firstOrNull { it.id == initialAssetId }
    var selectedSymbol by remember(assets, initialAssetId) {
        mutableStateOf(initialAsset?.symbol?.uppercase())
    }
    var selectedBlockchain by remember(assets, initialAssetId) {
        mutableStateOf(initialAsset?.blockchain)
    }
    var showAssetPicker by remember { mutableStateOf(false) }
    var showNetworkPicker by remember { mutableStateOf(false) }
    var showSavedAddressPicker by remember { mutableStateOf(false) }
    var showSaveAddressDialog by remember { mutableStateOf(false) }
    var savedAddressName by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<TransferDraft?>(null) }
    var sentTransaction by remember { mutableStateOf<WalletTransaction?>(null) }
    var isPreparing by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val selectedAssetNetworks = assets.filter { it.symbol.equals(selectedSymbol, ignoreCase = true) }
    val selectedAsset = selectedAssetNetworks.firstOrNull { it.blockchain == selectedBlockchain }
    val requiresNetworkSelection = selectedAssetNetworks.size > 1
    val recipientStep = if (requiresNetworkSelection) 3 else 2
    val recipientValid = selectedBlockchain?.let { recipient.isValidAddressFor(it) } == true
    val compatibleSavedAddresses = state.savedAddresses.filter { saved ->
        selectedBlockchain?.let { saved.address.isValidAddressFor(it) } == true
    }
    val amountValue = amount.toDoubleOrNull() ?: 0.0
    val amountValid = selectedAsset != null && amountValue > 0 && amountValue <= selectedAsset.balance
    val context = LocalContext.current
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            recipient = scanned.extractWalletAddress()
            amount = ""
            draft = null
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
            ScreenHeader(title = stringResource(R.string.send_title), onBack = onBack)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .background(Indigo.copy(alpha = 0.14f), CircleShape)
                                .border(1.dp, Indigo.copy(alpha = 0.18f), CircleShape),
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                contentDescription = null,
                                tint = Indigo,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(
                                    if (selectedBlockchain == Blockchain.BITCOIN) {
                                        R.string.send_bitcoin_title
                                    } else {
                                        R.string.send_funds_title
                                    },
                                ),
                                fontSize = 23.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(
                                    if (selectedBlockchain == Blockchain.BITCOIN) {
                                        R.string.send_bitcoin_subtitle
                                    } else {
                                        R.string.send_funds_subtitle
                                    },
                                ),
                                color = TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                item {
                    SendStepCard(
                        stepNumber = 1,
                        title = stringResource(R.string.send_select_asset),
                        isComplete = selectedAsset != null,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable { showAssetPicker = true },
                        ) {
                            if (selectedAsset != null) {
                                CryptoTokenIcon(asset = selectedAsset, size = 36)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(start = 13.dp).weight(1f),
                                ) {
                                    Text(
                                        selectedAsset.symbol.uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.common_balance_format,
                                            selectedAsset.balance.displayAmount(),
                                            selectedAsset.symbol,
                                        ),
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Indigo.copy(alpha = 0.14f), CircleShape),
                                ) {
                                    Text("$", color = Indigo, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    stringResource(R.string.send_select_token),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 13.dp).weight(1f),
                                )
                                Icon(
                                    Icons.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                if (requiresNetworkSelection) {
                    item {
                        SendStepCard(
                            stepNumber = 2,
                            title = stringResource(R.string.send_select_network),
                            isComplete = selectedBlockchain != null,
                        ) {
                            NetworkSelectionRow(
                                asset = selectedAsset,
                                availableCount = selectedAssetNetworks.size,
                                onClick = { showNetworkPicker = true },
                            )
                        }
                    }
                }

                if (selectedAsset != null) {
                    item {
                        SendStepCard(
                            stepNumber = recipientStep,
                            title = stringResource(R.string.send_recipient_address),
                            isComplete = recipientValid,
                        ) {
                            OutlinedTextField(
                                value = recipient,
                                onValueChange = {
                                    recipient = it.trim()
                                    amount = ""
                                    draft = null
                                    localError = null
                                },
                                placeholder = {
                                    Text(
                                        when (selectedAsset.blockchain) {
                                            Blockchain.BITCOIN -> "bc1..."
                                            Blockchain.SOLANA -> stringResource(R.string.send_solana_address_placeholder)
                                            else -> "0x..."
                                        },
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 12.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.QrCodeScanner,
                                            contentDescription = stringResource(R.string.send_scan_qr),
                                            tint = Indigo,
                                            modifier = Modifier.clickable {
                                                scanLauncher.launch(
                                                    ScanOptions()
                                                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                                        .setPrompt("")
                                                        .setBeepEnabled(false)
                                                        .setOrientationLocked(true),
                                                )
                                            },
                                        )
                                        Icon(
                                            Icons.Rounded.ContentPaste,
                                            contentDescription = stringResource(R.string.send_paste_address),
                                            tint = Indigo,
                                            modifier = Modifier.clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let {
                                                    recipient = it.trim()
                                                    draft = null
                                                    localError = null
                                                }
                                            },
                                        )
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                isError = recipient.isNotBlank() && !recipientValid,
                                singleLine = true,
                                shape = RoundedCornerShape(15.dp),
                                colors = sendFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                when {
                                    recipient.isBlank() -> stringResource(R.string.send_enter_address_hint)
                                    recipientValid -> stringResource(
                                        R.string.send_address_valid,
                                        selectedAsset.blockchain.displayName,
                                    )
                                    else -> stringResource(R.string.send_address_invalid)
                                },
                                color = when {
                                    recipient.isBlank() -> TextTertiary
                                    recipientValid -> Success
                                    else -> MaterialTheme.colorScheme.error
                                },
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 10.dp, start = 3.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .background(SurfaceLight, RoundedCornerShape(13.dp))
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(13.dp))
                                        .clickable { showSavedAddressPicker = true },
                                ) {
                                    Icon(
                                        Icons.Rounded.BookmarkBorder,
                                        contentDescription = null,
                                        tint = Indigo,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Text(
                                        stringResource(R.string.send_saved_count, compatibleSavedAddresses.size),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .background(
                                            if (recipientValid) Indigo.copy(alpha = 0.12f) else SurfaceLight,
                                            RoundedCornerShape(13.dp),
                                        )
                                        .border(
                                            1.dp,
                                            if (recipientValid) Indigo.copy(alpha = 0.3f) else SurfaceBorder,
                                            RoundedCornerShape(13.dp),
                                        )
                                        .clickable(enabled = recipientValid) {
                                            savedAddressName = ""
                                            showSaveAddressDialog = true
                                        },
                                ) {
                                    Icon(
                                        Icons.Rounded.Save,
                                        contentDescription = null,
                                        tint = if (recipientValid) Indigo else TextTertiary,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Text(
                                        stringResource(R.string.send_save_address),
                                        color = if (recipientValid) Color.White else TextTertiary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedAsset != null && recipientValid) {
                    item {
                        SendStepCard(
                            stepNumber = recipientStep + 1,
                            title = stringResource(R.string.send_amount),
                            isComplete = amountValid,
                        ) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = {
                                    if (it.matches(Regex("^[0-9]*([.,][0-9]*)?$"))) {
                                        amount = it.replace(',', '.')
                                        draft = null
                                        localError = null
                                    }
                                },
                                placeholder = { Text("0.0") },
                                suffix = { Text(selectedAsset.symbol, fontWeight = FontWeight.SemiBold) },
                                trailingIcon = {
                                    Text(
                                        stringResource(R.string.send_max),
                                        color = Indigo,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            amount = selectedAsset.balance.toEditableAmount()
                                            draft = null
                                        },
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(15.dp),
                                colors = sendFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 3.dp, end = 3.dp),
                            ) {
                                Text(stringResource(R.string.send_available), color = TextSecondary, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${selectedAsset.balance.displayAmount()} ${selectedAsset.symbol}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    item {
                        SendStepCard(
                            stepNumber = recipientStep + 2,
                            title = stringResource(R.string.send_network_fee),
                            isComplete = false,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(SurfaceLight, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.send_fee_standard), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(R.string.send_fee_calculated_before_signing),
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                    )
                                }
                                Text(
                                    stringResource(R.string.send_fee_estimate_at_review),
                                    color = Indigo,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                localError?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }
            }

            val prepareFailedMessage = stringResource(R.string.send_unable_to_prepare)
            PrimaryButton(
                title = stringResource(if (isPreparing) R.string.send_estimating_fee else R.string.send_review_and_sign),
                enabled = selectedAsset != null && recipientValid && amountValid && !isPreparing,
                onClick = {
                    val asset = selectedAsset ?: return@PrimaryButton
                    isPreparing = true
                    localError = null
                    onPrepare(asset.id, recipient, amount) { result ->
                        isPreparing = false
                        result.fold(
                            onSuccess = { draft = it },
                            onFailure = { localError = it.message ?: prepareFailedMessage },
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }

    if (showAssetPicker) {
        AlertDialog(
            onDismissRequest = { showAssetPicker = false },
            title = { Text(stringResource(R.string.send_select_asset)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    assetSymbols.forEach { symbol ->
                        val candidates = assets.filter { it.symbol.equals(symbol, ignoreCase = true) }
                        val representative = candidates.maxByOrNull(Asset::balance) ?: return@forEach
                        val totalBalance = candidates.sumOf(Asset::balance)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selectedSymbol == symbol) Indigo.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable {
                                    selectedSymbol = symbol
                                    selectedBlockchain = representative.blockchain
                                    recipient = ""
                                    amount = ""
                                    draft = null
                                    localError = null
                                    showAssetPicker = false
                                }
                                .padding(12.dp),
                        ) {
                            CryptoTokenIcon(asset = representative, size = 40)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(representative.name, fontWeight = FontWeight.SemiBold)
                                Text(symbol, color = TextSecondary, fontSize = 12.sp)
                            }
                            Text(
                                "${totalBalance.displayAmount()} $symbol",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssetPicker = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    if (showNetworkPicker) {
        AlertDialog(
            onDismissRequest = { showNetworkPicker = false },
            title = { Text(stringResource(R.string.send_select_network)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedAssetNetworks.forEach { asset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selectedBlockchain == asset.blockchain) Indigo.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable {
                                    selectedBlockchain = asset.blockchain
                                    recipient = ""
                                    amount = ""
                                    draft = null
                                    localError = null
                                    showNetworkPicker = false
                                }
                                .padding(12.dp),
                        ) {
                            CryptoNetworkIcon(asset.blockchain, 36)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(asset.blockchain.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${asset.balance.displayAmount()} ${asset.symbol}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            if (selectedBlockchain == asset.blockchain) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Success)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNetworkPicker = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    if (showSavedAddressPicker) {
        AlertDialog(
            onDismissRequest = { showSavedAddressPicker = false },
            title = { Text(stringResource(R.string.send_saved_addresses)) },
            text = {
                if (compatibleSavedAddresses.isEmpty()) {
                    Text(
                        stringResource(
                            R.string.send_no_compatible_saved,
                            selectedBlockchain?.displayName ?: stringResource(R.string.send_review_network),
                        ),
                        color = TextSecondary,
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        compatibleSavedAddresses.forEach { saved ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceLight, RoundedCornerShape(15.dp))
                                    .clickable {
                                        recipient = saved.address
                                        amount = ""
                                        draft = null
                                        localError = null
                                        showSavedAddressPicker = false
                                    }
                                    .padding(12.dp),
                            ) {
                                Icon(Icons.Rounded.BookmarkBorder, contentDescription = null, tint = Indigo)
                                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                    Text(saved.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        saved.address.shortAddress(),
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.send_delete_saved_address),
                                    tint = TextTertiary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onRemoveSavedAddress(saved.id) },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedAddressPicker = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }

    if (showSaveAddressDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAddressDialog = false },
            title = { Text(stringResource(R.string.send_save_address)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        recipient.shortAddress(),
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                    OutlinedTextField(
                        value = savedAddressName,
                        onValueChange = {
                            savedAddressName = it.take(50)
                            localError = null
                        },
                        label = { Text(stringResource(R.string.send_name)) },
                        placeholder = { Text(stringResource(R.string.send_name_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                        colors = sendFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    localError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAddressDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            confirmButton = {
                val saveFailedMessage = stringResource(R.string.send_unable_to_save_address)
                TextButton(
                    enabled = savedAddressName.isNotBlank(),
                    onClick = {
                        onAddSavedAddress(savedAddressName, recipient).fold(
                            onSuccess = {
                                showSaveAddressDialog = false
                                savedAddressName = ""
                            },
                            onFailure = { localError = it.message ?: saveFailedMessage },
                        )
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
        )
    }

    draft?.let { review ->
        AlertDialog(
            onDismissRequest = { if (!state.isSending) draft = null },
            title = { Text(stringResource(R.string.send_review_transfer)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    ReviewLine(stringResource(R.string.send_review_asset), "${review.amount} ${review.symbol}")
                    ReviewLine(stringResource(R.string.send_review_network), review.blockchain.displayName)
                    ReviewLine(stringResource(R.string.send_review_recipient), review.to.shortAddress())
                    ReviewLine(
                        stringResource(R.string.send_review_max_fee),
                        "${review.maximumNetworkFee.displayAmount()} ${review.blockchain.symbol}",
                    )
                    if (review is EvmTransferDraft) {
                        review.platformFeeTransfer?.let { fee ->
                            ReviewLine(stringResource(R.string.send_application_fee), "${fee.amount} ${fee.symbol}")
                            ReviewLine(stringResource(R.string.send_total_asset_debit), "${review.totalAssetAmount.stripTrailingZeros().toPlainString()} ${review.symbol}")
                            ReviewLine(stringResource(R.string.send_fee_recipient), fee.to)
                            Text(stringResource(R.string.send_separate_fee_note), style = MaterialTheme.typography.bodySmall)
                        }
                        ReviewLine(
                            stringResource(R.string.send_review_gas_price),
                            "${review.gasPriceWei.toBigDecimal().movePointLeft(9).stripTrailingZeros().toPlainString()} Gwei",
                        )
                    }
                    if (review is BitcoinTransferDraft) {
                        ReviewLine(stringResource(R.string.send_review_fee_rate), "${review.feeRateSatsPerVByte} sat/vB")
                        ReviewLine(stringResource(R.string.send_review_inputs), review.selectedUtxos.size.toString())
                    }
                    if (review is SolanaTransferDraft) {
                        ReviewLine(
                            stringResource(R.string.send_review_blockhash),
                            stringResource(R.string.send_review_blockhash_value, review.lastValidBlockHeight),
                        )
                    }
                    Text(
                        stringResource(R.string.send_fee_note),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(enabled = !state.isSending, onClick = { draft = null }) {
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
                                    onSend(review) { result ->
                                        result.fold(
                                            onSuccess = {
                                                sentTransaction = it
                                                draft = null
                                            },
                                            onFailure = {
                                                localError = it.message ?: transactionFailedMessage
                                                draft = null
                                            },
                                        )
                                    }
                                },
                                onFailure = { localError = it.message ?: authFailedMessage },
                            )
                        }
                    },
                ) { Text(stringResource(if (state.isSending) R.string.send_sending else R.string.send_confirm)) }
            },
        )
    }

    sentTransaction?.let { transaction ->
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.send_submitted), color = Success) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.send_submitted_message, transaction.blockchain.displayName))
                    Text(
                        transaction.hash,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = TextSecondary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { uriHandler.openUri(transaction.explorerUrl) }) {
                    Text(stringResource(R.string.common_view_on_explorer))
                }
            },
            confirmButton = {
                TextButton(onClick = onFinished) { Text(stringResource(R.string.common_done)) }
            },
        )
    }
}

@Composable
private fun SendStepCard(
    stepNumber: Int,
    title: String,
    isComplete: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(22.dp))
            .border(
                1.dp,
                if (isComplete) Success.copy(alpha = 0.16f) else SurfaceBorder,
                RoundedCornerShape(22.dp),
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (isComplete) Success.copy(alpha = 0.16f) else Indigo.copy(alpha = 0.14f),
                        CircleShape,
                    ),
            ) {
                if (isComplete) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(15.dp),
                    )
                } else {
                    Text(stepNumber.toString(), color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 11.dp),
            )
        }
        content()
    }
}

@Composable
private fun NetworkSelectionRow(
    asset: Asset?,
    availableCount: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
    ) {
        if (asset != null) {
            CryptoNetworkIcon(asset.blockchain, 34)
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(asset.blockchain.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${asset.balance.displayAmount()} ${asset.symbol}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).background(Indigo.copy(alpha = 0.14f), CircleShape),
            ) {
                Text("◎", color = Indigo, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(stringResource(R.string.send_select_network), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.send_networks_available, availableCount),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun sendFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Indigo,
    unfocusedBorderColor = SurfaceBorder,
    focusedContainerColor = SurfaceLight,
    unfocusedContainerColor = SurfaceLight,
    errorContainerColor = SurfaceLight,
)

private fun String.isValidAddressFor(blockchain: Blockchain): Boolean = when {
    blockchain == Blockchain.BITCOIN ->
        (lowercase().startsWith("bc1") && length >= 14) ||
            ((startsWith("1") || startsWith("3")) && length in 26..35)
    blockchain == Blockchain.SOLANA ->
        length in 32..44 && all { it in "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz" }
    blockchain.isEvm ->
        length == 42 && startsWith("0x") &&
            drop(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    else -> false
}

private fun String.shortAddress(): String = if (length <= 16) this else "${take(7)}…${takeLast(6)}"

/** Accepts raw addresses plus BIP-21/EIP-681 style URIs ("bitcoin:bc1…?amount=", "ethereum:0x…"). */
private fun String.extractWalletAddress(): String =
    trim().substringBefore('?').substringAfterLast(':').substringBefore('@').trim()

private fun Double.displayAmount(): String = when {
    this == 0.0 -> "0"
    this < 0.000001 -> "<0.000001"
    else -> "%,.8f".format(Locale.US, this).trimEnd('0').trimEnd('.')
}

private fun Double.toEditableAmount(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
