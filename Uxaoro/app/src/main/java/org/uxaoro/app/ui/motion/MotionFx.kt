package org.uxaoro.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Extended motion FX kit. Everything respects `Prefs.reduceMotion` via
 * [motionOn] and degrades to a static presentation when motion is off.
 *
 * Contents:
 *  - Entrances: [zoomPop], [tiltPress]
 *  - Ambient: [floaty], [pulseRing], [glowPulse], [sheenSweep], [SparkleField]
 *  - Feedback: [shake], [spinWhile], [BurstOverlay], [ConfettiRain]
 *  - Loading: [shimmerEffect], [ShimmerBox], [TypingDots], [AnimatedCounter]
 *  - Text: [TypewriterText]
 *  - Scroll: [parallax]
 */

// ---------------------------------------------------------------------------
// Entrances & press
// ---------------------------------------------------------------------------

/** Springy zoom entrance with a touch of rotation — livelier sibling of popIn. */
@Composable
fun Modifier.zoomPop(from: Float = 0.72f, twist: Float = -10f): Modifier {
    if (!motionOn()) return this
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    val scale by animateFloatAsState(
        if (ready) 1f else from,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "zoomPopS",
    )
    val rot by animateFloatAsState(
        if (ready) 0f else twist,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "zoomPopR",
    )
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(200), label = "zoomPopA")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationZ = rot
        this.alpha = alpha
    }
}

/** Subtle 3D tilt while pressed — feels physical on cards and tiles. */
@Composable
fun Modifier.tiltPress(interaction: MutableInteractionSource, maxDeg: Float = 5f): Modifier {
    if (!motionOn()) return this
    val pressed by interaction.collectIsPressedAsState()
    val t by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tilt",
    )
    return graphicsLayer {
        rotationX = t * maxDeg * 0.55f
        rotationY = t * maxDeg
        cameraDistance = 13f * density
    }
}

// ---------------------------------------------------------------------------
// Ambient loops
// ---------------------------------------------------------------------------

/** Gentle bobbing — logos, avatars, floating action buttons. */
@Composable
fun Modifier.floaty(amplitude: Float = 5f, durationMillis: Int = 2300): Modifier {
    if (!motionOn()) return this
    val t = rememberInfiniteTransition(label = "floaty")
    val y by t.animateFloat(
        initialValue = -amplitude,
        targetValue = amplitude,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )
    return graphicsLayer { translationY = y }
}

/** Expanding halo rings — live/record/recording affordances. */
@Composable
fun Modifier.pulseRing(color: Color, rings: Int = 2, durationMillis: Int = 1900): Modifier {
    if (!motionOn()) return this
    val t = rememberInfiniteTransition(label = "pRing")
    val p by t.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pRingP",
    )
    return drawBehind {
        val base = size.minDimension / 2f
        repeat(rings) { i ->
            val lp = (p + i * 1f / rings) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - lp) * 0.45f),
                radius = base * (0.55f + lp * 0.72f),
                style = Stroke(width = 2.5f),
            )
        }
    }
}

/** Breathing radial halo behind an element (logos, live avatars). */
@Composable
fun Modifier.glowPulse(
    color: Color,
    minAlpha: Float = 0.16f,
    maxAlpha: Float = 0.40f,
    durationMillis: Int = 1700,
): Modifier {
    if (!motionOn()) return this
    val t = rememberInfiniteTransition(label = "glow")
    val a by t.animateFloat(
        minAlpha,
        maxAlpha,
        infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowA",
    )
    val s by t.animateFloat(
        0.92f,
        1.08f,
        infiniteRepeatable(tween(durationMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowS",
    )
    return drawBehind {
        val r = size.minDimension * 0.74f * s
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = a), color.copy(alpha = a * 0.32f), Color.Transparent),
                radius = r,
            ),
            radius = r,
        )
    }
}

/** Diagonal glass-light sweep across a surface. */
@Composable
fun Modifier.sheenSweep(periodMillis: Int = 2600, strength: Float = 0.14f): Modifier {
    if (!motionOn()) return this
    val t = rememberInfiniteTransition(label = "sheen")
    val p by t.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "sheenP",
    )
    return drawBehind {
        val w = size.width
        val h = size.height
        val x = (p * 1.7f - 0.35f) * w
        val band = w * 0.34f
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent),
                start = Offset(x - band, -h * 0.2f),
                end = Offset(x + band, h * 1.2f),
            ),
        )
    }
}

