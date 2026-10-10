package org.uxaoro.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.TokenPair
import org.uxaoro.app.ui.AppRoot
import org.uxaoro.app.ui.AppVm

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveDeepLinkCallback(intent)
        setContent { AppRoot(vm) }
    }

    override fun onStart() {
        super.onStart()
        // Coming back from the background: the app lock may have to return, and
        // the screenshot rule is re-applied in case it changed in Settings.
        vm.onAppForegrounded()
        applyScreenshotRule()
        // Coming back from the installer (or a long pause) is a good moment to
        // re-check the update manifest; Updater throttles this internally.
        org.uxaoro.app.upd.Updater.maybeAutoCheck(this)
    }

    override fun onStop() {
        super.onStop()
        // Remember when the app left the foreground; the lock timeout is
        // measured from here.
        vm.onAppBackgrounded()
    }

    /**
     * FLAG_SECURE blocks screenshots and screen recording and keeps the app out
     * of the recents thumbnail. Re-applied on every resume so toggling it in
     * Settings takes effect without a restart.
     */
    private fun applyScreenshotRule() {
        if (Prefs.blockScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveDeepLinkCallback(intent)
    }

    private fun receiveDeepLinkCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!"uxaoro".equals(uri.scheme, ignoreCase = true)) return
        if ("captcha".equals(uri.host, ignoreCase = true)) {
            val state = uri.getQueryParameter("state") ?: return
            // Newer backends deep-link the raw Turnstile response
            // (turnstile_token) and the app exchanges it itself, so the
            // IP-bound pass is issued to the same network path that consumes
            // it. Older backends deep-link an already-exchanged captcha_pass;
            // an empty captcha_pass is the CAPTCHA-disabled signal.
            val captchaPass = uri.getQueryParameter("captcha_pass")
            val turnstileToken = uri.getQueryParameter("turnstile_token")
            if (captchaPass == null && turnstileToken == null) return
            vm.receiveCaptchaCallback(state, captchaPass.orEmpty(), turnstileToken.orEmpty())
            return
        }
        if ("oauth".equals(uri.host, ignoreCase = true)) {
            val payloadB64 = uri.getQueryParameter("oauth_payload") ?: return
            runCatching {
                val decoded = String(Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                val obj = JsonParser.parseString(decoded).asJsonObject
                val err = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
                if (!err.isNullOrBlank()) {
                    vm.snack = err
                    return
                }
                val pair = Gson().fromJson(decoded, TokenPair::class.java)
                if (!pair.accessToken.isNullOrBlank()) {
                    Prefs.saveSession(pair)
                    if (vm.screen == org.uxaoro.app.ui.Screen.Settings) {
                        vm.me = pair.user
                        vm.snack = "Account linked"
                    } else {
                        vm.onLoggedIn()
                    }
                }
            }
        }
    }
}
