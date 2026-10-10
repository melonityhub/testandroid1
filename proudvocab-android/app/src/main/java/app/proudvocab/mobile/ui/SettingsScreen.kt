@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.proudvocab.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R
import app.proudvocab.mobile.core.Cefr
import app.proudvocab.mobile.data.CrashLogger
import app.proudvocab.mobile.data.Languages
import app.proudvocab.mobile.data.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val s by container.settings.state.collectAsState()
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
    var crashText by remember { mutableStateOf<String?>(null) }
    var crashAvailable by remember { mutableStateOf(CrashLogger.read(context) != null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall)

        SectionHeader(R.string.settings_section_translation)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_target_lang), Modifier.weight(1f))
            OptionDropdown(
                selectedLabel = Languages.nameOf(s.targetLang),
                options = Languages.TARGETS,
                onSelect = { v -> container.settings.update { it.copy(targetLang = v) } },
            )
        }
        Text(
            stringResource(R.string.settings_translation_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader(R.string.settings_section_subtitles)
        SwitchRow(R.string.settings_cefr_colors, R.string.settings_cefr_colors_desc, s.showCefrColors) { v ->
            container.settings.update { it.copy(showCefrColors = v) }
        }
        if (s.showCefrColors) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_min_level), Modifier.weight(1f))
                OptionDropdown(
                    selectedLabel = s.minCefrLevel,
                    options = Cefr.LEVELS.map { it to it },
                    onSelect = { v -> container.settings.update { it.copy(minCefrLevel = v) } },
                )
            }
        }
        SwitchRow(R.string.settings_dual, R.string.settings_dual_desc, s.dualSubtitles) { v ->
            container.settings.update { it.copy(dualSubtitles = v) }
        }
        SwitchRow(R.string.settings_sub_bg, null, s.subtitleBackground) { v ->
            container.settings.update { it.copy(subtitleBackground = v) }
        }
        SliderRow(
            label = stringResource(R.string.settings_text_size, s.subtitleTextSp),
            value = s.subtitleTextSp.toFloat(),
            range = 14f..40f,
        ) { v -> container.settings.update { it.copy(subtitleTextSp = v.roundToInt()) } }

        SectionHeader(R.string.settings_section_player)
        SwitchRow(R.string.settings_auto_pause, R.string.settings_auto_pause_desc, s.autoPauseOnWordTap) { v ->
            container.settings.update { it.copy(autoPauseOnWordTap = v) }
        }
        SwitchRow(R.string.settings_auto_speak, null, s.autoSpeakWord) { v ->
            container.settings.update { it.copy(autoSpeakWord = v) }
        }
        SwitchRow(R.string.settings_keep_screen, null, s.keepScreenOn) { v ->
            container.settings.update { it.copy(keepScreenOn = v) }
        }
        SwitchRow(R.string.settings_resume, null, s.resumePlayback) { v ->
            container.settings.update { it.copy(resumePlayback = v) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_seek_step), Modifier.weight(1f))
            OptionDropdown(
                selectedLabel = stringResource(R.string.settings_seconds, s.seekStepSec),
                options = listOf(3, 5, 10, 15, 30).map { it.toString() to context.getString(R.string.settings_seconds, it) },
                onSelect = { v -> container.settings.update { it.copy(seekStepSec = v.toInt()) } },
            )
        }

        SectionHeader(R.string.settings_section_study)
        SliderRow(
            label = stringResource(R.string.settings_new_cards, s.newCardsPerSession),
            value = s.newCardsPerSession.toFloat(),
            range = 0f..100f,
        ) { v -> container.settings.update { it.copy(newCardsPerSession = v.roundToInt()) } }

        SectionHeader(R.string.settings_section_appearance)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = s.theme == mode,
                    onClick = { container.settings.update { it.copy(theme = mode) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        SectionHeader(R.string.settings_section_privacy)
        Text(stringResource(R.string.settings_privacy_text), style = MaterialTheme.typography.bodyMedium)

        SectionHeader(R.string.settings_section_about)
        Text(stringResource(R.string.settings_about_version, version), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_about_free),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (crashAvailable) {
            OutlinedButton(onClick = { crashText = CrashLogger.read(context) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_show_crash))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    crashText?.let { text ->
        AlertDialog(
            onDismissRequest = { crashText = null },
            title = { Text(stringResource(R.string.settings_crash_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    context.toast(context.getString(R.string.settings_crash_copied))
                }) { Text(stringResource(R.string.action_copy)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        CrashLogger.clear(context)
                        crashAvailable = false
                        crashText = null
                    }) { Text(stringResource(R.string.action_delete)) }
                    TextButton(onClick = { crashText = null }) { Text(stringResource(R.string.action_close)) }
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(textRes: Int) {
    Spacer(Modifier.height(14.dp))
    Text(stringResource(textRes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider()
}

@Composable
private fun SwitchRow(labelRes: Int, descRes: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
            if (descRes != null) {
                Text(
                    stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        // keep the thumb responsive while dragging, persist when the value settles on a new integer
        var local by remember(value) { mutableStateOf(value) }
        Slider(
            value = local,
            onValueChange = {
                local = it
                if (it.roundToInt() != value.roundToInt()) onChange(it)
            },
            valueRange = range,
        )
    }
}