/** Drifting sparkle dust — auth/boot backdrops, celebration screens. */
@Composable
fun SparkleField(
    modifier: Modifier = Modifier,
    count: Int = 24,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (!motionOn()) return
    val t = rememberInfiniteTransition(label = "spark")
    val p by t.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Restart),
        label = "sparkP",
    )
    val seeds = remember(count) { List(count) { Random(it * 917 + 7) } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        seeds.forEachIndexed { i, rnd ->
            val x0 = rnd.nextFloat() * w
            val y0 = rnd.nextFloat() * h
            val speed = 0.45f + rnd.nextFloat() * 0.9f
            val wobble = sin((p * 2f * PI).toFloat() * speed + i) * w * 0.02f
            val x = (x0 + wobble).coerceIn(0f, w)
            val y = ((y0 - p * h * (0.3f + speed * 0.4f)) % h + h) % h
            val tw = 0.5f + 0.5f * sin((p * 2f * PI).toFloat() * (1.4f + speed) + i * 1.7f)
            val r = 1.1f + (i % 4) * 0.7f
            drawCircle(color.copy(alpha = 0.08f + 0.22f * tw), radius = r * 2.4f, center = Offset(x, y))
            drawCircle(color.copy(alpha = 0.25f + 0.55f * tw), radius = r, center = Offset(x, y))
        }
    }
}

// ---------------------------------------------------------------------------
// Feedback
// ---------------------------------------------------------------------------

/** Horizontal error shake — bump [trigger] whenever validation fails. */
@Composable
fun Modifier.shake(trigger: Int): Modifier {
    if (!motionOn() || trigger <= 0) return this
    val dx = remember { Animatable(0f) }
    val rot = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        dx.snapTo(0f)
        rot.snapTo(0f)
        repeat(5) { i ->
            val dir = if (i % 2 == 0) 1f else -1f
            val mag = 15f - i * 2.5f
            dx.animateTo(dir * mag, tween(40, easing = LinearEasing))
            rot.animateTo(dir * (2.6f - i * 0.45f), tween(40, easing = LinearEasing))
        }
        dx.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
        rot.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
    }
    return graphicsLayer {
        translationX = dx.value
        rotationZ = rot.value
    }
}

/** Continuous rotation while [on] (sending, refreshing), springs back when off. */
@Composable
fun Modifier.spinWhile(on: Boolean, millisPerTurn: Int = 850): Modifier {
    if (!motionOn()) return this
    val rot = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) {
            while (true) {
                rot.animateTo(rot.value + 360f, tween(millisPerTurn, easing = LinearEasing))
            }
        } else {
            rot.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    return graphicsLayer { rotationZ = rot.value }
}

/**
 * Radial particle burst on [trigger] around [content] — likes, reactions,
 * send confirmations. Bump the trigger to fire.
 */
@Composable
fun BurstOverlay(
    trigger: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    particleCount: Int = 14,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        content()
        if (motionOn() && trigger > 0) {
            val t = remember { Animatable(0f) }
            val rnd = remember(trigger) { Random(trigger * 71 + 3) }
            LaunchedEffect(trigger) {
                t.snapTo(0f)
                t.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
            }
            Canvas(Modifier.matchParentSize()) {
                val prog = t.value
                if (prog <= 0f || prog >= 1f) return@Canvas
                val dist = size.minDimension * (0.30f + 0.85f * prog)
                val fade = 1f - prog
                repeat(particleCount) { i ->
                    val ang = ((i * 360f / particleCount) + rnd.nextFloat() * 22f) * (PI.toFloat() / 180f)
                    val jitter = 0.75f + (i % 4) * 0.14f
                    val x = size.width / 2f + cos(ang) * dist * jitter
                    val y = size.height / 2f + sin(ang) * dist * jitter
                    drawCircle(color.copy(alpha = fade), radius = (3.2f + (i % 3)) * (1f - 0.45f * prog), center = Offset(x, y))
                }
            }
        }
    }
}

private class ConfettiPiece(
    val x: Float,
    val delayFrac: Float,
    val speed: Float,
    val sway: Float,
    val rotSpeed: Float,
    val size: Float,
    val colorIdx: Int,
)

