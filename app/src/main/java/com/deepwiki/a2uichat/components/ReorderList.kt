package com.deepwiki.a2uichat.components

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect
import android.graphics.Shader

/* ── what this block expects the page to provide ───────

#arr-goo — the web version mounts an SVG filter once. Android has no DOM filter id,
so the native port keeps the same visual layering: an opaque blob layer underneath
an unfiltered ink layer. On API 31+, a small RenderEffect blur supplies the soft merge;
older Android versions keep the crisp fallback rather than paying for a software shader.
*/

/* ── inlined from lab/motionkit ──────────────────────── */
/* ══ liquid ═══════════════════════════════════════════════
   Shared parts for the motion layer: one spring, one goo-like
   blur fallback, one velocity→skew binding.

   ── why the filter is a hook ────────────────────────────
   The web filter is embedded in each component's return, but
   its id cannot be a literal because two mounted copies would
   collide. Compose has no DOM ids here, so each component owns
   its own RenderEffect instance instead.

   ── what the goo can and cannot do ──────────────────────
   The web version is feGaussianBlur + feColorMatrix. The Android
   equivalent below preserves the blur and the two-layer rule;
   text never touches the blurred layer. This keeps antialiased
   glyphs sharp, while nearby opaque pills soften into each other.
*/

/* the elastic, as specified: ζ = 14 / (2·√(220·0.5)) ≈ 0.67,
   so it overshoots before it settles. A deliberate bounce, not a wobble. */
private val LIQUID_SPRING = spring<Float>(
    stiffness = 220f,
    dampingRatio = 0.67f,
)

private const val PILL = 196f
private const val ROW_HEIGHT = 44f
private const val STEP = 50f
private const val STAGE_WIDTH = 262f
private const val STAGE_HEIGHT = 200f
private const val ARR_INSET = 6f
private const val AVATAR = 32f
private const val AVATAR_GAP = 11f
private const val CORNER = 22f
private const val GOO_BLUR = 2.6f
private const val GOO_CUT = 28f

/* source defaults retained exactly */
typealias ReorderGive = Int
typealias ReorderLean = Int

data class ReorderPerson(
    val id: String,
    val name: String,
    val here: Boolean,
    val tint: Color,
    val avatarType: String,
    val avatarFace: String = "eyes",
    val avatarState: String = "default",
    val avatarSeed: Float = 0.5f,
)

private val DEFAULT_PEOPLE = listOf(
    ReorderPerson("mara", "Mara Quinn", true, Color(0xFF7C3AED), "clover", "eyes", "default", 0.12f),
    ReorderPerson("ines", "Tomás Oliveira", false, Color(0xFF0E7490), "flower", "mouth", "default", 0.34f),
    ReorderPerson("kai", "Lars Andersen", true, Color(0xFFB8431C), "mech", "eyes", "working", 0.56f),
    ReorderPerson("sofia", "Sofia Ricci", false, Color(0xFF15803D), "cat", "mouth", "default", 0.78f),
)

private fun clamp(v: Float, lo: Float, hi: Float) = v.coerceIn(lo, hi)

private fun liquidBlurModifier(enabled: Boolean): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < 31) return Modifier
    val blur = RenderEffect.createBlurEffect(GOO_BLUR, GOO_BLUR, Shader.TileMode.CLAMP)
    return Modifier.graphicsLayer { renderEffect = blur.asComposeRenderEffect() }
}

private fun Modifier.captureReorderGestures(
    rowId: Int,
    onStart: (id: Int, y: Float) -> Unit,
    onMove: (id: Int, startY: Float, currentY: Float, deltaY: Float, dtMs: Long) -> Unit,
    onEnd: (id: Int) -> Unit,
) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startedAt = down.position.y
        var current = startedAt
        var previousTime = down.uptimeMillis
        onStart(rowId, startedAt)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            when {
                !change.pressed && !change.changedToUp() -> {
                    onEnd(rowId)
                    break
                }
                change.changedToUp() -> {
                    onEnd(rowId)
                    break
                }
                else -> {
                    val deltaY = change.positionChange().y
                    current += deltaY
                    val now = change.uptimeMillis
                    val dtMs = (now - previousTime).coerceAtLeast(1L)
                    onMove(rowId, startedAt, current, deltaY, dtMs)
                    previousTime = now
                    change.consume()
                }
            }
        }
    }
}

/**
 * Native Compose port of Bencho's ReorderList / Arrange study.
 *
 * The row data may be supplied by A2UI. When omitted, the original four-person
 * roster is used. Avatars deliberately reuse this project's BotAvatar renderer;
 * Bencho's private photographs are not bundled or fetched.
 */
