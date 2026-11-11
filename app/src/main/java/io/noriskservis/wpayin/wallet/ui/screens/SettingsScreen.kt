package io.noriskservis.wpayin.wallet.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Pentagon
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.data.SecureWalletStore
import io.noriskservis.wpayin.wallet.data.WalletState
import io.noriskservis.wpayin.wallet.model.AppLanguage
import io.noriskservis.wpayin.wallet.model.AppColorTheme
import io.noriskservis.wpayin.wallet.model.AssetListStyle
import io.noriskservis.wpayin.wallet.model.AUTOMATIC_TIME_ZONE_ID
import io.noriskservis.wpayin.wallet.model.AutoLockDuration
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.QuintiumConfig
import io.noriskservis.wpayin.wallet.model.QuintiumNetwork
import io.noriskservis.wpayin.wallet.model.FiatCurrency
import io.noriskservis.wpayin.wallet.model.ExportedPrivateKey
import io.noriskservis.wpayin.wallet.model.WalletKind
import io.noriskservis.wpayin.wallet.ui.components.CryptoNetworkIcon
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.theme.Error
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.InkRaised
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    state: WalletState,
    onLockWallet: () -> Unit,
    onSetAutoLock: (AutoLockDuration) -> Unit = {},
    onDeleteWallet: () -> Unit,
    onNetworkEnabled: (Blockchain, Boolean) -> Unit,
    onSetCurrency: (String) -> Unit = {},
    onSetNotifications: (Boolean) -> Unit = {},
    onSetTimeZone: (String) -> Unit = {},
    onSetColorTheme: (AppColorTheme) -> Unit = {},
    onSetAssetListStyle: (AssetListStyle) -> Unit = {},
    onRevealSecret: ((Result<SecureWalletStore.WalletSecret>) -> Unit) -> Unit = { done ->
        done(Result.failure(IllegalStateException("Backup is unavailable")))
    },
    onRevealPrivateKeys: ((Result<List<ExportedPrivateKey>>) -> Unit) -> Unit = { done ->
        done(Result.failure(IllegalStateException("Private keys are unavailable")))
    },
    onSaveQuintium: (QuintiumNetwork, String, String, (Result<Unit>) -> Unit) -> Unit = { _, _, _, done ->
        done(Result.failure(IllegalStateException("Quintium settings are unavailable")))
    },
    onNestedScreenChanged: (Boolean) -> Unit = {},
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showNetworks by remember { mutableStateOf(false) }
    var showQuintium by remember { mutableStateOf(false) }
    var showCurrencies by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var showHelpCenter by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showWalletConnect by remember { mutableStateOf(false) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showTimeZones by remember { mutableStateOf(false) }
    var showExportCompliance by remember { mutableStateOf(false) }
    var showPrivateKeys by remember { mutableStateOf(false) }
    var privateKeys by remember { mutableStateOf<List<ExportedPrivateKey>?>(null) }
    var revealedSecret by remember { mutableStateOf<SecureWalletStore.WalletSecret?>(null) }
    var revealError by remember { mutableStateOf<String?>(null) }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    val nestedScreenVisible = showNetworks ||
        showQuintium ||
        showCurrencies ||
        showLanguages ||
        showHelpCenter ||
        showAbout ||
        showAppearance ||
        showWalletConnect ||
        showAutoLock ||
        showTimeZones ||
        showExportCompliance ||
        showPrivateKeys ||
        revealedSecret != null
    LaunchedEffect(nestedScreenVisible) {
        onNestedScreenChanged(nestedScreenVisible)
    }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSetNotifications(granted)
        notificationPermissionDenied = !granted
    }

    fun updateNotifications(enabled: Boolean) {
        if (!enabled) {
            onSetNotifications(false)
            notificationPermissionDenied = false
            return
        }
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onSetNotifications(true)
            notificationPermissionDenied = false
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showQuintium) {
        QuintiumSettingsScreen(
            onBack = { showQuintium = false },
            onSave = onSaveQuintium,
        )
        return
    }

    if (showNetworks) {
        ManageNetworksScreen(
            state = state,
            onBack = { showNetworks = false },
            onNetworkEnabled = onNetworkEnabled,
            onOpenQuintium = { showQuintium = true },
        )
        return
    }

    if (showLanguages) {
        LanguageSelectionScreen(onBack = { showLanguages = false })
        return
    }

    if (showHelpCenter) {
        HelpCenterScreen(onBack = { showHelpCenter = false })
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    if (showAppearance) {
        AppearanceScreen(
            selectedColorTheme = state.settings.colorTheme,
            selectedAssetListStyle = state.settings.assetListStyle,
            onSetColorTheme = onSetColorTheme,
            onSetAssetListStyle = onSetAssetListStyle,
            onBack = { showAppearance = false },
        )
        return
    }

    if (showWalletConnect) {
        WalletConnectInfoScreen(onBack = { showWalletConnect = false })
        return
    }

    if (showAutoLock) {
        AutoLockSelectionScreen(
            selected = state.settings.autoLockDuration,
            onSelect = {
                onSetAutoLock(it)
                showAutoLock = false
            },
            onBack = { showAutoLock = false },
        )
        return
    }

    if (showTimeZones) {
        TimeZoneSelectionScreen(
            selectedId = state.settings.timeZoneId,
            onSelect = {
                onSetTimeZone(it)
                showTimeZones = false
            },
            onBack = { showTimeZones = false },
        )
        return
    }

    if (showExportCompliance) {
        ExportWalletComplianceScreen(
            onContinue = {
                revealError = null
                onRevealSecret { result ->
                    result.fold(
                        onSuccess = {
                            showExportCompliance = false
                            revealedSecret = it
                        },
                        onFailure = { revealError = it.message ?: context.getString(R.string.auth_failed) },
                    )
                }
            },
            errorMessage = revealError,
            onBack = {
                revealError = null
                showExportCompliance = false
            },
        )
        return
    }

    if (showPrivateKeys) {
        PrivateKeysScreen(
            keys = privateKeys,
            errorMessage = revealError,
            onReveal = {
                revealError = null
                onRevealPrivateKeys { result ->
                    result.fold(
                        onSuccess = { privateKeys = it },
                        onFailure = { revealError = it.message ?: context.getString(R.string.auth_failed) },
                    )
                }
            },
            onBack = {
                privateKeys = null
                revealError = null
                showPrivateKeys = false
            },
        )
        return
    }

    if (showCurrencies) {
        CurrencySelectionScreen(
            selectedCode = state.settings.currencyCode,
            onSelect = {
                onSetCurrency(it)
                showCurrencies = false
            },
            onBack = { showCurrencies = false },
        )
        return
    }

    revealedSecret?.let { secret ->
        WalletBackupScreen(
            secret = secret,
            onBack = { revealedSecret = null },
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
    ) {
        item { Text(stringResource(R.string.common_settings), fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        item { SettingsSectionTitle(stringResource(R.string.settings_wallet)) }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Key,
                    title = stringResource(R.string.settings_show_private_key),
                    subtitle = stringResource(R.string.settings_show_private_key_subtitle),
                    onClick = {
                        privateKeys = null
                        revealError = null
                        showPrivateKeys = true
                    },
                )
                SettingsRow(
                    icon = Icons.Rounded.FileUpload,
                    title = stringResource(R.string.settings_export_wallet),
                    subtitle = stringResource(R.string.settings_export_wallet_subtitle),
                    onClick = {
                        revealError = null
                        showExportCompliance = true
                    },
                )
                revealError?.takeIf { !showPrivateKeys && !showExportCompliance }?.let { message ->
                    Text(
                        message,
                        color = Error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_security)) }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Fingerprint,
                    title = stringResource(R.string.settings_biometric_lock),
                    subtitle = stringResource(R.string.settings_biometric_subtitle),
                )
                SettingsRow(
                    icon = Icons.Rounded.LockClock,
                    title = stringResource(R.string.settings_auto_lock),
                    subtitle = autoLockDurationLabel(state.settings.autoLockDuration),
                    onClick = { showAutoLock = true },
                )
                SettingsRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.settings_lock_now),
                    subtitle = stringResource(R.string.settings_lock_now_subtitle),
                    onClick = onLockWallet,
                )
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_preferences)) }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.CurrencyExchange,
                    title = stringResource(R.string.settings_currency),
                    subtitle = FiatCurrency.fromCode(state.settings.currencyCode)
                        .let { "${it.symbol} ${it.code} — ${it.displayName}" },
                    onClick = { showCurrencies = true },
                )
                val currentLanguage = AppLanguage.fromTag(
                    AppCompatDelegate.getApplicationLocales().toLanguageTags(),
                )
                SettingsRow(
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = currentLanguage?.let { "${it.flag} ${it.nativeName}" }
                        ?: stringResource(R.string.settings_language_system),
                    onClick = { showLanguages = true },
                )
                SettingsRow(
                    icon = Icons.Rounded.AccessTime,
                    title = stringResource(R.string.settings_time_zone),
                    subtitle = timeZoneSummary(state.settings.timeZoneId),
                    onClick = { showTimeZones = true },
                )
                SettingsRow(
                    icon = Icons.Rounded.Brightness4,
                    title = stringResource(R.string.settings_appearance),
                    subtitle = "${colorThemeLabel(state.settings.colorTheme)} • " +
                        assetListStyleLabel(state.settings.assetListStyle),
                    onClick = { showAppearance = true },
                )
                SettingsRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_subtitle),
                    trailing = {
                        Switch(
                            checked = state.settings.notificationsEnabled,
                            onCheckedChange = ::updateNotifications,
                            colors = SwitchDefaults.colors(checkedTrackColor = Indigo),
                        )
                    },
                )
                if (notificationPermissionDenied) {
                    Text(
                        stringResource(R.string.settings_notifications_denied),
                        color = Error,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_networks)) }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.Hub,
                    title = stringResource(R.string.settings_manage_networks),
                    subtitle = stringResource(
                        R.string.settings_networks_enabled,
                        state.settings.enabledNetworks.count { state.wallet?.account(it) != null },
                    ),
                    onClick = { showNetworks = true },
                )
                SettingsRow(
                    icon = Icons.Rounded.Pentagon,
                    title = stringResource(R.string.quintium_title),
                    subtitle = "${Blockchain.QUINTIUM.symbol} · " + stringResource(R.string.quintium_settings_subtitle),
                    tint = Color(0xFFF2B544),
                    onClick = { showQuintium = true },
                )
                SettingsRow(
                    icon = Icons.Rounded.Link,
                    title = stringResource(R.string.settings_walletconnect),
                    subtitle = stringResource(R.string.settings_walletconnect_subtitle),
                    onClick = { showWalletConnect = true },
                )
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_support)) }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.HelpOutline,
                    title = stringResource(R.string.settings_help_center),
                    subtitle = stringResource(R.string.settings_help_center_subtitle),
                    onClick = { showHelpCenter = true },
                )
                SettingsRow(
                    icon = Icons.Rounded.MailOutline,
                    title = stringResource(R.string.settings_contact_us),
                    subtitle = stringResource(R.string.settings_contact_us_subtitle),
                    onClick = { uriHandler.openUri("mailto:support@wpayin.com") },
                )
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(
                        R.string.settings_about_version,
                        BuildConfig.VERSION_NAME,
                        stringResource(
                            if (BuildConfig.REAL_WALLET_ENABLED) {
                                R.string.settings_build_wallet_core
                            } else {
                                R.string.settings_build_preview
                            },
                        ),
                    ),
                    onClick = { showAbout = true },
                )
            }
        }

        item { SettingsSectionTitle(stringResource(R.string.settings_danger_zone)) }
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = stringResource(R.string.settings_remove_wallet),
                    subtitle = stringResource(R.string.settings_remove_wallet_subtitle),
                    tint = Error,
                    onClick = { showDeleteConfirmation = true },
                )
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.settings_remove_confirm_title)) },
            text = {
                Text(stringResource(R.string.settings_remove_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteWallet()
                    },
                ) { Text(stringResource(R.string.settings_remove), color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ManageNetworksScreen(
    state: WalletState,
    onBack: () -> Unit,
    onNetworkEnabled: (Blockchain, Boolean) -> Unit,
    onOpenQuintium: () -> Unit = {},
) {
    val categories = listOf(
        R.string.settings_base_layer to listOf(Blockchain.ETHEREUM, Blockchain.BITCOIN),
        R.string.quintium_title to listOf(Blockchain.QUINTIUM),
        R.string.settings_evm_networks to listOf(
            Blockchain.ARBITRUM,
            Blockchain.BASE,
            Blockchain.OPTIMISM,
            Blockchain.POLYGON,
            Blockchain.BNB_CHAIN,
            Blockchain.AVALANCHE,
        ),
        R.string.settings_alt_l1 to listOf(Blockchain.SOLANA),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_manage_networks), onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_networks_hint),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            categories.forEach { (title, networks) ->
                item { SettingsSectionTitle(stringResource(title)) }
                item {
                    SettingsCard {
                        networks.forEach { blockchain ->
                            val available = state.wallet?.account(blockchain) != null
                            val enabled = blockchain in state.settings.enabledNetworks && available
                            NetworkSettingsRow(
                                blockchain = blockchain,
                                enabled = enabled,
                                available = available,
                                canDisable = state.settings.enabledNetworks.count {
                                    state.wallet?.account(it) != null
                                } > 1,
                                onToggle = { onNetworkEnabled(blockchain, it) },
                                onClick = if (blockchain == Blockchain.QUINTIUM) onOpenQuintium else null,
                            )
                        }
                    }
                }
            }
            state.errorMessage?.let { message ->
                item { Text(message, color = Error, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun NetworkSettingsRow(
    blockchain: Blockchain,
    enabled: Boolean,
    available: Boolean,
    canDisable: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CryptoNetworkIcon(blockchain, 38)
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(
                blockchain.displayName,
                color = if (available) Color.White else TextTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    !available -> stringResource(R.string.settings_network_unavailable)
                    blockchain == Blockchain.QUINTIUM && !QuintiumConfig.isConfigured ->
                        stringResource(R.string.quintium_no_node)
                    blockchain.chainId != null -> stringResource(R.string.settings_chain_id, blockchain.chainId)
                    else -> blockchain.symbol
                },
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(
            checked = enabled,
            enabled = available && (enabled.not() || canDisable),
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Indigo),
        )
    }
}

@Composable
private fun LanguageSelectionScreen(onBack: () -> Unit) {
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val current = AppLanguage.fromTag(currentTag)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_language), onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_language_hint),
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }
            item {
                LanguageRow(
                    flag = "🌐",
                    name = stringResource(R.string.settings_language_system),
                    selected = current == null,
                ) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    onBack()
                }
            }
            items(AppLanguage.entries) { language ->
                LanguageRow(
                    flag = language.flag,
                    name = language.nativeName,
                    selected = current == language,
                ) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(language.tag),
                    )
                    onBack()
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    flag: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(flag, fontSize = 20.sp)
        Text(
            name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.settings_selected),
                tint = Indigo,
            )
        }
    }
}

