@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.proudvocab.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R
import app.proudvocab.mobile.core.WordInfo
import app.proudvocab.mobile.core.Words
import app.proudvocab.mobile.data.AppSettings
import app.proudvocab.mobile.data.Languages

data class SelectedWord(val word: String, val context: String, val timeSec: Long)

/** Bottom sheet shown when a subtitle word is tapped: translation, level, dictionary info, save/remove. */
@Composable
fun WordSheet(
    container: AppContainer,
    selected: SelectedWord,
    settings: AppSettings,
    videoTitle: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lookup by container.cefr.lookup.collectAsState()
    val data by container.words.data.collectAsState()

    val shownWord = remember(selected.word) { Words.normalize(selected.word).ifEmpty { selected.word.trim() } }
    val level = remember(lookup, selected.word) { lookup?.lookupWord(selected.word) }
    val savedEntry = data.words.firstOrNull { it.key == shownWord }

    var translation by remember(shownWord) { mutableStateOf("") }
    var edited by remember(shownWord) { mutableStateOf(false) }
    var loading by remember(shownWord) { mutableStateOf(true) }
    var offline by remember(shownWord) { mutableStateOf(false) }
    var info by remember(shownWord) { mutableStateOf<WordInfo?>(null) }

    LaunchedEffect(shownWord, settings.targetLang) {
        loading = true
        val tr = container.translator.translate(shownWord, settings.targetLang, "en")
        if (!edited) translation = tr?.text.orEmpty()
        offline = tr == null
        loading = false
    }
    LaunchedEffect(shownWord) {
        info = container.dictionary.lookup(Words.lookupForm(selected.word))
    }
    LaunchedEffect(shownWord) {
        if (settings.autoSpeakWord) {
            container.speech.speak(shownWord) { context.toast(context.getString(R.string.tts_unavailable)) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(shownWord, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                CefrBadge(level)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    container.speech.speak(shownWord) { context.toast(context.getString(R.string.tts_unavailable)) }
                }) {
                    AppIcon(R.drawable.ic_volume_up, stringResource(R.string.cd_speak))
                }
            }

            val phonetic = info?.phonetic
            val pos = info?.partOfSpeech
            if (phonetic != null || pos != null) {
                Text(
                    listOfNotNull(phonetic, pos).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = translation,
                onValueChange = { translation = it; edited = true },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.word_translation, Languages.nameOf(settings.targetLang))) },
                trailingIcon = {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                },
                minLines = 1,
                maxLines = 3,
            )
            if (offline && !loading) {
                Text(
                    stringResource(R.string.word_offline_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (selected.context.isNotBlank()) {
                Text(
                    selected.context.replace('\n', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            info?.definition?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            val syn = info?.synonyms.orEmpty()
            if (syn.isNotEmpty()) {
                Text(
                    stringResource(R.string.word_synonyms, syn.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (savedEntry == null) {
                    Button(
                        onClick = {
                            container.words.saveWord(
                                word = shownWord,
                                translation = translation,
                                context = selected.context.replace('\n', ' '),
                                cefrLevel = level,
                                videoTitle = videoTitle,
                                videoTimeSec = selected.timeSec,
                            )
                            context.toast(context.getString(R.string.word_saved))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        AppIcon(R.drawable.ic_bookmark, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.word_save))
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            container.words.delete(savedEntry.id)
                            context.toast(context.getString(R.string.word_removed))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        AppIcon(R.drawable.ic_bookmark_border, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.word_remove))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}
