package app.proudvocab.mobile.data

import android.content.res.AssetManager
import app.proudvocab.mobile.core.Cefr
import app.proudvocab.mobile.core.CefrLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads the bundled CEFR word list (8.8k words) on a background thread; null until ready. */
class CefrRepository(private val assets: AssetManager, scope: CoroutineScope) {
    private val _lookup = MutableStateFlow<CefrLookup?>(null)
    val lookup: StateFlow<CefrLookup?> = _lookup.asStateFlow()

    init {
        scope.launch {
            _lookup.value = try {
                CefrLookup(
                    Cefr.parseTsv(readAsset("cefr_map.tsv")),
                    Cefr.parseTsv(readAsset("irregular_forms.tsv")),
                )
            } catch (e: Exception) {
                CefrLookup(emptyMap(), emptyMap()) // app stays usable, just without level colours
            }
        }
    }

    private fun readAsset(name: String): String =
        assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
