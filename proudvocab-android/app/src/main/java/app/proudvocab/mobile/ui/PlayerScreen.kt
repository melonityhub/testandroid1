@file:OptIn(ExperimentalLayoutApi::class, UnstableApi::class)

package app.proudvocab.mobile.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.proudvocab.mobile.AppContainer
import app.proudvocab.mobile.R
import app.proudvocab.mobile.core.Cefr
import app.proudvocab.mobile.core.CefrLookup
import app.proudvocab.mobile.core.Cue
import app.proudvocab.mobile.core.CueIndex
import app.proudvocab.mobile.core.SubtitleParser
import app.proudvocab.mobile.core.Tokenizer
import app.proudvocab.mobile.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Values that change every few hundred ms live in their own holder so only the readers recompose. */
@Stable
class PlaybackClock {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

private enum class SubState { NONE, LOADING, OK, EMPTY, ERROR }

private val PanelColor = Color(0xFF111827)
private const val MAX_SUBTITLE_BYTES = 8 * 1024 * 1024

private suspend fun loadCues(context: Context, uri: Uri, name: String): List<Cue>? = withContext(Dispatchers.IO) {
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        if (bytes.size > MAX_SUBTITLE_BYTES) return@withContext null
        SubtitleParser.parse(SubtitleParser.decode(bytes, name))
    } catch (e: Exception) {
        null
    }
}

