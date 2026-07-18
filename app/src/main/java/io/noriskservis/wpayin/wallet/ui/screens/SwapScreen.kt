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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.ui.components.CryptoTokenIcon
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.InkRaised
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceLight
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Violet
import java.util.Locale

@Composable
fun SwapScreen(state: WalletState) {
    val assets = state.wallet?.assets.orEmpty()
    var payAsset by remember(assets) { mutableStateOf(assets.firstOrNull()) }
    var receiveAsset by remember(assets) { mutableStateOf(assets.drop(1).firstOrNull() ?: assets.firstOrNull()) }
    var amount by remember { mutableStateOf("") }
    var swapMode by remember { mutableStateOf(true) }
    val numericAmount = amount.toDoubleOrNull() ?: 0.0
    val estimated = if (payAsset != null && receiveAsset != null && receiveAsset!!.priceUsd > 0) {
        numericAmount * payAsset!!.priceUsd / receiveAsset!!.priceUsd
    } else {
        0.0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF11162A), Ink, Ink))),
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SwapHeader()
            SwapModePicker(swapMode = swapMode, onModeChange = { swapMode = it })

            if (!swapMode) {
                Box(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 2.dp)) {
                    BridgePreviewCard()
                }
            } else if (assets.isEmpty()) {
                Text(
                    "Asset balances are still loading.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 48.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item {
                        SwapBalanceRow(state = state, asset = payAsset)
                    }
                    item {
                        Box {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SwapAssetCard(
                                    label = "You pay",
                                    asset = payAsset,
                                    amount = amount,
                                    onAmountChange = { value ->
                                        if (value.isEmpty() || value.matches(Regex("\\d*\\.?\\d*"))) amount = value
                                    },
                                    input = true,
                                )
                                SwapAssetCard(
                                    label = "You receive",
                                    asset = receiveAsset,
                                    amount = if (estimated == 0.0) "" else "%.6f".format(Locale.US, estimated),
                                    onAmountChange = {},
                                    input = false,
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(48.dp)
                                    .background(InkRaised, CircleShape)
                                    .border(1.5.dp, Indigo, CircleShape)
                                    .clickable {
                                        val previousPay = payAsset
                                        payAsset = receiveAsset
                                        receiveAsset = previousPay
                                        amount = ""
                                    },
                            ) {
                                Icon(Icons.Rounded.SwapVert, contentDescription = "Reverse assets", tint = Indigo)
                            }
                        }
                    }
                    item {
                        SwapDetailsCard(payAsset, receiveAsset)
                    }
                    item { Spacer(Modifier.height(2.dp)) }
                }

                PrimaryButton(
                    title = "Review Swap",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SwapHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Spacer(Modifier.size(44.dp))
        Text(
            "Swap",
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
            Icon(Icons.Rounded.Tune, contentDescription = "Swap settings", modifier = Modifier.size(20.dp))
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
        SwapMode("Swap", Icons.Rounded.SwapVert, swapMode, Modifier.weight(1f)) { onModeChange(true) }
        SwapMode("Bridge", Icons.Rounded.Link, !swapMode, Modifier.weight(1f)) { onModeChange(false) }
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
private fun SwapBalanceRow(state: WalletState, asset: Asset?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Balance", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                "$${"%,.2f".format(Locale.US, state.wallet?.totalValueUsd ?: 0.0)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(SurfaceLight, RoundedCornerShape(24.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SwapAssetIcon(asset, 28)
            Text(asset?.blockchain?.displayName ?: "Network", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(start = 5.dp).size(18.dp))
        }
    }
}

@Composable
private fun SwapAssetCard(
    label: String,
    asset: Asset?,
    amount: String,
    onAmountChange: (String) -> Unit,
    input: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Indigo.copy(alpha = if (input) 0.11f else 0.07f), Surface, Surface),
                ),
                LargeShape,
            )
            .border(1.dp, if (input) Indigo.copy(alpha = 0.42f) else SurfaceBorder, LargeShape)
            .padding(18.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            if (input) {
                BasicTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Indigo),
                    decorationBox = { inner ->
                        Box {
                            if (amount.isEmpty()) Text("0.0", color = TextTertiary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    amount.ifEmpty { "0.0" },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(SurfaceLight, RoundedCornerShape(24.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                SwapAssetIcon(asset, 30)
                Text(asset?.symbol ?: "—", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 7.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(17.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 11.dp)) {
            Text(
                if (input) "≈ \$0.00" else "Estimated amount",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Balance: ${asset?.balance?.let { "%.4f".format(Locale.US, it) } ?: "—"} ${asset?.symbol.orEmpty()}",
                color = TextSecondary,
                fontSize = 12.sp,
            )
            if (input) {
                Text(
                    "MAX",
                    color = Indigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .border(1.dp, Indigo.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun SwapAssetIcon(asset: Asset?, size: Int) {
    if (asset != null) {
        CryptoTokenIcon(asset = asset, size = size)
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size.dp).background(SurfaceLight, CircleShape),
        ) {
            Text("?", color = TextSecondary, fontSize = (size * 0.45f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SwapDetailsCard(payAsset: Asset?, receiveAsset: Asset?) {
    val rate = if (payAsset != null && receiveAsset != null && receiveAsset.priceUsd > 0) {
        "1 ${payAsset.symbol} ≈ ${"%.6f".format(Locale.US, payAsset.priceUsd / receiveAsset.priceUsd)} ${receiveAsset.symbol}"
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
        QuoteRow("Exchange Rate", rate)
        DetailDivider()
        QuoteRow("Network Fee", "Calculated before signing")
        DetailDivider()
        QuoteRow("Provider", "Not connected yet")
    }
}

@Composable
private fun DetailDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceBorder))
}

@Composable
private fun QuoteRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun BridgePreviewCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Surface, LargeShape)
            .border(1.dp, SurfaceBorder, LargeShape)
            .padding(28.dp),
    ) {
        Icon(Icons.Rounded.Link, contentDescription = null, tint = Indigo, modifier = Modifier.size(42.dp))
        Text("Cross-chain Bridge", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Bridge routing will be enabled after the Android service passes parity and security testing.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
