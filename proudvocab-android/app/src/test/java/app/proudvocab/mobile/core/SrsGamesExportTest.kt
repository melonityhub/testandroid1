package app.proudvocab.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SrsGamesExportTest {

    private fun w(word: String, tr: String = "tr-$word", ctx: String = "", learned: Boolean = false, reviewCount: Int = 0,
                  nextReview: Long = 0, added: Long = 0) =
        SavedWord(id = word, word = word, translation = tr, context = ctx, learned = learned,
            reviewCount = reviewCount, nextReview = nextReview, addedAt = added)

    // ------------------------------------------------------------------ SRS
    @Test
    fun srs_ratings_matchOriginalAlgorithm() {
        val base = w("x").copy(easeFactor = 2.5, streak = 4)
        val again = Srs.next(base, Srs.AGAIN, 1000)
        assertEquals(0, again.streak)
        assertEquals(0.007, again.interval, 1e-9)
        assertEquals(2.3, again.easeFactor, 1e-9)
        assertEquals(1, again.reviewCount)

        val hard = Srs.next(base, Srs.HARD, 0)
        assertEquals(1, hard.streak); assertEquals(1.0, hard.interval, 1e-9); assertEquals(2.35, hard.easeFactor, 1e-9)

        val good = Srs.next(base, Srs.GOOD, 0)
        assertEquals(5, good.streak); assertEquals(3.0, good.interval, 1e-9); assertEquals(2.5, good.easeFactor, 1e-9)
        assertEquals(3L * 86_400_000L, good.nextReview)

        val easy = Srs.next(base, Srs.EASY, 0)
        assertEquals(7.0, easy.interval, 1e-9); assertEquals(2.65, easy.easeFactor, 1e-9)
    }

    @Test
    fun srs_easeBounds() {
        var x = w("x").copy(easeFactor = 1.35)
        repeat(5) { x = Srs.next(x, Srs.AGAIN, 0) }
        assertEquals(1.3, x.easeFactor, 1e-9)
        var y = w("y").copy(easeFactor = 2.95)
        repeat(5) { y = Srs.next(y, Srs.EASY, 0) }
        assertEquals(3.0, y.easeFactor, 1e-9)
    }

    @Test
    fun srs_dueNewAndQueue() {
        val now = 10_000L
        val due = w("due", reviewCount = 1, nextReview = 5_000, added = 1)
        val notYet = w("later", reviewCount = 1, nextReview = 99_999)
        val learned = w("done", learned = true, reviewCount = 1, nextReview = 1)
        val n1 = w("new1", added = 2)
        val n2 = w("new2", added = 3)
        val n3 = w("new3", added = 4)
        val all = listOf(notYet, n3, learned, due, n2, n1)
        assertTrue(Srs.isDue(due, now)); assertFalse(Srs.isDue(notYet, now)); assertFalse(Srs.isDue(learned, now))
        assertEquals(1, Srs.dueCount(all, now))
        assertEquals(3, Srs.newCount(all))
        assertEquals(listOf("due", "new1", "new2"), Srs.buildQueue(all, now, newLimit = 2).map { it.word })
        assertEquals(listOf("due"), Srs.buildQueue(all, now, newLimit = 0).map { it.word })
    }

    @Test
    fun srs_labels() {
        assertEquals("10m", Srs.intervalLabel(0.007))
        assertEquals("12h", Srs.intervalLabel(0.5))
        assertEquals("3d", Srs.intervalLabel(3.0))
        assertEquals("2mo", Srs.intervalLabel(60.0))
        assertEquals("1.0y", Srs.intervalLabel(365.0))
        assertEquals(3.0, Srs.previewDays(w("x"), Srs.GOOD), 1e-9)
    }

    @Test
    fun streak_rules() {
        val s0 = StudyStreak()
        val s1 = Srs.updateStreak(s0, "2026-10-08", "2026-10-07")
        assertEquals(StudyStreak(1, "2026-10-08", 1), s1)
        assertEquals(s1, Srs.updateStreak(s1, "2026-10-08", "2026-10-07")) // same day: unchanged
        val s2 = Srs.updateStreak(s1, "2026-10-09", "2026-10-08")
        assertEquals(StudyStreak(2, "2026-10-09", 2), s2)
        val s3 = Srs.updateStreak(s2, "2026-10-12", "2026-10-11") // gap: restart, best kept
        assertEquals(StudyStreak(1, "2026-10-12", 2), s3)
    }

    // ---------------------------------------------------------------- games
    @Test
    fun games_editDistanceAndDictation() {
        assertEquals(0, GameLogic.editDistance("abc", "abc"))
        assertEquals(3, GameLogic.editDistance("", "abc"))
        assertEquals(3, GameLogic.editDistance("kitten", "sitting"))
        assertEquals(DictationResult.CORRECT, GameLogic.checkDictation(" House ", "house"))
        assertEquals(DictationResult.TYPO, GameLogic.checkDictation("hose", "house"))       // short word: 1 edit
        assertEquals(DictationResult.WRONG, GameLogic.checkDictation("hxxxe", "house"))             // 3 edits
        assertEquals(DictationResult.TYPO, GameLogic.checkDictation("abandn", "abandon"))   // long word: 2 edits ok
        assertEquals(DictationResult.WRONG, GameLogic.checkDictation("table", "abandon"))
    }

    @Test
    fun games_maskWord() {
        assertEquals("He was _____ fast", GameLogic.maskWord("He was running fast", "run"))
        assertEquals("_____ is hard. I _____ it.", GameLogic.maskWord("Abandon is hard. I abandoned it.", "abandon"))
        assertEquals("They _____ up", GameLogic.maskWord("They gave up", "gave"))
        assertEquals("", GameLogic.maskWord("", "x"))
        assertEquals("No match here", GameLogic.maskWord("No match here", "zebra"))
        assertEquals("Price is _____", GameLogic.maskWord("Price is c++", "c++")) // regex metacharacters are escaped
    }

    @Test
    fun games_eligibilityAndRounds() {
        val words = (1..12).map { w("w$it") } + w("blank", tr = "") + w("old", learned = true)
        assertEquals(12, GameLogic.eligible(words, includeLearned = false).size)
        assertEquals(13, GameLogic.eligible(words, includeLearned = true).size)
        assertTrue(GameLogic.canPlay(words, false))
        assertFalse(GameLogic.canPlay(words.take(3), false))
        val round = GameLogic.pickRound(words, GameType.MULTIPLE_CHOICE, false, Random(1))
        assertEquals(10, round.size)
        assertEquals(10, round.map { it.word }.toSet().size)
    }

    @Test
    fun games_fillBlankPrefersWordsWithContext() {
        val words = (1..12).map { w("w$it") } + w("ctx", ctx = "I ctx it")
        repeat(10) { seed ->
            val r = GameLogic.pickRound(words, GameType.FILL_BLANK, false, Random(seed))
            assertTrue(r.any { it.word == "ctx" })
        }
    }

    @Test
    fun games_choicesAreUniqueAndContainCorrect() {
        val others = listOf("a", "b", "c", "d", "a", "CORRECT")
        repeat(20) { seed ->
            val c = GameLogic.buildChoices("correct", others, Random(seed))
            assertEquals(4, c.options.size)
            assertEquals("correct", c.options[c.correctIndex])
            assertEquals(4, c.options.toSet().size)
        }
        val padded = GameLogic.buildChoices("only", emptyList(), Random(3))
        assertEquals("only", padded.options[padded.correctIndex])
        assertEquals(3, padded.options.count { it == "—" })
    }

    @Test
    fun games_scrambleKeepsLettersAndChangesOrder() {
        repeat(20) { seed ->
            val s = GameLogic.scramble("Abandon", Random(seed))
            assertEquals("abandon".toList().sorted(), s.sorted())
            assertFalse(s.joinToString("") == "abandon")
        }
        assertEquals(listOf('a'), GameLogic.scramble("a"))
        assertEquals(emptyList<Char>(), GameLogic.scramble("123"))
    }

    @Test
    fun games_hardWordStreaks() {
        var x = w("x")
        x = GameLogic.applyAnswer(x, false, 1); x = GameLogic.applyAnswer(x, false, 2)
        assertFalse(x.hard)
        x = GameLogic.applyAnswer(x, false, 3)
        assertTrue(x.hard); assertEquals(0, x.wrongStreak)
        x = GameLogic.applyAnswer(x, true, 4); x = GameLogic.applyAnswer(x, true, 5)
        assertTrue(x.hard)
        x = GameLogic.applyAnswer(x, true, 6)
        assertFalse(x.hard); assertEquals(0, x.hardStreak)
        // a wrong answer resets the right-streak
        var y = w("y").copy(hard = true)
        y = GameLogic.applyAnswer(y, true, 1); y = GameLogic.applyAnswer(y, true, 1); y = GameLogic.applyAnswer(y, false, 1)
        assertEquals(0, y.hardStreak); assertTrue(y.hard)
    }

    @Test
    fun games_pagesAndMemoryDeck() {
        val words = (1..7).map { w("w$it") }
        assertEquals(listOf(5, 2), GameLogic.pages(words).map { it.size })
        val deck = GameLogic.memoryDeck(words.take(5), Random(2))
        assertEquals(10, deck.size)
        assertEquals(5, deck.count { it.isWord })
        assertEquals(5, deck.map { it.wordKey }.toSet().size)
    }

    // --------------------------------------------------------------- export
    @Test
    fun export_csv() {
        val words = listOf(
            w("hello", tr = "سلام", ctx = "Say \"hello\", please").copy(hard = true, videoTitle = "Show S01", videoTimeSec = 75),
            w("=evil", tr = "+x"),
        )
        val csv = Exporter.csv(words)
        val lines = csv.split('\n')
        assertEquals("\uFEFFWord,Translation,Context,Hard,Source,Time", lines[0])
        assertEquals("\"hello\",\"سلام\",\"Say \"\"hello\"\", please\",1,\"Show S01\",\"01:15\"", lines[1])
        assertTrue(lines[2].startsWith("\"'=evil\",\"'+x\""))
        assertTrue(lines[2].endsWith(",0,\"\",\"\""))
    }

    @Test
    fun export_anki() {
        val words = listOf(
            w("run", tr = "دویدن", ctx = "I <run> every day\nreally").copy(cefrLevel = "A1", hard = true),
            w("plain", tr = "ساده"),
        )
        val out = Exporter.ankiTsv(words)
        val lines = out.trimEnd('\n').split('\n')
        assertEquals("#separator:tab", lines[0])
        assertEquals("#html:true", lines[1])
        assertEquals("#tags column:3", lines[2])
        val cols = lines[3].split('\t')
        assertEquals(3, cols.size)
        assertEquals("run", cols[0])
        assertTrue(cols[1].startsWith("دویدن<br><br><i>"))
        assertTrue(cols[1].contains("&lt;<b>run</b>&gt;"))
        assertTrue(cols[1].contains("<br>really"))
        assertEquals("ProudVocab A1 hard", cols[2])
        assertEquals("ProudVocab", lines[4].split('\t')[2])
    }
}
