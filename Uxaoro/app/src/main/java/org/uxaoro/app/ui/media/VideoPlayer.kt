package org.uxaoro.app.ui.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.uxaoro.app.data.Api

/* ---------- video player (posts, messages, stories, previews) ---------- */

/**
 * Inline video player used by posts, messages, stories and composer previews.
 *
 * Compared with the plain `VideoView` + `MediaController` it replaces this one:
 *  - follows the clip's real aspect ratio (portrait clips are no longer squashed
 *    into a fixed 240dp letterbox) and caps very tall clips;
 *  - draws its own glass controls: play/pause, scrubbable progress, elapsed and
 *    total time, mute, fullscreen. They fade out while playing, come back on
 *    tap, and a double tap seeks +/-10s;
 *  - shows a poster frame for local clips, a spinner while buffering and a
 *    replay button when the clip ends;
 *  - keeps position, mute and play state when switching inline <-> fullscreen.
 *
 * @param compact tiny preview (attachment row): first frame plus a play badge
 *        that opens the fullscreen player.
 * @param immersive fill the given bounds and letterbox the clip (stories).
 * @param autoPlay start as soon as the clip is ready (stories).
 * @param loop replay forever (stories).
 */
@Composable
fun InlineVideo(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    loop: Boolean = false,
    compact: Boolean = false,
    immersive: Boolean = false,
    allowFullscreen: Boolean = true,
) {
    if (url.isBlank()) return
    val ctx = LocalContext.current
    val abs = remember(url) { Api.mediaUrl(url).ifBlank { url } }
    val isLocal = remember(abs) { abs.startsWith("content://") || abs.startsWith("file://") }
    val session = remember(abs, loop, autoPlay) { VideoSession(abs, isLocal, loop, autoPlay) }
    var fullscreen by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(!compact) }
    var retry by remember { mutableIntStateOf(0) }
    var seekFlash by remember { mutableIntStateOf(0) }

    DisposableEffect(session) { onDispose { session.release() } }

    // Poster frame for clips we can read from the device; remote clips paint
    // their first frame as soon as the player is prepared.
    LaunchedEffect(session) {
        if (!session.isLocal) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) { readFrame(ctx, session.abs) } ?: return@LaunchedEffect
        session.poster = bmp.asImageBitmap()
        session.updateAspect(bmp.width.toFloat() / bmp.height.coerceAtLeast(1))
    }

    // Shared clock: the session stays correct whichever surface is mounted.
    LaunchedEffect(session) {
        while (true) {
            session.tick()
            delay(250)
        }
    }

    LaunchedEffect(controls, session.playing, session.ended) {
        if (controls && session.playing && !session.ended) {
            delay(3200)
            controls = false
        }
    }
    LaunchedEffect(fullscreen) { if (fullscreen) controls = true }
    LaunchedEffect(seekFlash) {
        if (seekFlash != 0) {
            delay(650)
            seekFlash = 0
        }
    }

    val showControls = !compact && (controls || !session.playing || session.ended)
    val onRetry: () -> Unit = { session.failed = false; retry++ }
    if (compact || immersive) {
        VideoStage(
            session = session,
            frame = modifier
                .fillMaxSize()
                .clip(if (immersive) RoundedCornerShape(16.dp) else RoundedCornerShape(16.dp))
                .background(Color.Black),
            compact = compact,
            controlsVisible = showControls,
            seekFlash = seekFlash,
            retryKey = retry,
            allowFullscreen = allowFullscreen,
            onToggleControls = { controls = !controls },
            onSeekBy = { d -> session.nudge(d); seekFlash = if (d < 0) -10 else 10 },
            onOpenFullscreen = { fullscreen = true },
            onRetry = onRetry,
        )
    } else {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            VideoStage(
                session = session,
                frame = Modifier
                    .heightIn(max = 420.dp)
                    .aspectRatio(session.aspect.coerceIn(0.45f, 2.6f))
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .background(Color.Black),
                compact = false,
                controlsVisible = showControls,
                seekFlash = seekFlash,
                retryKey = retry,
                allowFullscreen = allowFullscreen,
                onToggleControls = { controls = !controls },
                onSeekBy = { d -> session.nudge(d); seekFlash = if (d < 0) -10 else 10 },
                onOpenFullscreen = { fullscreen = true },
                onRetry = onRetry,
            )
        }
    }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures(onTap = { controls = !controls }) },
            ) {
                // The dialog owns the surface while it is open; the inline
                // player above simply stops (position/mute are kept).
                VideoStage(
                    session = session,
                    frame = Modifier.fillMaxSize(),
                    compact = false,
                    controlsVisible = showControls,
                    seekFlash = seekFlash,
                    retryKey = retry,
                    allowFullscreen = true,
                    fullscreen = true,
                    onToggleControls = { controls = !controls },
                    onSeekBy = { d -> session.nudge(d); seekFlash = if (d < 0) -10 else 10 },
                    onOpenFullscreen = { fullscreen = false },
                    onRetry = onRetry,
                )
                IconButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close video", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VideoStage(
    session: VideoSession,
    frame: Modifier,
    compact: Boolean,
    controlsVisible: Boolean,
    seekFlash: Int,
    retryKey: Int,
    allowFullscreen: Boolean,
    onToggleControls: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onOpenFullscreen: () -> Unit,
    onRetry: () -> Unit,
    fullscreen: Boolean = false,
) {
    Box(
        frame.pointerInput(compact) {
            if (compact) {
                detectTapGestures(onTap = { onOpenFullscreen() })
            } else {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { at -> onSeekBy(if (at.x < size.width / 2f) -10_000 else 10_000) },
                )
            }
        },
    ) {
        key(retryKey) { VideoSurface(session, Modifier.fillMaxSize()) }

        val poster = session.poster
        if (poster != null && !session.started && !session.failed) {
            Image(
                bitmap = poster,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        if (compact) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Open video",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        } else {
            AnimatedVisibility(
                visible = controlsVisible && !session.failed && !session.buffering,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.Center),
            ) { VideoCenterButton(session) }

            AnimatedVisibility(
                visible = controlsVisible && !session.failed,
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                VideoControls(
                    session = session,
                    fullscreen = fullscreen,
                    allowFullscreen = allowFullscreen,
                    onToggleFullscreen = onOpenFullscreen,
                )
            }
        }

        if (session.failed) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Can't play this video",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                TextButton(onClick = onRetry) { Text("Retry", color = MaterialTheme.colorScheme.primary) }
            }
        } else if (session.buffering) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        if (seekFlash != 0) {
            Text(
                if (seekFlash < 0) "\u00AB 10s" else "10s \u00BB",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(if (seekFlash < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun VideoCenterButton(session: VideoSession) {
    Box(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable { session.togglePlay() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when {
                session.ended -> Icons.Default.Replay
                session.playing -> Icons.Default.Pause
                else -> Icons.Default.PlayArrow
            },
            contentDescription = if (session.playing) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun VideoControls(
    session: VideoSession,
    fullscreen: Boolean,
    allowFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    val duration = session.durationMs.coerceAtLeast(1).toFloat()
    val position = if (scrubbing) scrub else session.positionMs.toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            .padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 2.dp),
    ) {
        Slider(
            value = position.coerceIn(0f, duration),
            onValueChange = { scrubbing = true; scrub = it },
            onValueChangeFinished = { session.seekTo(scrub.toInt()); scrubbing = false },
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.34f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { session.togglePlay() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    when {
                        session.ended -> Icons.Default.Replay
                        session.playing -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = if (session.playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                "${fmt(session.positionMs)} / ${fmt(session.durationMs)}",
                color = Color.White,
                fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { session.toggleMute() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (session.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (session.muted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            if (allowFullscreen) {
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 1:05 / 12:34 style clock used by the controls. */
private fun fmt(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/* ---------- the actual surface + playback state ---------- */

@Composable
private fun VideoSurface(session: VideoSession, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            VideoView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setOnPreparedListener { mp ->
                    session.attach(mp)
                    mp.isLooping = session.loop
                    // Keep the clip's own aspect ratio inside the surface.
                    runCatching { mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT) }
                    session.durationMs = runCatching { mp.duration }.getOrDefault(0).coerceAtLeast(0)
                    session.updateAspect(mp.videoWidth.toFloat() / mp.videoHeight.coerceAtLeast(1))
                    mp.setOnVideoSizeChangedListener { _, w, h -> session.updateAspect(w.toFloat() / h.coerceAtLeast(1)) }
                    session.buffering = false
                    val resumeAt = session.positionMs
                    when {
                        session.playing -> {
                            if (resumeAt > 0) seekTo(resumeAt)
                            session.play()
                        }
                        session.autoPlay -> session.play()
                        resumeAt > 0 -> seekTo(resumeAt)
                        // Nudge into the stream so the first frame is painted.
                        else -> runCatching { seekTo(1) }
                    }
                }
                setOnCompletionListener {
                    session.playing = false
                    session.ended = true
                    if (!session.loop) session.positionMs = session.durationMs
                }
                setOnErrorListener { _, _, _ ->
                    session.failed = true
                    session.buffering = false
                    session.playing = false
                    true
                }
                setOnInfoListener { _, what, _ ->
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                            session.buffering = true
                            true
                        }
                        MediaPlayer.MEDIA_INFO_BUFFERING_END,
                        MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START,
                        -> {
                            session.buffering = false
                            session.started = true
                            true
                        }
                        else -> false
                    }
                }
                if (session.isLocal) setVideoURI(Uri.parse(session.abs)) else setVideoPath(session.abs)
                session.view = this
            }
        },
        onRelease = { view ->
            session.detach(view)
            runCatching { view.stopPlayback() }
        },
    )
}

/**
 * Playback state shared by the inline card and the fullscreen dialog, so
 * switching between them keeps position, mute, aspect ratio and play state.
 */
private class VideoSession(
    val abs: String,
    val isLocal: Boolean,
    val loop: Boolean,
    val autoPlay: Boolean,
) {
    var view: VideoView? = null
    private var player: MediaPlayer? = null

    var durationMs by mutableIntStateOf(0)
    var positionMs by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var started by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var aspect by mutableFloatStateOf(16f / 9f)
    var poster by mutableStateOf<ImageBitmap?>(null)

    fun updateAspect(v: Float) {
        if (v.isFinite() && v > 0.1f) aspect = v
    }

    fun attach(mp: MediaPlayer) {
        player = mp
        applyVolume()
    }

    private fun applyVolume() {
        val v = if (muted) 0f else 1f
        runCatching { player?.setVolume(v, v) }
    }

    fun toggleMute() {
        muted = !muted
        applyVolume()
    }

    fun play() {
        runCatching { view?.start() }
        playing = true
        ended = false
        started = true
    }

    fun togglePlay() {
        if (failed || ended) {
            failed = false
            seekTo(0)
            play()
        } else if (playing) {
            runCatching { view?.pause() }
            playing = false
        } else {
            play()
        }
    }

    fun seekTo(ms: Int) {
        positionMs = ms.coerceAtLeast(0)
        runCatching { view?.seekTo(positionMs) }
        if (ended && durationMs <= 0) ended = false
    }

    fun nudge(deltaMs: Int) {
        val max = if (durationMs > 0) durationMs else Int.MAX_VALUE
        seekTo((positionMs + deltaMs).coerceIn(0, max))
    }

    /** Keeps [positionMs] and [playing] in sync with whichever surface is mounted. */
    fun tick() {
        val v = view ?: return
        if (!ended) positionMs = runCatching { v.currentPosition }.getOrDefault(positionMs)
        val isPlaying = runCatching { v.isPlaying }.getOrDefault(false)
        if (isPlaying && !playing) {
            playing = true
            started = true
        } else if (!isPlaying && playing && !ended) {
            playing = false
        }
    }

    fun detach(v: VideoView) {
        if (view !== v) return
        positionMs = runCatching { v.currentPosition }.getOrDefault(positionMs)
        playing = runCatching { v.isPlaying }.getOrDefault(playing)
        view = null
        player = null
    }

    fun release() {
        view?.let { v ->
            positionMs = runCatching { v.currentPosition }.getOrDefault(positionMs)
            runCatching { v.stopPlayback() }
        }
        view = null
        player = null
        playing = false
        buffering = false
    }
}

/** First frame of a local clip, used as the poster while nothing has played. */
private fun readFrame(ctx: android.content.Context, abs: String): Bitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        if (abs.startsWith("content://")) {
            retriever.setDataSource(ctx, Uri.parse(abs))
        } else {
            retriever.setDataSource(abs)
        }
        retriever.getFrameAtTime(0)
    } finally {
        runCatching { retriever.release() }
    }
}.getOrNull()
