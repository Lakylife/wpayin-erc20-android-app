package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.model.P2POffer
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A published listing from the public offer board: raw payload + parsed offer. */
data class P2PListing(
    val id: String,
    val payload: String,
    val offer: P2POffer,
    val createdAt: Instant?,
)

/**
 * Read-only client for the public P2P offer board (Supabase PostgREST),
 * wire-compatible with the iOS P2PMarketService `p2p_offers` table.
 * Publishing requires offer creation, which is not available on Android yet.
 */
class P2PMarketService(
    private val client: HttpJsonClient = HttpJsonClient(),
) {
    val isConfigured: Boolean
        get() = BuildConfig.P2P_BOARD_URL.isNotBlank() && BuildConfig.P2P_BOARD_ANON_KEY.isNotBlank()

    suspend fun fetchListings(): List<P2PListing> {
        check(isConfigured) { "The public offer board is not configured" }
        val now = URLEncoder.encode(
            "gt." + isoPlain.format(Instant.now().atOffset(ZoneOffset.UTC)),
            StandardCharsets.UTF_8.toString(),
        )
        val url = BuildConfig.P2P_BOARD_URL.trimEnd('/') +
            "/rest/v1/p2p_offers?select=id,payload,created_at&expiry=$now&order=created_at.desc&limit=100"
        val rows = client.getArray(
            url,
            mapOf(
                "apikey" to BuildConfig.P2P_BOARD_ANON_KEY,
                "Authorization" to "Bearer ${BuildConfig.P2P_BOARD_ANON_KEY}",
            ),
        )
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val payload = row.optString("payload").takeIf(String::isNotBlank) ?: continue
                val offer = runCatching { P2POffer.parse(payload) }.getOrNull() ?: continue
                if (offer.isExpired) continue
                add(
                    P2PListing(
                        id = row.optString("id").ifBlank { "offer-${offer.chainId}-${offer.nonce}" },
                        payload = payload,
                        offer = offer,
                        createdAt = row.optString("created_at")
                            .takeIf(String::isNotBlank)
                            ?.let { value ->
                                runCatching { Instant.parse(if (value.endsWith("Z")) value else value + "Z") }
                                    .getOrNull()
                            },
                    ),
                )
            }
        }
    }

    private companion object {
        val isoPlain: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