@Composable
private fun CurrencySelectionScreen(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_currency), onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_currency_hint),
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            }
            items(FiatCurrency.entries) { currency ->
                val isSelected = currency.code.equals(selectedCode, ignoreCase = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(18.dp))
                        .clickable { onSelect(currency.code) }
                        .padding(16.dp),
                ) {
                    Text(
                        currency.symbol,
                        color = Indigo,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(currency.code, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            currency.displayName,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.settings_selected),
                            tint = Indigo,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoLockSelectionScreen(
    selected: AutoLockDuration,
    onSelect: (AutoLockDuration) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_auto_lock), onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_auto_lock_hint),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            items(AutoLockDuration.entries) { duration ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(18.dp))
                        .clickable { onSelect(duration) }
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.LockClock,
                        contentDescription = null,
                        tint = Indigo,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        autoLockDurationLabel(duration),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 14.dp).weight(1f),
                    )
                    if (selected == duration) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.settings_selected),
                            tint = Indigo,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun autoLockDurationLabel(duration: AutoLockDuration): String =
    stringResource(
        when (duration) {
            AutoLockDuration.IMMEDIATELY -> R.string.settings_auto_lock_immediately
            AutoLockDuration.AFTER_1_MINUTE -> R.string.settings_auto_lock_1_minute
            AutoLockDuration.AFTER_5_MINUTES -> R.string.settings_auto_lock_5_minutes
            AutoLockDuration.AFTER_15_MINUTES -> R.string.settings_auto_lock_15_minutes
            AutoLockDuration.AFTER_1_HOUR -> R.string.settings_auto_lock_1_hour
            AutoLockDuration.NEVER -> R.string.settings_auto_lock_never
        },
    )

