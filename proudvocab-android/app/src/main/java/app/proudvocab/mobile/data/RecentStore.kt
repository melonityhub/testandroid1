package app.proudvocab.mobile.data

import app.proudvocab.mobile.core.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecentVideo(
    val uri: String,
    val name: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val subtitleUri: String? = null,
    val subtitleName: String? = null,
    val lastOpened: Long = 0L,
)

/** Recently opened videos with their resume position and chosen subtitle file. */
class RecentStore(private val store: JsonStore) {
    private val _items = MutableStateFlow<List<RecentVideo>>(emptyList())
    val items: StateFlow<List<RecentVideo>> = _items.asStateFlow()

    init {
        val root = store.read()?.let { Json.parseOrNull(it) } as? List<*>
        _items.value = root.orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val uri = Json.str(m, "uri")
            if (uri.isEmpty()) null else RecentVideo(
                uri = uri,
                name = Json.str(m, "name", "video"),
                positionMs = Json.long(m, "positionMs"),
                durationMs = Json.long(m, "durationMs"),
                subtitleUri = Json.strOrNull(m, "subtitleUri"),
                subtitleName = Json.strOrNull(m, "subtitleName"),
                lastOpened = Json.long(m, "lastOpened"),
            )
        }
    }

    fun find(uri: String): RecentVideo? = _items.value.firstOrNull { it.uri == uri }

    private fun mutate(transform: (List<RecentVideo>) -> List<RecentVideo>) {
        _items.update(transform)
        store.scheduleWrite { serialize(_items.value) }
    }

    fun open(uri: String, name: String, subtitleUri: String?, subtitleName: String?) = mutate { list ->
        val old = list.firstOrNull { it.uri == uri }
        val item = RecentVideo(
            uri = uri,
            name = name.ifBlank { old?.name ?: "video" },
            positionMs = old?.positionMs ?: 0L,
            durationMs = old?.durationMs ?: 0L,
            subtitleUri = subtitleUri ?: old?.subtitleUri,
            subtitleName = if (subtitleUri != null) subtitleName else old?.subtitleName,
            lastOpened = System.currentTimeMillis(),
        )
        (listOf(item) + list.filterNot { it.uri == uri }).take(MAX_ITEMS)
    }

    fun setSubtitle(uri: String, subtitleUri: String?, subtitleName: String?) = mutate { list ->
        list.map { if (it.uri == uri) it.copy(subtitleUri = subtitleUri, subtitleName = subtitleName) else it }
    }

    fun updateProgress(uri: String, positionMs: Long, durationMs: Long) = mutate { list ->
        list.map {
            if (it.uri == uri) {
                it.copy(positionMs = positionMs.coerceAtLeast(0L), durationMs = durationMs.coerceAtLeast(0L))
            } else {
                it
            }
        }
    }

    fun remove(uri: String) = mutate { list -> list.filterNot { it.uri == uri } }

    fun flushNow() = store.flushNow()

    private fun serialize(list: List<RecentVideo>): String = Json.stringify(
        list.map {
            linkedMapOf(
                "uri" to it.uri,
                "name" to it.name,
                "positionMs" to it.positionMs,
                "durationMs" to it.durationMs,
                "subtitleUri" to it.subtitleUri,
                "subtitleName" to it.subtitleName,
                "lastOpened" to it.lastOpened,
            )
        }
    )

    companion object {
        private const val MAX_ITEMS = 40
    }
}
