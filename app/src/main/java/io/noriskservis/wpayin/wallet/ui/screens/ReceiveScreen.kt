package io.noriskservis.wpayin.wallet.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.WalletAccount
import io.noriskservis.wpayin.wallet.model.WalletSnapshot
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.components.SecondaryButton
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.components.WpayinLogo
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceLight
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Warning
import java.util.Locale

@Composable
fun ReceiveScreen(
    wallet: WalletSnapshot,
    initialAssetId: String? = null,
    onBack: () -> Unit,
) {
    val availableAssets = wallet.assets
        .filter { asset -> wallet.account(asset.blockchain) != null }
        .sortedWith(compareBy({ it.symbol.uppercase() }, { it.blockchain.ordinal }))
    val symbols = availableAssets.map { it.symbol.uppercase() }.distinct()
    val initialAsset = availableAssets.firstOrNull { it.id == initialAssetId }
    var selectedSymbol by remember(availableAssets, initialAssetId) {
        mutableStateOf(initialAsset?.symbol?.uppercase())
    }
    var selectedBlockchain by remember(availableAssets, initialAssetId) {
        mutableStateOf(initialAsset?.blockchain)
    }
    var showDetails by remember { mutableStateOf(false) }
    var showAssetPicker by remember { mutableStateOf(false) }
    var showNetworkPicker by remember { mutableStateOf(false) }
    var addressCopied by remember { mutableStateOf(false) }
    val selectedNetworks = availableAssets.filter { it.symbol.equals(selectedSymbol, ignoreCase = true) }
    val selectedAsset = selectedNetworks.firstOrNull { it.blockchain == selectedBlockchain }
    val account = selectedAsset?.let { wallet.account(it.blockchain) }
    val context = LocalContext.current
    val resources = LocalResources.current

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(
                title = stringResource(if (showDetails) R.string.receive_crypto else R.string.receive_deposit),
                onBack = {
                    if (showDetails) {
                        showDetails = false
                        addressCopied = false
                    } else {
                        onBack()
                    }
                },
            )

            if (showDetails && selectedAsset != null && account != null) {
                ReceiveDetails(
                    asset = selectedAsset,
                    account = account,
                    addressCopied = addressCopied,
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(resources.getString(R.string.receive_address), account.address),
                        )
                        addressCopied = true
                    },
                    onChangeNetwork = { showNetworkPicker = true },
                    onRequestPayment = {
                        shareReceiveDetails(context, resources, selectedAsset, account, paymentRequest = true)
                    },
                    onShare = {
                        shareReceiveDetails(context, resources, selectedAsset, account, paymentRequest = false)
                    },
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Indigo.copy(alpha = 0.14f), CircleShape)
                                    .border(1.dp, Indigo.copy(alpha = 0.18f), CircleShape),
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowDownward,
                                    contentDescription = null,
                                    tint = Indigo,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Text(
                                stringResource(R.string.receive_crypto),
                                fontSize = 24.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.receive_select_asset_network),
                                color = TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }

                    item {
                        ReceiveSelectionCard(
                            title = stringResource(R.string.send_select_asset),
                            isComplete = selectedSymbol != null,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clickable { showAssetPicker = true },
                            ) {
                                if (selectedAsset != null) {
                                    CryptoTokenIcon(asset = selectedAsset, size = 38)
                                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                                        Text(
                                            selectedAsset.symbol.uppercase(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            "${selectedNetworks.sumOf(Asset::balance).displayAmount()} ${selectedAsset.symbol}",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(Indigo.copy(alpha = 0.14f), CircleShape),
                                    ) {
                                        Text("$", color = Indigo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                                    )
                                }
                            }
                        }
                    }

                    if (selectedSymbol != null) {
                        item {
                            ReceiveSelectionCard(
                                title = stringResource(R.string.send_select_network),
                                isComplete = selectedAsset != null,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clickable { showNetworkPicker = true },
                                ) {
                                    if (selectedAsset != null) {
                                        CryptoNetworkIcon(selectedAsset.blockchain, 36)
                                        Column(Modifier.padding(start = 13.dp).weight(1f)) {
                                            Text(
                                                selectedAsset.blockchain.displayName,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                selectedAsset.blockchain.symbol,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 3.dp),
                                            )
                                        }
                                        Icon(
                                            Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = TextTertiary,
                                        )
                                    } else {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Indigo.copy(alpha = 0.14f), CircleShape),
                                        ) {
                                            Text("◎", color = Indigo, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Column(Modifier.padding(start = 13.dp).weight(1f)) {
                                            Text(
                                                stringResource(R.string.send_select_network),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                stringResource(R.string.send_networks_available, selectedNetworks.size),
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                            )
                                        }
                                        Icon(
                                            Icons.Rounded.ArrowForward,
                                            contentDescription = null,
                                            tint = TextTertiary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        PrimaryButton(
                            title = stringResource(R.string.receive_continue),
                            enabled = selectedAsset != null && account != null,
                            onClick = { showDetails = true },
                        )
                    }
                }
            }
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
                    symbols.forEach { symbol ->
                        val candidates = availableAssets.filter { it.symbol.equals(symbol, ignoreCase = true) }
                        val representative = candidates.firstOrNull() ?: return@forEach
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
                                    selectedBlockchain = if (candidates.size == 1) candidates.first().blockchain else null
                                    showDetails = false
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
                                "${candidates.sumOf(Asset::balance).displayAmount()} $symbol",
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
                    selectedNetworks.forEach { asset ->
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
                                    showDetails = false
                                    showNetworkPicker = false
                                }
                                .padding(12.dp),
                        ) {
                            CryptoNetworkIcon(asset.blockchain, 36)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(asset.blockchain.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    asset.blockchain.symbol,
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
}

@Composable
private fun ReceiveDetails(
    asset: Asset,
    account: WalletAccount,
    addressCopied: Boolean,
    onCopy: () -> Unit,
    onChangeNetwork: () -> Unit,
    onRequestPayment: () -> Unit,
    onShare: () -> Unit,
) {
    val qrBitmap = remember(account.address) { generateQrCode(account.address, 720) }
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CryptoTokenIcon(asset = asset, size = 64)
                Text(asset.symbol.uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Surface, CircleShape)
                        .border(1.dp, SurfaceBorder, CircleShape)
                        .clickable(onClick = onChangeNetwork)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    CryptoNetworkIcon(asset.blockchain, 18)
                    Text(
                        asset.blockchain.displayName,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    stringResource(R.string.receive_send_to_address, asset.symbol),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(248.dp)
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .padding(19.dp),
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.receive_qr_content_description,
                            account.blockchain.displayName,
                        ),
                        modifier = Modifier.size(210.dp),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White, RoundedCornerShape(13.dp)),
                    ) {
                        WpayinLogo(size = 40, tint = Color.Black)
                    }
                }
            }
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface, RoundedCornerShape(20.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Text(stringResource(R.string.receive_address), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.address,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .background(Indigo.copy(alpha = 0.1f), CircleShape)
                            .clickable(onClick = onCopy),
                    ) {
                        Icon(
                            if (addressCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.receive_copy_address),
                            tint = if (addressCopied) Success else Indigo,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Warning.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .border(1.dp, Warning.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Text("⚠", color = Warning, fontSize = 19.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.receive_important), color = Warning, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.receive_warning, asset.symbol, asset.blockchain.displayName),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        item {
            PrimaryButton(title = stringResource(R.string.receive_request_payment), onClick = onRequestPayment)
        }
        item {
            SecondaryButton(title = stringResource(R.string.receive_share_address), onClick = onShare)
        }
    }
}

@Composable
private fun ReceiveSelectionCard(
    title: String,
    isComplete: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(22.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (isComplete) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        content()
    }
}

private fun shareReceiveDetails(
    context: Context,
    resources: Resources,
    asset: Asset,
    account: WalletAccount,
    paymentRequest: Boolean,
) {
    val prefix = if (paymentRequest) {
        resources.getString(R.string.receive_payment_request)
    } else {
        "${asset.symbol} • ${asset.blockchain.displayName}"
    }
    val message = "$prefix\n${resources.getString(R.string.receive_address)}: ${account.address}\n\n" +
        resources.getString(R.string.receive_warning, asset.symbol, asset.blockchain.displayName)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            },
            resources.getString(
                if (paymentRequest) R.string.receive_request_payment else R.string.receive_share,
            ),
        ),
    )
}

private fun generateQrCode(content: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 0),
    )
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()

private fun Double.displayAmount(): String = when {
    this == 0.0 -> "0"
    this < 0.000001 -> "<0.000001"
    else -> "%,.8f".format(Locale.US, this).trimEnd('0').trimEnd('.')
}
