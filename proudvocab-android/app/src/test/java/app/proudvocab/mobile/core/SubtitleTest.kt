package app.proudvocab.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTest {

    @Test
    fun srt_basic_crlf_bom_tagsAndEntities() {
        val srt = "\uFEFF1\r\n00:00:01,000 --> 00:00:03,500\r\n<i>Hello</i> {\\an8}world &amp; co\r\nsecond line\r\n\r\n" +
            "2\r\n00:00:04,000 --> 00:00:05,000\r\nBye\r\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3500L, cues[0].endMs)
        assertEquals("Hello world & co\nsecond line", cues[0].text)
        assertEquals("Bye", cues[1].text)
    }

    @Test
    fun srt_missingBlankLineBetweenCues() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nFirst\n2\n00:00:03,000 --> 00:00:04,000\nSecond\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(listOf("First", "Second"), cues.map { it.text })
    }

    @Test
    fun srt_numericTextIsKept() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\n42\n\n2\n00:00:03,000 --> 00:00:04,000\nok\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(listOf("42", "ok"), cues.map { it.text })
    }

    @Test
    fun srt_unsortedAndBadEndAreFixed() {
        val srt = "1\n00:00:10,000 --> 00:00:09,000\nLate\n\n2\n00:00:01,000 --> 00:00:02,000\nEarly\n"
        val cues = SubtitleParser.parse(srt)
        assertEquals(listOf("Early", "Late"), cues.map { it.text })
        assertTrue(cues[1].endMs > cues[1].startMs)
    }

    @Test
    fun vtt_headerNotesIdsAndSettings() {
        val vtt = "WEBVTT\n\nNOTE this is a comment\nmore note\n\nintro\n00:01.000 --> 00:03.000 align:start position:0%\nHi there\n\n" +
            "01:00:00.500 --> 01:00:02.000\nOne hour in\n"
        val cues = SubtitleParser.parse(vtt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals("Hi there", cues[0].text)
        assertEquals(3_600_500L, cues[1].startMs)
    }

    @Test
    fun ass_dialogueWithCommasAndOverrides() {
        val ass = "[Script Info]\nTitle: x\n\n[V4+ Styles]\nStyle: Default,Arial\n\n[Events]\n" +
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
            "Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\\i1}Well, hello{\\i0}\\Nthere\n" +
            "Dialogue: 0,0:00:05.00,0:00:06.00,Default,,0,0,0,,{\\p1}m 0 0 l 10 10{\\p0}\n"
        val cues = SubtitleParser.parse(ass)
        assertEquals(1, cues.size)
        assertEquals(1500L, cues[0].startMs)
        assertEquals("Well, hello\nthere", cues[0].text)
    }

    @Test
    fun decode_utf8_bomVariants_andWindows1256() {
        assertEquals("héllo", SubtitleParser.decode("héllo".toByteArray(Charsets.UTF_8)))
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "abc".toByteArray()
        assertEquals("abc", SubtitleParser.decode(withBom))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "سلام".toByteArray(Charsets.UTF_16LE)
        assertEquals("سلام", SubtitleParser.decode(utf16))
        // Real cp1256 subtitle files use Arabic yeh (U+064A): Persian yeh (U+06CC) does not exist in that code page.
        val persian = "سلام دنيا، اين يک زيرنويس است"
        val cp1256 = java.nio.charset.Charset.forName("windows-1256")
        assertEquals(persian, SubtitleParser.decode(persian.toByteArray(cp1256), "movie.fa.srt"))
        assertEquals(persian, SubtitleParser.decode(persian.toByteArray(cp1256)))
        assertEquals("", SubtitleParser.decode(ByteArray(0)))
    }

    @Test
    fun cueIndex_lookups() {
        val cues = listOf(Cue(1000, 2000, "a"), Cue(3000, 4000, "b"), Cue(3500, 6000, "c"))
        assertEquals(-1, CueIndex.indexAt(cues, 500))
        assertEquals(0, CueIndex.indexAt(cues, 1500))
        assertEquals(-1, CueIndex.indexAt(cues, 2500))
        assertEquals(1, CueIndex.indexAt(cues, 3100))
        assertEquals(2, CueIndex.indexAt(cues, 3700)) // overlap: the later cue wins
        assertEquals(2, CueIndex.indexAt(cues, 5000)) // earlier one ended, later one still active
        assertEquals(1000L, CueIndex.nextTarget(cues, 0))
        assertEquals(3000L, CueIndex.nextTarget(cues, 1000))
        assertNull(CueIndex.nextTarget(cues, 3500))
        assertNull(CueIndex.prevTarget(cues, 500))
        assertEquals(1000L, CueIndex.prevTarget(cues, 1300)) // just started: first cue stays
        assertEquals(1000L, CueIndex.prevTarget(cues, 2500)) // well into cue a: restart it
        assertEquals(3000L, CueIndex.prevTarget(cues, 3600)) // just started c -> go back to b
    }
}

class SubtitleExtrasTest {
    @Test
    fun cueIndex_displayGracePeriod() {
        val cues = listOf(Cue(1000, 2000, "a"), Cue(6000, 7000, "b"))
        assertEquals(0, CueIndex.indexForDisplay(cues, 1500))
        assertEquals(0, CueIndex.indexForDisplay(cues, 3000))   // 1s after end: still shown
        assertEquals(-1, CueIndex.indexForDisplay(cues, 4000))  // 2s after end: hidden
        assertEquals(-1, CueIndex.indexForDisplay(cues, 500))   // before the first cue
        assertEquals(1, CueIndex.indexForDisplay(cues, 6500))
    }

    @Test
    fun fileKinds_classification() {
        assertTrue(FileKinds.isSubtitle("Movie.EN.SRT"))
        assertTrue(FileKinds.isSubtitle("a.ass"))
        assertTrue(!FileKinds.isSubtitle("a.mp4"))
        assertTrue(FileKinds.isMedia("clip.mkv", null))
        assertTrue(FileKinds.isMedia("noext", "video/mp4"))
        assertTrue(!FileKinds.isMedia("notes.txt", "text/plain"))
        assertEquals("My.Movie", FileKinds.stripExtension("My.Movie.mkv"))
        assertEquals(".hidden", FileKinds.stripExtension(".hidden"))
    }

    @Test
    fun tokenizer_chunksKeepPunctuationWithWords() {
        val c = Tokenizer.chunks(Tokenizer.tokenizeLine("- Well, hello there!"))
        assertEquals(listOf("Well", "hello", "there"), c.map { it.word })
        assertEquals("- ", c[0].lead)
        assertEquals(", ", c[0].trail)
        assertEquals(" ", c[1].trail)
        assertEquals("!", c[2].trail)
        val onlyPunct = Tokenizer.chunks(Tokenizer.tokenizeLine("♪ ... ♪"))
        assertEquals(1, onlyPunct.size)
        assertEquals("", onlyPunct[0].word)
        assertEquals("♪ ... ♪", onlyPunct[0].lead)
    }
}
