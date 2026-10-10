package app.proudvocab.mobile.core

data class Translation(val text: String, val detectedLang: String?)

data class WordInfo(
    val phonetic: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val example: String?,
    val synonyms: List<String>,
)

/** Pure parsers for the two public web APIs the app uses (kept separate so they can be unit-tested offline). */
object ServiceParsers {

    /** translate.googleapis.com/translate_a/single response: [[["translated","original",...],...],null,"en",...] */
    fun translation(body: String): Translation? {
        val root = Json.parseOrNull(body) as? List<*> ?: return null
        val segments = root.getOrNull(0) as? List<*> ?: return null
        val sb = StringBuilder()
        for (seg in segments) {
            val part = (seg as? List<*>)?.getOrNull(0) as? String
            if (part != null) sb.append(part)
        }
        val text = sb.toString().trim()
        if (text.isEmpty()) return null
        return Translation(text, root.getOrNull(2) as? String)
    }

    /** api.dictionaryapi.dev/api/v2/entries/en/<word> response. */
    fun dictionary(body: String): WordInfo? {
        val root = Json.parseOrNull(body) as? List<*> ?: return null
        val entry = root.firstOrNull() as? Map<*, *> ?: return null

        var phonetic = (entry["phonetic"] as? String)?.takeIf { it.isNotBlank() }
        if (phonetic == null) {
            phonetic = (entry["phonetics"] as? List<*>)
                ?.mapNotNull { ((it as? Map<*, *>)?.get("text") as? String)?.takeIf { t -> t.isNotBlank() } }
                ?.firstOrNull()
        }

        val meanings = (entry["meanings"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        val first = meanings.firstOrNull()
        val pos = (first?.get("partOfSpeech") as? String)?.takeIf { it.isNotBlank() }
        val firstDef = ((first?.get("definitions") as? List<*>)?.firstOrNull() as? Map<*, *>)
        val definition = (firstDef?.get("definition") as? String)?.takeIf { it.isNotBlank() }
        val example = (firstDef?.get("example") as? String)?.takeIf { it.isNotBlank() }

        val synonyms = LinkedHashSet<String>()
        for (m in meanings) {
            (m["synonyms"] as? List<*>)?.forEach { (it as? String)?.let { s -> if (s.isNotBlank()) synonyms.add(s) } }
            (m["definitions"] as? List<*>)?.forEach { d ->
                ((d as? Map<*, *>)?.get("synonyms") as? List<*>)?.forEach { (it as? String)?.let { s -> if (s.isNotBlank()) synonyms.add(s) } }
            }
        }
        if (definition == null && pos == null && phonetic == null && synonyms.isEmpty()) return null
        return WordInfo(phonetic, pos, definition, example, synonyms.take(6))
    }
}
