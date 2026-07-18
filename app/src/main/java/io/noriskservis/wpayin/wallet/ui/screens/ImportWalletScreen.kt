package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.noriskservis.wpayin.wallet.ui.components.MediumShape
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary

@Composable
fun ImportWalletScreen(
    onImportMnemonic: (String, (Boolean) -> Unit) -> Unit,
    onImportPrivateKey: (String, (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    var method by remember { mutableIntStateOf(0) }
    var input by remember(method) { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val wordCount = input.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val canSubmit = if (method == 0) wordCount in setOf(12, 15, 18, 21, 24) else {
        input.trim().removePrefix("0x").length == 64
    }

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = "Import Wallet", onBack = onBack)
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize().padding(22.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(22.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(22.dp))
                        .padding(5.dp),
                ) {
                    listOf("Recovery Phrase", "Private Key").forEachIndexed { index, title ->
                        Text(
                            title,
                            color = if (method == index) androidx.compose.ui.graphics.Color.White else TextSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (method == index) Indigo.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(17.dp),
                                )
                                .clickable { method = index }
                                .padding(vertical = 13.dp),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Indigo.copy(alpha = 0.1f), MediumShape)
                        .padding(15.dp),
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Indigo)
                    Text(
                        if (method == 0) {
                            "Enter a BIP-39 phrase. It is encrypted locally and never sent over the network."
                        } else {
                            "Private-key import creates EVM accounts only. Use a recovery phrase for Bitcoin and Solana."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    label = { Text(if (method == 0) "Recovery phrase" else "Private key") },
                    supportingText = {
                        Text(if (method == 0) "$wordCount words" else "64 hexadecimal characters")
                    },
                    visualTransformation = if (method == 0) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    minLines = if (method == 0) 5 else 1,
                    maxLines = if (method == 0) 7 else 1,
                    shape = MediumShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    title = if (isBusy) "Importing…" else "Import Wallet",
                    enabled = canSubmit && !isBusy,
                    onClick = {
                        isBusy = true
                        val completion: (Boolean) -> Unit = { success ->
                            isBusy = false
                            if (success) onFinished() else error = "The wallet data is not valid"
                        }
                        if (method == 0) onImportMnemonic(input, completion)
                        else onImportPrivateKey(input, completion)
                    },
                )
            }
        }
    }
}
