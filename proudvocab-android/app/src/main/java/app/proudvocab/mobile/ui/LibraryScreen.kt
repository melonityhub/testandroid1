package app.proudvocab.mobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R
import app.proudvocab.mobile.core.FileKinds
import app.proudvocab.mobile.data.RecentVideo

@Composable
fun LibraryScreen(
    container: AppContainer,
    onPlay: (uri: String, name: String, subtitleUri: String?, subtitleName: String?) -> Unit,
) {
    val context = LocalContext.current
    val recents by container.recents.items.collectAsState()
    var toDelete by remember { mutableStateOf<RecentVideo?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var video: Uri? = null
        var videoName = ""
        var sub: Uri? = null
        var subName = ""
        for (u in uris) {
            persistReadAccess(context, u)
            val name = displayName(context, u)
            val mime = try { context.contentResolver.getType(u) } catch (e: Exception) { null }
            when {
                FileKinds.isSubtitle(name) -> if (sub == null) { sub = u; subName = name }
                FileKinds.isMedia(name, mime) -> if (video == null) { video = u; videoName = name }
            }
        }
        val v = video
        if (v == null) {
            context.toast(context.getString(R.string.library_pick_video_first))
            return@rememberLauncherForActivityResult
        }
        container.recents.open(v.toString(), FileKinds.stripExtension(videoName), sub?.toString(), subName.ifEmpty { null })
        val rec = container.recents.find(v.toString())
        onPlay(v.toString(), rec?.name ?: FileKinds.stripExtension(videoName), rec?.subtitleUri, rec?.subtitleName)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.library_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    AppIcon(R.drawable.ic_add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library_open))
                }
                Text(
                    stringResource(R.string.library_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recents.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.library_recent), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (recents.isEmpty()) {
            item {
                EmptyState(
                    iconRes = R.drawable.ic_library,
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                )
            }
        }
        items(recents, key = { it.uri }) { item ->
            RecentCard(
                item = item,
                onClick = {
                    container.recents.open(item.uri, item.name, null, null)
                    onPlay(item.uri, item.name, item.subtitleUri, item.subtitleName)
                },
                onDelete = { toDelete = item },
            )
        }
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.library_remove_title),
            text = stringResource(R.string.library_remove_text, item.name),
            confirmLabel = stringResource(R.string.action_remove),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { container.recents.remove(item.uri); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun RecentCard(item: RecentVideo, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = item.subtitleName ?: stringResource(R.string.library_no_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${formatClock(item.positionMs)} / ${formatClock(item.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                AppIcon(R.drawable.ic_delete, stringResource(R.string.action_remove))
            }
        }
    }
}
