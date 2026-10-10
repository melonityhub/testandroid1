package app.proudvocab.mobile.core

/** One saved vocabulary entry (all features are free: there is no license/premium state anywhere). */
data class SavedWord(
    val id: String,
    val word: String,
    val translation: String,
    val context: String = "",
    val cefrLevel: String? = null,
    val lang: String = "en",
    val videoTitle: String = "",
    val videoTimeSec: Long = -1L,
    val addedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val learned: Boolean = false,
    val hard: Boolean = false,
    val hardStreak: Int = 0,
    val wrongStreak: Int = 0,
    // spaced repetition state
    val interval: Double = 0.0,
    val easeFactor: Double = 2.5,
    val nextReview: Long = 0L,
    val reviewCount: Int = 0,
    val streak: Int = 0,
    val lastReviewedAt: Long = 0L,
) {
    /** Case-insensitive identity used to avoid duplicates. */
    val key: String get() = word.trim().lowercase()

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "word" to word,
        "translation" to translation,
        "context" to context,
        "cefrLevel" to cefrLevel,
        "lang" to lang,
        "videoTitle" to videoTitle,
        "videoTimeSec" to videoTimeSec,
        "addedAt" to addedAt,
        "updatedAt" to updatedAt,
        "learned" to learned,
        "hard" to hard,
        "hardStreak" to hardStreak,
        "wrongStreak" to wrongStreak,
        "interval" to interval,
        "easeFactor" to easeFactor,
        "nextReview" to nextReview,
        "reviewCount" to reviewCount,
        "streak" to streak,
        "lastReviewedAt" to lastReviewedAt,
    )

    companion object {
        fun fromMap(m: Map<*, *>): SavedWord? {
            val word = Json.str(m, "word").trim()
            if (word.isEmpty()) return null
            val now = System.currentTimeMillis()
            return SavedWord(
                id = Json.str(m, "id").ifEmpty { java.util.UUID.randomUUID().toString() },
                word = word,
                translation = Json.str(m, "translation"),
                context = Json.str(m, "context"),
                cefrLevel = Json.strOrNull(m, "cefrLevel")?.takeIf { it.isNotBlank() },
                lang = Json.str(m, "lang", "en").ifEmpty { "en" },
                videoTitle = Json.str(m, "videoTitle"),
                videoTimeSec = Json.long(m, "videoTimeSec", -1L),
                addedAt = Json.long(m, "addedAt", now),
                updatedAt = Json.long(m, "updatedAt", now),
                learned = Json.bool(m, "learned"),
                hard = Json.bool(m, "hard"),
                hardStreak = Json.int(m, "hardStreak"),
                wrongStreak = Json.int(m, "wrongStreak"),
                interval = Json.dbl(m, "interval"),
                easeFactor = Json.dbl(m, "easeFactor", 2.5).coerceIn(1.3, 3.0),
                nextReview = Json.long(m, "nextReview"),
                reviewCount = Json.int(m, "reviewCount"),
                streak = Json.int(m, "streak"),
                lastReviewedAt = Json.long(m, "lastReviewedAt"),
            )
        }
    }
}

data class StudyStreak(
    val current: Int = 0,
    val lastStudyDate: String = "",
    val best: Int = 0,
)

data class GameStats(
    val totalExp: Long = 0L,
    val gamesPlayed: Int = 0,
    val correctAnswers: Int = 0,
) {
    val level: Int get() = Exp.levelFor(totalExp)
}

object Exp {
    const val PER_CORRECT = 10L
    const val PER_REVIEW = 5L
    const val EXP_PER_LEVEL = 100L
    fun levelFor(exp: Long): Int = (exp.coerceAtLeast(0L) / EXP_PER_LEVEL).toInt() + 1
    fun progressInLevel(exp: Long): Int = (exp.coerceAtLeast(0L) % EXP_PER_LEVEL).toInt()
}

/** Everything persisted on disk (and exported/imported as a JSON backup). */
data class UserData(
    val words: List<SavedWord> = emptyList(),
    val streak: StudyStreak = StudyStreak(),
    val stats: GameStats = GameStats(),
) {
    fun toJson(): String = Json.stringify(
        linkedMapOf(
            "app" to "proudvocab-android",
            "version" to 1,
            "exportedAt" to System.currentTimeMillis(),
            "words" to words.map { it.toMap() },
            "streak" to linkedMapOf(
                "current" to streak.current,
                "lastStudyDate" to streak.lastStudyDate,
                "best" to streak.best,
            ),
            "stats" to linkedMapOf(
                "totalExp" to stats.totalExp,
                "gamesPlayed" to stats.gamesPlayed,
                "correctAnswers" to stats.correctAnswers,
            ),
        )
    )

    companion object {
        /**
         * Lenient parser. Accepts our own backup format, and also a bare JSON array of word objects
         * (so older exports keep working). Returns null if the text is not valid JSON at all.
         */
        fun fromJson(text: String): UserData? {
            val root = Json.parseOrNull(text) ?: return null
            val wordList: List<*>
            var streak = StudyStreak()
            var stats = GameStats()
            when (root) {
                is List<*> -> wordList = root
                is Map<*, *> -> {
                    wordList = (root["words"] as? List<*>) ?: (root["savedWords"] as? List<*>) ?: emptyList<Any?>()
                    (root["streak"] as? Map<*, *>)?.let {
                        streak = StudyStreak(
                            current = Json.int(it, "current"),
                            lastStudyDate = Json.str(it, "lastStudyDate"),
                            best = Json.int(it, "best"),
                        )
                    }
                    (root["stats"] as? Map<*, *>)?.let {
                        stats = GameStats(
                            totalExp = Json.long(it, "totalExp"),
                            gamesPlayed = Json.int(it, "gamesPlayed"),
                            correctAnswers = Json.int(it, "correctAnswers"),
                        )
                    }
                }
                else -> return null
            }
            val words = wordList.mapNotNull { (it as? Map<*, *>)?.let(SavedWord::fromMap) }
            return UserData(words, streak, stats)
        }
    }
}
