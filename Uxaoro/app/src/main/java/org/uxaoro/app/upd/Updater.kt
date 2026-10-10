package org.uxaoro.app.upd

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.uxaoro.app.BuildConfig
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.net.AppProxy
import org.uxaoro.app.net.TlsGuard
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Uxaoro Android self-update engine.
 *
 * Every published build is described in a single manifest on the Uxaoro
 * download host:
 *
 *     https://fourtexec.site/downloads/uxaoro/upd/upd.manifest.json
 *
 * The engine checks that manifest, compares the published `version_code`
 * with the installed one, downloads the APK, verifies its SHA-256 and size,
 * and hands it to the system package installer ([ApkInstaller]).
 *
 * Updates are either **normal** (the user may say "later" or skip the version)
 * or **required** (the update prompt cannot be dismissed until the new APK is
 * installed) — the publisher chooses per release, and a version floor or a
 * `mandatory_from` deadline can promote a normal update to a required one.
 */
object Updater {

    const val DEFAULT_BASE = UpdateManifest.DEFAULT_BASE
    const val MANIFEST_NAME = UpdateManifest.MANIFEST_NAME

    /** Background re-checks are throttled to this interval. */
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_APK_BYTES = 512L * 1024 * 1024

    enum class Phase { IDLE, CHECKING, DOWNLOADING, READY, INSTALLING }

    data class State(
        val phase: Phase = Phase.IDLE,
        /** The published release, when it is newer than the installed one. */
        val update: AndroidUpdate? = null,
        /** True when the update must be installed before the app can be used. */
        val required: Boolean = false,
        /** The user pressed "Later" on a normal update (this session). */
        val postponed: Boolean = false,
        /** The user chose "Skip this version" (persisted). */
        val skipped: Boolean = false,
        val progress: Int = 0,
        val bytesReceived: Long = 0,
        val bytesTotal: Long = 0,
        val apkPath: String? = null,
        val lastCheckAt: Long = 0,
        val error: String? = null,
        val status: String? = null,
    ) {
        val active: Boolean get() = phase == Phase.CHECKING || phase == Phase.DOWNLOADING
        val hasDownloadedApk: Boolean get() = !apkPath.isNullOrBlank()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var runningCall: okhttp3.Call? = null

    val installedVersion: String get() = BuildConfig.VERSION_NAME
    val installedVersionCode: Int get() = BuildConfig.VERSION_CODE

    /** Update source, overridable in developer/update settings. */
    fun base(): String {
        val custom = runCatching { Prefs.updateBase.trim() }.getOrDefault("")
        return (custom.ifBlank { DEFAULT_BASE }).let { if (it.endsWith("/")) it else "$it/" }
    }

    fun manifestUrl(): String = UpdateManifest.manifestUrl(base())

    private fun client(readTimeoutSec: Long = 60): OkHttpClient = TlsGuard.harden(
        AppProxy.configure(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true),
        ),
    ).build()

    /* ---------------- checking ---------------- */