@Composable
private fun TimeZoneSelectionScreen(
    selectedId: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val systemId = ZoneId.systemDefault().id
    val suggested = remember(systemId) { listOf(systemId, "Europe/Prague").distinct() }
    val allIds = remember { ZoneId.getAvailableZoneIds().sortedBy(::timeZoneCity) }
    val displayed = remember(query, allIds) {
        val normalized = query.trim()
        if (normalized.isEmpty()) allIds.filterNot(suggested::contains) else allIds.filter {
            it.contains(normalized, ignoreCase = true) ||
                timeZoneCity(it).contains(normalized, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_time_zone), onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.settings_time_zone_search)) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Indigo,
                unfocusedBorderColor = SurfaceBorder,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (query.isBlank()) {
                item { SettingsSectionTitle(stringResource(R.string.settings_time_zone_automatic)) }
                item {
                    TimeZoneRow(
                        title = stringResource(R.string.settings_use_android_time_zone),
                        subtitle = "${timeZoneCity(systemId)} • ${timeZoneOffset(systemId)}",
                        selected = selectedId == AUTOMATIC_TIME_ZONE_ID,
                        icon = Icons.Rounded.LocationOn,
                    ) { onSelect(AUTOMATIC_TIME_ZONE_ID) }
                }
                item { SettingsSectionTitle(stringResource(R.string.settings_time_zone_suggested)) }
                items(suggested, key = { "suggested:$it" }) { id ->
                    TimeZoneRow(
                        title = timeZoneCity(id),
                        subtitle = "$id • ${timeZoneOffset(id)}",
                        selected = selectedId == id,
                    ) { onSelect(id) }
                }
                item { SettingsSectionTitle(stringResource(R.string.settings_time_zone_all)) }
            } else {
                item { SettingsSectionTitle(stringResource(R.string.settings_time_zone_results)) }
            }
            items(displayed, key = { "zone:$it" }) { id ->
                TimeZoneRow(
                    title = timeZoneCity(id),
                    subtitle = "$id • ${timeZoneOffset(id)}",
                    selected = selectedId == id,
                ) { onSelect(id) }
            }
        }
    }
}

