package io.noriskservis.wpayin.wallet.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.QuintiumNodeClient
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.QuintiumConfig
import io.noriskservis.wpayin.wallet.model.QuintiumNetwork
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.theme.Error
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.InkRaised
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Warning
import kotlinx.coroutines.launch

private sealed interface NodeCheck {
    data object Idle : NodeCheck
    data object Checking : NodeCheck
    data class Ok(val chainId: Long, val block: Long) : NodeCheck
    data class Failed(val message: String) : NodeCheck
}

/** Network, node and explorer of the Quintium chain, like Settings → Quintium on iOS. */
@Composable
fun QuintiumSettingsScreen(
    onBack: () -> Unit,
    onSave: (QuintiumNetwork, String, String, (Result<Unit>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nodeClient = remember { QuintiumNodeClient() }
    var network by remember { mutableStateOf(QuintiumConfig.network) }
    var rpcUrl by remember { mutableStateOf(QuintiumConfig.rpcUrl(QuintiumConfig.network)) }
    var explorerUrl by remember { mutableStateOf(QuintiumConfig.explorerUrl(QuintiumConfig.network)) }
    var check by remember { mutableStateOf<NodeCheck>(NodeCheck.Idle) }
    var saved by remember { mutableStateOf(false) }

    fun selectNetwork(item: QuintiumNetwork) {
        if (item == network) return
        network = item
        rpcUrl = QuintiumConfig.rpcUrl(item)
        explorerUrl = QuintiumConfig.explorerUrl(item)
        check = NodeCheck.Idle
        saved = false
    }

    val invalidUrl = stringResource(R.string.quintium_invalid_url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.quintium_title), onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CryptoNetworkIcon(Blockchain.QUINTIUM, 72)
                    Text(
                        "Quintium (${network.symbol})",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.quintium_intro),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.quintium_network))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Surface, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                ) {
                    listOf(QuintiumNetwork.TESTNET, QuintiumNetwork.MAINNET).forEach { item ->
                        val selected = item == network
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(if (selected) Indigo else Color.Transparent, RoundedCornerShape(11.dp))
                                .clickable { selectNetwork(item) },
                        ) {
                            Text(
                                stringResource(
                                    if (item.isTestnet) R.string.quintium_testnet else R.string.quintium_mainnet,
                                ),
                                color = if (selected) Color.White else TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                if (!network.isLaunched()) {
                    Note(Icons.Rounded.AccessTime, stringResource(R.string.quintium_mainnet_pending), Warning)
                }
            }

            item {
                SectionTitle(stringResource(R.string.quintium_node))
                UrlField(
                    label = stringResource(R.string.quintium_rpc_url),
                    value = rpcUrl,
                    placeholder = "https://rpc.example.com",
                    onValueChange = {
                        rpcUrl = it
                        check = NodeCheck.Idle
                        saved = false
                    },
                )
                Text(
                    stringResource(R.string.quintium_rpc_hint, network.defaultRpcPort),
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                UrlField(
                    label = stringResource(R.string.quintium_explorer_url),
                    value = explorerUrl,
                    placeholder = "https://explorer.example.com",
                    onValueChange = {
                        explorerUrl = it
                        saved = false
                    },
                )
                Text(
                    stringResource(R.string.quintium_explorer_hint),
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .background(Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                        .clickable(enabled = check != NodeCheck.Checking) {
                            val selected = network
                            val url = rpcUrl
                            check = NodeCheck.Checking
                            scope.launch {
                                check = runCatching { nodeClient.check(url, selected) }.fold(
                                    onSuccess = { NodeCheck.Ok(it.chainId, it.blockNumber) },
                                    onFailure = { error ->
                                        NodeCheck.Failed(
                                            when {
                                                error is QuintiumNodeClient.WrongChainException ->
                                                    context.getString(
                                                        R.string.quintium_wrong_chain,
                                                        error.actual,
                                                        error.expected,
                                                    )
                                                !QuintiumConfig.isValidUrl(url) -> invalidUrl
                                                else -> context.getString(
                                                    R.string.quintium_check_failed,
                                                    error.message ?: error.javaClass.simpleName,
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        .padding(vertical = 13.dp),
                ) {
                    if (check == NodeCheck.Checking) {
                        CircularProgressIndicator(color = Indigo, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        stringResource(R.string.quintium_test),
                        color = Indigo,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                when (val state = check) {
                    is NodeCheck.Ok -> Note(
                        Icons.Rounded.CheckCircle,
                        stringResource(R.string.quintium_connected, state.chainId, state.block),
                        Success,
                    )
                    is NodeCheck.Failed -> Note(Icons.Rounded.Warning, state.message, Error)
                    else -> Unit
                }
            }

            item {
                SectionTitle(stringResource(R.string.quintium_parameters))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Surface, RoundedCornerShape(14.dp)),
                ) {
                    ParameterRow(stringResource(R.string.quintium_chain_id), network.chainId.toString())
                    ParameterRow(
                        stringResource(R.string.quintium_currency),
                        stringResource(R.string.quintium_currency_value, network.symbol),
                    )
                    ParameterRow(
                        stringResource(R.string.quintium_block_time),
                        stringResource(R.string.quintium_block_time_value, QuintiumNetwork.BLOCK_TIME_SECONDS),
                    )
                    ParameterRow(stringResource(R.string.quintium_address_path), QuintiumNetwork.DERIVATION_PATH)
                }
                Note(Icons.Rounded.Info, stringResource(R.string.quintium_not_supported), TextSecondary)
            }

            item {
                PrimaryButton(
                    title = stringResource(R.string.quintium_save),
                    onClick = {
                        val rpc = rpcUrl.trim()
                        val explorer = explorerUrl.trim()
                        if ((rpc.isNotEmpty() && !QuintiumConfig.isValidUrl(rpc)) ||
                            (explorer.isNotEmpty() && !QuintiumConfig.isValidUrl(explorer))
                        ) {
                            check = NodeCheck.Failed(invalidUrl)
                            return@PrimaryButton
                        }
                        onSave(network, rpc, explorer) { result ->
                            result.fold(
                                onSuccess = { saved = true },
                                onFailure = { check = NodeCheck.Failed(it.message ?: invalidUrl) },
                            )
                        }
                    },
                )
                if (saved) {
                    Text(
                        stringResource(R.string.quintium_saved),
                        color = Success,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun UrlField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextTertiary) },
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Indigo,
            unfocusedBorderColor = SurfaceBorder,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
private fun ParameterRow(title: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Note(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 10.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Text(
            text,
            color = color,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
