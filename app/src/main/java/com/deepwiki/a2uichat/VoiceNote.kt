package com.deepwiki.a2uichat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.changedToCanceled
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/* ══ Voice note ═══════════════════════════════════════════
   Hold to record. The pill opens into a bar and the level
   rises in it as bars, newest on the right. Let go and the
   recording becomes a clip: press play and the bars light up
   as the playhead passes them, drag across them to scrub.
   Slide left while holding and it is thrown away — the bars
   collapse and the pill closes back to where it started.

   ── A CONCEPT, NOT A RECORDER ───────────────────────
   Nothing listens. The level is a stand-in shaped like speech —
   syllables inside phrases, with pauses — so the block is about
   the gesture and the shapes, and asks nobody for a microphone.

   ── THE PLAYHEAD IS A NUMBER, NOT A RENDER ──────────────
   Progress is one state value on the wave, and each bar
   decides its own brightness from it and its index in draw().
   Android Compose uses one animation state for the playhead. */

/* 56 tall and 17px type, the Slide to confirm pill's size, so the
   pills on the wall read as one family */
private const val H = 56
private const val IDLE_W = 216
private const val OPEN_W = 316
/* one bar per sample while recording */
private const val SAMPLE = 70L
/* pointer travel, in CSS px at mdpi; Android keeps the same logical distance as dp */
private const val CANCEL_DP = 90f
/* shorter than this is a tap, not a recording */
private const val MIN_MS = 500L
private const val MAX_MS = 30000L

private enum class VoicePhase { IDLE, REC, CANCEL, CLIP }
private data class VoiceClip(val raw: List<Float>, val ms: Long)

private fun clamp(v: Float, a: Float, b: Float): Float = min(b, maxOf(a, v))

/* speech, roughly: syllables inside phrases, and gaps between */
private fun standIn(t: Float, random: Random): Float {
    val syll = abs(sin(t * 6.1f) * sin(t * 1.9f + 0.7f))
    val phrase = if (sin(t * 0.9f + 0.4f) > -0.55f) 1f else 0.1f
    return min(1f, 0.06f + syll * phrase * (0.5f + random.nextFloat() * 0.5f))
}

/* n bars from however many samples, each the loudest it covers */
private fun resample(src: List<Float>, n: Int): List<Float> {
    if (src.isEmpty()) return List(n) { 0f }
    return List(n) { i ->
        val a = (i * src.size) / n
        val b = maxOf(a + 1, ((i + 1) * src.size) / n)
        var m = 0f
        for (k in a until min(b, src.size)) m = maxOf(m, src[k])
        m
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000L).toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/* ── play into pause, drawn ─────────────────────────────────
   The source uses the same two quadrilaterals, wound the same way:
   the pause's bars, and the play triangle split down its own axis.
   Android uses the corresponding Hugeicons play/pause vectors so
   the surrounding UI keeps one soft icon language. */

@Composable
private fun PlayMark(on: Boolean) {
    Icon(
        painter = painterResource(if (on) R.drawable.ic_huge_pause else R.drawable.ic_huge_play),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
    )
}

/* the capsule filled, the stand drawn: solid like the play
   mark it becomes a clip beside, rather than lucide's outline */
@Composable
private fun MicMark() {
    Icon(
        painter = painterResource(R.drawable.ic_huge_mic),
        contentDescription = null,
        modifier = Modifier.size(21.dp),
    )
}

@Composable
private fun VoiceWave(
    levels: List<Float>,
    n: Int,
    gain: Float,
    progress: Float,
    pull: Float,
    scrubEnabled: Boolean,
    onSeek: (Float) -> Unit,
    color: Color,
    fold: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerInput(scrubEnabled, n, levels.size) {
                if (!scrubEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onSeek(clamp(down.position.x / size.width, 0f, 1f))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when {
                            change.changedToCanceled() -> break
                            change.changedToUp() -> {
                                onSeek(clamp(change.position.x / size.width, 0f, 1f))
                                change.consume()
                                break
                            }
                            else -> {
                                onSeek(clamp(change.position.x / size.width, 0f, 1f))
                                change.consume()
                            }
                        }
                    }
                }
            },
    ) {
        val barWidth = with(density) { 3.dp.toPx() }
        val gap = if (n <= 1) 0f else ((size.width - n * barWidth) / (n - 1)).coerceAtLeast(0f)
        val top = size.height / 2f
        val maxHeight = with(density) { 32.dp.toPx() }
        val minHeight = with(density) { 4.dp.toPx() }
        val current = if (scrubEnabled) resample(levels, n) else {
            val start = maxOf(0, levels.size - n)
            List(n) { index -> levels.getOrNull(start + index) ?: 0f }
        }
        val alphaPull = 1f - pull * 0.7f
        for (i in 0 until n) {
            val level = clamp(current[i] * gain, 0f, 1f)
            val h = (minHeight + level * (maxHeight - minHeight)) * fold
            val opacity = if (scrubEnabled) {
                clamp((progress * n - i) * 99f, 0.3f, 1f)
            } else {
                1f
            }
            drawRoundRect(
                color = color.copy(alpha = opacity * alphaPull),
                topLeft = androidx.compose.ui.geometry.Offset(i * (barWidth + gap), top - h / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
                style = Fill,
            )
        }
    }
}