    /**
     * Fetches the manifest and compares it with the installed build.
     *
     * @param manual user initiated: errors and "up to date" are surfaced in the
     *   UI, and the "skip this version" choice is ignored.
     */
    fun check(ctx: Context, manual: Boolean = false) {
        checkJob?.cancel()
        checkJob = scope.launch {
            val app = ctx.applicationContext
            _state.value = _state.value.copy(
                phase = Phase.CHECKING,
                error = null,
                status = if (manual) "Checking for updates…" else null,
            )
            try {
                val json = fetch(manifestUrl(), readTimeoutSec = 20)
                val parsed = UpdateManifest.parse(json, manifestUrl())
                Prefs.updateLastCheck = System.currentTimeMillis()

                if (parsed == null) {
                    _state.value = _state.value.copy(
                        phase = Phase.IDLE,
                        update = null,
                        lastCheckAt = Prefs.updateLastCheck,
                        error = if (manual) "The update manifest does not describe the Android app." else null,
                    )
                    return@launch
                }

                if (!appPackageMatches(app, parsed)) {
                    _state.value = _state.value.copy(
                        phase = Phase.IDLE,
                        update = null,
                        lastCheckAt = Prefs.updateLastCheck,
                        error = if (manual) {
                            "The published APK is built for a different package (${parsed.apkPackage})."
                        } else {
                            null
                        },
                    )
                    return@launch
                }

                val newer = parsed.versionCode > installedVersionCode ||
                    (parsed.versionCode == 0 && Versions.compare(parsed.version, installedVersion) > 0)
                // A skipped version stays hidden until the user checks manually,
                // or until the publisher marks the release as required.
                val skipped = !manual && Prefs.updateSkippedVersion == parsed.version

                val apk = ApkInstaller.downloadedApk(app, parsed)
                _state.value = _state.value.copy(
                    phase = if (apk != null) Phase.READY else Phase.IDLE,
                    update = if (newer) parsed else null,
                    required = newer && parsed.severity == UpdateSeverity.REQUIRED,
                    skipped = skipped,
                    postponed = false,
                    apkPath = apk?.absolutePath,
                    lastCheckAt = Prefs.updateLastCheck,
                    progress = 0,
                    bytesReceived = 0,
                    bytesTotal = if (newer) parsed.apkSize else 0,
                    error = null,
                    status = if (manual && !newer) "Uxaoro ${installedVersion} is the latest version." else null,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    phase = Phase.IDLE,
                    lastCheckAt = Prefs.updateLastCheck,
                    error = if (manual) errorText(t) else null,
                )
            }
        }
    }

    private fun appPackageMatches(ctx: Context, parsed: AndroidUpdate): Boolean {
        val declared = parsed.apkPackage.trim()
        if (declared.isEmpty()) return true
        return declared == ctx.packageName
    }

    /** Turns a network failure into a readable line for the update UI. */
    private fun errorText(t: Throwable): String {
        TlsGuard.userMessage(t)?.let { return it }
        return when (t) {
            is IOException -> "Cannot reach the update server (${t.message ?: "network error"})."
            else -> t.message ?: t::class.java.simpleName
        }
    }

