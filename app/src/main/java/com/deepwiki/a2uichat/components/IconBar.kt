package com.deepwiki.a2uichat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/* ── native Compose port of Bencho's elastic motion ─────────
   The React source exposes simple 0..100 controls. The Android
   port keeps the same public numbers, then converts them to real
   durations and cubic-bezier curves for Compose.

   The important rule survives intact: 50 is the tuned middle.
   Nothing needs to know what the slider means to get the original
   feel back. */

/* How long it takes, slower to faster, as a multiplier on
   whatever the component's own tuned duration is. 0 is a
   little over half again as slow, 100 is two and a half times
   as fast, 50 is exactly 1. */
private fun rate(speed: Int): Float = 1.6f - (speed.coerceIn(0, 100) / 100f) * 1.2f

/* How hard it lands.

   The second control point's y is the overshoot. At 1 the
   indicator stops exactly on its target; above 1 it travels
   beyond and comes back. */
private fun overshoot(bounce: Int, tuned: Float): Float =
    (1f + (bounce.coerceIn(0, 100) / 100f) * (tuned - 1f) * 2f)
        .let { (it * 1000f).roundToInt() / 1000f }

private fun curve(bounce: Int, tuned: Float, x1: Float, x2: Float): CubicBezierEasing =
    CubicBezierEasing(x1, overshoot(bounce, tuned), x2, 1f)

private fun clamp(value: Float, lo: Float, hi: Float): Float = value.coerceIn(lo, hi)

/* the bar and the dock carry the same set: they are the same
   navigation in two different materials */
data class IconBarItem(
    val key: String,
    val label: String,
    val icon: Int,
)

/* ══ icon nav, horizontal or vertical ═════════════════════
   Same two phase indicator as the segmented pill: the leading
   edge runs ahead, the pill dilates across both slots, then
   the trailing edge catches up and overshoots on landing. */

private data class Ind(val p: Float, val s: Float)

private const val DEFAULT_SLOT = 38f
private const val DEFAULT_GAP = 4f
private const val DEFAULT_PAD = 7f
private const val DEFAULT_CORNER = 26f

private data class IconBarPalette(
    val pane: Color,
    val ink: Color,
    val paneEdge: Color,
    val paneThumb: Color,
    val shadow: Color,
    val rimHi: Float,
    val rimLo: Float,
    val rimFar: Float,
    val lift: Float,
    val spec: Float,
)

/* Bencho's design tokens are deliberately kept local to this
   component. There are no new app-wide globals here: --pane maps
   to the project's Material surface, --ink to onSurface, and the
   edge/shadow steps follow Material's own outline and surface.

   The token names ending in -rgb in the original CSS are colours
   used inside rgba(). Compose has Color values instead, so the
   equivalent is represented directly as a Color. */
@Composable
private fun rememberPalette(): IconBarPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return remember(scheme, dark) {
        if (dark) {
            IconBarPalette(
                pane = scheme.surface.copy(alpha = 0.82f),
                ink = scheme.onSurface,
                paneEdge = scheme.outlineVariant.copy(alpha = 0.62f),
                paneThumb = scheme.onSurface.copy(alpha = 0.10f),
                shadow = Color.Black.copy(alpha = 0.40f),
                rimHi = 0.30f,
                rimLo = 0.08f,
                rimFar = 0.02f,
                lift = 0.22f,
                spec = 0.20f,
            )
        } else {
            IconBarPalette(
                pane = Color.White.copy(alpha = 0.78f),
                ink = scheme.onSurface,
                paneEdge = scheme.outlineVariant.copy(alpha = 0.54f),
                paneThumb = scheme.onSurface.copy(alpha = 0.075f),
                shadow = Color.Black.copy(alpha = 0.16f),
                rimHi = 0.54f,
                rimLo = 0.18f,
                rimFar = 0.04f,
                lift = 0.16f,
                spec = 0.28f,
            )
        }
    }
}

