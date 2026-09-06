package com.fitcheck.app.data

import com.fitcheck.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** Free fashion-image search through the Pexels API. */
object PexelsImageSearch {
    fun searchProductImage(query: String): String? {
        val key = BuildConfig.PEXELS_API_KEY.trim()
        if (key.isBlank()) return null
        val encoded = URLEncoder.encode("$query clothing fashion", "UTF-8")
        val endpoint = "https://api.pexels.com/v1/search?query=$encoded&per_page=1&orientation=portrait"
        return runCatching {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Authorization", key)
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { reader ->
                val photo = JSONObject(reader.readText()).optJSONArray("photos")?.optJSONObject(0)
                photo?.optJSONObject("src")?.optString("large2x")
                    ?.takeIf { it.startsWith("http") }
            }.also { connection.disconnect() }
        }.getOrNull()
    }
}
