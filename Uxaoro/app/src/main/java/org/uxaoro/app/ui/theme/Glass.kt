package org.uxaoro.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.uxaoro.app.data.Prefs

val LocalHazeState = compositionLocalOf<HazeState?> { null }
val LocalMainChrome = compositionLocalOf { false }

fun isDarkSurface(bg: Color) = bg.luminance() < 0.45f

@Composable
fun glassHazeStyle(): HazeStyle {
    val dark = isDarkSurface(MaterialTheme.colorScheme.background)
    return HazeStyle(
        // White tint made blurred chrome look milky in dark mode. A near-black
        // tint keeps the depth of the backdrop while matching a quiet messenger
        // interface such as Telegram's dark chat surfaces.
        tint = if (dark) Color.Black.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.38f),
        blurRadius = if (dark) 20.dp else 24.dp,
        noiseFactor = 0f,
    )
}

/**
 * Elevated solid surface for cards, fields and composers. Frosting is reserved
 * for the app chrome (headers and bottom tab bar), so content areas stay calm
 * and readable over a wallpaper.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    intense: Boolean = false,
    elevation: Dp = 8.dp,
): Modifier {
    val dark = isDarkSurface(MaterialTheme.colorScheme.background)
    val fill = when {
        dark && intense -> Color(0xFF11121A)
        dark -> Color(0xFF0D0E14)
        intense -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = accentShadow(dark),
            spotColor = accentShadow(dark),
        )
        .clip(shape)
        .background(fill, shape)
}

// Shadow tinted toward the accent so card lift matches the palette; much
// subtler than pure black on the light theme.
@Composable
private fun accentShadow(dark: Boolean) =
    if (dark) Color.Black.copy(alpha = 0.68f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

/**
 * The 1dp highlight along the edge of a glass surface. Real frosted glass is
 * read mostly from this bright rim: without it a translucent card looks like a
 * faded panel rather than a pane. Kept separate so menus, toasts and chrome all
 * catch the light the same way.
 */
@Composable
fun glassHairline(): Color {
    val dark = isDarkSurface(MaterialTheme.colorScheme.background)
    return if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.70f)
}

/**
 * Frosted chrome for headers and navigation. Dark Haze tint keeps the result
 * close to black in dark mode instead of turning blurred areas pale.
 */
@Composable
fun Modifier.glassBlurBar(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 8.dp,
): Modifier {
    val dark = isDarkSurface(MaterialTheme.colorScheme.background)
    val fallback = if (dark) Color(0xFF101119) else MaterialTheme.colorScheme.surfaceVariant
    val haze = LocalHazeState.current
    // Clip the bar's own content; Haze also needs the shape passed explicitly
    // below because it paints the blur into the *source* layer, not this one.
    val layerShape = shape
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = accentShadow(dark),
            spotColor = accentShadow(dark),
        )
        .graphicsLayer {
            this.shape = layerShape
            clip = true
        }
        .clip(shape)
        .then(
            if (haze != null) Modifier.hazeChild(state = haze, shape = shape, style = glassHazeStyle())
            else Modifier.background(fallback, shape),
        )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    intense: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier.liquidGlass(shape, intense)) { content() }
}

/**
 * Ambient mesh gradient: soft accent-colored glows drifting slowly over the
 * base background. Gives the flat color real depth behind the glass surfaces.
 *
 * Note: Brush.radialGradient() works in PIXELS, so centers/radii are derived
 * from the real screen size in px (dp * density) — feeding raw dp values made
 * the glows a dim smudge in the top-left corner.
 *
 * Dark theme: slightly stronger glows + a faint top sheen so the canvas reads
 * as a deep, layered surface instead of flat black. With "reduce motion" the
 * same layered look is drawn statically.
 */
