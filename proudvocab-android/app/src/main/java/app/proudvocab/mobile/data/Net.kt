package app.proudvocab.mobile.data

import app.proudvocab.mobile.core.ServiceParsers
import app.proudvocab.mobile.core.Translation
import app.proudvocab.mobile.core.WordInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections

internal class HttpResult(val code: Int, val body: String?)

internal object Http {
    private const val UA = "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private const val MAX_BYTES = 1_000_000

    fun get(url: String, connectMs: Int = 6000, readMs: Int = 8000): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = connectMs
            conn.readTimeout = readMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { readLimited(it) }
            return HttpResult(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) break
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}

/** Google Translate public endpoint (same one the original extension used). Needs internet. */
class TranslationService {
    private val cache: MutableMap<String, Translation> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Translation>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translation>?): Boolean = size > MAX_CACHE
        }
    )

    /** Returns null when offline / blocked / empty. Results are cached in memory. */
    suspend fun translate(text: String, target: String, source: String = "auto"): Translation? {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_CHARS) return null
        val key = "$source|$target|$clean"
        cache[key]?.let { return it }
        val result = withContext(Dispatchers.IO) { fetch(clean, target, source) }
        if (result != null) cache[key] = result
        return result
    }

    private fun fetch(text: String, target: String, source: String): Translation? {
        val q = URLEncoder.encode(text, "UTF-8")
        for (client in CLIENTS) {
            try {
                val url = "https://translate.googleapis.com/translate_a/single?client=$client&sl=$source&tl=$target&dt=t&q=$q"
                val r = Http.get(url)
                val body = r.body
                if (r.code == 200 && body != null) {
                    ServiceParsers.translation(body)?.let { return it }
                }
            } catch (e: Exception) {
                // try the next client id
            }
        }
        return null
    }

    companion object {
        private val CLIENTS = listOf("gtx", "dict-chrome-ex", "it")
        private const val MAX_CACHE = 600
        private const val MAX_CHARS = 1500
    }
}

/** Free dictionary API for part of speech, definition and synonyms (English words only). */
class DictionaryService {
    private val cache = HashMap<String, WordInfo?>()

    suspend fun lookup(word: String): WordInfo? {
        val w = word.trim().lowercase()
        if (w.isEmpty() || w.length > 40 || w.contains(' ')) return null
        synchronized(cache) {
            if (cache.containsKey(w)) return cache[w]
        }
        val info = withContext(Dispatchers.IO) {
            try {
                val r = Http.get("https://api.dictionaryapi.dev/api/v2/entries/en/" + URLEncoder.encode(w, "UTF-8"), 5000, 6000)
                val body = r.body
                if (r.code == 200 && body != null) ServiceParsers.dictionary(body) else null
            } catch (e: Exception) {
                null
            }
        }
        // do not cache failures caused by being offline (null could be transient)
        if (info != null) synchronized(cache) { cache[w] = info }
        return info
    }
}
