@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.proudvocab.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R
import app.proudvocab.mobile.core.Choices
import app.proudvocab.mobile.core.DictationResult
import app.proudvocab.mobile.core.Exp
import app.proudvocab.mobile.core.GameLogic
import app.proudvocab.mobile.core.GameType
import app.proudvocab.mobile.core.MemoryCard
import app.proudvocab.mobile.core.SavedWord
import kotlinx.coroutines.delay

private val Green = Color(0xFF22C55E)
private val Red = Color(0xFFEF4444)

private data class GameMeta(val icon: Int, val title: Int, val desc: Int)

private fun metaOf(type: GameType): GameMeta = when (type) {
    GameType.MULTIPLE_CHOICE -> GameMeta(R.drawable.ic_check_circle, R.string.game_mc_title, R.string.game_mc_desc)
    GameType.FILL_BLANK -> GameMeta(R.drawable.ic_edit, R.string.game_fill_title, R.string.game_fill_desc)
    GameType.SCRAMBLE -> GameMeta(R.drawable.ic_replay, R.string.game_scramble_title, R.string.game_scramble_desc)
    GameType.MATCH -> GameMeta(R.drawable.ic_games, R.string.game_match_title, R.string.game_match_desc)
    GameType.DICTATION -> GameMeta(R.drawable.ic_volume_up, R.string.game_dictation_title, R.string.game_dictation_desc)
    GameType.MEMORY -> GameMeta(R.drawable.ic_star, R.string.game_memory_title, R.string.game_memory_desc)
}

// -------------------------------------------------------------------------- hub

