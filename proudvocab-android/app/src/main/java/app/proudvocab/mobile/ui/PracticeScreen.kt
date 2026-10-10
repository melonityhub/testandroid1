@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.proudvocab.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R
import app.proudvocab.mobile.core.Cefr
import app.proudvocab.mobile.core.Exp
import app.proudvocab.mobile.core.GameType
import app.proudvocab.mobile.core.SavedWord
import app.proudvocab.mobile.core.Srs

@Composable
fun PracticeScreen(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var reviewActive by rememberSaveable { mutableStateOf(false) }
    var game by rememberSaveable { mutableStateOf<String?>(null) }
    var gameRun by rememberSaveable { mutableStateOf(0) }

    val activeGame = game?.let { name -> GameType.values().firstOrNull { it.name == name } }

    when {
        reviewActive -> {
            BackHandler { reviewActive = false }
            ReviewSession(container) { reviewActive = false }
        }
        activeGame != null -> {
            BackHandler { game = null }
            key(gameRun) { GameSession(container, activeGame, { gameRun++ }, { game = null }) }
        }
        else -> Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.practice_review, R.string.practice_games, R.string.practice_stats).forEachIndexed { i, res ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) })
                }
            }
            when (tab) {
                0 -> ReviewHome(container) { reviewActive = true }
                1 -> GamesHome(container) { game = it.name }
                else -> StatsTab(container)
            }
        }
    }
}

// ----------------------------------------------------------------------- review

@Composable
private fun ReviewHome(container: AppContainer, onStart: () -> Unit) {
    val data by container.words.data.collectAsState()
    val settings by container.settings.state.collectAsState()
    val now = System.currentTimeMillis()
    val due = Srs.dueCount(data.words, now)
    val fresh = minOf(Srs.newCount(data.words), settings.newCardsPerSession)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (data.words.isEmpty()) {
            EmptyState(R.drawable.ic_school, stringResource(R.string.review_no_words_title), stringResource(R.string.review_no_words_body))
            return@Column
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.review_today), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Counter(due, stringResource(R.string.review_due))
                    Counter(fresh, stringResource(R.string.review_new))
                    Counter(data.streak.current, stringResource(R.string.review_streak))
                }
            }
        }
        Button(
            onClick = onStart,
            enabled = due + fresh > 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.review_start)) }
        if (due + fresh == 0) {
            Text(
                stringResource(R.string.review_all_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Counter(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ReviewSession(container: AppContainer, onExit: () -> Unit) {
    val context = LocalContext.current
    val settings by container.settings.state.collectAsState()
    val queue = remember {
        mutableStateListOf<SavedWord>().apply {
            addAll(Srs.buildQueue(container.words.data.value.words, System.currentTimeMillis(), settings.newCardsPerSession))
        }
    }
    var index by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var reviewed by remember { mutableStateOf(0) }
    var remembered by remember { mutableStateOf(0) }

    if (index >= queue.size) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppIcon(R.drawable.ic_check_circle, null, Modifier.size(72.dp), Color(0xFF22C55E))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.review_done_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.review_done_stats, reviewed, remembered), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.action_done))
            }
        }
        return
    }

    val card = queue[index]
    fun rate(rating: Int) {
        val now = System.currentTimeMillis()
        container.words.rate(card.id, rating)
        reviewed++
        if (rating >= Srs.GOOD) remembered++
        if (rating == Srs.AGAIN) queue.add(Srs.next(card, Srs.AGAIN, now)) // see it again this session
        flipped = false
        index++
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { AppIcon(R.drawable.ic_close, stringResource(R.string.action_close)) }
            LinearProgressIndicator(
                progress = { index.toFloat() / queue.size.toFloat() },
                modifier = Modifier.weight(1f),
            )
            Text("  ${index + 1}/${queue.size}", style = MaterialTheme.typography.labelMedium)
        }
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f).clickable { flipped = !flipped },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.word, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    CefrBadge(card.cefrLevel)
                }
                IconButton(onClick = {
                    container.speech.speak(card.word) { context.toast(context.getString(R.string.tts_unavailable)) }
                }) { AppIcon(R.drawable.ic_volume_up, stringResource(R.string.cd_speak)) }
                if (flipped) {
                    Spacer(Modifier.height(12.dp))
                    Text(card.translation.ifBlank { "—" }, fontSize = 26.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                    if (card.context.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            card.context,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.review_tap_to_flip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (flipped) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RateButton(R.string.rate_again, Srs.previewDays(card, Srs.AGAIN), Modifier.weight(1f)) { rate(Srs.AGAIN) }
                RateButton(R.string.rate_hard, Srs.previewDays(card, Srs.HARD), Modifier.weight(1f)) { rate(Srs.HARD) }
                RateButton(R.string.rate_good, Srs.previewDays(card, Srs.GOOD), Modifier.weight(1f)) { rate(Srs.GOOD) }
                RateButton(R.string.rate_easy, Srs.previewDays(card, Srs.EASY), Modifier.weight(1f)) { rate(Srs.EASY) }
            }
            TextButton(
                onClick = {
                    container.words.setLearned(card.id, true)
                    flipped = false
                    index++
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.review_mark_learned)) }
        } else {
            Button(onClick = { flipped = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.review_show_answer))
            }
        }
    }
}

@Composable
private fun RateButton(labelRes: Int, days: Double, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(labelRes), fontWeight = FontWeight.SemiBold)
            Text(Srs.intervalLabel(days), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ------------------------------------------------------------------------ stats

@Composable
private fun StatsTab(container: AppContainer) {
    val data by container.words.data.collectAsState()
    val now = System.currentTimeMillis()
    val words = data.words
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stats_level, data.stats.level), style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(
                    progress = { Exp.progressInLevel(data.stats.totalExp).toFloat() / Exp.EXP_PER_LEVEL.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.stats_exp, data.stats.totalExp, data.stats.gamesPlayed, data.stats.correctAnswers),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(words.size, stringResource(R.string.stats_total))
            StatTile(words.count { it.learned }, stringResource(R.string.stats_learned))
            StatTile(words.count { it.hard }, stringResource(R.string.stats_hard))
            StatTile(Srs.dueCount(words, now), stringResource(R.string.review_due))
            StatTile(data.streak.current, stringResource(R.string.review_streak))
            StatTile(data.streak.best, stringResource(R.string.stats_best_streak))
        }
        Text(stringResource(R.string.stats_by_level), style = MaterialTheme.typography.titleMedium)
        val counts = Cefr.LEVELS.associateWith { lv -> words.count { it.cefrLevel.equals(lv, ignoreCase = true) } }
        val unknown = words.count { Cefr.rank(it.cefrLevel) == 0 }
        val maxCount = maxOf(1, counts.values.maxOrNull() ?: 0, unknown)
        Cefr.LEVELS.forEach { lv -> LevelBar(lv, counts[lv] ?: 0, maxCount, Color(Cefr.colorArgb(lv))) }
        LevelBar(stringResource(R.string.stats_unknown_level), unknown, maxCount, Color(Cefr.colorArgb(null)))
    }
}

@Composable
private fun StatTile(value: Int, label: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.width(104.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LevelBar(label: String, count: Int, max: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { count.toFloat() / max.toFloat() },
            modifier = Modifier.weight(1f).height(10.dp),
            color = color,
        )
        Text(count.toString(), modifier = Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
    }
}
