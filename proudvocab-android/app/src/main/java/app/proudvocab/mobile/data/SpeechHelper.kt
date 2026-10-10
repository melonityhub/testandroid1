package app.proudvocab.mobile.data

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Text-to-speech wrapper. Created lazily; reports when no engine/voice is installed. Main thread only. */
class SpeechHelper(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var queued: String? = null
    private var unavailableCallback: (() -> Unit)? = null

    fun speak(text: String, onUnavailable: () -> Unit = {}) {
        val t = text.trim()
        if (t.isEmpty()) return
        unavailableCallback = onUnavailable
        if (failed) {
            onUnavailable()
            return
        }
        if (tts == null) {
            queued = t
            tts = TextToSpeech(appContext) { status -> handleInit(status) }
            return
        }
        if (!ready) {
            queued = t
            return
        }
        say(t)
    }

    private fun handleInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            markFailed()
            return
        }
        val r = engine.setLanguage(Locale.US)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            markFailed()
            return
        }
        engine.setSpeechRate(0.9f)
        ready = true
        queued?.let { say(it) }
        queued = null
    }

    private fun markFailed() {
        failed = true
        queued = null
        unavailableCallback?.invoke()
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pv-utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        failed = false
        queued = null
    }
}
