package app.proudvocab.mobile.core

import kotlin.random.Random

enum class GameType { MULTIPLE_CHOICE, FILL_BLANK, SCRAMBLE, MATCH, DICTATION, MEMORY }

enum class DictationResult { CORRECT, TYPO, WRONG }

data class Choices(val options: List<String>, val correctIndex: Int)

data class MemoryCard(val id: Int, val wordKey: String, val isWord: Boolean, val text: String)

/** Rules of the six mini-games, ported from the extension and kept free of any UI code. */
object GameLogic {
    const val MIN_WORDS = 4
    const val ROUND_SIZE = 10
    const val PAGE_SIZE = 5
    const val BLANK = "_____"

    fun editDistance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(a.length + 1) { it }
        var cur = IntArray(a.length + 1)
        for (j in 1..b.length) {
            cur[0] = j
            for (i in 1..a.length) {
                cur[i] = if (b[j - 1] == a[i - 1]) {
                    prev[i - 1]
                } else {
                    minOf(prev[i - 1] + 1, cur[i - 1] + 1, prev[i] + 1)
                }
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[a.length]
    }

    /** Exact = correct; small typo (<=1 edit for short words, <=2 otherwise) still counts as correct. */
    fun checkDictation(input: String, word: String): DictationResult {
        val d = editDistance(input.trim().lowercase(), word.trim().lowercase())
        val tolerance = if (word.trim().length <= 5) 1 else 2
        return when {
            d == 0 -> DictationResult.CORRECT
            d <= tolerance -> DictationResult.TYPO
            else -> DictationResult.WRONG
        }
    }

    /** Replaces the target word (and simple inflections of it) in [sentence] with a blank. */
    fun maskWord(sentence: String, word: String): String {
        if (sentence.isEmpty() || word.isBlank()) return ""
        val a = word.trim()
        var stem = a
        if (a.length >= 5) {
            stem = when {
                a.endsWith("ies") || a.endsWith("ing") -> a.dropLast(3)
                a.endsWith("ed") || a.endsWith("es") -> a.dropLast(2)
                a.endsWith("s") || a.endsWith("d") -> a.dropLast(1)
                else -> a
            }
        }
        val full = Regex.escape(a)
        val stemEsc = Regex.escape(stem)
        val flexible = Regex("""\b($full|$stemEsc[a-zA-Z]*)\b""", RegexOption.IGNORE_CASE)
        return if (flexible.containsMatchIn(sentence)) {
            flexible.replace(sentence) { BLANK }
        } else {
            Regex(full, RegexOption.IGNORE_CASE).replace(sentence) { BLANK }
        }
    }

    fun hasUsableContext(w: SavedWord): Boolean =
        w.context.isNotBlank() && w.context.lowercase().contains(w.word.trim().lowercase())

    fun eligible(words: List<SavedWord>, includeLearned: Boolean): List<SavedWord> =
        words.filter { it.translation.isNotBlank() && (includeLearned || !it.learned) }

    fun canPlay(words: List<SavedWord>, includeLearned: Boolean): Boolean =
        eligible(words, includeLearned).size >= MIN_WORDS

    fun pickRound(words: List<SavedWord>, type: GameType, includeLearned: Boolean, random: Random = Random.Default): List<SavedWord> {
        val pool = eligible(words, includeLearned)
        val ordered = if (type == GameType.FILL_BLANK) {
            val (withCtx, without) = pool.partition { hasUsableContext(it) }
            withCtx.shuffled(random) + without.shuffled(random)
        } else {
            pool.shuffled(random)
        }
        return ordered.take(ROUND_SIZE)
    }

    fun buildChoices(correct: String, others: List<String>, random: Random = Random.Default, distractors: Int = 3): Choices {
        val c = correct.ifBlank { "—" }
        val picked = others.map { it.trim() }
            .filter { it.isNotEmpty() && it != "—" && !it.equals(c.trim(), ignoreCase = true) }
            .distinct()
            .shuffled(random)
            .take(distractors)
            .toMutableList()
        while (picked.size < distractors) picked.add("—")
        val all = (picked + c).shuffled(random)
        return Choices(all, all.indexOf(c))
    }

    /** Letters of [word] in a shuffled order that differs from the original when possible. */
    fun scramble(word: String, random: Random = Random.Default): List<Char> {
        val letters = word.filter { it.isLetter() }.lowercase().toList()
        if (letters.size <= 1) return letters
        var attempt = 0
        var shuffled = letters.shuffled(random)
        while (shuffled == letters && attempt < 10) {
            shuffled = letters.shuffled(random)
            attempt++
        }
        return shuffled
    }

    fun pages(words: List<SavedWord>, size: Int = PAGE_SIZE): List<List<SavedWord>> =
        words.chunked(size.coerceAtLeast(1))

    fun memoryDeck(page: List<SavedWord>, random: Random = Random.Default): List<MemoryCard> {
        val cards = ArrayList<MemoryCard>()
        page.forEachIndexed { i, w ->
            cards.add(MemoryCard(2 * i, w.key, true, w.word))
            cards.add(MemoryCard(2 * i + 1, w.key, false, w.translation))
        }
        return cards.shuffled(random)
    }

    /** Tracks hard-word status: 3 wrong in a row marks it hard, 3 right in a row un-marks it. */
    fun applyAnswer(w: SavedWord, correct: Boolean, now: Long): SavedWord {
        var right = w.hardStreak
        var wrong = w.wrongStreak
        var hard = w.hard
        if (correct) {
            right++
            wrong = 0
            if (hard && right >= 3) {
                hard = false
                right = 0
            }
        } else {
            wrong++
            right = 0
            if (!hard && wrong >= 3) {
                hard = true
                wrong = 0
            }
        }
        return w.copy(hard = hard, hardStreak = right, wrongStreak = wrong, updatedAt = now, lastReviewedAt = now)
    }
}
