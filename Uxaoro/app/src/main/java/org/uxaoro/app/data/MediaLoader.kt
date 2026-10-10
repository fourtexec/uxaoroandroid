package org.uxaoro.app.data

import android.content.Context
import coil.Coil
import coil.ImageLoader

/**
 * Coil loader for Uxaoro media (avatars, stickers, post images, video posters).
 *
 * It is built from [Api.mediaClient] so images and video follow exactly the same
 * TLS rules as the API and websocket clients — including the
 * [org.uxaoro.app.net.TlsGuard] reset behaviour when an SSL-proxying proxy tries
 * to intercept HTTPS. [install] runs at startup and again whenever the network
 * layer is rebuilt (guard toggles, API host changes).
 */
object MediaLoader {
    private var app: Context? = null

    fun attach(ctx: Context) {
        app = ctx.applicationContext
    }

    fun install() {
        val ctx = app ?: return
        runCatching {
            Coil.setImageLoader(
                ImageLoader.Builder(ctx)
                    .okHttpClient(Api.mediaClient())
                    .crossfade(true)
                    .build(),
            )
        }
    }
}
