package app.proudvocab.mobile

import android.app.Application
import app.proudvocab.mobile.data.CefrRepository
import app.proudvocab.mobile.data.CrashLogger
import app.proudvocab.mobile.data.DictionaryService
import app.proudvocab.mobile.data.JsonStore
import app.proudvocab.mobile.data.RecentStore
import app.proudvocab.mobile.data.SettingsStore
import app.proudvocab.mobile.data.SpeechHelper
import app.proudvocab.mobile.data.TranslationService
import app.proudvocab.mobile.data.WordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency container: one instance for the whole process. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(app)
    val words = WordRepository(JsonStore(File(app.filesDir, "userdata.json"), scope))
    val recents = RecentStore(JsonStore(File(app.filesDir, "recent.json"), scope))
    val cefr = CefrRepository(app.assets, scope)
    val translator = TranslationService()
    val dictionary = DictionaryService()
    val speech = SpeechHelper(app)

    fun flushAll() {
        words.flushNow()
        recents.flushNow()
    }
}

class ProudVocabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        container = AppContainer(this)
    }
}
