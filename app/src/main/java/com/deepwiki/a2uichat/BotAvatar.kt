package com.deepwiki.a2uichat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Native Compose port of the Libraries.dev bot-avatars visual model.
 *
 * The web package is React-only, so Android uses an equivalent 2D Canvas renderer
 * instead of the npm package. The normalized body box is 100x100, matching the
 * library's custom-path coordinate system.
 */

private const val BODY_UNITS = 100f

private data class BotAvatarPalette(val body: Color, val dark: Color, val highlight: Color)

private fun baseColor(type: String): Color = when (type.lowercase()) {
    "clover" -> Color(0xFF70C97C)
    "flower" -> Color(0xFFF39AB4)
    "triangle" -> Color(0xFFFFB84D)
    "square" -> Color(0xFF73A9FF)
    "blob" -> Color(0xFFB38AF4)
    "ghost" -> Color(0xFFA6B9CF)
    "circle" -> Color(0xFF63C9C3)
    "drop" -> Color(0xFF5EA8FF)
    "star" -> Color(0xFFFFCC56)
    "droid" -> Color(0xFFAEB6C2)
    "mech" -> Color(0xFF76869B)
    "alien" -> Color(0xFF84D861)
    "hexagon" -> Color(0xFF9878DA)
    "cat" -> Color(0xFFF0A264)
    "cloud" -> Color(0xFFB5C9DB)
    "pill" -> Color(0xFF80AFFF)
    "pebble" -> Color(0xFF8CB69A)
    "puddle" -> Color(0xFF66AAA7)
    else -> Color(0xFF7EA6D8)
}

private fun adjustBodyColor(color: Color, brightness: Float, saturation: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * saturation).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * brightness).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((color.alpha * 255f).toInt(), hsv))
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255f).toInt().coerceIn(0, 255),
    (red * 255f).toInt().coerceIn(0, 255),
    (green * 255f).toInt().coerceIn(0, 255),
    (blue * 255f).toInt().coerceIn(0, 255),
)

private fun luminance(color: Color): Float = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

private fun avatarPalette(type: String, colorHex: String?, brightness: Float, saturation: Float): BotAvatarPalette {
    val raw = colorHex?.let(::parseColorOrNull) ?: baseColor(type)
    val baked = when (type.lowercase()) {
        "clover" -> adjustBodyColor(raw, brightness * 1.2f, saturation * 1.59f)
        "star" -> adjustBodyColor(raw, brightness * 1.1f, saturation * 1.84f)
        "cat" -> adjustBodyColor(raw, brightness * 1.22f, saturation * 1.88f)
        else -> adjustBodyColor(raw, brightness, saturation)
    }
    return BotAvatarPalette(
        body = baked,
        dark = lerp(baked, Color.Black, 0.33f),
        highlight = lerp(baked, Color.White, 0.58f),
    )
}

private fun parseColorOrNull(raw: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(raw))
}.getOrNull()