@Composable
private fun TimeZoneRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: ImageVector = Icons.Rounded.AccessTime,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) Indigo.copy(alpha = 0.5f) else SurfaceBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Indigo, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Indigo)
    }
}

@Composable
private fun PrivateKeysScreen(
    keys: List<ExportedPrivateKey>?,
    errorMessage: String?,
    onReveal: () -> Unit,
    onBack: () -> Unit,
) {
    var accepted by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_show_private_key), onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (keys == null) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Error,
                            modifier = Modifier.size(58.dp),
                        )
                        Text(
                            stringResource(R.string.private_key_legal_notice),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            stringResource(R.string.private_key_warning),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                item {
                    AcknowledgementRow(
                        checked = accepted,
                        text = stringResource(R.string.private_key_acknowledgement),
                        onClick = { accepted = !accepted },
                    )
                }
                item {
                    SettingsPrimaryAction(
                        title = stringResource(R.string.private_key_reveal),
                        enabled = accepted,
                        onClick = onReveal,
                    )
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.private_key_your_keys),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(keys, key = ExportedPrivateKey::blockchain) { key ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(18.dp))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CryptoNetworkIcon(key.blockchain, 32)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(key.blockchain.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(key.address, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                        Text(
                            key.privateKey,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .background(Ink, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                        )
                        Text(
                            stringResource(R.string.backup_copy),
                            color = Indigo,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clipboard.setText(AnnotatedString(key.privateKey)) }
                                .padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                }
            }
            errorMessage?.let { message -> item { Text(message, color = Error, fontSize = 13.sp) } }
        }
    }
}

