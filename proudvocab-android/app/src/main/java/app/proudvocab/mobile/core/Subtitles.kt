package app.proudvocab.mobile.core

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

data class Cue(val startMs: Long, val endMs: Long, val text: String)

/** Parses SRT / WebVTT / ASS-SSA subtitle files and decodes their bytes with encoding detection. */
object SubtitleParser {

    // ---------------------------------------------------------------- decoding

    fun decode(bytes: ByteArray, nameHint: String = ""): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        strictUtf8(bytes)?.let { return it }

        val hint = nameHint.lowercase()
        val hintArabic = hint.contains(".fa.") || hint.contains(".ar.") || hint.contains(".fas.") || hint.contains(".per.")
        val hintTurkish = hint.contains(".tr.") || hint.contains(".tur.")

        charsetOrNull("windows-1256")?.let { cs ->
            val s = String(bytes, cs)
            if (hintArabic || arabicRatio(s) > 0.5) return s
        }
        charsetOrNull("windows-1254")?.let { cs ->
            val s = String(bytes, cs)
            if (hintTurkish || s.any { it in "şğıİŞĞ" }) return s
        }
        return String(bytes, charsetOrNull("windows-1252") ?: Charsets.ISO_8859_1)
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun charsetOrNull(name: String): Charset? = try {
        if (Charset.isSupported(name)) Charset.forName(name) else null
    } catch (e: Exception) {
        null
    }

    private fun arabicRatio(s: String): Double {
        var nonAscii = 0
        var arabic = 0
        for (ch in s) {
            if (ch.code >= 0x80) {
                nonAscii++
                if (ch.code in 0x0600..0x06FF) arabic++
            }
        }
        return if (nonAscii < 5) 0.0 else arabic.toDouble() / nonAscii
    }

    // ----------------------------------------------------------------- parsing

    private const val TS = """(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})"""
    private val TIME_LINE = Regex("""^\s*$TS\s*-->\s*$TS""")
    private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")
    private val BRACE_TAG = Regex("""\{[^}]*\}""")
    private val ASS_TIME = Regex("""^\s*(\d+):(\d{2}):(\d{2})[.,](\d{1,3})\s*$""")

    fun parse(text: String): List<Cue> {
        val normalized = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n')
        val cues = if (normalized.contains("[Events]", ignoreCase = true) && normalized.contains("Dialogue:", ignoreCase = true)) {
            parseAss(normalized)
        } else {
            parseSrtOrVtt(normalized)
        }
        return cues
            .map { if (it.endMs <= it.startMs) it.copy(endMs = it.startMs + 2000) else it }
            .sortedBy { it.startMs }
    }

    private fun toMs(h: String?, m: String, s: String, frac: String): Long {
        val hours = h?.toLongOrNull() ?: 0L
        val ms = frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return hours * 3_600_000L + (m.toLongOrNull() ?: 0L) * 60_000L + (s.toLongOrNull() ?: 0L) * 1000L + ms
    }

    private fun parseSrtOrVtt(text: String): List<Cue> {
        val lines = text.split('\n')
        val out = ArrayList<Cue>()
        var start = -1L
        var end = -1L
        val buf = ArrayList<String>()

        fun flush() {
            if (start >= 0) {
                val cleaned = cleanText(buf.joinToString("\n"))
                if (cleaned.isNotBlank()) out.add(Cue(start, end, cleaned))
            }
            start = -1L
            end = -1L
            buf.clear()
        }

        for (i in lines.indices) {
            val line = lines[i].trimEnd()
            val m = TIME_LINE.find(line)
            if (m != null) {
                flush()
                val g = m.groupValues
                start = toMs(g[1].ifEmpty { null }, g[2], g[3], g[4])
                end = toMs(g[5].ifEmpty { null }, g[6], g[7], g[8])
                continue
            }
            if (start < 0) continue // header, cue ids, NOTE/STYLE blocks, garbage between cues
            if (line.isBlank()) {
                flush()
            } else if (line.trim().all { it.isDigit() } && nextNonBlankIsTiming(lines, i + 1)) {
                flush() // index line of the next cue when the blank separator is missing
            } else {
                buf.add(line)
            }
        }
        flush()
        return out
    }