/** Falling confetti celebration — purchases, milestones. Fire by bumping [trigger]. */
@Composable
fun ConfettiRain(trigger: Int, modifier: Modifier = Modifier) {
    if (!motionOn() || trigger <= 0) return
    val t = remember { Animatable(0f) }
    val pieces = remember(trigger) {
        List(56) { i ->
            val r = Random(i * 77 + trigger * 13 + 5)
            ConfettiPiece(
                x = r.nextFloat(),
                delayFrac = r.nextFloat() * 0.35f,
                speed = 0.55f + r.nextFloat() * 0.8f,
                sway = 0.5f + r.nextFloat() * 1.6f,
                rotSpeed = -2.2f + r.nextFloat() * 4.4f,
                size = 6f + r.nextFloat() * 8f,
                colorIdx = r.nextInt(4),
            )
        }
    }
    LaunchedEffect(trigger) {
        t.snapTo(0f)
        t.animateTo(1f, tween(2600, easing = LinearEasing))
    }
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.tertiary, cs.secondary, Color(0xFFFFC53D))
    Canvas(modifier) {
        val prog = t.value
        if (prog <= 0f || prog >= 1f) return@Canvas
        pieces.forEach { pc ->
            val lp = ((prog - pc.delayFrac) / (1f - pc.delayFrac)).coerceIn(0f, 1f)
            if (lp <= 0f) return@forEach
            val x = pc.x * size.width + sin(lp * PI.toFloat() * 2f * pc.sway) * size.width * 0.05f
            val y = lp * lp * (size.height * 1.15f) - size.height * 0.08f
            rotate(degrees = lp * 360f * pc.rotSpeed, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[pc.colorIdx % colors.size],
                    topLeft = Offset(x - pc.size / 2f, y - pc.size / 4f),
                    size = Size(pc.size, pc.size / 2f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Loading & text
// ---------------------------------------------------------------------------

/** Animated shimmer highlight for skeleton placeholders. */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    if (!motionOn()) return this
    val t = rememberInfiniteTransition(label = "shim")
    val phase by t.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimP",
    )
    val base = Color.White.copy(alpha = 0.07f)
    val hi = Color.White.copy(alpha = 0.22f)
    return drawBehind {
        val w = size.width
        val x = (phase * 2f - 0.6f) * w
        drawRect(
            brush = Brush.linearGradient(
                listOf(base, hi, base),
                start = Offset(x - w * 0.45f, 0f),
                end = Offset(x + w * 0.45f, size.height),
            ),
        )
    }
}

/** Skeleton placeholder block with shimmer. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp)) {
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
            .shimmerEffect(),
    )
}

/** Three bouncing dots — typing indicators, background work. */
@Composable
fun TypingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (!motionOn()) {
        Text("•••", modifier = modifier, color = color, fontSize = 14.sp)
        return
    }
    val t = rememberInfiniteTransition(label = "dots")
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            val p by t.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(520, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 150),
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .padding(horizontal = 2.5.dp)
                    .size(6.dp)
                    .graphicsLayer {
                        translationY = -7f * p
                        alpha = 0.4f + 0.6f * p
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Typed text reveal with a caret — taglines, boot slogans. */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    charDelayMillis: Long = 55,
) {
    if (!motionOn()) {
        Text(text, modifier = modifier, fontSize = fontSize, fontWeight = fontWeight, color = color)
        return
    }
    var shown by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        shown = ""
        for (i in text.indices) {
            shown = text.substring(0, i + 1)
            delay(charDelayMillis)
        }
    }
    val typing = shown.length < text.length
    val caret = if (typing || (System.currentTimeMillis() / 500) % 2 == 0L) "▍" else ""
    Text(
        shown + caret,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
    )
}

private fun formatBigNumber(n: Long): String {
    val s = n.toString()
    val neg = s.startsWith("-")
    val digits = s.removePrefix("-")
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(' ')
        sb.append(digits[i])
    }
    return if (neg) "-$sb" else sb.toString()
}

/** Spring count-up number — balances, counters, stats. */
@Composable
fun AnimatedCounter(
    value: Long,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val anim = remember { Animatable(value.toFloat()) }
    var prev by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        anim.snapTo(prev.toFloat())
        anim.animateTo(
            value.toFloat(),
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        )
        prev = value
    }
    val display = if (!motionOn()) value else anim.value.toLong()
    Text(formatBigNumber(display), modifier = modifier, fontSize = fontSize, fontWeight = fontWeight, color = color)
}

// ---------------------------------------------------------------------------
// Scroll
// ---------------------------------------------------------------------------

/**
 * Header parallax: attach to a cover image inside a scrolling container so it
 * drifts slower than the content (needs the host LazyListState / scroll state).
 */
@Composable
fun Modifier.parallax(state: androidx.compose.foundation.lazy.LazyListState, factor: Float = 0.4f): Modifier {
    if (!motionOn()) return this
    return graphicsLayer {
        translationY = state.firstVisibleItemScrollOffset * factor
    }
}
