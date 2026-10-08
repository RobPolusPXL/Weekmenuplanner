package com.example.data.web

import com.example.domain.ImportedRecipe
import com.example.domain.RecipeJsonLdParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Haalt een receptpagina op en leest het recept eruit. Draait op de IO-thread. */
object RecipeFetcher {

    private const val MAX_BYTES = 3 * 1024 * 1024
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    class ImportException(message: String) : Exception(message)

    suspend fun fetch(rawUrl: String): ImportedRecipe = withContext(Dispatchers.IO) {
        val url = normalizeUrl(rawUrl) ?: throw ImportException("Dit is geen geldige link.")
        val html = try {
            download(url)
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("Pagina ophalen mislukt. Controleer de link en je verbinding.")
        }
        RecipeJsonLdParser.parse(html)
            ?: throw ImportException("Geen recept gevonden op deze pagina. Typ de ingrediënten zelf in.")
    }

    internal fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            else -> "https://$trimmed"
        }
        return if (withScheme.length > 8 && withScheme.contains('.')) withScheme else null
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            conn.setRequestProperty("Accept-Language", "nl-BE,nl;q=0.9,en;q=0.5")
            val code = conn.responseCode
            if (code !in 200..299) {
                throw ImportException("De site gaf foutcode $code. Typ de ingrediënten zelf in.")
            }
            val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                .find(conn.contentType ?: "")?.groupValues?.get(1)
                ?.let { runCatching { charset(it) }.getOrNull() }
                ?: Charsets.UTF_8
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (out.size() < MAX_BYTES) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            }
            return out.toString(charset.name())
        } finally {
            conn.disconnect()
        }
    }
}
