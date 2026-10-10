package app.proudvocab.mobile.data

import app.proudvocab.mobile.core.Exp
import app.proudvocab.mobile.core.GameLogic
import app.proudvocab.mobile.core.GameStats
import app.proudvocab.mobile.core.SavedWord
import app.proudvocab.mobile.core.Srs
import app.proudvocab.mobile.core.UserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class SaveResult { ADDED, ALREADY_SAVED, EMPTY }

data class ImportResult(val added: Int, val skipped: Int, val ok: Boolean)

/** Single source of truth for saved words, streak and game stats. There is no word limit and no license state. */
class WordRepository(private val store: JsonStore) {
    private val _data = MutableStateFlow(UserData())
    val data: StateFlow<UserData> = _data.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val text = store.read() ?: return
        if (text.isBlank()) return
        val parsed = UserData.fromJson(text)
        if (parsed == null) {
            store.quarantineCorrupt()
        } else {
            _data.value = parsed
        }
    }

    private fun mutate(transform: (UserData) -> UserData) {
        _data.update(transform)
        store.scheduleWrite { _data.value.toJson() }
    }

    fun flushNow() = store.flushNow()

    fun isSaved(word: String): Boolean {
        val k = word.trim().lowercase()
        return _data.value.words.any { it.key == k }
    }

    fun saveWord(
        word: String,
        translation: String,
        context: String,
        cefrLevel: String?,
        videoTitle: String,
        videoTimeSec: Long,
        lang: String = "en",
    ): SaveResult {
        val w = word.trim()
        if (w.isEmpty()) return SaveResult.EMPTY
        var result = SaveResult.ADDED
        mutate { d ->
            if (d.words.any { it.key == w.lowercase() }) {
                result = SaveResult.ALREADY_SAVED
                d
            } else {
                val now = System.currentTimeMillis()
                val entry = SavedWord(
                    id = UUID.randomUUID().toString(),
                    word = w,
                    translation = translation.trim(),
                    context = context.trim(),
                    cefrLevel = cefrLevel,
                    lang = lang,
                    videoTitle = videoTitle,
                    videoTimeSec = videoTimeSec,
                    addedAt = now,
                    updatedAt = now,
                )
                d.copy(words = listOf(entry) + d.words)
            }
        }
        return result
    }

    private fun updateWord(id: String, transform: (SavedWord) -> SavedWord) = mutate { d ->
        d.copy(words = d.words.map { if (it.id == id) transform(it) else it })
    }

    fun setLearned(id: String, learned: Boolean) =
        updateWord(id) { it.copy(learned = learned, updatedAt = System.currentTimeMillis()) }

    fun setHard(id: String, hard: Boolean) =
        updateWord(id) { it.copy(hard = hard, hardStreak = 0, wrongStreak = 0, updatedAt = System.currentTimeMillis()) }

    fun editWord(id: String, translation: String, context: String) =
        updateWord(id) { it.copy(translation = translation.trim(), context = context.trim(), updatedAt = System.currentTimeMillis()) }

    fun delete(id: String) = mutate { d -> d.copy(words = d.words.filterNot { it.id == id }) }

    fun clearAllWords() = mutate { d -> d.copy(words = emptyList()) }

    /** Applies an SRS rating to a card, awards EXP and updates the study streak. */
    fun rate(id: String, rating: Int) {
        val now = System.currentTimeMillis()
        mutate { d ->
            d.copy(
                words = d.words.map { if (it.id == id) Srs.next(it, rating, now) else it },
                streak = Srs.updateStreakNow(d.streak, now),
                stats = d.stats.copy(totalExp = d.stats.totalExp + Exp.PER_REVIEW),
            )
        }
    }

    fun recordGameAnswer(wordId: String, correct: Boolean) {
        val now = System.currentTimeMillis()
        mutate { d ->
            d.copy(
                words = d.words.map { if (it.id == wordId) GameLogic.applyAnswer(it, correct, now) else it },
                stats = if (correct) {
                    d.stats.copy(totalExp = d.stats.totalExp + Exp.PER_CORRECT, correctAnswers = d.stats.correctAnswers + 1)
                } else {
                    d.stats
                },
            )
        }
    }

    fun recordGameFinished() = mutate { d ->
        d.copy(stats = d.stats.copy(gamesPlayed = d.stats.gamesPlayed + 1), streak = Srs.updateStreakNow(d.streak))
    }

    fun exportJson(): String = _data.value.toJson()

    /** Merges a backup into the current data: existing words win, new words are added, stats keep the max. */
    fun importJson(text: String): ImportResult {
        val incoming = UserData.fromJson(text) ?: return ImportResult(0, 0, false)
        var added = 0
        var skipped = 0
        mutate { d ->
            val known = d.words.map { it.key }.toHashSet()
            val fresh = ArrayList<SavedWord>()
            for (w in incoming.words) {
                if (known.add(w.key)) {
                    fresh.add(w)
                    added++
                } else {
                    skipped++
                }
            }
            d.copy(
                words = d.words + fresh,
                streak = if (incoming.streak.best > d.streak.best) d.streak.copy(best = incoming.streak.best) else d.streak,
                stats = GameStats(
                    totalExp = maxOf(d.stats.totalExp, incoming.stats.totalExp),
                    gamesPlayed = maxOf(d.stats.gamesPlayed, incoming.stats.gamesPlayed),
                    correctAnswers = maxOf(d.stats.correctAnswers, incoming.stats.correctAnswers),
                ),
            )
        }
        return ImportResult(added, skipped, true)
    }
}
