package org.uxaoro.app

import android.app.Application
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.MediaLoader
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.net.AppProxy
import org.uxaoro.app.upd.Updater

class UxaoroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AppProxy.attach(this)
        Api.rebuild()
        // Avatars, stickers and video posters must follow the same TLS guard as
        // the API/websocket client (see org.uxaoro.app.net.TlsGuard).
        MediaLoader.attach(this)
        MediaLoader.install()
        // Check https://fourtexec.site/downloads/uxaoro/upd/upd.manifest.json
        // in the background (throttled, and never in debug builds).
        Updater.maybeAutoCheck(this)
    }
}
