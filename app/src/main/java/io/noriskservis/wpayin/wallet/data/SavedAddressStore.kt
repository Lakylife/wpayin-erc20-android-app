package io.noriskservis.wpayin.wallet.data

import android.content.Context
import io.noriskservis.wpayin.wallet.model.SavedAddress
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

class SavedAddressStore(context: Context) {
    private val preferences = context.getSharedPreferences("wpayin_saved_addresses", Context.MODE_PRIVATE)

    fun load(): List<SavedAddress> {
        val raw = preferences.getString(KEY_ADDRESSES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name").trim()
                    val address = item.optString("address").trim()
                    val createdAt = runCatching {
                        Instant.parse(item.optString("createdAt"))
                    }.getOrDefault(Instant.now())
                    if (id.isNotBlank() && name.isNotBlank() && address.isNotBlank()) {
                        add(SavedAddress(id, name, address, createdAt))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(addresses: List<SavedAddress>) {
        val array = JSONArray()
        addresses.forEach { saved ->
            array.put(
                JSONObject()
                    .put("id", saved.id)
                    .put("name", saved.name)
                    .put("address", saved.address)
                    .put("createdAt", saved.createdAt.toString()),
            )
        }
        preferences.edit().putString(KEY_ADDRESSES, array.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_ADDRESSES).apply()
    }

    companion object {
        private const val KEY_ADDRESSES = "addresses"
    }
}