@Composable
private fun ExportWalletComplianceScreen(
    onContinue: () -> Unit,
    errorMessage: String?,
    onBack: () -> Unit,
) {
    var acceptsResponsibility by remember { mutableStateOf(false) }
    var acceptsNoRecovery by remember { mutableStateOf(false) }
    val isEuOrEeaRegion = remember {
        Locale.getDefault().country.uppercase(Locale.ROOT) in EU_AND_EEA_COUNTRY_CODES
    }
    val canContinue = acceptsResponsibility && acceptsNoRecovery
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_export_wallet), onBack = onBack)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(stringResource(R.string.export_wallet_notice), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.export_wallet_notice_subtitle),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                ExportNoticeBlock(
                    stringResource(R.string.export_wallet_responsibility_title),
                    stringResource(R.string.export_wallet_responsibility_message),
                )
            }
            item {
                ExportNoticeBlock(
                    stringResource(R.string.export_wallet_no_recovery_title),
                    stringResource(R.string.export_wallet_no_recovery_message),
                )
            }
            if (isEuOrEeaRegion) {
                item {
                    ExportNoticeBlock(
                        stringResource(R.string.export_wallet_mica_title),
                        stringResource(R.string.export_wallet_mica_message),
                    )
                }
            }
            item {
                AcknowledgementRow(
                    checked = acceptsResponsibility,
                    text = stringResource(R.string.export_wallet_acknowledge_control),
                    onClick = { acceptsResponsibility = !acceptsResponsibility },
                )
            }
            item {
                AcknowledgementRow(
                    checked = acceptsNoRecovery,
                    text = stringResource(R.string.export_wallet_acknowledge_recovery),
                    onClick = { acceptsNoRecovery = !acceptsNoRecovery },
                )
            }
            item {
                SettingsPrimaryAction(
                    title = stringResource(R.string.export_wallet_continue),
                    enabled = canContinue,
                    onClick = onContinue,
                )
            }
            errorMessage?.let { message -> item { Text(message, color = Error, fontSize = 13.sp) } }
        }
    }
}

