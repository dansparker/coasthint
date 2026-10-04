package io.github.dansparker.coasthint.speedlimit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OverpassException(val httpCode: Int) : IOException("Overpass HTTP $httpCode")

/** Sends a query to an Overpass API server and returns the raw JSON answer. */
class OverpassClient(private val serverUrl: String = DEFAULT_SERVER) {

    suspend fun fetch(query: String): String = withContext(Dispatchers.IO) {
        val connection = URL(serverUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw OverpassException(code)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_SERVER = "https://overpass-api.de/api/interpreter"
        private const val TIMEOUT_MILLIS = 30_000
        private const val USER_AGENT = "CoastHint (+https://github.com/dansparker/coasthint)"
    }
}
