package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.KnownToken
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary

@Composable
fun AddTokenScreen(
    state: WalletState,
    onAdd: (Blockchain, String, (Result<KnownToken>) -> Unit) -> Unit,
    onRemove: (KnownToken) -> Unit,
    onBack: () -> Unit,
) {
    val wallet = requireNotNull(state.wallet)
    val networks = wallet.accounts.map { it.blockchain }.filter(Blockchain::isEvm)
    var selectedNetwork by remember(networks) { mutableStateOf(networks.firstOrNull() ?: Blockchain.ETHEREUM) }
    var contractAddress by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = stringResource(R.string.token_add_title), onBack = onBack)
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.token_network_hint),
                        color = TextSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                    ) {
                        networks.forEach { network ->
                            val selected = network == selectedNetwork
                            Text(
                                network.displayName,
                                color = if (selected) Color.White else TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(if (selected) Indigo.copy(alpha = 0.2f) else Surface, CircleShape)
                                    .border(1.dp, if (selected) Indigo else SurfaceBorder, CircleShape)
                                    .clickable {
                                        selectedNetwork = network
                                        message = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = contractAddress,
                        onValueChange = {
                            contractAddress = it.trim()
                            message = null
                        },
                        label = { Text(stringResource(R.string.token_contract)) },
                        placeholder = { Text("0x…") },
                        supportingText = { Text(stringResource(R.string.token_contract_hint)) },
                        singleLine = true,
                        shape = LargeShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Indigo,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedContainerColor = Surface,
                            unfocusedContainerColor = Surface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), LargeShape)
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f), LargeShape)
                            .padding(16.dp),
                    ) {
                        Text(stringResource(R.string.token_safety), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.token_safety_message),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                message?.let { text ->
                    item { Text(text, color = if (isError) MaterialTheme.colorScheme.error else Success) }
                }

                item {
                    val resources = LocalResources.current
                    PrimaryButton(
                        title = stringResource(if (isLoading) R.string.token_verifying else R.string.token_verify_add),
                        enabled = contractAddress.isNotBlank() && !isLoading,
                        onClick = {
                            isLoading = true
                            message = null
                            onAdd(selectedNetwork, contractAddress) { result ->
                                isLoading = false
                                result.fold(
                                    onSuccess = { token ->
                                        isError = false
                                        message = resources.getString(
                                            R.string.token_added,
                                            token.name,
                                            token.symbol,
                                            token.decimals,
                                        )
                                        contractAddress = ""
                                    },
                                    onFailure = { error ->
                                        isError = true
                                        message = error.message ?: resources.getString(R.string.token_verify_failed)
                                    },
                                )
                            }
                        },
                    )
                }

                if (state.customTokens.isNotEmpty()) {
                    item { Text(stringResource(R.string.token_custom_tokens), style = MaterialTheme.typography.headlineSmall) }
                    items(state.customTokens.size) { index ->
                        val token = state.customTokens[index]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface, LargeShape)
                                .border(1.dp, SurfaceBorder, LargeShape)
                                .padding(16.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${token.symbol} · ${token.blockchain.displayName}", fontWeight = FontWeight.SemiBold)
                                Text(token.name, color = TextSecondary)
                                Text(
                                    token.contractAddress,
                                    color = TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                stringResource(R.string.token_remove),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onRemove(token) }.padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
