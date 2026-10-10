package app.proudvocab.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WordsAndCefrTest {

    // Unit tests run with the module directory (app/) as working dir.
    private val lookup: CefrLookup by lazy {
        val dir = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.exists() }
        CefrLookup(
            Cefr.parseTsv(File(dir, "cefr_map.tsv").readText()),
            Cefr.parseTsv(File(dir, "irregular_forms.tsv").readText()),
        )
    }

    @Test
    fun tokenizer_wordsContractionsHyphensAndPunctuation() {
        val line = Tokenizer.tokenizeLine("Don't stop, well-known R2D2! 42 — ok")
        val words = line.filter { it.isWord }.map { it.text }
        assertEquals(listOf("Don't", "stop", "well-known", "R2D2", "ok"), words)
        assertEquals("Don't stop, well-known R2D2! 42 — ok", line.joinToString("") { it.text })
    }

    @Test
    fun tokenizer_multiLineAndLeadingDash() {
        val lines = Tokenizer.tokenize("- Hello there\n\n- Yes?")
        assertEquals(2, lines.size)
        assertEquals("- ", lines[0][0].text)
        assertFalse(lines[0][0].isWord)
        assertEquals("Hello", lines[0][1].text)
    }

    @Test
    fun tokenizer_curlyApostropheStaysInsideWord() {
        val words = Tokenizer.tokenizeLine("It’s mine").filter { it.isWord }.map { it.text }
        assertEquals(listOf("It’s", "mine"), words)
    }

    @Test
    fun words_lookupForm() {
        assertEquals("do", Words.lookupForm("Don't"))
        assertEquals("can", Words.lookupForm("can't"))
        assertEquals("will", Words.lookupForm("won't"))
        assertEquals("john", Words.lookupForm("John's"))
        assertEquals("i", Words.lookupForm("I'm"))
        assertEquals("they", Words.lookupForm("They’ve"))
        assertEquals("hello", Words.lookupForm("\"Hello!\""))
        assertEquals("", Words.lookupForm("..."))
    }

    @Test
    fun cefr_dataLoaded() {
        assertEquals(8833, lookup.size())
    }

    @Test
    fun cefr_directLookups() {
        assertEquals("A1", lookup.lookup("a"))
        assertEquals("B1", lookup.lookup("abandon"))
        assertEquals("B2", lookup.lookup("abandoned"))
        assertEquals("C2", lookup.lookup("aberration"))
        assertEquals("B1", lookup.lookup("ABANDON"))
    }

    @Test
    fun cefr_lemmatisation() {
        assertEquals(lookup.lookup("ability"), lookup.lookup("abilities")) // ies -> y
        assertNotNull(lookup.lookup("went"))                               // irregular -> go
        assertEquals(lookup.lookup("go"), lookup.lookup("went"))
        assertNull(lookup.lookup("zzzzqqqq"))
        assertNull(lookup.lookup(""))
    }

    @Test
    fun cefr_lookupWord_surfaceForms() {
        assertEquals(lookup.lookup("do"), lookup.lookupWord("Don't"))
        assertEquals(lookup.lookup("abandon"), lookup.lookupWord("\"Abandon,\""))
        assertNull(lookup.lookupWord("123"))
        // hyphenated compound -> hardest component
        val a = lookup.lookup("able")
        val b = lookup.lookup("aberration")
        assertNotNull(a)
        assertEquals(b, lookup.lookupWord("able-aberration"))
    }

    @Test
    fun cefr_ranksAndColours() {
        assertTrue(Cefr.isAtLeast("B2", "B1"))
        assertFalse(Cefr.isAtLeast("A2", "B1"))
        assertFalse(Cefr.isAtLeast(null, "A1"))
        assertEquals(0xFF22C55E, Cefr.colorArgb("a1"))
        assertEquals(0xFF94A3B8, Cefr.colorArgb(null))
        assertEquals(6, Cefr.LEVELS.size)
    }
}
