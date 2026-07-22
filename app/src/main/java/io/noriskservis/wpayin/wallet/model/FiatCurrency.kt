package io.noriskservis.wpayin.wallet.model

import java.util.Locale

/**
 * Fiat display currencies, mirroring the iOS Currency enum. Portfolio values
 * are tracked in USD and converted for display with a USD→fiat rate.
 */
enum class FiatCurrency(
    val code: String,
    val symbol: String,
    val displayName: String,
    val fractionDigits: Int,
    val fallbackUsdRate: Double,
) {
    USD("USD", "$", "US Dollar", 2, 1.0),
    EUR("EUR", "€", "Euro", 2, 0.92),
    GBP("GBP", "£", "British Pound", 2, 0.78),
    JPY("JPY", "¥", "Japanese Yen", 0, 157.0),
    CNY("CNY", "¥", "Chinese Yuan", 2, 7.25),
    KRW("KRW", "₩", "South Korean Won", 0, 1380.0),
    CZK("CZK", "Kč", "Czech Koruna", 0, 23.0);

    fun format(convertedValue: Double): String {
        val amount = "%,.${fractionDigits}f".format(Locale.US, convertedValue)
        return if (this == CZK) "$amount $symbol" else "$symbol$amount"
    }

    companion object {
        fun fromCode(code: String): FiatCurrency =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: USD

        fun formatUsdValue(
            valueUsd: Double,
            currencyCode: String,
            rates: Map<String, Double>,
        ): String {
            val currency = fromCode(currencyCode)
            val rate = rates[currency.code]?.takeIf { it > 0 } ?: currency.fallbackUsdRate
            return currency.format(valueUsd * rate)
        }
    }
}
