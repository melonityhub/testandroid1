package app.proudvocab.mobile.core

import java.util.Calendar
import java.util.Locale

/** Spaced repetition, ported 1:1 from the extension (ratings: 0 again, 1 hard, 2 good, 3 easy). */
object Srs {
    const val AGAIN = 0
    const val HARD = 1
    const val GOOD = 2
    const val EASY = 3

    private const val DAY_MS = 86_400_000L
    const val AGAIN_INTERVAL_DAYS = 0.007
    const val MIN_EASE = 1.3
    const val MAX_EASE = 3.0

    fun next(w: SavedWord, rating: Int, now: Long): SavedWord {
        var interval: Double
        var ease = w.easeFactor
        var streak = w.streak
        when (rating) {
            AGAIN -> {
                streak = 0
                interval = AGAIN_INTERVAL_DAYS
                ease = maxOf(MIN_EASE, ease - 0.2)
            }
            HARD -> {
                streak = 1
                interval = 1.0
                ease = maxOf(MIN_EASE, ease - 0.15)
            }
            GOOD -> {
                streak += 1
                interval = 3.0
            }
            else -> {
                streak += 1
                interval = 7.0
                ease = minOf(MAX_EASE, ease + 0.15)
            }
        }
        return w.copy(
            interval = interval,
            easeFactor = ease,
            nextReview = now + (DAY_MS * interval).toLong(),
            reviewCount = w.reviewCount + 1,
            streak = streak,
            lastReviewedAt = now,
            updatedAt = now,
        )
    }

    /** Interval (in days) that [rating] would give, for the labels on the answer buttons. */
    fun previewDays(w: SavedWord, rating: Int): Double = next(w, rating, 0L).interval

    fun isDue(w: SavedWord, now: Long): Boolean = !w.learned && w.reviewCount != 0 && w.nextReview <= now

    fun isNew(w: SavedWord): Boolean = !w.learned && w.reviewCount == 0

    fun dueCount(words: List<SavedWord>, now: Long): Int = words.count { isDue(it, now) }

    fun newCount(words: List<SavedWord>): Int = words.count { isNew(it) }

    /** Due cards first (oldest due first), then new cards (oldest first), capped at [newLimit]. */
    fun buildQueue(words: List<SavedWord>, now: Long, newLimit: Int = 20): List<SavedWord> {
        val due = words.filter { isDue(it, now) }.sortedBy { it.nextReview }
        val fresh = words.filter { isNew(it) }.sortedBy { it.addedAt }.take(newLimit.coerceAtLeast(0))
        return due + fresh
    }

    /** Short neutral label: 10m / 3h / 3d / 2mo / 1.5y. */
    fun intervalLabel(days: Double): String = when {
        days < 0.1 -> "10m"
        days < 1 -> "${Math.round(days * 24)}h"
        days < 30 -> "${Math.round(days)}d"
        days < 365 -> "${Math.round(days / 30)}mo"
        else -> String.format(Locale.US, "%.1fy", days / 365)
    }

    // ------------------------------------------------------------ study streak

    fun dayKey(cal: Calendar): String = String.format(
        Locale.US, "%04d-%02d-%02d",
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
    )

    fun updateStreak(s: StudyStreak, today: String, yesterday: String): StudyStreak {
        if (s.lastStudyDate == today) return s
        val cur = if (s.lastStudyDate == yesterday) s.current + 1 else 1
        return StudyStreak(current = cur, lastStudyDate = today, best = maxOf(s.best, cur))
    }

    fun updateStreakNow(s: StudyStreak, nowMs: Long = System.currentTimeMillis()): StudyStreak {
        val c = Calendar.getInstance().apply { timeInMillis = nowMs }
        val today = dayKey(c)
        c.add(Calendar.DAY_OF_MONTH, -1)
        return updateStreak(s, today, dayKey(c))
    }
}