@Composable
fun PlayerScreen(
    container: AppContainer,
    videoUri: String,
    videoName: String,
    subtitleUri: String?,
    subtitleName: String?,
    onSubtitleChosen: (uri: String, name: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val settings by container.settings.state.collectAsState()
    val cefr by container.cefr.lookup.collectAsState()
    val userData by container.words.data.collectAsState()
    val savedKeys = remember(userData.words) { userData.words.map { it.key }.toHashSet() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val clock = remember { PlaybackClock() }

    // ------------------------------------------------------------ subtitles
    var cues by remember(subtitleUri) { mutableStateOf<List<Cue>>(emptyList()) }
    var subState by remember(subtitleUri) { mutableStateOf(if (subtitleUri == null) SubState.NONE else SubState.LOADING) }
    LaunchedEffect(subtitleUri) {
        if (subtitleUri != null) {
            val result = loadCues(context, Uri.parse(subtitleUri), subtitleName.orEmpty())
            when {
                result == null -> subState = SubState.ERROR
                result.isEmpty() -> subState = SubState.EMPTY
                else -> {
                    cues = result
                    subState = SubState.OK
                }
            }
        }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            persistReadAccess(context, uri)
            onSubtitleChosen(uri.toString(), displayName(context, uri))
        }
    }

    // --------------------------------------------------------------- player
    val player = remember {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    var speed by remember { mutableStateOf(1f) }
    var delayMs by remember { mutableLongStateOf(0L) }
    var subsVisible by rememberSaveable { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }

    fun saveProgress() {
        val d = clock.durationMs
        val p = player.currentPosition
        val pos = if (d > 0 && p > d - 10_000) 0L else p
        container.recents.updateProgress(videoUri, pos, d)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                clock.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                clock.isBuffering = playbackState == Player.STATE_BUFFERING
                val d = player.duration
                if (d != C.TIME_UNSET && d > 0) clock.durationMs = d
            }

            override fun onPlayerError(error: PlaybackException) {
                clock.error = error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            saveProgress()
            player.release()
        }
    }

    LaunchedEffect(videoUri) {
        val start = if (settings.resumePlayback) (container.recents.find(videoUri)?.positionMs ?: 0L) else 0L
        clock.error = null
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)), start.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player) {
        var tick = 0
        while (true) {
            clock.positionMs = player.currentPosition
            val d = player.duration
            if (d != C.TIME_UNSET && d > 0) clock.durationMs = d
            tick++
            if (tick % 40 == 0 && clock.isPlaying) saveProgress()
            delay(150)
        }
    }

    // Pause + persist whenever the app goes to the background.
    DisposableEffect(activity) {
        val lifecycle = (activity as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                saveProgress()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Keep the screen on while watching.
    DisposableEffect(view, settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Immersive mode in landscape, normal system bars in portrait.
    LaunchedEffect(landscape) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (landscape) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // Landscape controls hide themselves a few seconds after the last interaction while playing.
    LaunchedEffect(controlsVisible, clock.isPlaying, interaction, landscape) {
        if (landscape && controlsVisible && clock.isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    // ------------------------------------------------- current subtitle line
    val displayIdx by remember(cues) {
        derivedStateOf { CueIndex.indexForDisplay(cues, clock.positionMs - delayMs) }
    }
    val cue = cues.getOrNull(displayIdx)
    val cueText = cue?.text

    var lineTranslation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cueText, settings.dualSubtitles, settings.targetLang) {
        lineTranslation = null
        if (cueText != null && settings.dualSubtitles && settings.targetLang != "en") {
            delay(150) // do not hammer the network while seeking
            lineTranslation = container.translator.translate(cueText.replace('\n', ' '), settings.targetLang, "en")?.text
        }
    }

    // ------------------------------------------------------- word selection
    var selected by remember { mutableStateOf<SelectedWord?>(null) }
    var resumeAfterSheet by remember { mutableStateOf(false) }
    val onWordTap: (String) -> Unit = { word ->
        resumeAfterSheet = settings.autoPauseOnWordTap && player.isPlaying
        if (resumeAfterSheet) player.pause()
        selected = SelectedWord(word, cueText.orEmpty(), ((clock.positionMs - delayMs) / 1000).coerceAtLeast(0L))
    }
    selected?.let { s ->
        WordSheet(
            container = container,
            selected = s,
            settings = settings,
            videoTitle = videoName,
            onDismiss = {
                selected = null
                if (resumeAfterSheet) {
                    player.play()
                    resumeAfterSheet = false
                }
            },
        )
    }

    // ----------------------------------------------------------- actions
    fun poke() {
        interaction++
    }

    val actions = PlayerActions(
        onPlayPause = {
            poke()
            if (player.isPlaying) player.pause() else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
        },
        onSeekBy = { deltaMs ->
            poke()
            val d = clock.durationMs
            val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
            player.seekTo(if (d > 0) target.coerceAtMost(d) else target)
        },
        onSeekTo = { poke(); player.seekTo(it) },
        onPrevCue = {
            poke()
            CueIndex.prevTarget(cues, clock.positionMs - delayMs)?.let { player.seekTo((it + delayMs).coerceAtLeast(0L)) }
        },
        onNextCue = {
            poke()
            CueIndex.nextTarget(cues, clock.positionMs - delayMs)?.let { player.seekTo(it + delayMs) }
        },
        onReplayCue = {
            poke()
            val i = CueIndex.indexAt(cues, clock.positionMs - delayMs).let { if (it >= 0) it else displayIdx }
            cues.getOrNull(i)?.let {
                player.seekTo((it.startMs + delayMs).coerceAtLeast(0L))
                player.play()
            }
        },
        onSpeed = { poke(); speed = it; player.setPlaybackSpeed(it) },
        onDelay = { poke(); delayMs = if (it == 0L) 0L else (delayMs + it).coerceIn(-30_000L, 30_000L) },
        onToggleSubs = { poke(); subsVisible = !subsVisible },
        // Forcing an orientation also works when system auto-rotate is off; it is released when leaving the player.
        onRotate = {
            poke()
            activity?.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        },
    )

    BackHandler {
        if (landscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            onClose()
        }
    }

    val subtitleContent: @Composable (Modifier, Boolean) -> Unit = { mod, overlay ->
        SubtitleArea(
            cue = cue,
            translation = lineTranslation,
            subState = subState,
            subsVisible = subsVisible,
            cefr = cefr,
            settings = settings,
            savedKeys = savedKeys,
            onWord = onWordTap,
            onLoadSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
            overlay = overlay,
            modifier = mod,
        )
    }

    if (landscape) {
        LandscapeLayout(
            player = player,
            clock = clock,
            title = videoName,
            settings = settings,
            speed = speed,
            delayMs = delayMs,
            subsVisible = subsVisible,
            hasCues = cues.isNotEmpty(),
            controlsVisible = controlsVisible,
            onToggleControls = { controlsVisible = !controlsVisible; poke() },
            actions = actions,
            onBack = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT },
            subtitleContent = subtitleContent,
        )
    } else {
        PortraitLayout(
            player = player,
            clock = clock,
            title = videoName,
            settings = settings,
            speed = speed,
            delayMs = delayMs,
            subsVisible = subsVisible,
            hasCues = cues.isNotEmpty(),
            cues = cues,
            displayIdx = displayIdx,
            subState = subState,
            actions = actions,
            onBack = onClose,
            onSeekToCue = { player.seekTo((it.startMs + delayMs).coerceAtLeast(0L)) },
            onLoadSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
            subtitleContent = subtitleContent,
        )
    }
}

@Stable
class PlayerActions(
    val onPlayPause: () -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onPrevCue: () -> Unit,
    val onNextCue: () -> Unit,
    val onReplayCue: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onDelay: (Long) -> Unit,
    val onToggleSubs: () -> Unit,
    val onRotate: () -> Unit,
)

// ===================================================================== layouts

@Composable
private fun VideoSurface(exo: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                player = exo
            }
        },
        update = { it.player = exo },
        modifier = modifier,
    )
}

