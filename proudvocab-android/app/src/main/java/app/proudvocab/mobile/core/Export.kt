package app.proudvocab.mobile.core

import java.util.Locale

object Exporter {
    fun csvSafe(s: String): String = if (s.isNotEmpty() && s[0] in "=+-@\t\r") "'$s" else s

    private fun csvField(s: String): String = "\"" + csvSafe(s).replace("\"", "\"\"") + "\""

    private fun mmss(sec: Long): String =
        if (sec < 0) "" else String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

    /** Same columns as the extension's CSV; UTF-8 with BOM so Excel shows Persian correctly. */
    fun csv(words: List<SavedWord>): String {
        val sb = StringBuilder("\uFEFFWord,Translation,Context,Hard,Source,Time\n")
        for (w in words) {
            sb.append(csvField(w.word)).append(',')
                .append(csvField(w.translation)).append(',')
                .append(csvField(w.context)).append(',')
                .append(if (w.hard) "1" else "0").append(',')
                .append(csvField(w.videoTitle)).append(',')
                .append(csvField(mmss(w.videoTimeSec)))
                .append('\n')
        }
        return sb.toString()
    }

    private fun html(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun oneLine(s: String): String = s.replace("\t", " ").replace("\r", "").replace("\n", "<br>")

    /** Anki "Import file" text: front, back, tags (tab separated, HTML enabled). */
    fun ankiTsv(words: List<SavedWord>): String {
        val sb = StringBuilder()
        sb.append("#separator:tab\n#html:true\n#tags column:3\n")
        for (w in words) {
            val front = oneLine(html(w.word))
            val back = StringBuilder(oneLine(html(w.translation)))
            if (w.context.isNotBlank()) {
                val ctx = html(w.context)
                val bolded = try {
                    Regex(Regex.escape(html(w.word)), RegexOption.IGNORE_CASE).replace(ctx) { "<b>" + it.value + "</b>" }
                } catch (e: Exception) {
                    ctx
                }
                back.append("<br><br><i>").append(oneLine(bolded)).append("</i>")
            }
            val tags = buildString {
                append("ProudVocab")
                if (!w.cefrLevel.isNullOrBlank()) append(' ').append(w.cefrLevel)
                if (w.hard) append(" hard")
            }
            sb.append(front).append('\t').append(back).append('\t').append(tags).append('\n')
        }
        return sb.toString()
    }
}