@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val base = cs.background
    val dark = isDarkSurface(base)
    val density = LocalDensity.current.density

    // Glows stay restrained on dark so content, not the animated backdrop, leads.
    val glowA = cs.primary.copy(alpha = if (dark) 0.11f else 0.10f)
    val glowB = cs.tertiary.copy(alpha = if (dark) 0.08f else 0.07f)
    val glowC = cs.primary.copy(alpha = if (dark) 0.06f else 0.06f)

    @Composable
    fun drawMesh(drift: Float) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = (maxWidth.value.coerceAtLeast(1f) * density).coerceAtLeast(1f)
            val h = (maxHeight.value.coerceAtLeast(1f) * density).coerceAtLeast(1f)
            // Three glows: top-left, mid-right, bottom-center (fully on screen).
            val c1 = Offset(w * (0.22f + 0.10f * sin(drift)), h * (0.14f + 0.08f * cos(drift * 0.7f)))
            val c2 = Offset(w * (0.80f + 0.08f * cos(drift * 0.9f)), h * (0.52f + 0.10f * sin(drift * 0.6f)))
            val c3 = Offset(w * (0.46f + 0.12f * sin(drift * 0.5f + 1.2f)), h * (0.96f + 0.03f * cos(drift * 0.8f)))
            // Three-stop radial gradients: soft falloff instead of a flat tint.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        listOf(glowA, glowA.copy(alpha = glowA.alpha * 0.45f), Color.Transparent),
                        center = c1,
                        radius = w * (if (dark) 0.78f else 0.95f),
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        listOf(glowB, glowB.copy(alpha = glowB.alpha * 0.4f), Color.Transparent),
                        center = c2,
                        radius = w * (if (dark) 0.70f else 0.85f),
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        listOf(glowC, glowC.copy(alpha = glowC.alpha * 0.45f), Color.Transparent),
                        center = c3,
                        radius = w * (if (dark) 0.85f else 0.95f),
                    ),
                ),
            )
            if (dark) {
                // Faint top sheen: lifts the header/tab-bar zone off the black.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(cs.primary.copy(alpha = 0.025f), Color.Transparent),
                            startY = 0f,
                            endY = (h * 0.45f).toFloat(),
                        ),
                    ),
                )
            }
        }
    }

    if (!Prefs.reduceMotion) {
        Box(modifier.fillMaxSize().background(base)) {
            val t = rememberInfiniteTransition(label = "mesh")
            val drift by t.animateFloat(
                initialValue = 0f,
                targetValue = (2.0 * PI).toFloat(),
                animationSpec = infiniteRepeatable(tween(26000), RepeatMode.Restart),
                label = "meshDrift",
            )
            drawMesh(drift)
        }
    } else {
        Box(modifier.fillMaxSize().background(base)) { drawMesh(0f) }
    }
}

/**
 * Scaffold whose top bar overlays the body so [glassBlurBar] can blur real
 * scrolling content. Body is the haze source; header (and the main tab bar)
 * are haze children.
 */
@Composable
fun BlurScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Transparent,
    content: @Composable (PaddingValues) -> Unit,
) {
    val parent = LocalHazeState.current
    val owned = remember { HazeState() }
    val main = LocalMainChrome.current
    // MainShell shares its source with the navigation bar; standalone routes
    // must keep separate sources so animated screen transitions cannot paint
    // a stale or unrelated screen into another screen's glass surfaces.
    val haze = if (main) parent ?: owned else owned
    // contentWindowInsets is deliberately zero so headers can draw edge-to-edge;
    // reserve the system gesture/navigation area in the scroll content instead.
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val style = glassHazeStyle()
    @Composable
    fun Body() {
        Scaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                Box(Modifier.padding(bottom = if (main) 56.dp else 0.dp)) {
                    floatingActionButton()
                }
            },
        ) { scaffoldPad ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().haze(state = haze, style = style)) {
                    content(
                        PaddingValues(
                            top = 52.dp,
                            bottom = scaffoldPad.calculateBottomPadding() + navigationBottom + (if (main) 62.dp else 0.dp),
                        ),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) { topBar() }
            }
        }
    }
    CompositionLocalProvider(LocalHazeState provides haze) { Body() }
}
