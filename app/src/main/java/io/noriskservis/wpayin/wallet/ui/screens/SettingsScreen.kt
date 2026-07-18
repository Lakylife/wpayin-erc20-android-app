package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.ui.theme.Error
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.InkRaised
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary

@Composable
fun SettingsScreen(
    state: WalletState,
    onToggleBalances: (Boolean) -> Unit,
    onLockWallet: () -> Unit,
    onDeleteWallet: () -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item { Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        item { SettingsSectionTitle("Wallet") }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = state.wallet?.name ?: "Wallet",
                    subtitle = "${state.wallet?.accounts?.size ?: 0} active networks",
                )
                SettingsRow(
                    icon = Icons.Rounded.Visibility,
                    title = "Hide Balances",
                    subtitle = "Mask portfolio amounts on screen",
                    trailing = {
                        Switch(
                            checked = state.settings.hideBalances,
                            onCheckedChange = onToggleBalances,
                            colors = SwitchDefaults.colors(checkedTrackColor = Indigo),
                        )
                    },
                )
            }
        }

        item { SettingsSectionTitle("Security") }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Biometric Lock",
                    subtitle = "Required before signing and unlocking",
                )
                SettingsRow(
                    icon = Icons.Rounded.LockClock,
                    title = "Auto-Lock",
                    subtitle = "After 5 minutes in background • tap to lock now",
                    onClick = onLockWallet,
                )
            }
        }

        item { SettingsSectionTitle("Preferences") }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.CurrencyExchange,
                    title = "Currency",
                    subtitle = "$ ${state.settings.currencyCode}",
                )
                SettingsRow(
                    icon = Icons.Rounded.Language,
                    title = "Language",
                    subtitle = "System language",
                )
            }
        }

        item { SettingsSectionTitle("About") }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = "Android version",
                    subtitle = "${BuildConfig.VERSION_NAME} • ${if (BuildConfig.REAL_WALLET_ENABLED) "Wallet Core" else "Preview"}",
                )
            }
        }

        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Remove Wallet",
                    subtitle = "Delete encrypted wallet material from this device",
                    tint = Error,
                    onClick = { showDeleteConfirmation = true },
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Remove this wallet?") },
            text = {
                Text("This deletes the encrypted recovery phrase or private key from this device. Continue only if your offline backup is complete.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteWallet()
                    },
                ) { Text("Remove", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Indigo,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                title,
                color = if (enabled) Color.White else TextTertiary,
                fontSize = 16.sp,
            )
            Text(
                subtitle,
                color = if (enabled) TextSecondary else TextTertiary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (trailing != null) trailing()
        else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