@Composable
private fun VideoOverlays(clock: PlaybackClock, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (clock.isBuffering && clock.error == null) {
            CircularProgressIndicator(color = Color.White)
        }
        val err = clock.error
        if (err != null) {
            Column(
                modifier = Modifier.background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.player_error, err), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.player_error_hint), color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    player: ExoPlayer,
    clock: PlaybackClock,
    title: String,
    settings: AppSettings,
    speed: Float,
    delayMs: Long,
    subsVisible: Boolean,
    hasCues: Boolean,
    cues: List<Cue>,
    displayIdx: Int,
    subState: SubState,
    actions: PlayerActions,
    onBack: () -> Unit,
    onSeekToCue: (Cue) -> Unit,
    onLoadSubtitle: () -> Unit,
    subtitleContent: @Composable (Modifier, Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            VideoSurface(player, Modifier.fillMaxSize())
            VideoOverlays(clock, Modifier.fillMaxSize())
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart).background(Color(0x66000000)).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.action_back), tint = Color.White) }
                Text(title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            }
        }
        subtitleContent(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 120.dp), false)
        ControlsPanel(
            clock = clock,
            stepSec = settings.seekStepSec,
            speed = speed,
            delayMs = delayMs,
            subsVisible = subsVisible,
            hasCues = hasCues,
            landscape = false,
            actions = actions,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        Transcript(
            cues = cues,
            currentIdx = displayIdx,
            subState = subState,
            onSeekToCue = onSeekToCue,
            onLoadSubtitle = onLoadSubtitle,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun LandscapeLayout(
    player: ExoPlayer,
    clock: PlaybackClock,
    title: String,
    settings: AppSettings,
    speed: Float,
    delayMs: Long,
    subsVisible: Boolean,
    hasCues: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    actions: PlayerActions,
    onBack: () -> Unit,
    subtitleContent: @Composable (Modifier, Boolean) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(player, Modifier.fillMaxSize())
        // transparent layer above the video that toggles the controls
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleControls,
            )
        )
        VideoOverlays(clock, Modifier.fillMaxSize())

        subtitleContent(
            Modifier
                .align(Alignment.BottomCenter)
                .displayCutoutPadding()
                .padding(horizontal = 32.dp)
                .padding(bottom = if (controlsVisible) 128.dp else 20.dp),
            true,
        )

        if (controlsVisible) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart).background(Color(0x66000000)).displayCutoutPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, stringResource(R.string.action_back), tint = Color.White) }
                Text(title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            }
            ControlsPanel(
                clock = clock,
                stepSec = settings.seekStepSec,
                speed = speed,
                delayMs = delayMs,
                subsVisible = subsVisible,
                hasCues = hasCues,
                landscape = true,
                actions = actions,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .displayCutoutPadding()
                    .padding(horizontal = 12.dp),
            )
        }
    }
}