private fun buildBody(type: String, customPath: String? = null): Path = Path().apply {
    if (!customPath.isNullOrBlank()) {
        addSvg(customPath)
        return@apply
    }
    when (type.lowercase()) {
        "clover" -> {
            moveTo(50f, 20f)
            cubicTo(34f, 5f, 12f, 16f, 18f, 34f)
            cubicTo(1f, 32f, 0f, 56f, 17f, 62f)
            cubicTo(11f, 81f, 32f, 94f, 45f, 78f)
            cubicTo(58f, 96f, 89f, 86f, 79f, 66f)
            cubicTo(99f, 57f, 95f, 28f, 77f, 33f)
            cubicTo(83f, 14f, 62f, 6f, 50f, 20f)
            close()
        }
        "flower" -> {
            moveTo(50f, 18f)
            repeat(8) { i ->
                val a0 = (-90 + i * 45) * PI / 180.0
                val a1 = (-90 + i * 45 + 22.5) * PI / 180.0
                val x0 = 50 + cos(a0) * 18
                val y0 = 50 + sin(a0) * 18
                val x1 = 50 + cos(a1) * 32
                val y1 = 50 + sin(a1) * 32
                if (i == 0) moveTo(x0.toFloat(), y0.toFloat()) else lineTo(x0.toFloat(), y0.toFloat())
                cubicTo(
                    (50 + cos(a0) * 26).toFloat(), (50 + sin(a0) * 26).toFloat(),
                    (50 + cos(a1) * 40).toFloat(), (50 + sin(a1) * 40).toFloat(),
                    x1.toFloat(), y1.toFloat()
                )
            }
            close()
        }
        "triangle" -> {
            moveTo(50f, 9f)
            lineTo(90f, 82f)
            quadraticBezierTo(88f, 92f, 78f, 91f)
            lineTo(22f, 91f)
            quadraticBezierTo(12f, 92f, 10f, 82f)
            close()
        }
        "square" -> {
            moveTo(31f, 12f); lineTo(69f, 12f)
            cubicTo(80f, 12f, 88f, 20f, 88f, 31f)
            lineTo(88f, 69f); cubicTo(88f, 80f, 80f, 88f, 69f, 88f)
            lineTo(31f, 88f); cubicTo(20f, 88f, 12f, 80f, 12f, 69f)
            lineTo(12f, 31f); cubicTo(12f, 20f, 20f, 12f, 31f, 12f); close()
        }
        "blob" -> {
            moveTo(22f, 27f)
            cubicTo(8f, 38f, 14f, 65f, 26f, 78f)
            cubicTo(40f, 92f, 73f, 91f, 84f, 70f)
            cubicTo(96f, 48f, 82f, 17f, 62f, 13f)
            cubicTo(44f, 9f, 30f, 18f, 22f, 27f)
            close()
        }
        "ghost" -> {
            moveTo(18f, 78f)
            lineTo(18f, 41f)
            cubicTo(18f, 20f, 31f, 10f, 50f, 10f)
            cubicTo(69f, 10f, 82f, 20f, 82f, 41f)
            lineTo(82f, 78f)
            cubicTo(74f, 68f, 68f, 84f, 60f, 76f)
            cubicTo(52f, 68f, 48f, 86f, 40f, 76f)
            cubicTo(32f, 67f, 26f, 87f, 18f, 78f)
            close()
        }
        "circle" -> addOval(androidx.compose.ui.geometry.Rect(12f, 12f, 88f, 88f))
        "drop" -> {
            moveTo(50f, 9f)
            cubicTo(43f, 24f, 20f, 43f, 20f, 61f)
            cubicTo(20f, 79f, 33f, 90f, 50f, 90f)
            cubicTo(67f, 90f, 80f, 79f, 80f, 61f)
            cubicTo(80f, 43f, 57f, 24f, 50f, 9f)
            close()
        }
        "star" -> {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 39.0 else 18.0
                val a = (-90 + i * 36) * PI / 180.0
                val x = 50 + cos(a) * r
                val y = 50 + sin(a) * r
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
        "droid" -> {
            moveTo(21f, 28f)
            cubicTo(21f, 18f, 29f, 13f, 40f, 13f)
            lineTo(44f, 6f); lineTo(47f, 13f)
            lineTo(53f, 13f); lineTo(56f, 6f); lineTo(59f, 13f)
            cubicTo(71f, 13f, 79f, 18f, 79f, 28f)
            lineTo(79f, 76f)
            cubicTo(79f, 84f, 72f, 89f, 64f, 89f)
            lineTo(36f, 89f)
            cubicTo(28f, 89f, 21f, 84f, 21f, 76f)
            close()
        }
        "mech" -> {
            moveTo(18f, 25f); lineTo(30f, 16f); lineTo(70f, 16f); lineTo(82f, 25f)
            lineTo(79f, 77f); lineTo(68f, 88f); lineTo(32f, 88f); lineTo(21f, 77f); close()
        }
        "alien" -> {
            moveTo(50f, 8f)
            cubicTo(26f, 8f, 12f, 25f, 16f, 53f)
            cubicTo(19f, 79f, 35f, 92f, 50f, 92f)
            cubicTo(65f, 92f, 81f, 79f, 84f, 53f)
            cubicTo(88f, 25f, 74f, 8f, 50f, 8f)
            close()
        }
        "hexagon" -> {
            moveTo(50f, 9f); lineTo(83f, 28f); lineTo(83f, 68f); lineTo(50f, 90f); lineTo(17f, 68f); lineTo(17f, 28f); close()
        }
        "cat" -> {
            moveTo(24f, 31f); lineTo(20f, 10f); lineTo(36f, 20f)
            cubicTo(45f, 17f, 55f, 17f, 64f, 20f)
            lineTo(80f, 10f); lineTo(76f, 31f)
            cubicTo(83f, 44f, 83f, 65f, 75f, 77f)
            cubicTo(66f, 90f, 34f, 90f, 25f, 77f)
            cubicTo(17f, 65f, 17f, 44f, 24f, 31f)
            close()
        }
        "cloud" -> {
            moveTo(22f, 76f)
            cubicTo(11f, 70f, 10f, 52f, 22f, 45f)
            cubicTo(24f, 28f, 37f, 20f, 50f, 28f)
            cubicTo(60f, 17f, 79f, 25f, 80f, 42f)
            cubicTo(94f, 46f, 93f, 69f, 81f, 76f)
            lineTo(22f, 76f); close()
        }
        "pill" -> {
            moveTo(37f, 27f); lineTo(63f, 27f)
            cubicTo(76f, 27f, 86f, 36f, 86f, 50f)
            cubicTo(86f, 64f, 76f, 73f, 63f, 73f)
            lineTo(37f, 73f)
            cubicTo(24f, 73f, 14f, 64f, 14f, 50f)
            cubicTo(14f, 36f, 24f, 27f, 37f, 27f); close()
        }
        "pebble" -> {
            moveTo(28f, 18f); cubicTo(12f, 30f, 15f, 62f, 26f, 78f)
            cubicTo(40f, 98f, 76f, 88f, 83f, 69f)
            cubicTo(93f, 43f, 75f, 12f, 55f, 12f)
            cubicTo(45f, 12f, 35f, 13f, 28f, 18f); close()
        }
        "puddle" -> {
            moveTo(18f, 48f)
            cubicTo(10f, 60f, 20f, 76f, 35f, 81f)
            cubicTo(54f, 88f, 78f, 80f, 84f, 65f)
            cubicTo(89f, 54f, 80f, 42f, 68f, 40f)
            cubicTo(49f, 36f, 25f, 37f, 18f, 48f); close()
        }
        else -> {
            moveTo(31f, 12f); lineTo(69f, 12f)
            cubicTo(80f, 12f, 88f, 20f, 88f, 31f); lineTo(88f, 69f)
            cubicTo(88f, 80f, 80f, 88f, 69f, 88f); lineTo(31f, 88f)
            cubicTo(20f, 88f, 12f, 80f, 12f, 69f); lineTo(12f, 31f)
            cubicTo(12f, 20f, 20f, 12f, 31f, 12f); close()
        }
    }
}

private fun DrawScope.drawBody(path: Path, palette: BotAvatarPalette, shading: String, shadow: Float, highlight: Float, rim: Float, spread: Float, roundness: Float, lightDegrees: Float) {
    val angle = (lightDegrees - 90f) * PI.toFloat() / 180f
    val lx = 50f + cos(angle) * 36f
    val ly = 50f + sin(angle) * 36f
    val dx = lx / BODY_UNITS * size.minDimension
    val dy = ly / BODY_UNITS * size.minDimension
    val radius = size.minDimension * (0.45f + roundness * 0.05f)

    val fillBrush = when (shading) {
        "flat" -> Brush.linearGradient(listOf(palette.body, palette.body))
        "crisp" -> Brush.linearGradient(listOf(palette.highlight.copy(alpha = .44f), palette.body, palette.dark))
        "smooth" -> Brush.radialGradient(listOf(palette.highlight.copy(alpha = .46f), palette.body, palette.dark), center = androidx.compose.ui.geometry.Offset(dx, dy), radius = radius * max(0.85f, spread))
        "plastic" -> Brush.radialGradient(listOf(Color.White.copy(alpha = .64f), palette.highlight.copy(alpha = .25f), palette.body, palette.dark), center = androidx.compose.ui.geometry.Offset(dx, dy), radius = radius * max(1f, spread))
        else -> Brush.radialGradient(listOf(palette.highlight.copy(alpha = .45f), palette.body, palette.dark), center = androidx.compose.ui.geometry.Offset(dx, dy), radius = radius * max(1f, spread))
    }

    drawOval(
        brush = Brush.radialGradient(listOf(Color.Black.copy(alpha = .16f), Color.Transparent)),
        topLeft = androidx.compose.ui.geometry.Offset(23f, 76f),
        size = androidx.compose.ui.geometry.Size(54f, 13f),
    )
    drawPath(path, brush = fillBrush)

    if (shading != "flat") {
        val shadowAlpha = (shadow * 0.16f).coerceIn(0f, .34f)
        drawPath(path, color = Color.Black.copy(alpha = shadowAlpha), style = Stroke(width = 2.2f))
        val hAlpha = (highlight * 0.22f).coerceIn(0f, .5f)
        drawPath(path, color = Color.White.copy(alpha = hAlpha), style = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (rim > 0f && shading != "flat" && shading != "smooth") {
        drawPath(path, color = palette.highlight.copy(alpha = (rim * 0.18f).coerceAtMost(.34f)), style = Stroke(width = 1.8f + rim, pathEffect = PathEffect.cornerPathEffect(4f)))
    }

    if (shading == "fabric") {
        androidx.compose.ui.graphics.drawscope.clipPath(path) {
            val strandCount = 28
            for (i in 0 until strandCount) {
                val x = 15f + i * 2.6f
                val y = 18f + ((sin(i * 1.7) + 1) * 18f).toFloat()
                drawLine(
                    color = palette.highlight.copy(alpha = .035f + .02f * (i % 3)),
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x + 0.9f, y + 12f),
                    strokeWidth = .8f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

}

private fun DrawScope.drawFace(type: String, face: String, ink: Color, lookX: Float, lookY: Float, sleeping: Boolean, pitch: Float) {
    val yBase = when (type.lowercase()) {
        "triangle", "drop" -> 48f
        "pill", "puddle" -> 52f
        else -> 50f
    }
    val eyeY = yBase + pitch * .05f
    val eyeSep = when (type.lowercase()) {
        "cat", "alien" -> 11f
        "droid", "mech" -> 10f
        else -> 9f
    }
    if (sleeping) {
        drawLine(50f - eyeSep, eyeY, 50f - eyeSep + 5f, eyeY + 1.3f, color = ink, strokeWidth = 1.8f, cap = StrokeCap.Round)
        drawLine(50f + eyeSep - 5f, eyeY + 1.3f, 50f + eyeSep, eyeY, color = ink, strokeWidth = 1.8f, cap = StrokeCap.Round)
    } else {
        drawEye(50f - eyeSep, eyeY, ink, lookX, lookY, type)
        drawEye(50f + eyeSep, eyeY, ink, lookX, lookY, type)
    }
    if (face == "mouth") {
        val mouthY = eyeY + 11f
        drawArc(
            color = ink,
            startAngle = if (sleeping) 180f else 15f,
            sweepAngle = if (sleeping) 180f else 150f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(45f, mouthY - 3f),
            size = androidx.compose.ui.geometry.Size(10f, 6f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawEye(x: Float, y: Float, ink: Color, lookX: Float, lookY: Float, type: String) {
    val eyeRadius = if (type.lowercase() in listOf("droid", "mech")) 4.3f else 3.5f
    drawCircle(Color.White.copy(alpha = .96f), radius = eyeRadius + 1.3f, center = androidx.compose.ui.geometry.Offset(x, y))
    drawCircle(ink, radius = eyeRadius, center = androidx.compose.ui.geometry.Offset(x + lookX * 1.7f, y + lookY * 1.3f))
    drawCircle(Color.White.copy(alpha = .82f), radius = 0.9f, center = androidx.compose.ui.geometry.Offset(x + lookX * 1.7f - .8f, y + lookY * 1.3f - .9f))
}

@Composable
fun BotAvatar(
    type: String,
    modifier: Modifier = Modifier,
    state: String = "default",
    face: String = "eyes",
    size: Dp = 64.dp,
    path: String? = null,
    color: Color? = null,
    brightness: Float = 1f,
    saturation: Float = 1.5f,
    ink: Color? = null,
    speed: Float = 1f,
    paused: Boolean = false,
    seed: Float = 0.5f,
    shading: String = "fabric",
    shadow: Float? = null,
    highlight: Float? = null,
    light: Float? = null,
    rim: Float? = null,
    spread: Float? = null,
    depth: Float = .65f,
    roundness: Float = 1f,
    jumpHeight: Float = 26f,
    jumpEvery: Float = 8f,
    jumpSpin: Float = 1f,
    turn: Float = 1f,
    interactive: Boolean = true,
    jumpLean: Float = 6f,
    label: String = "Bot avatar",
) {
    // Custom SVG path is accepted in the same 100×100 body coordinate space.
    // Built-in shapes remain dependency-free and deterministic for the type roster.
    var clickNonce by remember { mutableIntStateOf(0) }
    val clickJump = remember { Animatable(0f) }
    LaunchedEffect(clickNonce) {
        if (clickNonce == 0) return@LaunchedEffect
        clickJump.snapTo(0f)
        clickJump.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        clickJump.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
    }

    val transition = rememberInfiniteTransition(label = "bot-avatar-$type-$seed")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bot-phase",
    )
    val phase = if (paused) 0f else time * max(.05f, speed)
    val seeded = (seed.coerceIn(0f, 1f) + .137f) % 1f
    val sizePx = with(LocalDensity.current) { size.toPx() }

    val bodyPath = remember(type, path) { buildBody(type, path) }
    val idleWave = sin((phase + seeded) * 2f * PI.toFloat())
    val lookX = if (paused) 0f else idleWave * .9f * turn.coerceIn(0f, 2f)
    val lookY = if (paused) 0f else cos((phase + seeded) * 2f * PI.toFloat()) * .45f

    val stateName = state.lowercase()
    val working = stateName == "working"
    val sleeping = stateName == "sleeping"
    val jump = when {
        paused -> 0f
        working -> abs(sin((phase + seeded) * PI.toFloat() * 4.2f)) * 5.5f
        sleeping -> 0f
        jumpEvery <= 0f -> 0f
        else -> {
            val elapsedSeconds = time * 4f * max(.05f, speed)
            val t = if (jumpEvery > 0f) ((elapsedSeconds / jumpEvery) + seeded) % 1f else 1f
            if (t < .27f) sin(t / .27f * PI.toFloat()).coerceAtLeast(0f) * jumpHeight * .22f else 0f
        }
    } + clickJump.value * jumpHeight * .78f
    val jumpPx = jump * (sizePx / BODY_UNITS)

    val yaw = when {
        sleeping -> 0f
        working -> idleWave * .22f
        else -> lookX * .18f
    }
    val rotation = when {
        sleeping -> 0f
        working -> sin((phase + seeded) * PI.toFloat() * 2f) * (10f * jumpSpin)
        else -> yaw
    }
    val scaleX = 1f - (clickJump.value * .06f)
    val scaleY = 1f + (jump * .005f) - (clickJump.value * .04f)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = -jumpPx
                rotationZ = rotation + clickJump.value * jumpLean
                rotationX = if (sleeping) 8f else 0f
                scaleX = scaleX * if (working) .99f + .01f * sin(phase * 12f) else 1f
                scaleY = scaleY
            }
            .then(
                if (interactive) Modifier.pointerInput(type, seed) {
                    detectTapGestures(onTap = { clickNonce += 1 })
                } else Modifier
            )
            .semantics { contentDescription = label },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            scale(size.minDimension / BODY_UNITS, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                val palette = if (color != null) avatarPalette(type, null, brightness, saturation).copy(body = color) else avatarPalette(type, null, brightness, saturation)
                val effectiveInk = ink ?: if (luminance(palette.body) > .62f) Color(0xFF20242A) else Color.White
                val shadowValue = shadow ?: if (shading == "fabric") 1.15f else .35f
                val highlightValue = highlight ?: if (shading == "fabric") 1.45f else 1.3f
                val lightValue = light ?: if (shading == "fabric") 295f else 300f
                val rimValue = rim ?: if (shading == "fabric") .6f else .5f
                val spreadValue = spread ?: if (shading == "fabric") 1.6f else 1.55f
                drawBody(bodyPath, palette, shading.lowercase(), shadowValue, highlightValue, rimValue, spreadValue, roundness, lightValue)
                drawFace(type, face.lowercase(), effectiveInk, lookX, lookY, sleeping, yaw * 8f)
            }
        }
    }
}