@Composable
fun GamesHome(container: AppContainer, onStart: (GameType) -> Unit) {
    val data by container.words.data.collectAsState()
    val settings by container.settings.state.collectAsState()
    val playable = GameLogic.canPlay(data.words, settings.showLearnedInGames)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.games_include_learned), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = settings.showLearnedInGames,
                onCheckedChange = { v -> container.settings.update { it.copy(showLearnedInGames = v) } },
            )
        }
        if (!playable) {
            Text(
                stringResource(R.string.games_need_words, GameLogic.MIN_WORDS),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        GameType.values().forEach { type ->
            val meta = metaOf(type)
            Card(
                onClick = { onStart(type) },
                enabled = playable,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(meta.icon, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(meta.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(meta.desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------- session

@Composable
fun GameSession(container: AppContainer, type: GameType, onRestart: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val includeLearned = remember { container.settings.current.showLearnedInGames }
    val all = remember { container.words.data.value.words }
    val pool = remember { GameLogic.eligible(all, includeLearned) }
    val round = remember { GameLogic.pickRound(all, type, includeLearned) }
    val paged = type == GameType.MATCH || type == GameType.MEMORY
    val pages = remember { GameLogic.pages(round) }
    val steps = if (paged) pages.size else round.size

    var step by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    val missed = remember { mutableStateListOf<SavedWord>() }

    fun record(w: SavedWord, correct: Boolean) {
        container.words.recordGameAnswer(w.id, correct)
        if (correct) score++ else if (missed.none { it.id == w.id }) missed.add(w)
    }

    val finished = round.isEmpty() || step >= steps
    LaunchedEffect(finished) {
        if (finished && round.isNotEmpty()) container.words.recordGameFinished()
    }
    if (finished) {
        GameResult(score, round.size, missed, onRestart, onExit)
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { AppIcon(R.drawable.ic_close, stringResource(R.string.action_close)) }
            LinearProgressIndicator(progress = { step.toFloat() / steps.toFloat() }, modifier = Modifier.weight(1f))
            Text("  ${stringResource(R.string.game_score, score)}", style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            key(step) {
                when (type) {
                    GameType.MULTIPLE_CHOICE -> {
                        val w = round[step]
                        val choices = remember { GameLogic.buildChoices(w.translation, pool.map { it.translation }) }
                        ChoiceQuestion(
                            title = stringResource(R.string.game_mc_question),
                            prompt = w.word,
                            hint = null,
                            onSpeak = { container.speech.speak(w.word) { context.toast(context.getString(R.string.tts_unavailable)) } },
                            choices = choices,
                        ) { ok -> record(w, ok); step++ }
                    }
                    GameType.FILL_BLANK -> {
                        val w = round[step]
                        val choices = remember { GameLogic.buildChoices(w.word, pool.map { it.word }) }
                        val masked = remember { if (GameLogic.hasUsableContext(w)) GameLogic.maskWord(w.context, w.word) else "" }
                        ChoiceQuestion(
                            title = stringResource(R.string.game_fill_question),
                            prompt = masked.ifEmpty { GameLogic.BLANK },
                            hint = w.translation,
                            onSpeak = null,
                            choices = choices,
                        ) { ok -> record(w, ok); step++ }
                    }
                    GameType.SCRAMBLE -> ScrambleQuestion(round[step]) { ok -> record(round[step], ok); step++ }
                    GameType.DICTATION -> DictationQuestion(container, round[step]) { ok -> record(round[step], ok); step++ }
                    GameType.MATCH -> MatchPage(pages[step], onWord = { w, ok -> record(w, ok) }) { step++ }
                    GameType.MEMORY -> MemoryPage(pages[step], onWord = { w, ok -> record(w, ok) }) { step++ }
                }
            }
        }
    }
}

@Composable
private fun GameResult(score: Int, total: Int, missed: List<SavedWord>, onRestart: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        AppIcon(R.drawable.ic_star, null, Modifier.size(64.dp), Color(0xFFF59E0B))
        Text(stringResource(R.string.game_result_title), style = MaterialTheme.typography.headlineSmall)
        Text("$score / $total", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.game_result_exp, score * Exp.PER_CORRECT))
        if (missed.isNotEmpty()) {
            Text(stringResource(R.string.game_result_missed), style = MaterialTheme.typography.titleMedium)
            missed.forEach { w ->
                Text("${w.word} — ${w.translation}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.game_play_again))
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

// ---------------------------------------------------------------------- questions

@Composable
private fun ChoiceQuestion(
    title: String,
    prompt: String,
    hint: String?,
    onSpeak: (() -> Unit)?,
    choices: Choices,
    onResult: (Boolean) -> Unit,
) {
    var picked by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(picked) {
        val p = picked
        if (p != null) {
            delay(1000)
            onResult(p == choices.correctIndex)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(prompt, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
            if (onSpeak != null) {
                IconButton(onClick = onSpeak) { AppIcon(R.drawable.ic_volume_up, stringResource(R.string.cd_speak)) }
            }
        }
        if (!hint.isNullOrBlank()) {
            Text(hint, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        choices.options.forEachIndexed { i, option ->
            val state = picked
            val container = when {
                state == null -> MaterialTheme.colorScheme.surfaceVariant
                i == choices.correctIndex -> Green
                i == state -> Red
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val content = if (state != null && (i == choices.correctIndex || i == state)) Color.White else MaterialTheme.colorScheme.onSurface
            Button(
                onClick = { if (picked == null) picked = i },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
            ) { Text(option, fontSize = 17.sp, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun ScrambleQuestion(w: SavedWord, onResult: (Boolean) -> Unit) {
    val letters = remember { GameLogic.scramble(w.word) }
    val target = remember { w.word.filter { it.isLetter() }.lowercase() }
    val chosen = remember { mutableStateListOf<Int>() }
    var result by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(result) {
        val r = result
        if (r != null) {
            delay(1200)
            onResult(r)
        }
    }
    LaunchedEffect(Unit) { if (letters.isEmpty()) onResult(true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.game_scramble_question), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(w.translation, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
        val answer = chosen.joinToString("") { letters[it].toString() }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when (result) {
                true -> Green
                false -> Red
                null -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    answer.ifEmpty { "…" },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = if (result == null) MaterialTheme.colorScheme.onSurface else Color.White,
                )
            }
        }
        if (result == false) {
            Text(stringResource(R.string.game_correct_answer, w.word), color = Red, fontWeight = FontWeight.Bold)
        }
        // Letter tiles are always laid out left-to-right (English letters).
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                letters.forEachIndexed { i, ch ->
                    FilledTonalButton(
                        onClick = { chosen.add(i) },
                        enabled = i !in chosen && result == null,
                        modifier = Modifier.size(52.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(ch.toString().uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (chosen.isNotEmpty()) chosen.removeAt(chosen.size - 1) },
                enabled = chosen.isNotEmpty() && result == null,
            ) { Text(stringResource(R.string.game_undo)) }
            Button(
                onClick = { result = answer == target },
                enabled = chosen.size == letters.size && result == null,
            ) { Text(stringResource(R.string.game_check)) }
        }
    }
}

@Composable
private fun DictationQuestion(container: AppContainer, w: SavedWord, onResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<DictationResult?>(null) }
    val speak = { container.speech.speak(w.word) { context.toast(context.getString(R.string.tts_unavailable)) } }
    LaunchedEffect(Unit) { speak() }

    fun check() {
        if (verdict == null && input.isNotBlank()) verdict = GameLogic.checkDictation(input, w.word)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.game_dictation_question), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledIconButton(onClick = { speak() }, modifier = Modifier.size(72.dp)) {
            AppIcon(R.drawable.ic_volume_up, stringResource(R.string.cd_speak), Modifier.size(36.dp), MaterialTheme.colorScheme.onPrimary)
        }
        if (w.translation.isNotBlank()) {
            Text(w.translation, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        OutlinedTextField(
            value = input,
            onValueChange = { if (verdict == null) input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = verdict == null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            placeholder = { Text(stringResource(R.string.game_dictation_hint)) },
        )
        when (verdict) {
            DictationResult.CORRECT -> Text(stringResource(R.string.game_correct), color = Green, fontWeight = FontWeight.Bold)
            DictationResult.TYPO -> Text(stringResource(R.string.game_almost, w.word), color = Green, fontWeight = FontWeight.Bold)
            DictationResult.WRONG -> Text(stringResource(R.string.game_correct_answer, w.word), color = Red, fontWeight = FontWeight.Bold)
            null -> {}
        }
        if (verdict == null) {
            Button(onClick = { check() }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.game_check))
            }
            TextButton(onClick = { onResult(false) }) { Text(stringResource(R.string.game_skip)) }
        } else {
            Button(onClick = { onResult(verdict != DictationResult.WRONG) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.game_continue))
            }
        }
    }
}

// --------------------------------------------------------------------- pair games

@Composable
private fun GameTile(text: String, state: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // state: 0 normal, 1 selected, 2 matched, 3 wrong
    val color = when (state) {
        2 -> Green
        3 -> Red
        1 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = state == 0 || state == 1,
        shape = RoundedCornerShape(12.dp),
        color = color,
        border = if (state == 1) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                color = if (state >= 2) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MatchPage(words: List<SavedWord>, onWord: (SavedWord, Boolean) -> Unit, onDone: () -> Unit) {
    val left = remember { words.shuffled() }
    val right = remember { words.shuffled() }
    var selected by remember { mutableStateOf<String?>(null) }
    val matched = remember { mutableStateListOf<String>() }
    val wrong = remember { mutableStateListOf<String>() }

    LaunchedEffect(matched.size + wrong.size) {
        if (words.isNotEmpty() && matched.size + wrong.size >= words.size) {
            delay(800)
            onDone()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.game_match_question),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                left.forEach { w ->
                    val state = when {
                        w.id in matched -> 2
                        w.id in wrong -> 3
                        selected == w.id -> 1
                        else -> 0
                    }
                    GameTile(w.word, state) { selected = if (selected == w.id) null else w.id }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                right.forEach { w ->
                    val state = when {
                        w.id in matched -> 2
                        w.id in wrong -> 3
                        else -> 0
                    }
                    GameTile(w.translation, state) {
                        val sel = selected
                        if (sel != null) {
                            if (sel == w.id) {
                                matched.add(sel)
                                onWord(w, true)
                            } else {
                                wrong.add(sel)
                                words.firstOrNull { it.id == sel }?.let { onWord(it, false) }
                            }
                            selected = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryPage(words: List<SavedWord>, onWord: (SavedWord, Boolean) -> Unit, onDone: () -> Unit) {
    val deck = remember { GameLogic.memoryDeck(words) }
    val flipped = remember { mutableStateListOf<Int>() }
    val matched = remember { mutableStateListOf<String>() }
    val mismatches = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(flipped.size) {
        if (flipped.size == 2) {
            val a = deck.first { it.id == flipped[0] }
            val b = deck.first { it.id == flipped[1] }
            if (a.wordKey == b.wordKey && a.isWord != b.isWord) {
                matched.add(a.wordKey)
                flipped.clear()
            } else {
                mismatches[a.wordKey] = (mismatches[a.wordKey] ?: 0) + 1
                mismatches[b.wordKey] = (mismatches[b.wordKey] ?: 0) + 1
                delay(700)
                flipped.clear()
            }
        }
    }
    LaunchedEffect(matched.size) {
        if (words.isNotEmpty() && matched.size >= words.size) {
            delay(600)
            // a word counts as remembered when it caused at most one wrong flip
            words.forEach { w -> onWord(w, (mismatches[w.key] ?: 0) <= 1) }
            onDone()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.game_memory_question),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        deck.chunked(2).forEach { rowCards ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCards.forEach { card -> MemoryTile(card, flipped, matched, Modifier.weight(1f)) }
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MemoryTile(card: MemoryCard, flipped: MutableList<Int>, matched: List<String>, modifier: Modifier) {
    val isMatched = card.wordKey in matched
    val faceUp = isMatched || card.id in flipped
    Box(modifier) {
        GameTile(
            text = if (faceUp) card.text else "?",
            state = if (isMatched) 2 else if (faceUp) 1 else 0,
        ) {
            if (!faceUp && flipped.size < 2) flipped.add(card.id)
        }
    }
}