@Composable
fun VoiceNote(
    /* how many bars the wave is drawn with */
    bars: Int = 28,
    /* how tall a given level draws, 0..100 */
    gain: Int = 60,
    /* how far the pill overshoots as it opens and closes, 0..100 */
    bounce: Int = 40,
    corner: Dp = (H / 2).dp,
    modifier: Modifier = Modifier,
) {
    val n = bars.coerceIn(12, 48)
    val g = 0.4f + (gain.coerceIn(0, 100) / 100f) * 1.2f
    val r = corner.value.coerceIn(0f, H / 2f)
    val density = LocalDensity.current
    val cancelPx = with(density) { CANCEL_DP.dp.toPx() }
    /* the play disc sits 6 inside: concentric once there is a
       curve to follow, square with the pill when there is not */
    val inner = maxOf(0f, r - 6f * min(1f, r / 12f))

    val slab = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    /* Bencho design tokens mapped locally to the project's existing Material 3 palette.
       No new globals are introduced:
       --card     -> surface
       --fill-on  -> onSurface
       --fill-slab-> surfaceVariant
       --font-ui  -> MaterialTheme.typography.bodyLarge.fontFamily
       --ink      -> onSurface
       --ink-3    -> onSurface at 60% opacity
       --pane-edge-> outlineVariant
       --signal   -> primary
    */
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
    val red = Color(0xFFE5484D)

    var phase by remember { mutableStateOf(VoicePhase.IDLE) }
    var levels by remember { mutableStateOf(emptyList<Float>()) }
    val recordingRaw = remember { mutableStateListOf<Float>() }
    var ms by remember { mutableLongStateOf(0L) }
    var clip by remember { mutableStateOf<VoiceClip?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var playAnchorProgress by remember { mutableFloatStateOf(0f) }
    var playAnchorNanos by remember { mutableLongStateOf(0L) }
    var pull by remember { mutableFloatStateOf(0f) }
    var downX by remember { mutableFloatStateOf(0f) }
    var recordStartedAt by remember { mutableLongStateOf(0L) }
    val recordingPulse = androidx.compose.runtime.rememberInfiniteTransition(label = "voice-note-recording-pulse")
    val dotAlpha by recordingPulse.animateFloat(1f, 0.25f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "voice-note-dot")
    val random = remember { Random(0x5EED) }
    val latestPhase by rememberUpdatedState(phase)

    LaunchedEffect(phase) {
        if (phase != VoicePhase.REC) return@LaunchedEffect
        val started = recordStartedAt
        recordingRaw.clear()
        while (true) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            if (elapsed >= MAX_MS) {
                clip = VoiceClip(recordingRaw.toList(), MAX_MS)
                ms = MAX_MS
                pull = 0f
                phase = VoicePhase.CLIP
                break
            }
            recordingRaw += standIn(elapsed / 1000f, random)
            levels = recordingRaw.takeLast(64)
            ms = elapsed
            delay(SAMPLE)
        }
    }

    LaunchedEffect(playing, clip) {
        val c = clip ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        while (playing) {
            val elapsed = (android.os.SystemClock.elapsedRealtimeNanos() - playAnchorNanos) / 1_000_000_000f
            progress = clamp(playAnchorProgress + elapsed / (c.ms / 1000f).coerceAtLeast(0.001f), 0f, 1f)
            if (progress >= 1f) {
                playing = false
                break
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    LaunchedEffect(phase) {
        if (phase == VoicePhase.CANCEL) {
            delay(360L)
            levels = emptyList()
            recordingRaw.clear()
            phase = VoicePhase.IDLE
        }
    }

    fun close() {
        playing = false
        phase = VoicePhase.CANCEL
        pull = 0f
        clip = null
        progress = 0f
        /* thrown away: the bars fold to nothing before the pill closes */
    }

    fun begin() {
        if (latestPhase != VoicePhase.IDLE) return
        levels = emptyList()
        recordingRaw.clear()
        ms = 0L
        pull = 0f
        clip = null
        progress = 0f
        recordStartedAt = android.os.SystemClock.elapsedRealtime()
        phase = VoicePhase.REC
    }

    fun end(cancel: Boolean) {
        if (latestPhase != VoicePhase.REC) return
        val took = (android.os.SystemClock.elapsedRealtime() - recordStartedAt).coerceAtMost(MAX_MS)
        ms = took
        val keep = !cancel && took >= MIN_MS
        if (!keep) {
            close()
            return
        }
        progress = 0f
        clip = VoiceClip(recordingRaw.toList(), took)
        pull = 0f
        phase = VoicePhase.CLIP
    }

    fun seek(value: Float) {
        val c = clip ?: return
        progress = clamp(value, 0f, 1f)
        if (playing) {
            playAnchorProgress = progress
            playAnchorNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
        if (progress >= 1f) playing = false
        @Suppress("UNUSED_VARIABLE")
        val ignored = c.ms
    }

    val open = phase != VoicePhase.IDLE
    val showClip = clip != null && (phase == VoicePhase.CLIP || phase == VoicePhase.CANCEL)
    val widthTarget = if (open) OPEN_W else IDLE_W
    val overshoot = 1f + (bounce.coerceIn(0, 100) / 100f) * 0.7f
    val width by animateIntAsState(
        targetValue = widthTarget,
        animationSpec = tween(460, easing = CubicBezierEasing(0.3f, overshoot.coerceAtMost(1.5f), 0.4f, 1f)),
        label = "voice-note-width",
    )
    val fold by animateFloatAsState(
        targetValue = if (phase == VoicePhase.CANCEL) 0f else 1f,
        animationSpec = tween(220),
        label = "voice-note-collapse",
    )
    val scale by animateFloatAsState(
        targetValue = if (phase == VoicePhase.REC && pull >= 1f) 0.985f else 1f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "voice-note-scale",
    )

    val description = when (phase) {
        VoicePhase.IDLE -> "Hold to record a voice note"
        VoicePhase.REC -> "Recording. Release to keep, Escape to discard."
        VoicePhase.CANCEL -> "Voice note discarded"
        VoicePhase.CLIP -> if (playing) "Voice note playing" else "Voice note ready to play"
    }

    Box(
        modifier = modifier
            .size(336.dp, 156.dp)
            .semantics {
                contentDescription = description
                if (phase == VoicePhase.IDLE || phase == VoicePhase.REC) role = Role.Button
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && (event.key == Key.Space || event.key == Key.Enter) && !event.isShiftPressed && phase == VoicePhase.IDLE -> {
                        begin(); true
                    }
                    event.type == KeyEventType.KeyUp && (event.key == Key.Space || event.key == Key.Enter) && phase == VoicePhase.REC -> {
                        end(false); true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape && phase == VoicePhase.REC -> {
                        end(true); true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(width.dp, H.dp)
                .scale(scale)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        when (latestPhase) {
                            VoicePhase.IDLE -> {
                                downX = down.position.x
                                begin()
                                down.consume()
                            }
                            VoicePhase.REC -> down.consume()
                            else -> Unit
                        }
                        if (latestPhase == VoicePhase.IDLE || latestPhase == VoicePhase.REC) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                when {
                                    change.changedToCanceled() -> {
                                        if (latestPhase == VoicePhase.REC) end(false)
                                        break
                                    }
                                    change.changedToUp() -> {
                                        if (latestPhase == VoicePhase.REC) end(pull >= 1f)
                                        change.consume()
                                        break
                                    }
                                    else -> {
                                        if (latestPhase == VoicePhase.REC) {
                                            val nextPull = clamp((downX - change.position.x) / cancelPx, 0f, 1f)
                                            pull = nextPull
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                },
            shape = RoundedCornerShape(r.dp),
            color = slab,
            contentColor = ink,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            when {
                phase == VoicePhase.IDLE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(H.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        MicMark()
                        Text("Hold to record", fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                phase == VoicePhase.REC || (phase == VoicePhase.CANCEL && !showClip) -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(H.dp).padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(red.copy(alpha = dotAlpha)))
                        Text(fmt(ms), fontSize = 14.sp, color = ink)
                        VoiceWave(levels, n, g, 0f, pull, false, ::seek, ink, fold, Modifier.weight(1f).height(32.dp))
                    }
                }
                showClip -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(H.dp).padding(start = 6.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (playing) playing = false else {
                                    if (progress >= 1f) progress = 0f
                                    playAnchorProgress = progress
                                    playAnchorNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                    playing = true
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(inner.dp)).background(ink),
                        ) {
                            PlayMark(playing)
                        }
                        VoiceWave(
                            levels = clip?.raw.orEmpty(),
                            n = n,
                            gain = g,
                            progress = progress,
                            pull = 0f,
                            scrubEnabled = true,
                            onSeek = ::seek,
                            color = ink,
                            modifier = Modifier.weight(1f).height(32.dp),
                        )
                        Text(
                            if (progress > 0f && progress < 1f) fmt(((progress * (clip?.ms ?: 0L)).roundToInt()).toLong()) else fmt(clip?.ms ?: 0L),
                            fontSize = 14.sp,
                            color = ink.copy(alpha = 0.6f),
                        )
                        IconButton(
                            onClick = ::close,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_huge_cancel), contentDescription = "Discard", tint = ink.copy(alpha = 0.5f), modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }

        if (phase == VoicePhase.REC) {
            val armed = pull >= 1f
            Text(
                if (armed) "Release to cancel" else "‹ Slide to cancel",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                fontSize = 13.sp,
                color = if (armed) red else muted.copy(alpha = 1f - pull),
            )
        }
    }
}
