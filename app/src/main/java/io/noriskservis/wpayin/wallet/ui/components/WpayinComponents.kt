package io.noriskservis.wpayin.wallet.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.ui.theme.Indigo
import io.noriskservis.wpayin.wallet.ui.theme.IndigoDark
import io.noriskservis.wpayin.wallet.ui.theme.Ink
import io.noriskservis.wpayin.wallet.ui.theme.InkRaised
import io.noriskservis.wpayin.wallet.ui.theme.Surface
import io.noriskservis.wpayin.wallet.ui.theme.SurfaceBorder
import io.noriskservis.wpayin.wallet.ui.theme.TextSecondary
import io.noriskservis.wpayin.wallet.ui.theme.TextTertiary
import io.noriskservis.wpayin.wallet.ui.theme.Violet

val LargeShape = RoundedCornerShape(24.dp)
val MediumShape = RoundedCornerShape(18.dp)

@Composable
fun WpayinBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(InkRaised, Ink, Ink),
                ),
            ),
    ) {
        content()
    }
}

@Composable
fun WpayinLogo(size: Int = 80, tint: Color? = null) {
    Image(
        painter = painterResource(R.drawable.wpayin_logo),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let(ColorFilter::tint),
        modifier = Modifier.size(size.dp),
    )
}

@Composable
fun PrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MediumShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Surface,
            disabledContentColor = TextTertiary,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) Brush.horizontalGradient(listOf(Indigo, Violet))
                    else Brush.horizontalGradient(listOf(Surface, Surface)),
                    MediumShape,
                ),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SecondaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MediumShape,
        border = BorderStroke(1.dp, SurfaceBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            disabledContentColor = TextTertiary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (onBack != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(1.dp, SurfaceBorder, CircleShape)
                    .clickable(onClick = onBack),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Spacer(Modifier.size(14.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun LoadingScreen() {
    val transition = rememberInfiniteTransition(label = "logo pulse")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "logo scale",
    )
    WpayinBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
        ) {
            Spacer(Modifier.weight(0.72f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(210.dp)
                    .border(2.dp, Indigo.copy(alpha = 0.72f), CircleShape),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(126.dp)
                        .background(Ink.copy(alpha = 0.92f), RoundedCornerShape(34.dp))
                        .border(1.dp, Indigo.copy(alpha = 0.72f), RoundedCornerShape(34.dp)),
                ) {
                    WpayinLogo(88)
                }
                LoadingOrbitDot(Indigo, Modifier.align(Alignment.TopCenter).offset(y = (-6).dp))
                LoadingOrbitDot(IndigoDark, Modifier.align(Alignment.CenterStart).offset(x = (-6).dp, y = 28.dp))
                LoadingOrbitDot(Violet, Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = (-32).dp))
            }
            Text(
                "Wpayin",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 38.dp).scale(scale),
            )
            Text(
                stringResource(R.string.secure_simple_decentralized),
                color = TextSecondary,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(26.dp))
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(26.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp).background(Indigo.copy(alpha = 0.14f), CircleShape),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Indigo, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(stringResource(R.string.component_syncing), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.secure_simple_decentralized),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text("•••", color = Indigo, fontSize = 18.sp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape),
                ) {
                    Box(Modifier.fillMaxWidth(0.58f).height(4.dp).background(Indigo, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun LoadingOrbitDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(13.dp)
            .background(color, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
    )
}

@Composable
fun ProductionVariantNotice(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .background(Indigo.copy(alpha = 0.12f), MediumShape)
            .border(1.dp, Indigo.copy(alpha = 0.35f), MediumShape)
            .padding(16.dp),
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Indigo)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.component_preview_build),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                stringResource(R.string.component_preview_build_message),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
    ) {
        Text("W", color = Indigo, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
