package org.uxaoro.app.util

import android.content.Context
import android.content.Intent
import org.uxaoro.app.data.Prefs

/**
 * Build a web SPA link (`{api}/#/route`) for the same screen — the API origin
 * also serves the web client, so the link opens in any browser.
 */
fun uxaoroWebLink(route: String): String =
    Prefs.apiBase.trimEnd('/') + "/#" + if (route.startsWith("/")) route else "/$route"

/** Open the system share sheet with `text` (a link, usually). */
fun shareText(ctx: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
