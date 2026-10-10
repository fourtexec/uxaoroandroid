package org.uxaoro.app.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.ui.theme.AppearanceState

@Composable
fun motionOn(): Boolean {
    val tick = AppearanceState.rev
    @Suppress("UNUSED_VARIABLE")
    val seen = tick
    return !Prefs.reduceMotion
}

fun screenTransform(forward: Boolean): ContentTransform {
    if (Prefs.reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val enter = slideInHorizontally(
        animationSpec = tween(340, easing = FastOutSlowInEasing),
        initialOffsetX = { full -> if (forward) full / 4 else -full / 4 },
    ) + fadeIn(tween(280))
    val exit = slideOutHorizontally(
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        targetOffsetX = { full -> if (forward) -full / 5 else full / 5 },
    ) + fadeOut(tween(200))
    return enter togetherWith exit
}

fun tabTransform(from: Int, to: Int): ContentTransform {
    if (Prefs.reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val forward = to > from
    return (
        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (forward) it / 8 else -it / 8 } +
            fadeIn(tween(220))
        ) togetherWith (
        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { if (forward) -it / 8 else it / 8 } +
            fadeOut(tween(160))
        )
}

@Composable
fun Modifier.fadeUp(delayMillis: Int = 0, fromY: Float = 28f): Modifier {
    if (!motionOn()) return this
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(480, easing = FastOutSlowInEasing),
        label = "fadeUpA",
    )
    val y by animateFloatAsState(
        if (visible) 0f else fromY,
        tween(480, easing = FastOutSlowInEasing),
        label = "fadeUpY",
    )
    return graphicsLayer {
        this.alpha = alpha
        translationY = y
    }
}

@Composable
fun Modifier.staggerIn(index: Int, fromY: Float = 18f): Modifier {
    if (!motionOn() || index > 12) return this
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(10) * 42L)
        visible = true
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(360, easing = FastOutSlowInEasing), label = "stA")
    val y by animateFloatAsState(if (visible) 0f else fromY, tween(420, easing = FastOutSlowInEasing), label = "stY")
    return graphicsLayer {
        this.alpha = alpha
        translationY = y
    }
}

@Composable
fun Modifier.popIn(): Modifier {
    if (!motionOn()) return this
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    val scale by animateFloatAsState(
        if (ready) 1f else 0.55f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pop",
    )
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(180), label = "popA")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

@Composable
fun Modifier.bubbleIn(mine: Boolean, animate: Boolean = true): Modifier {
    if (!motionOn() || !animate) return this
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    val x by animateFloatAsState(
        if (ready) 0f else if (mine) 28f else -28f,
        tween(280, easing = FastOutSlowInEasing),
        label = "bubX",
    )
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(220), label = "bubA")
    return graphicsLayer {
        translationX = x
        this.alpha = alpha
    }
}

@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    if (!motionOn()) return this
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Modifier.pulse(min: Float = 0.92f, max: Float = 1.06f, duration: Int = 1100): Modifier {
    if (!motionOn()) return this
    val inf = rememberInfiniteTransition(label = "pulse")
    val s by inf.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseS",
    )
    return graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

@Composable
fun tabIconScale(selected: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (!motionOn()) 1f else if (selected) 1.14f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tabScale",
    )
    return scale
}

@Composable
fun HeartBurst(liked: Boolean): Float {
    if (!motionOn()) return 1f
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(liked) {
        if (liked) {
            popped = true
            delay(180)
            popped = false
        } else {
            popped = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1.38f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "heart",
    )
    return scale
}

@Composable
fun <S> ScreenFade(
    targetState: S,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        fadeIn(tween(280)) togetherWith fadeOut(tween(180))
    },
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = transitionSpec,
        label = "screen",
    ) { content(it) }
}
