package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.model.Blockchain
import org.json.JSONObject
import java.net.URLEncoder

class PriceService(
    private val client: HttpJsonClient = HttpJsonClient(),
) {
    suspend fun usdPrices(blockchains: Collection<Blockchain>): Map<String, Double> {
        return usdPricesForIds(blockchains.map(Blockchain::coinGeckoId))
    }

    suspend fun usdPricesForIds(coinGeckoIds: Collection<String>): Map<String, Double> {
        val ids = coinGeckoIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return emptyMap()
        val encodedIds = URLEncoder.encode(ids.joinToString(","), Charsets.UTF_8.name())
        val headers = BuildConfig.COINGECKO_API_KEY.takeIf(String::isNotBlank)
            ?.let { mapOf("x-cg-demo-api-key" to it) }
            .orEmpty()
        val response = client.get(
            "https://api.coingecko.com/api/v3/simple/price?ids=$encodedIds&vs_currencies=usd",
            headers,
        )
        return ids.associateWith { id -> response.optJSONObject(id)?.optDouble("usd", 0.0) ?: 0.0 }
    }
}
