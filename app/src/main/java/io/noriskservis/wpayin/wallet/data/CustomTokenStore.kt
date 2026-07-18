package io.noriskservis.wpayin.wallet.data

import android.content.Context
import io.noriskservis.wpayin.wallet.model.Blockchain
import io.noriskservis.wpayin.wallet.model.KnownToken
import org.json.JSONArray
import org.json.JSONObject

class CustomTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<KnownToken> {
        val encoded = preferences.getString(KEY_TOKENS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val blockchain = runCatching { Blockchain.valueOf(item.getString("blockchain")) }.getOrNull()
                        ?: continue
                    val address = item.optString("contractAddress")
                    val symbol = item.optString("symbol")
                    val name = item.optString("name")
                    val decimals = item.optInt("decimals", -1)
                    if (blockchain.isEvm && RpcService.isEvmAddress(address) && symbol.isNotBlank() &&
                        name.isNotBlank() && decimals in 0..36
                    ) {
                        add(KnownToken(blockchain, symbol, name, address.lowercase(), decimals, ""))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(tokens: Collection<KnownToken>) {
        val encoded = JSONArray().apply {
            tokens.forEach { token ->
                put(
                    JSONObject()
                        .put("blockchain", token.blockchain.name)
                        .put("symbol", token.symbol)
                        .put("name", token.name)
                        .put("contractAddress", token.contractAddress.lowercase())
                        .put("decimals", token.decimals),
                )
            }
        }.toString()
        preferences.edit().putString(KEY_TOKENS, encoded).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_TOKENS).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "wpayin_custom_tokens"
        private const val KEY_TOKENS = "tokens_v1"
    }
}
