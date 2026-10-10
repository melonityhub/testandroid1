package app.proudvocab.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceParsersTest {

    @Test
    fun translation_joinsSegments_andDetectsLanguage() {
        val body = """[[["سلام ","Hello ",null,null,10],["دنیا","world",null,null,10]],null,"en",null,null,null,1,[]]"""
        val t = ServiceParsers.translation(body)
        assertNotNull(t)
        assertEquals("سلام دنیا", t!!.text)
        assertEquals("en", t.detectedLang)
    }

    @Test
    fun translation_badOrEmptyBodiesReturnNull() {
        assertNull(ServiceParsers.translation("<html>blocked</html>"))
        assertNull(ServiceParsers.translation("[]"))
        assertNull(ServiceParsers.translation("""[[[null,"x"]],null,"en"]"""))
        assertNull(ServiceParsers.translation("""[[["   ","x"]],null,"en"]"""))
        assertNull(ServiceParsers.translation("""{"a":1}"""))
    }

    @Test
    fun dictionary_parsesPhoneticPosDefinitionAndSynonyms() {
        val body = """[{"word":"run","phonetic":"/rʌn/","phonetics":[{"text":"/rʌn/","audio":""}],
            "meanings":[
              {"partOfSpeech":"verb","definitions":[{"definition":"To move quickly on foot.","example":"I run every day.","synonyms":["sprint","dash"]}],"synonyms":["jog","sprint"]},
              {"partOfSpeech":"noun","definitions":[{"definition":"An act of running.","synonyms":[]}],"synonyms":["race"]}
            ]}]"""
        val info = ServiceParsers.dictionary(body)
        assertNotNull(info)
        assertEquals("/rʌn/", info!!.phonetic)
        assertEquals("verb", info.partOfSpeech)
        assertEquals("To move quickly on foot.", info.definition)
        assertEquals("I run every day.", info.example)
        assertEquals(listOf("jog", "sprint", "dash", "race"), info.synonyms)
    }

    @Test
    fun dictionary_fallsBackToPhoneticsListAndCapsSynonyms() {
        val syn = (1..20).joinToString(",") { "\"s$it\"" }
        val body = """[{"word":"x","phonetics":[{"text":""},{"text":"/eks/"}],"meanings":[{"partOfSpeech":"noun","definitions":[],"synonyms":[$syn]}]}]"""
        val info = ServiceParsers.dictionary(body)!!
        assertEquals("/eks/", info.phonetic)
        assertNull(info.definition)
        assertEquals(6, info.synonyms.size)
    }

    @Test
    fun dictionary_notFoundObjectOrGarbageReturnsNull() {
        assertNull(ServiceParsers.dictionary("""{"title":"No Definitions Found","message":"..."}"""))
        assertNull(ServiceParsers.dictionary("not json"))
        assertNull(ServiceParsers.dictionary("[]"))
        assertTrue(ServiceParsers.dictionary("""[{"word":"x","meanings":[]}]""") == null)
    }
}
