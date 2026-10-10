package org.uxaoro.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.uxaoro.app.R

/**
 * A space-doodle wallpaper based on the supplied reference: its transparent
 * line work is recolored at runtime, so the same stars, planets, astronauts,
 * rockets and UFOs read cleanly in either app theme.
 */
@Composable
fun ChatEmojiPattern(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.45f
    val canvas = if (dark) Color(0xFF07120E) else Color(0xFFD1EFCF)
    val ink = if (dark) Color(0xFF69C98C) else Color(0xFF367B4B)

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(canvas),
    ) {
        Image(
            painter = painterResource(R.drawable.chat_space_doodles),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(ink),
            // The pattern is deliberately quieter on the dark canvas. Message
            // bubbles and frosted controls remain the visual priority.
            alpha = if (dark) 0.30f else 0.52f,
        )
    }
}
