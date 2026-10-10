@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.proudvocab.mobile.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R
import app.proudvocab.mobile.core.Cefr
import app.proudvocab.mobile.core.Exporter
import app.proudvocab.mobile.core.SavedWord
import java.util.Locale

private fun writeText(context: Context, uri: Uri?, text: String?): Boolean {
    if (uri == null || text == null) return false
    return try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    } catch (e: Exception) {
        false
    }
}

private fun readText(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
} catch (e: Exception) {
    null
}

@Composable
fun WordsScreen(container: AppContainer) {
    val context = LocalContext.current
    val data by container.words.data.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 all, 1 hard, 2 learned, 3 not learned
    var level by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<SavedWord?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    fun finishExport(uri: Uri?) {
        val ok = writeText(context, uri, pendingExport)
        pendingExport = null
        if (uri != null) context.toast(context.getString(if (ok) R.string.export_done else R.string.export_failed))
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { finishExport(it) }
    val ankiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { finishExport(it) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { finishExport(it) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = readText(context, uri)
            val result = if (text == null) null else container.words.importJson(text)
            if (result == null || !result.ok) {
                context.toast(context.getString(R.string.import_failed))
            } else {
                context.toast(context.getString(R.string.import_done, result.added, result.skipped))
            }
        }
    }

    val words = data.words
    val shown = remember(words, query, filter, level) {
        val q = query.trim().lowercase()
        words.filter { w ->
            (q.isEmpty() || w.word.lowercase().contains(q) || w.translation.lowercase().contains(q)) &&
                when (filter) {
                    1 -> w.hard
                    2 -> w.learned
                    3 -> !w.learned
                    else -> true
                } &&
                (level.isEmpty() || w.cefrLevel.equals(level, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.words_title, words.size),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) { AppIcon(R.drawable.ic_more_vert, stringResource(R.string.cd_more)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.words_export_csv)) },
                        leadingIcon = { AppIcon(R.drawable.ic_download, null) },
                        onClick = { menuOpen = false; pendingExport = Exporter.csv(words); csvLauncher.launch("proudvocab_words.csv") },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.words_export_anki)) },
                        leadingIcon = { AppIcon(R.drawable.ic_download, null) },
                        onClick = { menuOpen = false; pendingExport = Exporter.ankiTsv(words); ankiLauncher.launch("proudvocab_anki.txt") },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.words_backup)) },
                        leadingIcon = { AppIcon(R.drawable.ic_download, null) },
                        onClick = { menuOpen = false; pendingExport = container.words.exportJson(); jsonLauncher.launch("proudvocab_backup.json") },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.words_restore)) },
                        leadingIcon = { AppIcon(R.drawable.ic_upload, null) },
                        onClick = { menuOpen = false; importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.words_clear_all)) },
                        leadingIcon = { AppIcon(R.drawable.ic_delete, null) },
                        enabled = words.isNotEmpty(),
                        onClick = { menuOpen = false; confirmClear = true },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { AppIcon(R.drawable.ic_search, null) },
            placeholder = { Text(stringResource(R.string.words_search)) },
        )

        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val labels = listOf(R.string.filter_all, R.string.filter_hard, R.string.filter_learned, R.string.filter_unlearned)
            labels.forEachIndexed { i, res ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(stringResource(res)) })
            }
            val allLevels = stringResource(R.string.filter_all_levels)
            OptionDropdown(
                selectedLabel = if (level.isEmpty()) allLevels else level,
                options = listOf("" to allLevels) + Cefr.LEVELS.map { it to it },
                onSelect = { level = it },
            )
        }

        if (words.isEmpty()) {
            EmptyState(R.drawable.ic_words, stringResource(R.string.words_empty_title), stringResource(R.string.words_empty_body))
        } else if (shown.isEmpty()) {
            EmptyState(R.drawable.ic_search, stringResource(R.string.words_no_match), "")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { w ->
                    WordCard(
                        word = w,
                        onSpeak = { container.speech.speak(w.word) { context.toast(context.getString(R.string.tts_unavailable)) } },
                        onToggleLearned = { container.words.setLearned(w.id, !w.learned) },
                        onToggleHard = { container.words.setHard(w.id, !w.hard) },
                        onEdit = { editing = w },
                        onDelete = { container.words.delete(w.id) },
                    )
                }
            }
        }
    }

    editing?.let { w ->
        var tr by remember(w.id) { mutableStateOf(w.translation) }
        var ctx by remember(w.id) { mutableStateOf(w.context) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(w.word) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(tr, { tr = it }, label = { Text(stringResource(R.string.edit_translation)) })
                    OutlinedTextField(ctx, { ctx = it }, label = { Text(stringResource(R.string.edit_context)) })
                }
            },
            confirmButton = {
                TextButton(onClick = { container.words.editWord(w.id, tr, ctx); editing = null }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.words_clear_title),
            text = stringResource(R.string.words_clear_text, words.size),
            confirmLabel = stringResource(R.string.action_delete),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { container.words.clearAllWords(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun WordCard(
    word: SavedWord,
    onSpeak: () -> Unit,
    onToggleLearned: () -> Unit,
    onToggleHard: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                CefrBadge(word.cefrLevel)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSpeak) { AppIcon(R.drawable.ic_volume_up, stringResource(R.string.cd_speak)) }
            }
            if (word.translation.isNotBlank()) {
                Text(word.translation, style = MaterialTheme.typography.bodyLarge)
            }
            if (word.context.isNotBlank()) {
                Text(
                    word.context,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (word.videoTitle.isNotBlank()) {
                val time = if (word.videoTimeSec >= 0) {
                    "  ·  " + String.format(Locale.US, "%02d:%02d", word.videoTimeSec / 60, word.videoTimeSec % 60)
                } else ""
                Text(
                    word.videoTitle + time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onToggleLearned) {
                    AppIcon(
                        R.drawable.ic_check_circle,
                        stringResource(R.string.cd_learned),
                        tint = if (word.learned) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                IconButton(onClick = onToggleHard) {
                    AppIcon(
                        if (word.hard) R.drawable.ic_star else R.drawable.ic_star_border,
                        stringResource(R.string.cd_hard),
                        tint = if (word.hard) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) { AppIcon(R.drawable.ic_edit, stringResource(R.string.action_edit)) }
                IconButton(onClick = onDelete) { AppIcon(R.drawable.ic_delete, stringResource(R.string.action_delete)) }
            }
        }
    }
}