private fun Color.luminance(): Float {
    fun linear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).let { it * it * it }
    return 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
}

@Composable
fun IconBar(
    items: List<IconBarItem>,
    glyph: Dp = 17.dp,
    axis: String = "row",
    dilate: Int = 100,
    bounce: Int = 50,
    speed: Int = 50,
    hug: Dp = DEFAULT_PAD.dp,
    corner: Dp = DEFAULT_CORNER.dp,
    slot: Dp = DEFAULT_SLOT.dp,
    gap: Dp = DEFAULT_GAP.dp,
    onSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val vertical = axis == "column"
    val palette = rememberPalette()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val positions = remember { mutableStateMapOf<String, Ind>() }
    var trackOrigin by remember { mutableStateOf(Offset.Zero) }
    var activeKey by remember(items) { mutableStateOf(items.first().key) }
    var current by remember { mutableStateOf<Ind?>(null) }
    var phase by remember { mutableStateOf("idle") }
    var animationJob by remember { mutableStateOf<Job?>(null) }

    val posAnim = remember { Animatable(0f) }
    val sizeAnim = remember { Animatable(slot.value) }
    val navCorner = corner.coerceIn(0.dp, minOf(26.dp, slot / 2f))
    val rateMultiplier = rate(speed)

    fun measure(key: String): Ind? = positions[key]

    fun select(key: String) {
        if (key == activeKey) return
        val to = measure(key) ?: return
        val from = if (current != null) Ind(posAnim.value, sizeAnim.value) else measure(activeKey)
        activeKey = key
        onSelect(key)
        animationJob?.cancel()
        if (from == null) {
            scope.launch {
                posAnim.snapTo(to.p)
                sizeAnim.snapTo(to.s)
                current = to
                phase = "settle"
                posAnim.animateTo(to.p, tween((420f * rateMultiplier).roundToInt().coerceAtLeast(1), easing = curve(bounce, 1.28f, 0.28f, 0.36f)))
                sizeAnim.animateTo(to.s, tween((420f * rateMultiplier).roundToInt().coerceAtLeast(1), easing = curve(bounce, 1.34f, 0.24f, 0.38f)))
            }.also { animationJob = it }
            return
        }

        /* The full union of both slots is as far as it can go —
           past that it is a pill reaching for somewhere neither
           end is. Dilate scales back from there toward simply
           moving, so 0 is a slide and 100 is the stretch. */
        val start = minOf(from.p, to.p)
        val end = maxOf(from.p + from.s, to.p + to.s)
        val grow = clamp(dilate / 100f, 0f, 1f)
        val stretched = Ind(
            p = to.p + (start - to.p) * grow,
            s = to.s + (end - start - to.s) * grow,
        )

        /* the handoff rides the same clock as the phases, or a
           fast bar hands over long after it has finished
           stretching and a slow one hands over mid-stretch */
        phase = "stretch"
        animationJob = scope.launch {
            current = stretched
            launch {
                posAnim.animateTo(
                    stretched.p,
                    tween((150f * rateMultiplier).roundToInt().coerceAtLeast(1), easing = CubicBezierEasing(0.32f, 0.72f, 0.24f, 1f)),
                )
            }
            launch {
                sizeAnim.animateTo(
                    stretched.s,
                    tween((150f * rateMultiplier).roundToInt().coerceAtLeast(1), easing = CubicBezierEasing(0.32f, 0.72f, 0.24f, 1f)),
                )
            }
            kotlinx.coroutines.delay((150f * rateMultiplier).roundToInt().toLong().coerceAtLeast(1L))
            phase = "settle"
            current = to
            launch {
                posAnim.animateTo(
                    to.p,
                    tween((420f * rateMultiplier).roundToInt().coerceAtLeast(1), easing = curve(bounce, 1.28f, 0.28f, 0.36f)),
                )
            }
            launch {
                sizeAnim.animateTo(
                    to.s,
                    tween((420f * rateMultiplier).roundToInt().coerceAtLeast(1), easing = curve(bounce, 1.34f, 0.24f, 0.38f)),
                )
            }
        }
    }


    val shape = RoundedCornerShape(navCorner)
    Box(
        modifier = modifier
            .wrapContentSize()
            .shadow(18.dp, shape, clip = false, ambientColor = palette.shadow, spotColor = palette.shadow)
            .background(palette.pane, shape)
            .border(1.dp, palette.paneEdge, shape)
            .padding(hug),
    ) {
        /* ── thickness ───────────────────────────────────────
           The web version uses a backdrop-filter plus a graded
           inner rim. Android does not expose CSS backdrop-filter,
           so the same visual hierarchy is reproduced with a
           translucent pane, a soft shadow, and a directional rim.
           The app's own surface remains the material source. */
        Box(
            modifier = Modifier
                .onGloballyPositioned { trackOrigin = it.positionInRoot() }
                .then(
                    if (!vertical) Modifier
                    else Modifier
                )
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = palette.rimHi),
                            Color.White.copy(alpha = palette.rimLo),
                            Color.White.copy(alpha = palette.rimFar),
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(480f, 480f),
                    ),
                    shape,
                )
                .padding(1.dp),
        ) {
            val indicatorModifier = if (!vertical) {
                Modifier
                    .align(Alignment.TopStart)
                    .size(width = sizeAnim.value.dp, height = slot)
                    .offset(x = posAnim.value.dp, y = 0.dp)
            } else {
                Modifier
                    .align(Alignment.TopStart)
                    .size(width = slot, height = sizeAnim.value.dp)
                    .offset(x = 0.dp, y = posAnim.value.dp)
            }

            Box(
                modifier = indicatorModifier
                    .background(palette.paneThumb, RoundedCornerShape(navCorner))
                    .then(Modifier),
            )

            if (!vertical) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEach { item ->
                        IconBarButton(
                            item = item,
                            active = item.key == activeKey,
                            glyph = glyph,
                            slot = slot,
                            palette = palette,
                            onPositioned = { coords ->
                                val p = coords.positionInRoot()
                                val pxToDp = with(density) { 1f / density.density }
                                val x = (p.x - trackOrigin.x) * pxToDp
                                val w = coords.size.width * pxToDp
                                positions[item.key] = Ind(x, w)
                                if (item.key == activeKey && current == null) {
                                    current = Ind(x, w)
                                    scope.launch {
                                        posAnim.snapTo(x)
                                        sizeAnim.snapTo(w)
                                    }
                                }
                            },
                            onClick = { select(item.key) },
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    items.forEach { item ->
                        IconBarButton(
                            item = item,
                            active = item.key == activeKey,
                            glyph = glyph,
                            slot = slot,
                            palette = palette,
                            onPositioned = { coords ->
                                val p = coords.positionInRoot()
                                val pxToDp = with(density) { 1f / density.density }
                                val y = (p.y - trackOrigin.y) * pxToDp
                                val h = coords.size.height * pxToDp
                                positions[item.key] = Ind(y, h)
                                if (item.key == activeKey && current == null) {
                                    current = Ind(y, h)
                                    scope.launch {
                                        posAnim.snapTo(y)
                                        sizeAnim.snapTo(h)
                                    }
                                }
                            },
                            onClick = { select(item.key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBarButton(
    item: IconBarItem,
    active: Boolean,
    glyph: Dp,
    slot: Dp,
    palette: IconBarPalette,
    onPositioned: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(slot)
            .onGloballyPositioned(onPositioned)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = item.label
                role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            modifier = Modifier.size(glyph).graphicsLayer { alpha = if (active) 1f else 0.42f },
            tint = palette.ink,
            /* active icon full strength; inactive icons sit back
               just enough to let the indicator carry selection */
        )
    }
}

