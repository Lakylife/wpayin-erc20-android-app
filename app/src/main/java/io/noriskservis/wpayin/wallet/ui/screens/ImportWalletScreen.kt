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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.components.SecondaryButton
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary

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
    val wordCount = input.trim().split(Regex("\\s+")).count(String::isNotBlank)
    val canSubmit = if (method == 0) {
        wordCount in setOf(12, 15, 18, 21, 24)
    } else {
        input.trim().removePrefix("0x").let { key ->
            key.length == 64 && key.all { it.digitToIntOrNull(16) != null }
        }
    }

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = stringResource(R.string.import_title), onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        R.string.settings_recovery_phrase,
                        R.string.settings_private_key,
                    ).forEachIndexed { index, title ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    method = index
                                    error = null
                                }
                                .padding(top = 6.dp),
                        ) {
                            Text(
                                stringResource(title),
                                color = if (method == index) Color.White else TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = if (method == index) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (method == index) Indigo else SurfaceBorder),
                            )
                        }
                    }
                }

                Text(
                    stringResource(R.string.import_your_wallet),
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 30.dp),
                )
                Text(
                    stringResource(if (method == 0) R.string.import_phrase_hint else R.string.import_key_hint),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Text(
                    stringResource(
                        if (method == 0) R.string.settings_recovery_phrase else R.string.settings_private_key,
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
                )
                BasicTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(Indigo),
                    visualTransformation = if (method == 0) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    minLines = if (method == 0) 5 else 2,
                    maxLines = if (method == 0) 7 else 3,
                    decorationBox = { field ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface, RoundedCornerShape(16.dp))
                                .border(
                                    1.dp,
                                    if (error == null) SurfaceBorder else MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(16.dp),
                        ) {
                            if (input.isEmpty()) {
                                Text(
                                    stringResource(
                                        if (method == 0) {
                                            R.string.import_phrase_placeholder
                                        } else {
                                            R.string.import_key_placeholder
                                        },
                                    ),
                                    color = TextTertiary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                )
                            }
                            field()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp)) {
                    Text(
                        if (method == 0) {
                            stringResource(R.string.import_word_count, wordCount)
                        } else {
                            stringResource(R.string.import_evm_networks)
                        },
                        color = TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (method == 0) {
                        Text(stringResource(R.string.import_word_count_hint), color = TextTertiary, fontSize = 12.sp)
                    }
                }

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .background(Indigo.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
                        .border(1.dp, Indigo.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = Indigo)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.import_security_notice), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.import_security_message),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                val importFailedMessage = stringResource(R.string.import_invalid)
                PrimaryButton(
                    title = stringResource(if (isBusy) R.string.import_importing else R.string.import_title),
                    enabled = canSubmit && !isBusy,
                    onClick = {
                        isBusy = true
                        val completion: (Boolean) -> Unit = { success ->
                            isBusy = false
                            if (success) onFinished() else error = importFailedMessage
                        }
                        if (method == 0) onImportMnemonic(input, completion)
                        else onImportPrivateKey(input, completion)
                    },
                )
                SecondaryButton(title = stringResource(R.string.common_cancel), enabled = !isBusy, onClick = onBack)
            }
        }
    }
}
