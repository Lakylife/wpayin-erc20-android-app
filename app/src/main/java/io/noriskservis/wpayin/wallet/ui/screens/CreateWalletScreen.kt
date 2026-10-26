package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.MediumShape
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.Warning

@Composable
fun CreateWalletScreen(
    mnemonic: String?,
    onGenerate: ((Result<String>) -> Unit) -> Unit,
    onConfirm: ((Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    var step by remember { mutableIntStateOf(if (mnemonic == null) 0 else 1) }
    var accepted by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(
                title = stringResource(
                    when (step) {
                        0 -> R.string.create_title
                        1 -> R.string.create_recovery_phrase
                        else -> R.string.create_verify_backup
                    },
                ),
                onBack = onBack,
            )

            when (step) {
                0 -> TermsStep(
                    accepted = accepted,
                    onAcceptedChange = { accepted = it },
                    isBusy = isBusy,
                    onContinue = {
                        isBusy = true
                        onGenerate { result ->
                            isBusy = false
                            result.fold(
                                onSuccess = { step = 1 },
                                onFailure = { localError = it.message },
                            )
                        }
                    },
                )
                1 -> BackupStep(
                    mnemonic = mnemonic.orEmpty(),
                    onContinue = { step = 2 },
                )
                else -> {
                    val createFailedMessage = stringResource(R.string.create_failed)
                    VerifyStep(
                        mnemonic = mnemonic.orEmpty(),
                        isBusy = isBusy,
                        error = localError,
                        onConfirm = {
                            isBusy = true
                            localError = null
                            onConfirm { success ->
                                isBusy = false
                                if (success) onFinished() else localError = createFailedMessage
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TermsStep(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    isBusy: Boolean,
    onContinue: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize().padding(22.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 34.dp)
                .background(Indigo.copy(alpha = 0.12f), CircleShape)
                .padding(25.dp),
        ) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = Indigo)
        }
        Text(
            stringResource(R.string.create_you_control),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.create_you_control_message),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, LargeShape)
                .border(1.dp, SurfaceBorder, LargeShape)
                .padding(18.dp),
        ) {
            SafetyLine(stringResource(R.string.create_safety_1))
            SafetyLine(stringResource(R.string.create_safety_2))
            SafetyLine(stringResource(R.string.create_safety_3))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = accepted,
                onCheckedChange = onAcceptedChange,
                colors = CheckboxDefaults.colors(checkedColor = Indigo),
            )
            Text(stringResource(R.string.create_accept))
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            title = stringResource(if (isBusy) R.string.create_generating else R.string.create_generate),
            onClick = onContinue,
            enabled = accepted && !isBusy,
        )
    }
}

@Composable
private fun SafetyLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text("•", color = Indigo, fontWeight = FontWeight.Bold)
        Text(text, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackupStep(mnemonic: String, onContinue: () -> Unit) {
    val words = mnemonic.split(" ").filter(String::isNotBlank)
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Warning.copy(alpha = 0.1f), MediumShape)
                .border(1.dp, Warning.copy(alpha = 0.35f), MediumShape)
                .padding(15.dp),
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Warning)
            Text(
                stringResource(R.string.create_write_down_warning),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(words) { index, word ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, MediumShape)
                        .border(1.dp, SurfaceBorder, MediumShape)
                        .padding(horizontal = 14.dp, vertical = 15.dp),
                ) {
                    Text("${index + 1}", color = Indigo, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        word,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
        PrimaryButton(
            title = stringResource(R.string.create_written_down),
            onClick = onContinue,
            enabled = words.size >= 12,
        )
    }
}

@Composable
private fun VerifyStep(
    mnemonic: String,
    isBusy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
) {
    val words = mnemonic.split(" ").filter(String::isNotBlank)
    val indexes = remember(words) {
        listOf(2, 5, 8).filter { it < words.size }
    }
    var answers by remember(words) { mutableStateOf(indexes.associateWith { "" }) }
    val valid = indexes.isNotEmpty() && indexes.all { index ->
        answers[index]?.trim()?.equals(words[index], ignoreCase = true) == true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(22.dp),
    ) {
        Text(
            stringResource(R.string.create_verify_hint),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
        indexes.forEach { index ->
            OutlinedTextField(
                value = answers[index].orEmpty(),
                onValueChange = { value -> answers = answers + (index to value) },
                label = { Text(stringResource(R.string.create_word_number, index + 1)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                shape = MediumShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            title = stringResource(if (isBusy) R.string.create_securing else R.string.create_title),
            onClick = onConfirm,
            enabled = valid && !isBusy,
        )
    }
}