@Composable
fun ReorderList(
    /* how much a flung row deforms, 0..100 */
    give: Int = 50,
    /* how far it tilts into the throw, 0..100 */
    lean: Int = 18,
    /* the row pitch, and it is not a knob any more */
    step: Dp = STEP.dp,
    /* the row's corner, 0..22 */
    corner: Dp = CORNER.dp,
    people: List<ReorderPerson> = DEFAULT_PEOPLE,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { step.toPx() }
    val pillDp = PILL.dp
    val rowHeightDp = ROW_HEIGHT.dp
    val insetDp = ARR_INSET.dp

    /* ── token → real colour ─────────────────────────────────
       The Bencho CSS tokens are mapped to this project's existing Material 3
       palette locally. Nothing is exported as a new global token:
         --arr-inset -> 6.dp local geometry
         --fill-on   -> onSurface
         --fill-slab -> surfaceVariant
         --font-ui   -> MaterialTheme.typography.bodyMedium.fontFamily
         --ink       -> onSurface
         --ink-2     -> onSurfaceVariant
         --ink-3     -> onSurfaceVariant with reduced alpha
         --ink-4     -> onSurfaceVariant with stronger reduction
         --ok        -> tertiary (the nearest semantic status colour)
         --pane-edge -> outlineVariant
         --surface-2 -> surfaceVariant
    */
    val fillOn = MaterialTheme.colorScheme.onSurface
    val fillSlab = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val ink2 = MaterialTheme.colorScheme.onSurfaceVariant
    val ink3 = ink2.copy(alpha = 0.66f)
    val ink4 = ink2.copy(alpha = 0.42f)
    val ok = MaterialTheme.colorScheme.tertiary
    val paneEdge = MaterialTheme.colorScheme.outlineVariant
    val surface2 = MaterialTheme.colorScheme.surfaceVariant
    val fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
    val warm = androidx.compose.ui.graphics.lerp(surface2, fillOn, 0.013f)
    val lift = androidx.compose.ui.graphics.lerp(fillSlab, fillOn, 0.16f)
    val pillCorner = clamp(corner.value, 0f, CORNER).dp

    /* Keep the stage faithful to the four-row source study. A larger A2UI roster
       can still be supplied, but only the rows that fit the reconstructed stage
       participate in this compact component. */
    val visiblePeople = people.take(4)

    /* visual order, as data indices. The row in hand is tracked separately so
       a drag does not move the pointer's coordinate when other rows reorder. */
    var order by remember(visiblePeople.map { it.id }) { mutableStateOf(visiblePeople.indices.toList()) }
    var held by remember { mutableStateOf<Int?>(null) }
    var over by remember { mutableStateOf<Int?>(null) }
    var heldY by remember { mutableFloatStateOf(0f) }
    var dragStartSlot by remember { mutableStateOf(0) }
    var velocity by remember { mutableFloatStateOf(0f) }

    /* One spring target per row. A held row is exempt: its value is written
       directly from the pointer and never fights the finger with easing. */
    val targetFor = { id: Int -> order.indexOf(id).coerceAtLeast(0) * stepPx }

    val velocityAbs = abs(velocity)
    val squash = 1f + velocityAbs * (clamp(give.toFloat(), 0f, 100f) / 100f) * 0.12f
    val wide = 1f / squash
    val tilt = velocity * (clamp(lean.toFloat(), 0f, 100f) / 100f) * 2.6f

    Box(
        modifier = modifier
            .size(STAGE_WIDTH.dp, STAGE_HEIGHT.dp)
            .semantics { contentDescription = "Reorderable list" },
    ) {
        /* Opaque, filtered/softened blob layer. It carries no text. */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(liquidBlurModifier(enabled = Build.VERSION.SDK_INT >= 31)),
        ) {
            visiblePeople.forEachIndexed { id, _ ->
                val isHeld = held == id
                val yAnimated by animateFloatAsState(
                    targetValue = targetFor(id),
                    animationSpec = LIQUID_SPRING,
                    label = "reorder-blob-y-$id",
                )
                val y = if (isHeld) heldY else yAnimated
                val width by animateDpAsState(
                    targetValue = if (isHeld) (PILL + 12f).dp else pillDp,
                    animationSpec = spring(stiffness = 220f, dampingRatio = 0.67f),
                    label = "reorder-blob-width-$id",
                )
                val blobColor = if (isHeld) lift else if (over == id) warm else fillSlab
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                         .offsetY(y)
                        .width(width)
                        .height(rowHeightDp)
                        .graphicsLayer {
                            scaleX = if (isHeld) wide else 1f
                            scaleY = if (isHeld) squash else 1f
                        }
                        .clip(RoundedCornerShape(pillCorner))
                        .background(blobColor),
                )
            }
        }

        /* The unfiltered layer carries the type. Anti-aliased text never passes
           through the goo effect, preserving the reason for the two layers. */
        Box(modifier = Modifier.fillMaxSize()) {
            visiblePeople.forEachIndexed { id, person ->
                val isHeld = held == id
                val targetY = targetFor(id)
                val animatedTarget by animateFloatAsState(
                    targetValue = targetY,
                    animationSpec = LIQUID_SPRING,
                    label = "reorder-row-y-$id",
                )
                val y = if (isHeld) heldY else animatedTarget
                val avatarScale = if (isHeld) 1.08f else 1f
                val rowColor = if (isHeld) ink else ink2
                val outline = if (isHeld) paneEdge else Color.Transparent

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offsetY(y)
                        .width(pillDp)
                        .height(rowHeightDp)
                        .zIndex(if (isHeld) 2f else 0f)
                        .clip(RoundedCornerShape(pillCorner))
                        .then(
                            if (outline.alpha > 0f) Modifier.border(1.dp, outline, RoundedCornerShape(pillCorner))
                            else Modifier
                        )
                        .captureReorderGestures(
                            rowId = id,
                            onStart = { startedId, _ ->
                                if (startedId == id && held == null) {
                                    held = id
                                    dragStartSlot = order.indexOf(id).coerceAtLeast(0)
                                    heldY = dragStartSlot * stepPx
                                    velocity = 0f
                                }
                            },
                            onMove = { movedId, startY, currentY, deltaY, dtMs ->
                                if (movedId == id && held == id) {
                                    val dy = currentY - startY
                                    /* THE fix for the jump. The held row is measured from
                                       the slot it was picked up in, never from the slot it
                                       currently occupies — that one changes underneath the
                                       gesture as the list reorders. */
                                    heldY = dragStartSlot * stepPx + dy
                                    velocity = clamp((deltaY / dtMs.toFloat()) * 16f, -3f, 3f)

                                    val want = clamp(
                                        ((heldY / stepPx).roundToInt()).toFloat(),
                                        0f,
                                        (visiblePeople.lastIndex).toFloat(),
                                    ).roundToInt()
                                    val oldSlot = order.indexOf(id)
                                    if (want != oldSlot) {
                                        order = order.toMutableList().also { next ->
                                            next.remove(id)
                                            next.add(want.coerceIn(0, next.size), id)
                                        }
                                    }
                                }
                            },
                            onEnd = { endedId ->
                                if (endedId == id) {
                                    held = null
                                    velocity = 0f
                                }
                            },
                        )
                        .semantics { contentDescription = "Reorder ${person.name}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    /* ── no grip: the source explicitly removes it ────────
                       The row can be picked up anywhere. Keeping the picture and
                       label fixed avoids the sideways jump the old grip caused. */
                    Spacer(Modifier.width(insetDp))
                    Box(
                        modifier = Modifier
                            .size(AVATAR.dp)
                            .graphicsLayer { scaleX = avatarScale; scaleY = avatarScale },
                    ) {
                        /* Bencho's AVATARS object is intentionally empty: its
                           photographs are not licensed to travel. This project already
                           has a native BotAvatar renderer, so that is the local artwork
                           source rather than a remote image or a bundled stranger's photo. */
                        com.deepwiki.a2uichat.BotAvatar(
                            type = person.avatarType,
                            state = person.avatarState,
                            face = person.avatarFace,
                            size = AVATAR.dp,
                            color = person.tint,
                            brightness = 1f,
                            saturation = 1.5f,
                            seed = person.avatarSeed,
                            shading = "fabric",
                            interactive = false,
                            label = person.name,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(9.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ok.copy(alpha = if (person.here) 1f else 0f))
                                .border(2.dp, surface2, RoundedCornerShape(50)),
                        )
                    }
                    Spacer(Modifier.width(AVATAR_GAP.dp))
                    androidx.compose.material3.Text(
                        text = person.name,
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                        color = rowColor,
                        fontFamily = fontFamily,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun Modifier.offsetY(yPx: Float): Modifier = this.graphicsLayer { translationY = yPx }

/** A2UI-facing variant that accepts primitive data instead of Kotlin models. */
@Composable
fun A2UIReorderList(
    people: List<ReorderPerson> = DEFAULT_PEOPLE,
    give: Int = 50,
    lean: Int = 18,
    step: Dp = STEP.dp,
    corner: Dp = CORNER.dp,
    modifier: Modifier = Modifier,
) {
    ReorderList(
        people = people.ifEmpty { DEFAULT_PEOPLE },
        give = give,
        lean = lean,
        step = step,
        corner = corner,
        modifier = modifier,
    )
}
