package org.uxaoro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.uxaoro.app.ui.theme.glassHairline
import org.uxaoro.app.ui.theme.glassBlurBar
import org.uxaoro.app.ui.theme.LocalHazeState
import org.uxaoro.app.ui.theme.isDarkSurface

/**
 * Rounded, glass-style menus and toasts.
 *
 * A custom [Popup] preserves anchor positioning while avoiding Material3's
 * opaque default menu. Popup windows use the tinted glass surface because Haze
 * children in a separate window can leave stale sampled pixels after dismissal.
 */
val GlassMenuShape = RoundedCornerShape(22.dp)

/**
 * Stable floating-surface fallback: tinted fill, soft highlight, shadow and rim.
 */
@Composable
fun Modifier.glassSurface(shape: Shape, elevation: Dp = 16.dp): Modifier {
    val dark = isDarkSurface(MaterialTheme.colorScheme.background)
    // Keep the popup menu visibly frosted even though we avoid Haze in a Popup
    // window: this gives enough translucency and highlight to read like glass
    // without the stale-blur artifact that can stick around after dismiss.
    val fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.72f else 0.78f)
    val sheen = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    val shadow = if (dark) Color.Black.copy(alpha = 0.52f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    return this
        .shadow(elevation, shape, ambientColor = shadow, spotColor = shadow)
        .clip(shape)
        .background(fill, shape)
        .background(
            Brush.verticalGradient(listOf(sheen, Color.Transparent, Color.Transparent)),
            shape,
        )
        .border(1.dp, glassHairline(), shape)
}

/** Inset kept inside the popup so the card's drop shadow is not clipped. */
private val GlassShadowInset = 14.dp

/** Distance between the anchor and the card's near edge. */
private val GlassMenuGap = 6.dp

@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val insetPx = with(density) { GlassShadowInset.roundToPx() }
    val gapPx = with(density) { GlassMenuGap.roundToPx() }
    val provider = remember(insetPx, gapPx) { GlassMenuPositionProvider(insetPx, gapPx) }
    val shape = GlassMenuShape
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        // Focusable so the back gesture and a tap outside both close the menu.
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.padding(GlassShadowInset)) {
            Column(
                modifier
                    .width(256.dp)
                    .glassSurface(shape, elevation = 18.dp)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                content = content,
            )
        }
    }
}

/**
 * One row of a [GlassMenu]. `leadingIcon` names an SVG asset; `danger` tints the
 * label like a destructive action.
 */
@Composable
fun GlassMenuItem(
    text: String,
    leadingIcon: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            AssetSvgIcon(leadingIcon, contentDescription = null, size = 18.dp, tint = ink)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 14.sp, color = ink)
    }
}

/** The app's toast: a rounded glass card rather than Material's flat snackbar. */
@Composable
fun GlassToast(text: String, modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(18.dp)) {
    val surface = if (LocalHazeState.current != null) {
        Modifier.glassBlurBar(shape, elevation = 14.dp)
    } else {
        Modifier.glassSurface(shape, elevation = 14.dp)
    }
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .widthIn(max = 300.dp)
                .then(surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Opens the card under the anchor, right edges aligned — the menus here hang off
 * "..." buttons in a top bar or the corner of a card. Flips above the anchor
 * when there is no room below, and never leaves the window.
 *
 * The popup is [inset] larger than the card on every side ([GlassShadowInset]):
 * a Popup window is exactly the size of its content, so without that room the
 * card's shadow would be clipped away at the window edge.
 */
private class GlassMenuPositionProvider(private val inset: Int, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val cardWidth = (popupContentSize.width - 2 * inset).coerceAtLeast(0)
        val cardHeight = (popupContentSize.height - 2 * inset).coerceAtLeast(0)
        var x = anchorBounds.right - cardWidth - inset
        var y = anchorBounds.bottom + gap - inset
        if (y + popupContentSize.height > windowSize.height) {
            // Not enough room below: open upwards instead of off-screen.
            y = anchorBounds.top - cardHeight - gap - inset
        }
        // Clamp the whole Popup window (including its shadow inset), not just
        // the visible card, or Android can clip its right/bottom edge.
        x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