// ================================================================== subtitles

@Composable
private fun SubtitleArea(
    cue: Cue?,
    translation: String?,
    subState: SubState,
    subsVisible: Boolean,
    cefr: CefrLookup?,
    settings: AppSettings,
    savedKeys: Set<String>,
    onWord: (String) -> Unit,
    onLoadSubtitle: () -> Unit,
    overlay: Boolean,
    modifier: Modifier = Modifier,
) {
    // Portrait: a solid dark panel under the video. Landscape: a translucent box over the video.
    val boxModifier = when {
        !overlay -> modifier.background(PanelColor, RoundedCornerShape(14.dp))
        settings.subtitleBackground -> modifier.background(Color(0xB3000000), RoundedCornerShape(14.dp))
        else -> modifier
    }
    Box(boxModifier.padding(12.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            when {
                subState == SubState.NONE || subState == SubState.ERROR || subState == SubState.EMPTY -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val msg = when (subState) {
                            SubState.ERROR -> R.string.player_subtitle_error
                            SubState.EMPTY -> R.string.player_subtitle_empty
                            else -> R.string.player_subtitle_none
                        }
                        Text(stringResource(msg), textAlign = TextAlign.Center, color = Color.White)
                        FilledTonalButton(onClick = onLoadSubtitle) { Text(stringResource(R.string.player_load_subtitle)) }
                    }
                }
                subState == SubState.LOADING -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                !subsVisible -> Text(stringResource(R.string.player_subtitles_hidden), color = Color(0xFF9CA3AF))
                cue == null -> Text("…", color = Color(0xFF9CA3AF))
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleChips(cue.text, cefr, settings, savedKeys, onWord)
                    if (!translation.isNullOrBlank()) {
                        Text(
                            translation,
                            color = Color(0xFFFDE68A),
                            fontSize = (settings.subtitleTextSp - 4).coerceAtLeast(12).sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** The current line as tappable words (always laid out left-to-right, since the text is English). */
@Composable
private fun SubtitleChips(
    text: String,
    cefr: CefrLookup?,
    settings: AppSettings,
    savedKeys: Set<String>,
    onWord: (String) -> Unit,
) {
    val lines = remember(text) { Tokenizer.tokenize(text).map { Tokenizer.chunks(it) } }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            for (line in lines) {
                FlowRow(horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
                    for (chunk in line) {
                        val sizeSp = settings.subtitleTextSp.sp
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (chunk.lead.isNotEmpty()) Text(chunk.lead, fontSize = sizeSp, color = Color.White)
                            if (chunk.word.isNotEmpty()) {
                                val level = if (settings.showCefrColors) cefr?.lookupWord(chunk.word) else null
                                val colored = level != null && Cefr.isAtLeast(level, settings.minCefrLevel)
                                val saved = savedKeys.contains(app.proudvocab.mobile.core.Words.normalize(chunk.word))
                                Text(
                                    text = chunk.word,
                                    fontSize = sizeSp,
                                    fontWeight = if (colored) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (colored) Color(Cefr.colorArgb(level)) else Color.White,
                                    textDecoration = if (saved) TextDecoration.Underline else TextDecoration.None,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onWord(chunk.word) }
                                        .padding(horizontal = 2.dp, vertical = 6.dp),
                                )
                            }
                            if (chunk.trail.isNotEmpty()) Text(chunk.trail, fontSize = sizeSp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Transcript(
    cues: List<Cue>,
    currentIdx: Int,
    subState: SubState,
    onSeekToCue: (Cue) -> Unit,
    onLoadSubtitle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (subState != SubState.OK) {
        Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.player_transcript_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIdx) {
        if (currentIdx >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem((currentIdx - 1).coerceAtLeast(0))
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(cues, key = { index, _ -> index }) { index, c ->
                val current = index == currentIdx
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSeekToCue(c) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        formatClock(c.startMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        c.text.replace('\n', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// =================================================================== controls

@Composable
private fun ControlsPanel(
    clock: PlaybackClock,
    stepSec: Int,
    speed: Float,
    delayMs: Long,
    subsVisible: Boolean,
    hasCues: Boolean,
    landscape: Boolean,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val tint = if (landscape) Color.White else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides tint) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            var dragging by remember { mutableStateOf<Float?>(null) }
            val dur = clock.durationMs
            val fraction = if (dur > 0) (clock.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(((dragging ?: fraction) * dur).toLong()), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = dragging ?: fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { actions.onSeekTo((it * dur).toLong()) }
                        dragging = null
                    },
                    enabled = dur > 0,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatClock(dur), style = MaterialTheme.typography.labelMedium)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onPrevCue, enabled = hasCues) {
                    AppIcon(R.drawable.ic_skip_previous, stringResource(R.string.cd_prev_line), Modifier.alpha(if (hasCues) 1f else 0.4f))
                }
                TextButton(onClick = { actions.onSeekBy(-stepSec * 1000L) }) { Text("−$stepSec") }
                FilledIconButton(onClick = actions.onPlayPause, modifier = Modifier.size(56.dp)) {
                    if (clock.isPlaying) {
                        AppIcon(R.drawable.ic_pause, stringResource(R.string.cd_pause), tint = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        AppIcon(R.drawable.ic_play, stringResource(R.string.cd_play), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                TextButton(onClick = { actions.onSeekBy(stepSec * 1000L) }) { Text("+$stepSec") }
                IconButton(onClick = actions.onNextCue, enabled = hasCues) {
                    AppIcon(R.drawable.ic_skip_next, stringResource(R.string.cd_next_line), Modifier.alpha(if (hasCues) 1f else 0.4f))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onReplayCue, enabled = hasCues) {
                    AppIcon(R.drawable.ic_replay, stringResource(R.string.cd_replay_line), Modifier.alpha(if (hasCues) 1f else 0.4f))
                }
                SpeedMenu(speed, actions.onSpeed)
                DelayMenu(delayMs, hasCues, actions.onDelay)
                IconButton(onClick = actions.onToggleSubs) {
                    AppIcon(R.drawable.ic_subtitles, stringResource(R.string.cd_toggle_subtitles), Modifier.alpha(if (subsVisible) 1f else 0.4f))
                }
                IconButton(onClick = actions.onRotate) {
                    AppIcon(
                        if (landscape) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
                        stringResource(R.string.cd_rotate),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedMenu(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(speedLabel(speed)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                DropdownMenuItem(text = { Text(speedLabel(s)) }, onClick = { open = false; onSpeed(s) })
            }
        }
    }
}

private fun speedLabel(s: Float): String = if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

@Composable
private fun DelayMenu(delayMs: Long, enabled: Boolean, onDelay: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = if (delayMs == 0L) stringResource(R.string.player_sync) else {
        val sign = if (delayMs > 0) "+" else "−"
        sign + String.format(java.util.Locale.US, "%.1fs", Math.abs(delayMs) / 1000.0)
    }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.player_sync_later)) }, onClick = { onDelay(500L) })
            DropdownMenuItem(text = { Text(stringResource(R.string.player_sync_earlier)) }, onClick = { onDelay(-500L) })
            DropdownMenuItem(text = { Text(stringResource(R.string.player_sync_reset)) }, onClick = { open = false; onDelay(0L) })
        }
    }
}
