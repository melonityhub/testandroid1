package app.proudvocab.mobile.core

data class Token(val text: String, val isWord: Boolean)

object Tokenizer {
    // A word starts with a letter; may contain letters/marks/digits and inner apostrophes or hyphens.
    private val WORD = Regex("""\p{L}[\p{L}\p{M}\p{N}]*(?:['’\-][\p{L}\p{M}\p{N}]+)*""")

    fun tokenizeLine(line: String): List<Token> {
        val out = ArrayList<Token>()
        var last = 0
        for (m in WORD.findAll(line)) {
            if (m.range.first > last) out.add(Token(line.substring(last, m.range.first), false))
            out.add(Token(m.value, true))
            last = m.range.last + 1
        }
        if (last < line.length) out.add(Token(line.substring(last), false))
        return out
    }

    /**
     * A clickable word plus the punctuation/spaces around it. [lead] is only used for text before the first word,
     * everything after a word (", " or "!" ...) is that word's [trail], so line wrapping never separates them.
     */
    data class Chunk(val lead: String, val word: String, val trail: String)

    fun chunks(tokens: List<Token>): List<Chunk> {
        val out = ArrayList<Chunk>()
        val lead = StringBuilder()
        for (t in tokens) {
            if (t.isWord) {
                out.add(Chunk(lead.toString(), t.text, ""))
                lead.setLength(0)
            } else if (out.isEmpty()) {
                lead.append(t.text)
            } else {
                val last = out.removeAt(out.size - 1)
                out.add(last.copy(trail = last.trail + t.text))
            }
        }
        if (out.isEmpty() && lead.isNotEmpty()) out.add(Chunk(lead.toString(), "", ""))
        return out
    }

    /** One list of tokens per non-empty line. */
    fun tokenize(text: String): List<List<Token>> =
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.map { tokenizeLine(it) }
}

object Words {
    private val EDGE_PUNCT = Regex("""^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$""")

    /** Lower-case, straight apostrophes, no surrounding punctuation. */
    fun normalize(raw: String): String =
        raw.replace('’', '\'').replace('‘', '\'').trim().replace(EDGE_PUNCT, "").lowercase()

    /** Form used for dictionary/CEFR lookups: possessives and contractions reduced to their base word. */
    fun lookupForm(raw: String): String {
        val n = normalize(raw)
        return when {
            n.endsWith("'s") -> n.dropLast(2)
            n == "can't" -> "can"
            n == "won't" -> "will"
            n == "shan't" -> "shall"
            n.endsWith("n't") -> n.dropLast(3)
            n.contains('\'') -> n.substringBefore('\'')
            else -> n
        }
    }
}

object Cefr {
    val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    fun rank(level: String?): Int = when (level?.uppercase()) {
        "A1" -> 1
        "A2" -> 2
        "B1" -> 3
        "B2" -> 4
        "C1" -> 5
        "C2" -> 6
        else -> 0
    }

    fun isAtLeast(level: String?, min: String): Boolean = rank(level) >= rank(min) && rank(level) > 0

    /** ARGB colours, identical to the original extension palette. */
    fun colorArgb(level: String?): Long = when (level?.uppercase()) {
        "A1" -> 0xFF22C55E
        "A2" -> 0xFF84CC16
        "B1" -> 0xFFEAB308
        "B2" -> 0xFFF97316
        "C1" -> 0xFFEF4444
        "C2" -> 0xFFA855F7
        else -> 0xFF94A3B8
    }

    fun parseTsv(text: String): Map<String, String> {
        val map = HashMap<String, String>(16384)
        for (line in text.split('\n')) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val k = line.substring(0, tab).trim()
            val v = line.substring(tab + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
        }
        return map
    }
}

/** CEFR level lookup with simple English lemmatisation (a faithful port of the extension's lookupCEFR). */
class CefrLookup(private val map: Map<String, String>, private val irregular: Map<String, String>) {

    fun size(): Int = map.size

    /** Lookup of a surface word as it appears in a subtitle (handles contractions, hyphens, case). */
    fun lookupWord(raw: String): String? {
        val n = Words.normalize(raw)
        if (n.isEmpty()) return null
        lookup(n)?.let { return it }
        val base = Words.lookupForm(raw)
        if (base != n) lookup(base)?.let { return it }
        if (n.contains('-')) {
            val levels = n.split('-').filter { it.isNotEmpty() }.map { lookup(it) }
            if (levels.isNotEmpty() && levels.all { it != null }) {
                return levels.filterNotNull().maxByOrNull { Cefr.rank(it) }
            }
        }
        return null
    }

    fun lookup(word: String): String? {
        var i = word.lowercase().trim()
        if (i.isEmpty()) return null
        irregular[i]?.let { i = it }
        map[i]?.let { return it }

        val cands = ArrayList<String>()
        if (i.endsWith("s") && i.length > 3) {
            if (i.endsWith("ies")) cands.add(i.dropLast(3) + "y")
            else if (i.endsWith("es")) cands.add(i.dropLast(2))
            cands.add(i.dropLast(1))
        }
        if (i.endsWith("ing") && i.length > 5) {
            val e = i.dropLast(3)
            cands.add(e)
            cands.add(e + "e")
            if (e.length > 2 && e[e.length - 1] == e[e.length - 2]) cands.add(e.dropLast(1))
        }
        if (i.endsWith("ed") && i.length > 4) {
            val e = i.dropLast(2)
            cands.add(e)
            cands.add(e + "e")
            if (e.length > 2 && e[e.length - 1] == e[e.length - 2]) cands.add(e.dropLast(1))
        }
        if (i.endsWith("ly") && i.length > 4) {
            cands.add(i.dropLast(2))
            if (i.endsWith("ily")) cands.add(i.dropLast(3) + "y")
        }
        if (i.endsWith("er") && i.length > 4) {
            val e = i.dropLast(2)
            cands.add(e)
            cands.add(e + "e")
            if (i.endsWith("ier")) cands.add(i.dropLast(3) + "y")
        }
        if (i.endsWith("est") && i.length > 5) {
            val e = i.dropLast(3)
            cands.add(e)
            cands.add(e + "e")
            if (i.endsWith("iest")) cands.add(i.dropLast(4) + "y")
        }
        for (c in cands) {
            val form = irregular[c] ?: c
            map[form]?.let { return it }
        }
        return null
    }
}
