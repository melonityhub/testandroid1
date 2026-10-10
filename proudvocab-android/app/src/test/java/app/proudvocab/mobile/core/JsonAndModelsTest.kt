package app.proudvocab.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonAndModelsTest {

    @Test
    fun json_roundTrip_nestedAndEscapes() {
        val src = linkedMapOf<String, Any?>(
            "s" to "He said \"hi\"\nline2\t\\ end ✓ سلام",
            "n" to 42L,
            "d" to 2.5,
            "b" to true,
            "z" to null,
            "list" to listOf(1L, "x", mapOf("k" to false)),
        )
        val text = Json.stringify(src)
        val back = Json.parse(text) as Map<*, *>
        assertEquals(src["s"], back["s"])
        assertEquals(42L, back["n"])
        assertEquals(2.5, back["d"] as Double, 1e-9)
        assertEquals(true, back["b"])
        assertNull(back["z"])
        assertEquals(3, (back["list"] as List<*>).size)
    }

    @Test
    fun json_controlCharsAreEscaped() {
        val text = Json.stringify("a\u0001b")
        assertTrue(text.contains("\\u0001"))
        assertEquals("a\u0001b", Json.parse(text))
    }

    @Test
    fun json_unicodeEscapeAndNumbers() {
        assertEquals("é", Json.parse("\"\\u00e9\""))
        assertEquals(-12L, Json.parse("-12"))
        assertEquals(1.5e3, Json.parse("1.5e3") as Double, 1e-9)
        assertEquals(9_000_000_000_000L, Json.parse("9000000000000"))
    }

    @Test
    fun json_garbageReturnsNull() {
        assertNull(Json.parseOrNull("{not json"))
        assertNull(Json.parseOrNull("[1,2"))
        assertNull(Json.parseOrNull("{\"a\":1} trailing"))
        assertNull(Json.parseOrNull(""))
    }

    @Test
    fun json_nanBecomesNull() {
        assertEquals("null", Json.stringify(Double.NaN))
    }

    @Test
    fun userData_roundTrip() {
        val w = SavedWord(
            id = "1", word = "abandon", translation = "رها کردن", context = "Never abandon your dreams.",
            cefrLevel = "B1", videoTitle = "Movie \"X\"", videoTimeSec = 75, addedAt = 10, updatedAt = 20,
            hard = true, interval = 3.0, easeFactor = 2.35, nextReview = 999, reviewCount = 2, streak = 2,
        )
        val ud = UserData(listOf(w), StudyStreak(3, "2026-10-09", 5), GameStats(120, 4, 33))
        val back = UserData.fromJson(ud.toJson())
        assertNotNull(back)
        assertEquals(w, back!!.words.single())
        assertEquals(StudyStreak(3, "2026-10-09", 5), back.streak)
        assertEquals(GameStats(120, 4, 33), back.stats)
        assertEquals(2, back.stats.level)
    }

    @Test
    fun userData_acceptsBareArrayAndSkipsJunk() {
        val back = UserData.fromJson("""[{"word":"hello","translation":"سلام"},{"nothing":1},"str",{"word":"  "}]""")
        assertNotNull(back)
        assertEquals(1, back!!.words.size)
        assertEquals("hello", back.words[0].word)
        assertTrue(back.words[0].id.isNotEmpty())
        assertNull(UserData.fromJson("not json"))
        assertNull(UserData.fromJson("42"))
    }

    @Test
    fun savedWord_easeIsClamped_andKeyIsCaseInsensitive() {
        val w = SavedWord.fromMap(mapOf("word" to " Hello ", "easeFactor" to 99.0))!!
        assertEquals(3.0, w.easeFactor, 1e-9)
        assertEquals("hello", w.key)
        assertFalse(w.learned)
    }

    @Test
    fun exp_levels() {
        assertEquals(1, Exp.levelFor(0))
        assertEquals(1, Exp.levelFor(99))
        assertEquals(2, Exp.levelFor(100))
        assertEquals(1, Exp.levelFor(-5))
        assertEquals(50, Exp.progressInLevel(250))
    }
}
