package io.noriskservis.wpayin.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noriskservis.wpayin.wallet.R
import io.noriskservis.wpayin.wallet.model.Asset
import io.noriskservis.wpayin.wallet.model.Blockchain

/**
 * Shared crypto icon system. The raster marks are the same bundled assets used
 * by the iOS app; programmatic SOL and stablecoin marks mirror its SwiftUI icons.
 */
@Composable
fun CryptoTokenIcon(
    asset: Asset,
    size: Int,
    showNetworkBadge: Boolean = false,
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        CryptoTokenMark(asset = asset, size = size)

        if (showNetworkBadge && !asset.isNative) {
            val badgeSize = (size * 0.42f).coerceAtLeast(14f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = (size * 0.1f).dp, y = (size * 0.1f).dp)
                    .size(badgeSize.dp)
                    .background(Color(0xFF080A12), CircleShape)
                    .padding((size * 0.045f).dp),
            ) {
                CryptoNetworkIcon(asset.blockchain, badgeSize.toInt())
            }
        }
    }
}

@Composable
private fun CryptoTokenMark(asset: Asset, size: Int) {
    // QU on mainnet and tQU on testnet. Only the native coin of the Quintium slot
    // gets the mark, so a look-alike token named "QU" elsewhere keeps a plain icon.
    if (asset.blockchain == Blockchain.QUINTIUM && asset.isNative) {
        RasterCryptoIcon(R.drawable.crypto_quintium, size)
        return
    }
    when (asset.symbol.uppercase()) {
        "ETH", "WETH" -> RasterCryptoIcon(R.drawable.crypto_eth, size)
        "BTC" -> RasterCryptoIcon(R.drawable.crypto_btc, size)
        "BNB", "WBNB" -> RasterCryptoIcon(R.drawable.crypto_bnb, size)
        "SOL" -> SolanaCryptoIcon(size)
        "USDC" -> StablecoinCryptoIcon(size, "$", Color(0xFF2775CA))
        "USDT" -> StablecoinCryptoIcon(size, "₮", Color(0xFF26A17B))
        "POL", "MATIC" -> CryptoNetworkIcon(Blockchain.POLYGON, size)
        "AVAX" -> CryptoNetworkIcon(Blockchain.AVALANCHE, size)
        else -> FallbackCryptoIcon(asset.symbol, asset.blockchain.cryptoBrandColor(), size)
    }
}

@Composable
fun CryptoNetworkIcon(blockchain: Blockchain, size: Int) {
    when (blockchain) {
        Blockchain.BITCOIN -> RasterCryptoIcon(R.drawable.crypto_btc, size)
        Blockchain.ETHEREUM -> RasterCryptoIcon(R.drawable.crypto_eth, size)
        Blockchain.BNB_CHAIN -> RasterCryptoIcon(R.drawable.crypto_bnb, size)
        Blockchain.SOLANA -> SolanaCryptoIcon(size)
        Blockchain.POLYGON -> BrandedGlyphIcon(
            resource = R.drawable.network_polygon_glyph,
            background = Color(0xFF8247E5),
            size = size,
            glyphScale = 0.68f,
        )
        Blockchain.ARBITRUM -> BrandedGlyphIcon(
            resource = R.drawable.network_arbitrum_glyph,
            background = Color(0xFF213147),
            size = size,
            glyphScale = 0.68f,
        )
        Blockchain.OPTIMISM -> BrandedGlyphIcon(
            resource = R.drawable.network_optimism_glyph,
            background = Color(0xFFFF0420),
            size = size,
            glyphScale = 0.64f,
        )
        Blockchain.AVALANCHE -> BrandedGlyphIcon(
            resource = R.drawable.network_avalanche_glyph,
            background = Color(0xFFE84142),
            size = size,
            glyphScale = 0.62f,
        )
        Blockchain.BASE -> BrandedGlyphIcon(
            resource = R.drawable.network_base_glyph,
            background = Color(0xFF0052FF),
            size = size,
            glyphScale = 0.64f,
        )
        Blockchain.QUINTIUM -> RasterCryptoIcon(R.drawable.crypto_quintium, size)
    }
}

@Composable
private fun RasterCryptoIcon(@DrawableRes resource: Int, size: Int) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size.dp).clip(CircleShape),
    )
}

@Composable
private fun BrandedGlyphIcon(
    @DrawableRes resource: Int,
    background: Color,
    size: Int,
    glyphScale: Float,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).background(background, CircleShape),
    ) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size((size * glyphScale).dp),
        )
    }
}

@Composable
private fun SolanaCryptoIcon(size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).background(Color.Black, CircleShape),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy((size * 0.075f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.rotate(-8f),
        ) {
            SolanaBar(
                size = size,
                colors = listOf(Color(0xFF00FFA3), Color(0xFF9945FF)),
                offset = size * 0.035f,
            )
            SolanaBar(
                size = size,
                colors = listOf(Color(0xFF9945FF), Color(0xFF03E1FF)),
                offset = -size * 0.035f,
            )
            SolanaBar(
                size = size,
                colors = listOf(Color(0xFF03E1FF), Color(0xFF00FFA3)),
                offset = size * 0.035f,
            )
        }
    }
}

@Composable
private fun SolanaBar(size: Int, colors: List<Color>, offset: Float) {
    Box(
        modifier = Modifier
            .offset(x = offset.dp)
            .size(width = (size * 0.58f).dp, height = (size * 0.11f).dp)
            .background(
                Brush.horizontalGradient(colors),
                RoundedCornerShape((size * 0.04f).dp),
            ),
    )
}

@Composable
private fun StablecoinCryptoIcon(size: Int, symbol: String, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape)
            .border((size * 0.065f).dp, Color.White.copy(alpha = 0.92f), CircleShape)
            .padding((size * 0.11f).dp),
    ) {
        Text(
            symbol,
            color = Color.White,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FallbackCryptoIcon(symbol: String, color: Color, size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).background(color, CircleShape),
    ) {
        Text(
            symbol.take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontSize = (size * 0.43f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Blockchain.cryptoBrandColor(): Color = when (this) {
    Blockchain.BITCOIN -> Color(0xFFF7931A)
    Blockchain.ETHEREUM -> Color(0xFF627EEA)
    Blockchain.SOLANA -> Color(0xFF14F195)
    Blockchain.BNB_CHAIN -> Color(0xFFF3BA2F)
    Blockchain.POLYGON -> Color(0xFF8247E5)
    Blockchain.ARBITRUM -> Color(0xFF213147)
    Blockchain.OPTIMISM -> Color(0xFFFF0420)
    Blockchain.AVALANCHE -> Color(0xFFE84142)
    Blockchain.BASE -> Color(0xFF0052FF)
    Blockchain.QUINTIUM -> Color(0xFFFFCD02)
}
