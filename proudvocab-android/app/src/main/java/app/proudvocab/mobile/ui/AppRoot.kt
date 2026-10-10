package app.proudvocab.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R

private data class TabSpec(val icon: Int, val label: Int)

private val TABS = listOf(
    TabSpec(R.drawable.ic_library, R.string.nav_library),
    TabSpec(R.drawable.ic_words, R.string.nav_words),
    TabSpec(R.drawable.ic_school, R.string.nav_practice),
    TabSpec(R.drawable.ic_settings, R.string.nav_settings),
)

@Composable
fun AppRoot(container: AppContainer) {
    val settings by container.settings.state.collectAsState()
    ProudVocabTheme(settings.theme) {
        var tab by rememberSaveable { mutableStateOf(0) }
        var videoUri by rememberSaveable { mutableStateOf<String?>(null) }
        var videoName by rememberSaveable { mutableStateOf("") }
        var subUri by rememberSaveable { mutableStateOf<String?>(null) }
        var subName by rememberSaveable { mutableStateOf<String?>(null) }

        val playing = videoUri
        if (playing != null) {
            PlayerScreen(
                container = container,
                videoUri = playing,
                videoName = videoName,
                subtitleUri = subUri,
                subtitleName = subName,
                onSubtitleChosen = { u, n ->
                    subUri = u
                    subName = n
                    container.recents.setSubtitle(playing, u, n)
                },
                onClose = {
                    container.flushAll()
                    videoUri = null
                },
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        TABS.forEachIndexed { index, spec ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { AppIcon(spec.icon, null) },
                                label = { Text(stringResource(spec.label)) },
                            )
                        }
                    }
                },
            ) { inner ->
                Box(Modifier.padding(inner).fillMaxSize()) {
                    when (tab) {
                        0 -> LibraryScreen(container) { uri, name, sUri, sName ->
                            videoName = name
                            subUri = sUri
                            subName = sName
                            videoUri = uri
                        }
                        1 -> WordsScreen(container)
                        2 -> PracticeScreen(container)
                        else -> SettingsScreen(container)
                    }
                }
            }
        }
    }
}
