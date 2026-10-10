package org.uxaoro.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.uxaoro.app.data.Prefs

data class Accent(val key: String, val label: String, val color: Color)

val Accents = listOf(
    Accent("indigo", "Indigo", Color(0xFF6366F1)),
    Accent("blue", "Blue", Color(0xFF3B82F6)),
    Accent("violet", "Violet", Color(0xFF8B5CF6)),
    Accent("teal", "Teal", Color(0xFF14B8A6)),
    Accent("green", "Green", Color(0xFF22C55E)),
    Accent("cyan", "Cyan", Color(0xFF06B6D4)),
    Accent("orange", "Orange", Color(0xFFF97316)),
    Accent("rose", "Rose", Color(0xFFF43F5E)),
    Accent("amber", "Amber", Color(0xFFF59E0B)),
    Accent("pink", "Pink", Color(0xFFEC4899)),
)

fun accentColor(key: String = Prefs.accent): Color =
    Accents.firstOrNull { it.key == key }?.color ?: Accents.first().color

object AppearanceState {
    var rev by mutableIntStateOf(0)
        private set

    fun bump() {
        rev++
    }

    fun syncFromPrefs() {
        rev++
    }
}

@Composable
private fun anim(c: Color) = animateColorAsState(c, tween(if (Prefs.reduceMotion) 0 else 420), label = "theme")

private fun TextStyle.ink(size: Float, scale: Float) =
    copy(fontSize = (size * scale).sp, color = Color.Unspecified)

@Composable
fun UxaoroTheme(content: @Composable () -> Unit) {
    val tick = AppearanceState.rev
    @Suppress("UNUSED_VARIABLE")
    val seen = tick
    val dark = when (Prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accent by anim(accentColor())
    val amoled = Prefs.amoled && dark
    // Dark base: a whisper of blue-violet instead of flat pure black —
    // with the mesh glows it reads as a deep layered surface. AMOLED keeps
    // true black for perfect pixels.
    val bg by anim(if (dark) (if (amoled) Color.Black else Color(0xFF0A0A10)) else Color(0xFFFBFAFE))
    val onBg by anim(if (dark) Color(0xFFF4F4F6) else Color(0xFF14131A))
    val surface by anim(
        when {
            amoled -> Color.Black
            dark -> Color(0xFF0C0C12)
            else -> Color(0xFFF3F2F8)
        },
    )
    // Surfaces carry a faint accent tint so the whole app reads as one palette
    // instead of neutral gray, whatever accent the user picked.
    val variant by anim(
        if (dark) {
            lerp(Color(0xFF16161E), accent, 0.10f)
        } else {
            lerp(Color(0xFFF0EFF6), accent, 0.06f)
        },
    )
    val onVar by anim(if (dark) Color(0xFFDDDDE4) else Color(0xFF565560))
    val outline by anim(if (dark) Color(0xFF2A2A34) else Color(0xFFE3E2EA))

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = onBg,
            onPrimaryContainer = bg,
            secondary = accent.copy(alpha = 0.85f),
            onSecondary = Color.White,
            tertiary = accent,
            onTertiary = Color.White,
            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onBg,
            surfaceVariant = variant,
            onSurfaceVariant = onVar,
            outline = outline,
            error = Color(0xFFEF4444),
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = onBg,
            onPrimaryContainer = bg,
            secondary = accent,
            onSecondary = Color.White,
            tertiary = accent,
            onTertiary = Color.White,
            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onBg,
            surfaceVariant = variant,
            onSurfaceVariant = onVar,
            outline = outline,
            error = Color(0xFFDC2626),
            onError = Color.White,
        )
    }
    val scale = when (Prefs.fontScale) {
        "small" -> 0.9f
        "large" -> 1.15f
        else -> 1f
    }
    val base = MaterialTheme.typography
    val ink = scheme.onBackground
    val typography = Typography(
        displayLarge = base.displayLarge.ink(57f, scale),
        displayMedium = base.displayMedium.ink(45f, scale),
        displaySmall = base.displaySmall.ink(36f, scale),
        headlineLarge = base.headlineLarge.ink(32f, scale),
        headlineMedium = base.headlineMedium.ink(28f, scale),
        headlineSmall = base.headlineSmall.ink(24f, scale),
        titleLarge = base.titleLarge.ink(22f, scale),
        titleMedium = base.titleMedium.ink(16f, scale),
        titleSmall = base.titleSmall.ink(14f, scale),
        bodyLarge = base.bodyLarge.ink(16f, scale),
        bodyMedium = base.bodyMedium.ink(14f, scale),
        bodySmall = base.bodySmall.ink(12f, scale),
        labelLarge = base.labelLarge.ink(14f, scale),
        labelMedium = base.labelMedium.ink(12f, scale),
        labelSmall = base.labelSmall.ink(11f, scale),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides ink, content = content)
    }
}