    private fun nextNonBlankIsTiming(lines: List<String>, from: Int): Boolean {
        var j = from
        while (j < lines.size && lines[j].isBlank()) j++
        return j < lines.size && TIME_LINE.containsMatchIn(lines[j])
    }

    private fun parseAss(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        var inEvents = false
        var format: List<String>? = null
        val defaultFormat = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue
            if (line.startsWith("Format:", ignoreCase = true)) {
                format = line.substringAfter(':').split(',').map { it.trim().lowercase() }
            } else if (line.startsWith("Dialogue:", ignoreCase = true)) {
                val f = format ?: defaultFormat
                val parts = line.substringAfter(':').trimStart().split(',', limit = f.size)
                if (parts.size < f.size) continue
                val si = f.indexOf("start")
                val ei = f.indexOf("end")
                val ti = f.indexOf("text")
                if (si < 0 || ei < 0 || ti < 0) continue
                val start = assTime(parts[si]) ?: continue
                val end = assTime(parts[ei]) ?: continue
                val rawText = parts[ti]
                if (rawText.contains("\\p1")) continue // vector drawing, not text
                val cleaned = cleanText(
                    rawText.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
                )
                if (cleaned.isNotBlank()) out.add(Cue(start, end, cleaned))
            }
        }
        return out
    }

    private fun assTime(s: String): Long? {
        val m = ASS_TIME.find(s) ?: return null
        val g = m.groupValues
        return toMs(g[1], g[2], g[3], g[4])
    }

    fun cleanText(raw: String): String {
        var t = raw
        t = HTML_TAG.replace(t, "")
        t = BRACE_TAG.replace(t, "")
        t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        return t.split('\n')
            .map { it.replace(Regex("""[ \t]+"""), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}

/** Lookups on a sorted cue list. */
object CueIndex {
    /** Index of the cue active at [posMs], or -1. */
    fun indexAt(cues: List<Cue>, posMs: Long): Int {
        val last = lastStartingAtOrBefore(cues, posMs)
        if (last < 0) return -1
        var i = last
        var steps = 0
        while (i >= 0 && steps < 5) {
            if (cues[i].endMs > posMs) return i
            i--
            steps++
        }
        return -1
    }

    /**
     * Like [indexAt], but keeps showing the previous cue for [graceMs] after it ended, so there is time to
     * tap a word before the line disappears.
     */
    fun indexForDisplay(cues: List<Cue>, posMs: Long, graceMs: Long = 1500L): Int {
        val active = indexAt(cues, posMs)
        if (active >= 0) return active
        val last = lastStartingAtOrBefore(cues, posMs)
        return if (last >= 0 && posMs - cues[last].endMs <= graceMs) last else -1
    }

    /** Start time to jump to for "next line", or null if there is none. */
    fun nextTarget(cues: List<Cue>, posMs: Long): Long? {
        var lo = 0
        var hi = cues.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs > posMs) hi = mid else lo = mid + 1
        }
        return cues.getOrNull(lo)?.startMs
    }

    /** Start time for "previous line": restart the current cue if we're well into it, else go one back. */
    fun prevTarget(cues: List<Cue>, posMs: Long): Long? {
        val idx = lastStartingAtOrBefore(cues, posMs)
        if (idx < 0) return null
        return if (posMs - cues[idx].startMs > 1200 || idx == 0) cues[idx].startMs else cues[idx - 1].startMs
    }

    private fun lastStartingAtOrBefore(cues: List<Cue>, posMs: Long): Int {
        var lo = 0
        var hi = cues.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= posMs) lo = mid + 1 else hi = mid
        }
        return lo - 1
    }
}