private val EU_AND_EEA_COUNTRY_CODES = setOf(
    "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
    "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK",
    "SI", "ES", "SE", "IS", "LI", "NO",
)

@Composable
private fun ExportNoticeBlock(title: String, message: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(Error.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
            .border(1.dp, Error.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(15.dp),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Error, modifier = Modifier.size(21.dp))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun AcknowledgementRow(checked: Boolean, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp))
            .border(1.dp, if (checked) Indigo.copy(alpha = 0.45f) else SurfaceBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (checked) Indigo else TextSecondary,
        )
        Text(text, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
    }
}

@Composable
private fun SettingsPrimaryAction(title: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        title,
        color = if (enabled) Color.White else TextTertiary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) Indigo else Surface, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
private fun timeZoneSummary(timeZoneId: String): String {
    val resolved = if (timeZoneId == AUTOMATIC_TIME_ZONE_ID) ZoneId.systemDefault().id else timeZoneId
    val summary = "${timeZoneCity(resolved)} (${timeZoneOffset(resolved)})"
    return if (timeZoneId == AUTOMATIC_TIME_ZONE_ID) {
        "${stringResource(R.string.settings_time_zone_automatic)} • $summary"
    } else summary
}

private fun timeZoneCity(id: String): String = id.substringAfterLast('/').replace('_', ' ')

private fun timeZoneOffset(id: String): String = runCatching {
    val offset = DateTimeFormatter.ofPattern("XXX").format(Instant.now().atZone(ZoneId.of(id)))
    if (offset == "Z") "GMT+0" else "GMT$offset"
}.getOrDefault("")

@Composable
private fun colorThemeLabel(theme: AppColorTheme): String = stringResource(
    when (theme) {
        AppColorTheme.INDIGO -> R.string.appearance_indigo
        AppColorTheme.EMERALD -> R.string.appearance_emerald
        AppColorTheme.AMBER -> R.string.appearance_amber
        AppColorTheme.ROSE -> R.string.appearance_rose
        AppColorTheme.SKY -> R.string.appearance_sky
    },
)

@Composable
private fun assetListStyleLabel(style: AssetListStyle): String = stringResource(
    when (style) {
        AssetListStyle.CARDS -> R.string.appearance_cards
        AssetListStyle.COMPACT -> R.string.appearance_compact
    },
)

@Composable
private fun WalletBackupScreen(
    secret: SecureWalletStore.WalletSecret,
    onBack: () -> Unit,
) {
    val isMnemonic = secret.kind == WalletKind.MNEMONIC
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkRaised, Ink, Ink)))
            .statusBarsPadding(),
    ) {
        ScreenHeader(
            title = stringResource(if (isMnemonic) R.string.settings_recovery_phrase else R.string.settings_private_key),
            onBack = onBack,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Text(
                    stringResource(if (isMnemonic) R.string.backup_phrase_warning else R.string.backup_key_warning),
                    color = Error,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Error.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                )
            }

            if (isMnemonic) {
                val words = secret.value.trim().split(Regex("\\s+"))
                items(words.withIndex().chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (index, word) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Surface, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text("${index + 1}.", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    word,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                item {
                    Text(
                        secret.value,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    )
                }
            }

            item {
                Text(
                    stringResource(if (copied) R.string.backup_copied else R.string.backup_copy),
                    color = if (copied) Error else Indigo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, RoundedCornerShape(16.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(secret.value))
                            copied = true
                        }
                        .padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(18.dp)),
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
            modifier = Modifier.size(21.dp),
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, color = if (enabled) Color.White else TextTertiary, fontSize = 15.sp)
            Text(
                subtitle,
                color = if (enabled) TextSecondary else TextTertiary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (trailing != null) trailing()
        else if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
