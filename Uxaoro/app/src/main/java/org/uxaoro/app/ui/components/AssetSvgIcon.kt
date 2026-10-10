package org.uxaoro.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

private object AssetIconImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
            .also { instance = it }
    }
}

@Composable
fun AssetSvgIcon(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color,
) {
    val context = LocalContext.current.applicationContext
    val imageLoader = remember(context) { AssetIconImageLoader.get(context) }
    val safeName = name.filter { it.isLetterOrDigit() || it == '-' }
    AsyncImage(
        model = "file:///android_asset/icons/$safeName-svgrepo-com.svg",
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
    )
}