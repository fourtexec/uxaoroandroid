package org.uxaoro.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import org.uxaoro.app.ui.theme.AppearanceState
import java.util.UUID

object Prefs {
    const val DEFAULT_API = "https://api.fourtexec.site"

    /**
     * Version of the installed build. Comes from Gradle (`uxaoroVersionName` /
     * `versionName`) so it can never drift from the APK that is running, which
     * is what the updater compares against `upd.manifest.json`.
     */
    val APP_VERSION: String = org.uxaoro.app.BuildConfig.VERSION_NAME
    val APP_VERSION_CODE: Int = org.uxaoro.app.BuildConfig.VERSION_CODE

    private lateinit var sp: SharedPreferences
    private val gson = Gson()

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("uxaoro", Context.MODE_PRIVATE)
        // Generate/retrieve stable per-install device id for server-side device registry.
        if (sp.getString("device_id", null).isNullOrBlank()) {
            sp.edit().putString("device_id", UUID.randomUUID().toString()).apply()
        }
        Cache.init(ctx)
        AppearanceState.syncFromPrefs()
    }

    /** Stable per-install UUID, sent as X-Device-Id on every API request. */
    val deviceId: String
        get() = sp.getString("device_id", "") ?: ""

    var apiBase: String
        get() = (sp.getString("api_base", DEFAULT_API) ?: DEFAULT_API).trimEnd('/')
        set(v) {
            sp.edit().putString("api_base", v.trim().trimEnd('/')).apply()
            Api.rebuild()
        }

    /**
     * Reset HTTPS connections whose TLS is intercepted by an SSL-proxying proxy
     * (mitmproxy, Charles, Fiddler, Burp, corporate/MDM inspection, ...).
     *
     * On by default: Uxaoro is end-to-end encrypted, so anything that can read
     * TLS is a downgrade and must not be allowed to carry the traffic.
     */
    var tlsGuard: Boolean
        get() = sp.getBoolean("tls_guard", true)
        set(v) {
            sp.edit().putBoolean("tls_guard", v).apply()
            Api.rebuild()
            MediaLoader.install()
        }

    /**
     * Aggressive mode: additionally reset two-certificate chains seen over a
     * configured proxy (appliances whose inspection root is trusted system
     * wide). Off by default because a few real sites sign leaf with root.
     */
    var tlsGuardStrict: Boolean
        get() = sp.getBoolean("tls_guard_strict", false)
        set(v) {
            sp.edit().putBoolean("tls_guard_strict", v).apply()
            Api.rebuild()
            MediaLoader.install()
        }

    // ---- App-only Proxy settings (Uxaoro traffic only) ----

    var proxyEnabled: Boolean
        get() = sp.getBoolean("proxy_enabled", false)
        set(v) {
            sp.edit().putBoolean("proxy_enabled", v).apply()
            applyProxyChanges()
        }

    /** "HTTP" or "SOCKS5" */
    var proxyType: String
        get() = sp.getString("proxy_type", "HTTP") ?: "HTTP"
        set(v) {
            sp.edit().putString("proxy_type", v.trim().uppercase()).apply()
            applyProxyChanges()
        }

    var proxyHost: String
        get() = sp.getString("proxy_host", "") ?: ""
        set(v) {
            sp.edit().putString("proxy_host", v.trim()).apply()
            applyProxyChanges()
        }

    var proxyPort: Int
        get() = sp.getInt("proxy_port", 8080)
        set(v) {
            sp.edit().putInt("proxy_port", v.coerceIn(1, 65535)).apply()
            applyProxyChanges()
        }

    var proxyUsername: String
        get() = sp.getString("proxy_username", "") ?: ""
        set(v) {
            sp.edit().putString("proxy_username", v.trim()).apply()
            applyProxyChanges()
        }

    var proxyPassword: String
        get() = sp.getString("proxy_password", "") ?: ""
        set(v) {
            sp.edit().putString("proxy_password", v).apply()
            applyProxyChanges()
        }

    /** "Don't use proxy when VPN is on" setting. */
    var proxyBypassOnVpn: Boolean
        get() = sp.getBoolean("proxy_bypass_vpn", true)
        set(v) {
            sp.edit().putBoolean("proxy_bypass_vpn", v).apply()
            applyProxyChanges()
        }

    fun saveProxyConfig(
        enabled: Boolean,
        type: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        bypassOnVpn: Boolean,
    ) {
        sp.edit()
            .putBoolean("proxy_enabled", enabled)
            .putString("proxy_type", type.trim().uppercase())
            .putString("proxy_host", host.trim())
            .putInt("proxy_port", port.coerceIn(1, 65535))
            .putString("proxy_username", username.trim())
            .putString("proxy_password", password)
            .putBoolean("proxy_bypass_vpn", bypassOnVpn)
            .apply()
        applyProxyChanges()
    }

    fun resetProxyConfig() {
        sp.edit()
            .putBoolean("proxy_enabled", false)
            .putString("proxy_type", "HTTP")
            .putString("proxy_host", "")
            .putInt("proxy_port", 8080)
            .putString("proxy_username", "")
            .putString("proxy_password", "")
            .apply()
        applyProxyChanges()
    }

    private fun applyProxyChanges() {
        runCatching { org.uxaoro.app.net.AppProxy.syncAuthenticator() }
        runCatching { Api.rebuild() }
        runCatching { MediaLoader.install() }
        runCatching {
            if (accessToken.isNotBlank()) {
                WsClient.connect()
            }
        }
    }

    // ---- App lock & screenshots (device-local, never sent to the server) ----

    /** Ask for a PIN before the app opens. */
    var appLockEnabled: Boolean
        get() = sp.getBoolean("app_lock", false)
        set(v) = sp.edit().putBoolean("app_lock", v).apply()

    /** Minutes of background time before the lock returns (0 = immediately). */
    var appLockTimeoutMin: Int
        get() = sp.getInt("app_lock_timeout", 0)
        set(v) = sp.edit().putInt("app_lock_timeout", v).apply()

    /** When the app last went to the background, for the timeout above. */
    var appLockLeftAt: Long
        get() = sp.getLong("app_lock_left", 0L)
        set(v) = sp.edit().putLong("app_lock_left", v).apply()

    var appLockSalt: String
        get() = sp.getString("app_lock_salt", "") ?: ""
        set(v) = sp.edit().putString("app_lock_salt", v).apply()

    var appLockPinHash: String
        get() = sp.getString("app_lock_pin", "") ?: ""
        set(v) = sp.edit().putString("app_lock_pin", v).apply()

    /** Screenshots, screen recording and the recents preview. */
    var blockScreenshots: Boolean
        get() = sp.getBoolean("block_shots", false)
        set(v) = sp.edit().putBoolean("block_shots", v).apply()

    private const val PIN_ITERATIONS = 120_000

    /**
     * Store a PIN as a salted PBKDF2 hash. An empty PIN clears it (used when
     * the app lock is switched off). The PIN itself is never stored and never
     * leaves the device.
     */
    fun setAppLockPin(pin: String) {
        if (pin.isBlank()) {
            appLockSalt = ""
            appLockPinHash = ""
            return
        }
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val hash = pinHash(pin, salt)
        appLockSalt = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
        appLockPinHash = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    fun verifyAppLockPin(pin: String): Boolean {
        val salt = appLockSalt
        val expected = appLockPinHash
        if (salt.isBlank() || expected.isBlank() || pin.isBlank()) return false
        return runCatching {
            val saltBytes = android.util.Base64.decode(salt, android.util.Base64.NO_WRAP)
            val got = android.util.Base64.encodeToString(pinHash(pin, saltBytes), android.util.Base64.NO_WRAP)
            java.security.MessageDigest.isEqual(got.toByteArray(), expected.toByteArray())
        }.getOrDefault(false)
    }

    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, 256)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    // ---- App updates (https://fourtexec.site/downloads/uxaoro/upd) ----

    /** Check `upd.manifest.json` automatically on start / resume. */
    var updateAutoCheck: Boolean
        get() = sp.getBoolean("update_auto", true)
        set(v) {
            sp.edit().putBoolean("update_auto", v).apply()
        }

    /**
     * Version the user chose to skip ("Skip this version"). Only normal updates
     * can be skipped: a required update always comes back.
     */
    var updateSkippedVersion: String
        get() = sp.getString("update_skip", "") ?: ""
        set(v) {
            sp.edit().putString("update_skip", v.trim()).apply()
        }

    /** Epoch millis of the last successful manifest check (throttles auto-checks). */
    var updateLastCheck: Long
        get() = sp.getLong("update_last", 0L)
        set(v) {
            sp.edit().putLong("update_last", v).apply()
        }

    /** Custom update directory; blank means the published default. */
    var updateBase: String
        get() = sp.getString("update_base", "") ?: ""
        set(v) {
            sp.edit().putString("update_base", v.trim()).apply()
        }

    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) {
            sp.edit().putString("theme", v).apply()
            AppearanceState.bump()
        }

    var accent: String
        get() = sp.getString("accent", "indigo") ?: "indigo"
        set(v) {
            sp.edit().putString("accent", v).apply()
            AppearanceState.bump()
        }

    var regMode: String
        get() = sp.getString("reg_mode", "open") ?: "open"
        set(v) {
            sp.edit().putString("reg_mode", v).apply()
        }

    var smileStyle: String
        get() = sp.getString("smile_style", "apple") ?: "apple"
        set(v) {
            sp.edit().putString("smile_style", v).apply()
        }

    var fontScale: String
        get() = sp.getString("font_scale", "normal") ?: "normal"
        set(v) {
            sp.edit().putString("font_scale", v).apply()
            AppearanceState.bump()
        }

    var amoled: Boolean
        get() = sp.getBoolean("amoled", false)
        set(v) {
            sp.edit().putBoolean("amoled", v).apply()
            AppearanceState.bump()
        }

    var compact: Boolean
        get() = sp.getBoolean("compact", false)
        set(v) {
            sp.edit().putBoolean("compact", v).apply()
            AppearanceState.bump()
        }

    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(v) {
            sp.edit().putBoolean("reduce_motion", v).apply()
            AppearanceState.bump()
        }

    var accessToken: String
        get() = sp.getString("access", "") ?: ""
        set(v) {
            sp.edit().putString("access", v).apply()
        }

    // Browser CAPTCHA callbacks can briefly outlive the activity (for example
    // when Android reclaims it while the browser is foregrounded). Persisting
    // only the random state/purpose lets the replacement ViewModel validate
    // the callback; no CAPTCHA pass is written to disk.
    var pendingCaptchaState: String?
        get() = sp.getString("captcha_state", null)
        set(v) {
            val editor = sp.edit()
            if (v.isNullOrBlank()) editor.remove("captcha_state") else editor.putString("captcha_state", v)
            editor.apply()
        }

    var pendingCaptchaPurpose: String?
        get() = sp.getString("captcha_purpose", null)
        set(v) {
            val editor = sp.edit()
            if (v.isNullOrBlank()) editor.remove("captcha_purpose") else editor.putString("captcha_purpose", v)
            editor.apply()
        }

    var refreshToken: String
        get() = sp.getString("refresh", "") ?: ""
        set(v) {
            sp.edit().putString("refresh", v).apply()
        }

    var user: UserMe?
        get() {
            val raw = sp.getString("user", null) ?: return null
            return runCatching { gson.fromJson(raw, UserMe::class.java) }.getOrNull()
        }
        set(v) {
            if (v == null) sp.edit().remove("user").apply()
            else sp.edit().putString("user", gson.toJson(v)).apply()
        }

    val loggedIn: Boolean get() = accessToken.isNotBlank()

    fun saveSession(pair: TokenPair) {
        accessToken = pair.accessToken
        refreshToken = pair.refreshToken
        user = pair.user
    }

    fun clearSession() {
        sp.edit().remove("access").remove("refresh").remove("user").apply()
        Cache.clear()
    }

    fun resetApi() {
        apiBase = DEFAULT_API
    }

    var lastTab: Int
        get() = sp.getInt("last_tab", 0)
        set(v) {
            sp.edit().putInt("last_tab", v).apply()
        }

    fun draft(chatId: String): String = sp.getString("draft_$chatId", "") ?: ""

    fun setDraft(chatId: String, text: String) {
        val e = sp.edit()
        if (text.isBlank()) e.remove("draft_$chatId") else e.putString("draft_$chatId", text)
        e.apply()
    }

    var favoriteStickers: Set<String>
        get() = sp.getStringSet("fav_stickers", emptySet())?.toSet() ?: emptySet()
        set(v) {
            sp.edit().putStringSet("fav_stickers", v.toSet()).apply()
        }

    fun isNewsPopupDismissed(id: String): Boolean =
        id.isNotBlank() && sp.getBoolean("news_pop_$id", false)

    fun setNewsPopupDismissed(id: String, dismissed: Boolean = true) {
        if (id.isNotBlank()) {
            sp.edit().putBoolean("news_pop_$id", dismissed).apply()
        }
    }
}
