package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.model.AppColorTheme
import io.noriskservis.wpayin.wallet.model.AssetListStyle
import io.noriskservis.wpayin.wallet.ui.components.LargeShape
import io.noriskservis.wpayin.wallet.ui.components.ScreenHeader
import io.noriskservis.wpayin.wallet.ui.components.WpayinBackground
import io.noriskservis.wpayin.wallet.ui.theme.Error
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Violet
import io.noriskservis.wpayin.wallet.ui.theme.Warning
import io.noriskservis.wpayin.wallet.ui.theme.accentColor
import io.noriskservis.wpayin.wallet.ui.theme.primaryColor

private data class HelpArticle(
    val title: String,
    val preview: String,
    val content: String,
    val steps: List<String> = emptyList(),
    val related: List<String> = emptyList(),
)

private data class HelpCategory(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val articles: List<HelpArticle>,
)

@Composable
private fun helpCategories(): List<HelpCategory> = listOf(
    HelpCategory(
        title = stringResource(R.string.help_getting_started),
        icon = Icons.Rounded.PlayCircle,
        color = Indigo,
        articles = listOf(
            helpArticle(
                "What is a Crypto Wallet?",
                "Learn the basics of cryptocurrency wallets",
                "A crypto wallet stores the private keys that prove ownership of your digital assets. Wpayin is non-custodial: only you can access the wallet and Wpayin never receives your recovery phrase or private keys.",
                related = listOf("How to Create a New Wallet", "What is a Seed Phrase?"),
            ),
            helpArticle(
                "How to Create a New Wallet",
                "Set up your first crypto wallet in minutes",
                "A new wallet is generated locally on this device. The recovery phrase is the only backup.",
                listOf(
                    "Tap Create New Wallet on the welcome screen",
                    "Write the recovery phrase on paper and keep it offline",
                    "Verify the phrase in the requested order",
                    "Enable device authentication",
                    "Refresh the wallet to load real blockchain balances",
                ),
                listOf("What is a Seed Phrase?", "How to Secure Your Wallet"),
            ),
            helpArticle(
                "What is a Seed Phrase?",
                "Understanding your recovery phrase",
                "The recovery phrase restores the entire wallet. Never share it, never enter it on a website and never save a screenshot. Anyone who knows it can control all funds.",
                related = listOf("How to Secure Your Wallet", "What if I Lose My Seed Phrase?"),
            ),
            helpArticle(
                "How to Import an Existing Wallet",
                "Restore a wallet using a recovery phrase or private key",
                "Import happens locally. Wpayin validates the secret on this device, derives the addresses and then loads balances directly from the supported networks.",
                listOf(
                    "Tap Import Existing Wallet",
                    "Choose recovery phrase or private key",
                    "Enter the secret only inside the Wpayin app",
                    "Verify the derived addresses",
                    "Refresh to load real balances and transaction history",
                ),
            ),
            helpArticle(
                "Managing Assets and Custom Tokens",
                "Add a token by contract address or hide it from Your Assets",
                "Use the plus button in All Assets to add a verified token contract. Removing a token only hides it from the list; funds remain on the blockchain.",
            ),
        ),
    ),
    HelpCategory(
        title = stringResource(R.string.help_wallet_security),
        icon = Icons.Rounded.Shield,
        color = Success,
        articles = listOf(
            helpArticle(
                "How to Secure Your Wallet",
                "Best practices for keeping your crypto safe",
                "Keep the recovery phrase offline, use a strong device passcode, enable biometric protection, verify every address and avoid public or untrusted devices.",
            ),
            helpArticle(
                "What if I Lose My Seed Phrase?",
                "Understand the risks and consequences",
                "If both the recovery phrase and access to the device are lost, nobody can restore a non-custodial wallet. Wpayin has no copy and there is no password reset.",
            ),
            helpArticle(
                "How to Avoid Scams",
                "Recognize phishing and fake support",
                "Real support never asks for a recovery phrase, private key, remote device access or a transfer to verify the wallet. Reject unexpected signing requests.",
            ),
            helpArticle(
                "Connecting to dApps with WalletConnect",
                "Review requests and manage active sessions",
                "WalletConnect does not reveal private keys. Connection alone does not authorize spending; every signature or transaction must be reviewed and approved separately.",
            ),
            helpArticle(
                "How Biometrics Protect Transactions",
                "Require device verification before signing",
                "When biometric protection is enabled, Wpayin requests fresh authentication before signing Send, Swap, Bridge, P2P and WalletConnect transactions.",
            ),
        ),
    ),
    HelpCategory(
        title = stringResource(R.string.help_sending),
        icon = Icons.AutoMirrored.Rounded.Send,
        color = Violet,
        articles = listOf(
            helpArticle(
                "How to Send Crypto",
                "Step-by-step guide to sending assets",
                "Blockchain transfers are irreversible. Choose the asset and network first, then verify the full destination, amount and maximum network fee before signing.",
                listOf(
                    "Choose the asset",
                    "Choose the exact network",
                    "Enter or scan the recipient address",
                    "Enter the amount",
                    "Review the address, network and maximum fee",
                    "Authenticate and wait for confirmation",
                ),
            ),
            helpArticle(
                "How to Receive Crypto",
                "Share the correct address and network",
                "Choose the asset and network, then share the displayed address or QR code. The sender must use the exact same network.",
            ),
            helpArticle(
                "What are Gas Fees?",
                "Understand blockchain transaction costs",
                "Network fees are paid to blockchain validators, not to Wpayin. Token transfers usually require the network's native asset for gas.",
            ),
            helpArticle(
                "How to Verify Transaction Addresses",
                "Avoid sending to the wrong address",
                "Compare the first and last characters, confirm the address format and network, and send a small test amount when using a new recipient.",
            ),
            helpArticle(
                "How to Request a Payment",
                "Create a QR code or link with asset, network and amount",
                "A payment request prepares shareable details but never moves funds. The sender still reviews and signs the transaction in their own wallet.",
            ),
            helpArticle(
                "Swaps, Bridges and Network Fees",
                "Understand same-network swaps, cross-network bridges and MAX",
                "Swap exchanges assets on a compatible network. Bridge moves value between networks. Select the networks first and then choose compatible assets.",
            ),
            helpArticle(
                "How P2P Exchange Works",
                "Trade wallet-to-wallet without Wpayin holding funds",
                "P2P offers settle on-chain. Review both assets, amounts, network, expiry and counterparty before approving the atomic settlement.",
            ),
        ),
    ),
    HelpCategory(
        title = stringResource(R.string.help_blockchain_networks),
        icon = Icons.Rounded.Hub,
        color = Warning,
        articles = listOf(
            helpArticle(
                "What are Blockchain Networks?",
                "Understand separate blockchains and their native assets",
                "Each blockchain has its own balances, fees and transaction history. Assets on one network cannot be sent directly to another without a supported bridge.",
            ),
            helpArticle(
                "How to Enable or Disable Networks",
                "Choose which blockchains appear in the wallet",
                "Open Settings and Manage Networks. Disabled networks are hidden but no on-chain funds are deleted.",
            ),
            helpArticle(
                "What are Layer 2 Networks?",
                "Understand Ethereum scaling networks",
                "Layer 2 networks process transactions more cheaply and settle results to Ethereum. The address may match Ethereum, but the balances remain separate per network.",
            ),
        ),
    ),
    HelpCategory(
        title = stringResource(R.string.help_nfts),
        icon = Icons.Rounded.Collections,
        color = Color(0xFFEC4899),
        articles = listOf(
            helpArticle(
                "What are NFTs?",
                "Understand non-fungible tokens",
                "NFTs are unique blockchain assets. Ownership is recorded on-chain while media and metadata may be hosted separately.",
            ),
            helpArticle(
                "How to Send NFTs",
                "Transfer a collectible to another wallet",
                "Verify the recipient and network exactly, review the network fee and authenticate the transfer.",
            ),
            helpArticle(
                "How NFT Spam Protection Works",
                "Review suspicious collectibles safely",
                "Unknown NFTs can contain malicious claim links. Do not connect or sign to claim unexpected rewards.",
            ),
            helpArticle(
                "Why Can't I See My NFTs?",
                "Troubleshoot missing collectibles",
                "Check the network, refresh the wallet and verify ownership on a blockchain explorer. Display issues never change on-chain ownership.",
            ),
        ),
    ),
    HelpCategory(
        title = stringResource(R.string.help_troubleshooting),
        icon = Icons.Rounded.Build,
        color = Error,
        articles = listOf(
            helpArticle(
                "Why Can't I See My Tokens?",
                "Troubleshoot missing balances",
                "Enable the correct network, refresh balances, verify the wallet address and add a custom token only using its official contract.",
            ),
            helpArticle(
                "Transaction is Stuck or Pending",
                "Understand delayed confirmation",
                "Check the transaction on the relevant explorer. Network congestion and low fees can delay confirmation; do not blindly send a duplicate.",
            ),
            helpArticle(
                "What if I Send to the Wrong Address?",
                "Understand irreversible transactions",
                "Confirmed blockchain transfers cannot be reversed by Wpayin. Prevention through address and network verification is essential.",
            ),
            helpArticle(
                "How to Contact Support",
                "Get help without exposing wallet secrets",
                "Describe the issue and share only public addresses or transaction hashes. Never share the recovery phrase or private key with support.",
            ),
        ),
    ),
)

