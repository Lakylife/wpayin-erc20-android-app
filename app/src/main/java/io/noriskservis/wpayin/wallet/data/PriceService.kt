package io.noriskservis.wpayin.wallet.data

import io.noriskservis.wpayin.wallet.BuildConfig
import io.noriskservis.wpayin.wallet.model.Blockchain
import org.json.JSONObject
import java.net.URLEncoder

data class UsdMarketQuote(
    val price: Double,
    val change24hPercent: Double?,
)

class PriceService(
    private val client: HttpJsonClient = HttpJsonClient(),
) {
    suspend fun usdPrices(blockchains: Collection<Blockchain>): Map<String, Double> {
        return usdPricesForIds(blockchains.map(Blockchain::coinGeckoId))
    }

    suspend fun usdPricesForIds(coinGeckoIds: Collection<String>): Map<String, Double> {
        return usdMarketQuotesForIds(coinGeckoIds).mapValues { it.value.price }
    }

    suspend fun usdMarketQuotesForIds(coinGeckoIds: Collection<String>): Map<String, UsdMarketQuote> {
        val ids = coinGeckoIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return emptyMap()
        val encodedIds = URLEncoder.encode(ids.joinToString(","), Charsets.UTF_8.name())
        val headers = BuildConfig.COINGECKO_API_KEY.takeIf(String::isNotBlank)
            ?.let { mapOf("x-cg-demo-api-key" to it) }
            .orEmpty()
        val response = client.get(
            "https://api.coingecko.com/api/v3/simple/price?ids=$encodedIds&vs_currencies=usd&include_24hr_change=true",
            headers,
        )
        return ids.associateWith { id ->
            val quote = response.optJSONObject(id)
            UsdMarketQuote(
                price = quote?.optDouble("usd", 0.0) ?: 0.0,
                change24hPercent = quote
                    ?.takeIf { it.has("usd_24h_change") && !it.isNull("usd_24h_change") }
                    ?.optDouble("usd_24h_change"),
            )
        }
    }

    /**
     * USD → fiat conversion rates, mirroring the iOS CurrencyConversionService
     * (USDC is used as a USD proxy on the keyless CoinGecko tier).
     */
    suspend fun usdFiatRates(currencyCodes: Collection<String>): Map<String, Double> {
        val codes = currencyCodes.map { it.lowercase() }.filter { it.isNotBlank() && it != "usd" }.distinct()
        if (codes.isEmpty()) return mapOf("USD" to 1.0)
        val headers = BuildConfig.COINGECKO_API_KEY.takeIf(String::isNotBlank)
            ?.let { mapOf("x-cg-demo-api-key" to it) }
            .orEmpty()
        val response = client.get(
            "https://api.coingecko.com/api/v3/simple/price?ids=usd-coin&vs_currencies=${codes.joinToString(",")}",
            headers,
        )
        val rates = response.optJSONObject("usd-coin") ?: JSONObject()
        return buildMap {
            put("USD", 1.0)
            codes.forEach { code ->
                val rate = rates.optDouble(code, 0.0)
                if (rate > 0) put(code.uppercase(), rate)
            }
        }
    }
}
