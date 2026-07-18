package io.noriskservis.wpayin.wallet.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.ui.components.PrimaryButton
import io.noriskservis.wpayin.wallet.ui.components.WpayinLogo
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.Success
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.Violet

private data class OnboardingPage(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val message: String,
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var showIntro by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }
    val pages = remember {
        listOf(
            OnboardingPage(
                Icons.Rounded.Lock,
                Indigo,
                "Your keys, your wallet",
                "Recovery phrases and private keys stay encrypted on this device. Wpayin never takes custody of your funds.",
            ),
            OnboardingPage(
                Icons.Rounded.SwapHoriz,
                Success,
                "Trade across networks",
                "Manage Bitcoin, Ethereum, Solana and the supported EVM networks from one clear interface.",
            ),
            OnboardingPage(
                Icons.Rounded.Language,
                Violet,
                "Built for the open web",
                "Receive assets, review transactions and connect to decentralized services without a bank account.",
            ),
        )
    }

    AnimatedContent(
        targetState = showIntro,
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
        label = "onboarding",
    ) { intro ->
        if (intro) {
            OnboardingIntro(onContinue = { showIntro = false })
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF0E101A), Ink)))
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    Text(
                        "Step ${page + 1} of ${pages.size}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pages.indices.forEach { index ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .background(
                                        if (index <= page) Indigo else Surface,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = page,
                        modifier = Modifier.weight(1f),
                        label = "onboarding page",
                    ) { index ->
                        OnboardingPageContent(pages[index])
                    }

                    PrimaryButton(
                        title = if (page == pages.lastIndex) "Open Wpayin Wallet" else "Continue",
                        onClick = {
                            if (page == pages.lastIndex) onComplete() else page += 1
                        },
                    )
                    AnimatedVisibility(page < pages.lastIndex) {
                        Text(
                            "Skip",
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingIntro(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Image(
            painter = painterResource(R.drawable.intro_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.48f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(22.dp),
        ) {
            Spacer(Modifier.weight(0.8f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(210.dp)
                    .background(
                        Brush.radialGradient(listOf(Indigo.copy(alpha = 0.35f), Color.Transparent)),
                        CircleShape,
                    ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(126.dp)
                        .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(30.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(30.dp)),
                ) {
                    WpayinLogo(88)
                }
            }
            Text("Wpayin Wallet", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your gateway to decentralized finance",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            PrimaryButton(title = "Get Started", onClick = onContinue)
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(122.dp)
                .background(page.color.copy(alpha = 0.12f), CircleShape),
        ) {
            Icon(page.icon, contentDescription = null, tint = page.color, modifier = Modifier.size(54.dp))
        }
        Spacer(Modifier.height(36.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
