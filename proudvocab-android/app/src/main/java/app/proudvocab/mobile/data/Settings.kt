package app.proudvocab.mobile.data

import android.content.Context
import app.proudvocab.mobile.core.Cefr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val targetLang: String = "fa",
    val showCefrColors: Boolean = true,
    val minCefrLevel: String = "B1",
    val dualSubtitles: Boolean = true,
    val autoPauseOnWordTap: Boolean = true,
    val autoSpeakWord: Boolean = false,
    val subtitleTextSp: Int = 22,
    val subtitleBackground: Boolean = true,
    val seekStepSec: Int = 5,
    val keepScreenOn: Boolean = true,
    val resumePlayback: Boolean = true,
    val newCardsPerSession: Int = 20,
    val showLearnedInGames: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

object Languages {
    /** Translation target languages, shown with their own names. */
    val TARGETS: List<Pair<String, String>> = listOf(
        "fa" to "فارسی",
        "ar" to "العربية",
        "tr" to "Türkçe",
        "ru" to "Русский",
        "uk" to "Українська",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "it" to "Italiano",
        "pt" to "Português",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "sv" to "Svenska",
        "hi" to "हिन्दी",
        "zh-CN" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "en" to "English",
    )

    fun nameOf(code: String): String = TARGETS.firstOrNull { it.first == code }?.second ?: code
}

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = sanitize(transform(_state.value))
        _state.value = next
        prefs.edit()
            .putString("targetLang", next.targetLang)
            .putBoolean("showCefrColors", next.showCefrColors)
            .putString("minCefrLevel", next.minCefrLevel)
            .putBoolean("dualSubtitles", next.dualSubtitles)
            .putBoolean("autoPauseOnWordTap", next.autoPauseOnWordTap)
            .putBoolean("autoSpeakWord", next.autoSpeakWord)
            .putInt("subtitleTextSp", next.subtitleTextSp)
            .putBoolean("subtitleBackground", next.subtitleBackground)
            .putInt("seekStepSec", next.seekStepSec)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .putBoolean("resumePlayback", next.resumePlayback)
            .putInt("newCardsPerSession", next.newCardsPerSession)
            .putBoolean("showLearnedInGames", next.showLearnedInGames)
            .putString("theme", next.theme.name)
            .apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return sanitize(
            AppSettings(
                targetLang = prefs.getString("targetLang", d.targetLang) ?: d.targetLang,
                showCefrColors = prefs.getBoolean("showCefrColors", d.showCefrColors),
                minCefrLevel = prefs.getString("minCefrLevel", d.minCefrLevel) ?: d.minCefrLevel,
                dualSubtitles = prefs.getBoolean("dualSubtitles", d.dualSubtitles),
                autoPauseOnWordTap = prefs.getBoolean("autoPauseOnWordTap", d.autoPauseOnWordTap),
                autoSpeakWord = prefs.getBoolean("autoSpeakWord", d.autoSpeakWord),
                subtitleTextSp = prefs.getInt("subtitleTextSp", d.subtitleTextSp),
                subtitleBackground = prefs.getBoolean("subtitleBackground", d.subtitleBackground),
                seekStepSec = prefs.getInt("seekStepSec", d.seekStepSec),
                keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
                resumePlayback = prefs.getBoolean("resumePlayback", d.resumePlayback),
                newCardsPerSession = prefs.getInt("newCardsPerSession", d.newCardsPerSession),
                showLearnedInGames = prefs.getBoolean("showLearnedInGames", d.showLearnedInGames),
                theme = try {
                    ThemeMode.valueOf(prefs.getString("theme", d.theme.name) ?: d.theme.name)
                } catch (e: IllegalArgumentException) {
                    d.theme
                },
            )
        )
    }

    private fun sanitize(s: AppSettings): AppSettings = s.copy(
        targetLang = if (Languages.TARGETS.any { it.first == s.targetLang }) s.targetLang else "fa",
        minCefrLevel = if (s.minCefrLevel in Cefr.LEVELS) s.minCefrLevel else "B1",
        subtitleTextSp = s.subtitleTextSp.coerceIn(14, 40),
        seekStepSec = s.seekStepSec.coerceIn(2, 30),
        newCardsPerSession = s.newCardsPerSession.coerceIn(0, 100),
    )
}