private fun helpArticle(
    title: String,
    preview: String,
    content: String,
    steps: List<String> = emptyList(),
    related: List<String> = emptyList(),
) = HelpArticle(title, preview, content, steps, related)

@Composable
fun HelpCenterScreen(onBack: () -> Unit) {
    val categories = helpCategories()
    val allArticles = categories.flatMap(HelpCategory::articles)
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<HelpCategory?>(null) }
    var selectedArticle by remember { mutableStateOf<HelpArticle?>(null) }
    var articleHistory by remember { mutableStateOf<List<HelpArticle>>(emptyList()) }

    when {
        selectedArticle != null -> HelpArticleDetail(
            article = requireNotNull(selectedArticle),
            onRelatedArticle = { relatedTitle ->
                allArticles.firstOrNull { it.title == relatedTitle }?.let { relatedArticle ->
                    articleHistory = articleHistory + requireNotNull(selectedArticle)
                    selectedArticle = relatedArticle
                }
            },
            onBack = {
                if (articleHistory.isNotEmpty()) {
                    selectedArticle = articleHistory.last()
                    articleHistory = articleHistory.dropLast(1)
                } else {
                    selectedArticle = null
                }
            },
        )
        selectedCategory != null -> HelpCategoryDetail(
            category = requireNotNull(selectedCategory),
            onArticle = {
                articleHistory = emptyList()
                selectedArticle = it
            },
            onBack = { selectedCategory = null },
        )
        else -> HelpCenterRoot(
            categories = categories,
            query = query,
            onQuery = { query = it },
            onCategory = { selectedCategory = it },
            onArticle = {
                articleHistory = emptyList()
                selectedArticle = it
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun HelpCenterRoot(
    categories: List<HelpCategory>,
    query: String,
    onQuery: (String) -> Unit,
    onCategory: (HelpCategory) -> Unit,
    onArticle: (HelpArticle) -> Unit,
    onBack: () -> Unit,
) {
    val results = categories.flatMap(HelpCategory::articles).filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.preview.contains(query, ignoreCase = true) ||
            it.content.contains(query, ignoreCase = true)
    }
    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ModalLargeHeader(
                title = stringResource(R.string.settings_help_center),
                onClose = onBack,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.help_search)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (query.isBlank()) 16.dp else 12.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (query.isBlank()) {
                    items(categories, key = HelpCategory::title) { category ->
                        HelpCategoryCard(category = category, onClick = { onCategory(category) })
                    }
                } else if (results.isEmpty()) {
                    item { HelpNoResults() }
                } else {
                    items(results, key = HelpArticle::title) { article ->
                        HelpArticleRow(article = article, onClick = { onArticle(article) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpCategoryCard(category: HelpCategory, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).background(category.color.copy(alpha = 0.12f), CircleShape),
        ) {
            Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(25.dp))
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(category.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.help_article_count, category.articles.size),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun HelpCategoryDetail(
    category: HelpCategory,
    onArticle: (HelpArticle) -> Unit,
    onBack: () -> Unit,
) {
    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = category.title, onBack = onBack)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(category.articles, key = HelpArticle::title) { article ->
                    HelpArticleRow(article = article, onClick = { onArticle(article) })
                }
            }
        }
    }
}

@Composable
private fun HelpArticleRow(article: HelpArticle, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(article.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                article.preview,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun HelpArticleDetail(
    article: HelpArticle,
    onRelatedArticle: (String) -> Unit,
    onBack: () -> Unit,
) {
    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = stringResource(R.string.settings_help_center), onBack = onBack)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Text(article.title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        article.content,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                if (article.steps.isNotEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            Text(
                                stringResource(R.string.help_step_by_step),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            article.steps.forEachIndexed { index, step ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Indigo.copy(alpha = 0.12f), CircleShape),
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            color = Indigo,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Text(
                                        step,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                if (article.related.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.help_related_articles),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            article.related.forEach { related ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Surface, RoundedCornerShape(8.dp))
                                        .clickable { onRelatedArticle(related) }
                                        .padding(12.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.TipsAndUpdates,
                                        contentDescription = null,
                                        tint = Indigo,
                                        modifier = Modifier.size(19.dp),
                                    )
                                    Text(related, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun HelpNoResults() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.help_no_results),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.help_try_different),
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    var showVersionHistory by remember { mutableStateOf(false) }
    if (showVersionHistory) {
        VersionHistoryScreen(onBack = { showVersionHistory = false })
        return
    }

    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ModalInlineHeader(
                title = stringResource(R.string.settings_about),
                onClose = onBack,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.wpayin_logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.size(84.dp),
                        )
                        Text(
                            "Wpayin Wallet",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                        Text(
                            stringResource(R.string.about_version_name, BuildConfig.VERSION_NAME),
                            color = TextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                item {
                    AboutSection(
                        title = stringResource(R.string.settings_about),
                        content = stringResource(R.string.about_description),
                    )
                }
                item {
                    AboutSection(
                        title = stringResource(R.string.about_version),
                        content = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(16.dp))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                            .clickable { showVersionHistory = true }
                            .padding(16.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .background(Indigo.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                        ) {
                            Icon(
                                Icons.Rounded.TipsAndUpdates,
                                contentDescription = null,
                                tint = Indigo,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(
                                stringResource(R.string.about_whats_new),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.about_whats_new_subtitle),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(content, color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun ModalLargeHeader(title: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            title,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Close,
            contentDescription = stringResource(R.string.common_close),
            tint = TextSecondary,
            modifier = Modifier
                .size(40.dp)
                .background(Surface, CircleShape)
                .clickable(onClick = onClose)
                .padding(9.dp),
        )
    }
}

@Composable
private fun ModalInlineHeader(title: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            stringResource(R.string.common_close),
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .size(width = 72.dp, height = 44.dp)
                .clickable(onClick = onClose)
                .padding(vertical = 11.dp),
        )
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(width = 72.dp, height = 44.dp))
    }
}

@Composable
private fun VersionHistoryScreen(onBack: () -> Unit) {
    val releases = androidReleases()
    var selectedVersion by remember { mutableStateOf(releases.first().version) }
    val selectedRelease = releases.firstOrNull { it.version == selectedVersion } ?: releases.first()
    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = stringResource(R.string.about_version_history), onBack = onBack)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Text(stringResource(R.string.about_release_notes), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.about_choose_version),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(releases, key = AndroidRelease::version) { release ->
                            val selected = release.version == selectedVersion
                            Text(
                                release.version,
                                color = if (selected) Color.White else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(if (selected) Indigo else Surface, CircleShape)
                                    .border(
                                        1.dp,
                                        if (selected) Color.Transparent else SurfaceBorder,
                                        CircleShape,
                                    )
                                    .clickable { selectedVersion = release.version }
                                    .padding(horizontal = 15.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                item {
                    AndroidReleaseCard(selectedRelease)
                }
            }
        }
    }
}

private data class AndroidRelease(
    val version: String,
    val date: String,
    val summary: String,
    val changes: List<String>,
    val current: Boolean,
)

@Composable
private fun androidReleases(): List<AndroidRelease> = listOf(
    AndroidRelease(
        version = BuildConfig.VERSION_NAME,
        date = stringResource(R.string.about_android_112_date),
        summary = stringResource(R.string.about_android_112_summary),
        changes = listOf(
            stringResource(R.string.about_android_112_change_1),
            stringResource(R.string.about_android_112_change_2),
            stringResource(R.string.about_android_112_change_3),
        ),
        current = true,
    ),
    AndroidRelease(
        version = "1.1.1",
        date = stringResource(R.string.about_android_111_date),
        summary = stringResource(R.string.about_android_111_summary),
        changes = listOf(
            stringResource(R.string.about_android_111_change_1),
            stringResource(R.string.about_android_111_change_2),
            stringResource(R.string.about_android_111_change_3),
            stringResource(R.string.about_android_111_change_4),
        ),
        current = false,
    ),
    AndroidRelease(
        version = "1.1.0",
        date = stringResource(R.string.about_android_110_date),
        summary = stringResource(R.string.about_android_110_summary),
        changes = listOf(
            stringResource(R.string.about_android_110_change_1),
            stringResource(R.string.about_android_110_change_2),
            stringResource(R.string.about_android_110_change_3),
            stringResource(R.string.about_android_110_change_4),
            stringResource(R.string.about_android_110_change_5),
            stringResource(R.string.about_android_110_change_6),
        ),
        current = false,
    ),
    AndroidRelease(
        version = "1.0.0",
        date = stringResource(R.string.about_android_100_date),
        summary = stringResource(R.string.about_android_100_summary),
        changes = listOf(
            stringResource(R.string.about_android_100_change_1),
            stringResource(R.string.about_android_100_change_2),
            stringResource(R.string.about_android_100_change_3),
            stringResource(R.string.about_android_100_change_4),
            stringResource(R.string.about_android_100_change_5),
        ),
        current = false,
    ),
)

@Composable
private fun AndroidReleaseCard(release: AndroidRelease) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(20.dp))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.about_version_name, release.version),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (release.current) {
                        Text(
                            stringResource(R.string.about_current_badge),
                            color = Success,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(Success.copy(alpha = 0.12f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(release.date, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            }
            Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null, tint = Indigo)
        }
        Text(release.summary, color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceBorder))
        release.changes.forEach { change ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(30.dp).background(Indigo.copy(alpha = 0.12f), CircleShape),
                ) {
                    Text("✓", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    change,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
            }
        }
    }
}

@Composable
fun AppearanceScreen(
    selectedColorTheme: AppColorTheme,
    selectedAssetListStyle: AssetListStyle,
    onSetColorTheme: (AppColorTheme) -> Unit,
    onSetAssetListStyle: (AssetListStyle) -> Unit,
    onBack: () -> Unit,
) {
    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = stringResource(R.string.settings_appearance_title), onBack = onBack)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                modifier = Modifier.weight(1f),
            ) {
                item { Text(stringResource(R.string.settings_app_color), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                items(AppColorTheme.entries) { theme ->
                    AppearanceColorRow(
                        theme = theme,
                        selected = theme == selectedColorTheme,
                        onClick = { onSetColorTheme(theme) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.appearance_asset_list_style),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        stringResource(R.string.appearance_asset_list_description),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(AssetListStyle.entries) { style ->
                    AppearanceAssetStyleRow(
                        style = style,
                        selected = style == selectedAssetListStyle,
                        onClick = { onSetAssetListStyle(style) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceColorRow(theme: AppColorTheme, selected: Boolean, onClick: () -> Unit) {
    val title = stringResource(
        when (theme) {
            AppColorTheme.INDIGO -> R.string.appearance_indigo
            AppColorTheme.EMERALD -> R.string.appearance_emerald
            AppColorTheme.AMBER -> R.string.appearance_amber
            AppColorTheme.ROSE -> R.string.appearance_rose
            AppColorTheme.SKY -> R.string.appearance_sky
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (selected) theme.primaryColor().copy(alpha = 0.5f) else SurfaceBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(theme.primaryColor(), theme.accentColor()),
                    ),
                    CircleShape,
                ),
        )
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp).weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = theme.primaryColor())
    }
}

@Composable
private fun AppearanceAssetStyleRow(style: AssetListStyle, selected: Boolean, onClick: () -> Unit) {
    val title = stringResource(
        if (style == AssetListStyle.CARDS) R.string.appearance_cards else R.string.appearance_compact,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) Indigo.copy(alpha = 0.5f) else SurfaceBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(
            if (style == AssetListStyle.CARDS) Icons.Rounded.GridView else Icons.Rounded.ViewList,
            contentDescription = null,
            tint = Indigo,
        )
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (style == AssetListStyle.CARDS) {
                Text(stringResource(R.string.appearance_default), color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Indigo)
    }
}

@Composable
fun WalletConnectInfoScreen(onBack: () -> Unit) {
    WpayinBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(title = stringResource(R.string.settings_walletconnect), onBack = onBack)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
            ) {
                Icon(
                    Icons.Rounded.Link,
                    contentDescription = null,
                    tint = Indigo,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Indigo.copy(alpha = 0.12f), CircleShape)
                        .padding(16.dp),
                )
                Text(
                    stringResource(R.string.settings_no_active_connections),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    stringResource(R.string.settings_walletconnect_message),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
        }
    }
}