    private fun fetch(url: String, readTimeoutSec: Long): String {
        val req = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/json")
            .header("X-Device-Id", runCatching { Prefs.deviceId }.getOrDefault(""))
            .header("X-Uxaoro-Client", "android/${installedVersion} (${installedVersionCode})")
            .build()
        val call = client(readTimeoutSec).newCall(req)
        runningCall = call
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                return resp.body?.string() ?: throw IOException("empty response")
            }
        } finally {
            if (runningCall === call) runningCall = null
        }
    }

    /* ---------------- downloading ---------------- */

    /**
     * Downloads the published APK, verifies it and (optionally) opens the system
     * installer right away.
     */
    fun downloadAndInstall(ctx: Context, installWhenReady: Boolean = true) {
        val app = ctx.applicationContext
        val update = _state.value.update
        if (update == null) {
            _state.value = _state.value.copy(error = "There is no update to download. Check for updates first.")
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.value = _state.value.copy(
                phase = Phase.DOWNLOADING,
                error = null,
                postponed = false,
                progress = 0,
                bytesReceived = 0,
                bytesTotal = update.apkSize,
                status = "Downloading Uxaoro ${update.version}…",
            )
            try {
                val existing = ApkInstaller.downloadedApk(app, update)
                val apk = existing ?: downloadTo(app, update)
                _state.value = _state.value.copy(
                    phase = Phase.READY,
                    apkPath = apk.absolutePath,
                    progress = 100,
                    status = "Downloaded Uxaoro ${update.version}.",
                )
                // The install intent is launched from the main thread so the
                // system installer always opens reliably.
                if (installWhenReady) withContext(Dispatchers.Main) { install(app) }
            } catch (t: Throwable) {
                coroutineContext.ensureActive()
                _state.value = _state.value.copy(
                    phase = Phase.IDLE,
                    progress = 0,
                    status = null,
                    error = errorText(t),
                )
            }
        }
    }

    private suspend fun downloadTo(app: Context, update: AndroidUpdate): File = withContext(Dispatchers.IO) {
        val dir = ApkInstaller.apkDir(app)
        val part = File(dir, "uxaoro-${update.version}.apk.part")
        val target = File(dir, "uxaoro-${update.version}.apk")
        if (part.exists()) part.delete()

        val req = Request.Builder()
            .url(update.apkUrl)
            .header("Cache-Control", "no-cache")
            .header("X-Device-Id", runCatching { Prefs.deviceId }.getOrDefault(""))
            .build()
        val call = client(readTimeoutSec = 120).newCall(req)
        runningCall = call

        var received = 0L
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty response body")
                val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize
                if (total > MAX_APK_BYTES) throw IOException("the published APK is unexpectedly large")
                val digest = MessageDigest.getInstance("SHA-256")

                FileOutputStream(part).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var lastEmit = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            received += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 200 || (total > 0 && received >= total)) {
                                lastEmit = now
                                val pct = if (total > 0) ((received * 100) / total).toInt() else 0
                                _state.value = _state.value.copy(
                                    progress = pct.coerceIn(0, 100),
                                    bytesReceived = received,
                                    bytesTotal = total,
                                )
                            }
                        }
                    }
                }

                // Verify what was actually written before it may be installed.
                if (update.apkSize > 0 && received != update.apkSize) {
                    throw IOException("size mismatch (expected ${update.apkSize} bytes, got $received)")
                }
                if (update.apkSha256.isBlank()) {
                    throw IOException("the update artifact is missing a required SHA-256 checksum")
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(update.apkSha256, ignoreCase = true)) {
                    throw IOException("the downloaded APK is not the published one (SHA-256 mismatch)")
                }
            }
        } catch (t: Throwable) {
            part.delete()
            throw t
        } finally {
            if (runningCall === call) runningCall = null
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.delete()
            throw IOException("could not store the downloaded APK")
        }
        target
    }

    /** Opens the system installer for an already downloaded APK. */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val update = _state.value.update
        val file = _state.value.apkPath?.let { File(it) } ?: ApkInstaller.downloadedApk(app, update)
        if (file == null) {
            _state.value = _state.value.copy(error = "Download the update first.")
            return
        }
        _state.value = _state.value.copy(phase = Phase.INSTALLING, status = "Waiting for the system installer…")
        val err = ApkInstaller.install(app, file)
        _state.value = _state.value.copy(
            phase = Phase.READY,
            status = if (err == null) "Confirm the installation in the Android dialog." else null,
            error = err,
        )
    }

    /* ---------------- user choices ---------------- */

    /** "Later" — hides a normal update for this session only. */
    fun postpone() {
        if (_state.value.required) return
        _state.value = _state.value.copy(postponed = true)
    }

    /** "Skip this version" — never offered again (required updates can't be skipped). */
    fun skip() {
        val info = _state.value.update ?: return
        if (_state.value.required) return
        Prefs.updateSkippedVersion = info.version
        _state.value = _state.value.copy(skipped = true, postponed = true)
    }

    /**
     * Hides even a required update for this session. Debug builds use it because
     * a release APK can never be installed over a debug build.
     */
    fun ignoreForSession() {
        _state.value = _state.value.copy(postponed = true)
    }

    /** Clears a "skip" so the release is offered again. */
    fun unskip() {
        Prefs.updateSkippedVersion = ""
        _state.value = _state.value.copy(skipped = false, postponed = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Clears the status line (used after the snackbar/banner consumed it). */
    fun clearStatus() {
        _state.value = _state.value.copy(status = null)
    }

    fun cancel() {
        downloadJob?.cancel()
        checkJob?.cancel()
        runCatching { runningCall?.cancel() }
        runningCall = null
        downloadJob = null
        checkJob = null
        _state.value = _state.value.copy(phase = Phase.IDLE, progress = 0, status = null)
    }

    /* ---------------- automatic checks ---------------- */

    /**
     * Called on app start / resume: checks at most once every
     * [AUTO_CHECK_INTERVAL_MS] when automatic checks are enabled.
     * Debug builds never auto-check (a release APK cannot replace them).
     */
    fun maybeAutoCheck(ctx: Context) {
        if (BuildConfig.DEBUG) return
        if (!runCatching { Prefs.updateAutoCheck }.getOrDefault(true)) return
        val last = runCatching { Prefs.updateLastCheck }.getOrDefault(0L)
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return
        check(ctx.applicationContext, manual = false)
    }
}
