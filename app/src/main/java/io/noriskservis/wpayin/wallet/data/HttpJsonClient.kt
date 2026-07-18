package io.noriskservis.wpayin.wallet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpJsonClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(request(url, "GET", null, headers))

    suspend fun getArray(url: String, headers: Map<String, String> = emptyMap()): JSONArray =
        JSONArray(request(url, "GET", null, headers))

    suspend fun post(
        url: String,
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject = JSONObject(request(url, "POST", body.toString(), headers))

    suspend fun postTextForText(
        url: String,
        body: String,
        contentType: String = "text/plain",
    ): String = request(url, "POST", body, mapOf("Content-Type" to contentType))

    private suspend fun request(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WpayinWallet-Android/1.0")
            headers.forEach(::setRequestProperty)
            if (body != null) {
                doOutput = true
                if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                    setRequestProperty("Content-Type", "application/json")
                }
            }
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code from ${URL(url).host}")
            response
        } finally {
            connection.disconnect()
        }
    }
}
