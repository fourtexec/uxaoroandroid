package org.uxaoro.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.uxaoro.app.call.CallEngine
import org.uxaoro.app.crypto.E2EE
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Cache
import org.uxaoro.app.data.DeviceRegisterIn
import org.uxaoro.app.data.KeyRotationAckIn
import org.uxaoro.app.data.KeyRotationCompleteIn
import org.uxaoro.app.data.KeyBackupIn
import org.uxaoro.app.data.KeyBackupOut
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.UserMe
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.data.WsClient
import org.uxaoro.app.ui.call.CallUi
import java.util.UUID

class AppVm(app: Application) : AndroidViewModel(app) {
    data class CaptchaResult(
        val purpose: String,
        val captchaPass: String,
        val turnstileToken: String = "",
        val state: String = "",
    )

    companion object {
        const val CAPTCHA_LOGIN = "login"
        const val CAPTCHA_REGISTER = "register"
    }

    private val stack = ArrayDeque<Screen>()
    private val keyRotationMutex = Mutex()
    private var keyRotationRetry: Job? = null
    // Tab switches are also part of navigation: pressing system Back inside the
    // main shell should first walk back through visited tabs, then leave the app.
    // Snapshot list so the BackHandler's `enabled` state recomputes as it changes.
    private val tabHistory = mutableStateListOf<Int>()
    var screen by mutableStateOf<Screen>(Screen.Boot)
        private set
    var navigatingForward by mutableStateOf(true)
        private set
    var tab by mutableIntStateOf(Prefs.lastTab.coerceIn(0, 5))
    var me by mutableStateOf<UserMe?>(Prefs.user)
    var themeRev by mutableIntStateOf(0)
    var snack by mutableStateOf<String?>(null)

    /**
     * Bookmarks — "this was saved to Saved Messages" — keyed by the id of the
     * message / post that was saved. They exist so a tap is answered before the
     * copy has been stored; the Saved Messages chat is the record, and
     * [syncSavedRefs] rebuilds these from it on any device.
     */
    val savedMessages = mutableStateMapOf<String, Boolean>()
    val savedPosts = mutableStateMapOf<String, Boolean>()
    // CAPTCHA is an app-wide browser round trip. Keeping its nonce/result in
    // the activity ViewModel lets MainActivity safely receive the deep link
    // and the visible auth screen consume only its own result.
    var captchaPurpose by mutableStateOf<String?>(Prefs.pendingCaptchaPurpose)
        private set
    private var captchaState: String? = Prefs.pendingCaptchaState
    var captchaResult by mutableStateOf<CaptchaResult?>(null)
        private set

    // Password-encrypted key backup living on the server (see E2EE.sealBackup).
    // `keyBackup` mirrors the last metadata we fetched so the settings card can
    // show whether a backup exists and whether it matches this device's key.
    var keyBackup by mutableStateOf<KeyBackupOut?>(null)
        private set
    var keyBackupBusy by mutableStateOf(false)
        private set

    /**
     * App lock state. On when a PIN-protected app comes back to the foreground
     * after the configured timeout, and on a cold start.
     */
    var appLocked by mutableStateOf(Prefs.appLockEnabled)
        private set

    fun onAppBackgrounded() {
        if (Prefs.appLockEnabled) Prefs.appLockLeftAt = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (!Prefs.appLockEnabled) {
            appLocked = false
            return
        }
        val left = Prefs.appLockLeftAt
        val timeoutMs = Prefs.appLockTimeoutMin.coerceAtLeast(0) * 60_000L
        // No timestamp means a fresh process: that is exactly when the PIN
        // matters most, so it locks too.
        appLocked = left <= 0L || System.currentTimeMillis() - left >= timeoutMs
    }

    /** "Lock now" from the settings screen. */
    fun lockAppNow() {
        appLocked = true
    }

    /** The PIN (and the switch) was turned off: stop asking for it at once. */
    fun onAppLockRemoved() {
        appLocked = false
    }

    fun unlockWith(pin: String): Boolean {
        if (!Prefs.appLockEnabled) {
            appLocked = false
            return true
        }
        val ok = Prefs.verifyAppLockPin(pin)
        if (ok) appLocked = false
        return ok
    }

    /** Live presence pushed over the socket: user_id -> (online, last seen). */
    data class Presence(val online: Boolean, val lastSeen: String?)
    val presenceOverrides = mutableStateMapOf<String, Presence>()

    /**
     * Presence as it should be shown: a socket update wins over a profile that
     * was fetched minutes ago, so dots and "last seen" lines stay honest
     * without refetching anything.
     */
    fun onlineOf(user: UserPublic?): Boolean {
        if (user == null) return false
        return presenceOverrides[user.id]?.online ?: user.online
    }

    fun lastSeenOf(user: UserPublic?): String? {
        if (user == null) return null
        val live = presenceOverrides[user.id]
        return if (live != null) live.lastSeen else user.lastSeen
    }

    /** "online" / "last seen 5 min ago" / "" — one line shared by every screen. */
    fun presenceLine(user: UserPublic?): String {
        if (user == null) return ""
        if (onlineOf(user)) return "online"
        val seen = lastSeenOf(user)
        return if (seen.isNullOrBlank()) "" else "last seen ${org.uxaoro.app.ui.components.formatLastSeen(seen)}"
    }

    /** Wipe the CAPTCHA result once the auth screen that owns it has consumed it. */
    var returnTo: Screen = Screen.Login
    var offline by mutableStateOf(false)
    var call by mutableStateOf<CallUi?>(null)
    var chatUnread by mutableIntStateOf(0)
    var noteUnread by mutableIntStateOf(0)
    private var listeningCalls = false

    fun themeBump() {
        themeRev++
    }

    fun go(s: Screen, push: Boolean = true) {
        navigatingForward = true
        if (push && screen !is Screen.Boot) stack.addLast(screen)
        screen = s
    }

    fun replace(s: Screen) {
        screen = s
    }

    /** Selects a bottom-nav tab and remembers it so Back can return to the previous one. */
    fun selectTab(index: Int) {
        if (index == tab) return
        tabHistory.add(tab)
        tab = index
        Prefs.lastTab = index
    }

    /** True when there is app-internal navigation that Back should consume. */
    fun canGoBack(): Boolean {
        // These two are the app's entry points: Back should leave the app.
        if (screen is Screen.Boot || screen is Screen.Login) return false
        // Inside the main shell only tab history is app-internal navigation.
        if (screen is Screen.Main) return tabHistory.isNotEmpty()
        return true
    }

    fun back() {
        navigatingForward = false
        // Inside the main shell Back walks back through visited tabs first; this
        // keeps the system back gesture from closing the app on the very first
        // press (it still leaves the app from the first tab).
        if (screen is Screen.Main) {
            val prevTab = tabHistory.removeLastOrNull()
            if (prevTab != null) {
                tab = prevTab
                Prefs.lastTab = prevTab
                return
            }
        }
        val prev = stack.removeLastOrNull()
        screen = prev ?: if (Prefs.loggedIn) Screen.Main else Screen.Login
    }

    /** A deleted community must not survive in the offline list or Back stack. */
    fun communityDeleted(id: String) {
        Cache.removeCommunity(id)
        stack.removeAll { route ->
            when (route) {
                is Screen.Community -> route.id == id
                is Screen.CommunityMembers -> route.id == id
                is Screen.CommunitySettings -> route.id == id
                else -> false
            }
        }
        selectTab(1)
        go(Screen.Main, push = false)
    }

    fun isSavedMessage(id: String) = savedMessages[id] == true

    fun isSavedPost(id: String) = savedPosts[id] == true

    fun markSavedMessage(id: String, on: Boolean) {
        if (on) savedMessages[id] = true else savedMessages.remove(id)
    }

    fun markSavedPost(id: String, on: Boolean) {
        if (on) savedPosts[id] = true else savedPosts.remove(id)
    }

    /**
     * Adopt the references found in the Saved Messages chat. Add-only: a page of
     * messages is not the whole history, so an id that is absent must not clear a
     * bookmark the user has just set.
     */
    fun syncSavedRefs(refs: List<org.uxaoro.app.util.MsgForward>) {
        for (id in refs.map { it.messageId }.filter { it.isNotBlank() }) savedMessages[id] = true
        for (id in refs.map { it.postId }.filter { it.isNotBlank() }) savedPosts[id] = true
    }

    fun toast(msg: String) {
        snack = msg
    }

    fun launchAppTask(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch(block = block)

    /**
     * Ids of rows that are on screen but not confirmed by the server yet
     * (a post/comment/message the user just created). Screens show a small
     * "sending..." note for these; the entry disappears as soon as the server
     * answers, or together with the row when the write fails.
     */
    val pendingIds = mutableStateMapOf<String, Boolean>()

    fun markPending(id: String) {
        pendingIds[id] = true
    }

    fun clearPending(id: String) {
        pendingIds.remove(id)
    }

    fun isPending(id: String?): Boolean = id != null && pendingIds.containsKey(id)

    /**
     * Posts the user just published, shown at the top of the timeline before
     * the upload finishes (the same overlay idea as the web client's
     * `Optim.create("posts", ...)`). A draft leaves the list when the server
     * row arrives, or when the upload fails.
     */
    val draftPosts = mutableStateListOf<org.uxaoro.app.data.PostOut>()

    fun addDraftPost(p: org.uxaoro.app.data.PostOut) {
        if (draftPosts.none { it.id == p.id }) draftPosts.add(0, p)
        markPending(p.id)
    }

    fun removeDraftPost(id: String) {
        draftPosts.removeAll { it.id == id }
        clearPending(id)
    }

    // Text/context kept while a post is being uploaded, so a failed publish can
    // hand the composer back exactly what the user wrote.
    var composeDraftText: String = ""
    var composeDraftCommunityId: String? = null
    var composeDraftWallId: String? = null
    var composeDraftOriginalId: String? = null

    fun clearComposeDraft() {
        composeDraftText = ""
        composeDraftCommunityId = null
        composeDraftWallId = null
        composeDraftOriginalId = null
    }

    /**
     * Stories the user just shared, shown in the tray before the upload is
     * confirmed (same contract as [draftPosts]).
     */
    val draftStories = mutableStateListOf<org.uxaoro.app.data.StoryOut>()

    fun addDraftStory(s: org.uxaoro.app.data.StoryOut) {
        if (draftStories.none { it.id == s.id }) draftStories.add(0, s)
        markPending(s.id)
    }

    fun removeDraftStory(id: String) {
        draftStories.removeAll { it.id == id }
        clearPending(id)
    }

    /**
     * A community the user just created, shown while the server confirms it.
     * The screen reads it only by id, so the row is impossible to confuse with
     * a real one - it disappears the moment the real community id arrives.
     */
    var pendingCommunity by mutableStateOf<org.uxaoro.app.data.CommunityOut?>(null)

    /** A draft as it should be drawn: the signed-in account as its author. */
    fun draftAuthor(): UserPublic? {
        val m = me ?: return null
        return UserPublic(
            id = m.id,
            username = m.username,
            displayName = m.displayName,
            avatarUrl = m.avatarUrl,
            premium = m.premium,
            customization = m.customization,
        )
    }

    /** Starts a CAPTCHA flow at the current configured API host. */
    fun beginCaptcha(purpose: String): String {
        val state = UUID.randomUUID().toString()
        captchaPurpose = purpose
        captchaState = state
        Prefs.pendingCaptchaPurpose = purpose
        Prefs.pendingCaptchaState = state
        captchaResult = null
        return Uri.parse(Prefs.apiBase)
            .buildUpon()
            .appendPath("auth")
            .appendPath("captcha")
            .appendQueryParameter("state", state)
            .build()
            .toString()
    }

    /** Called by MainActivity only after validating the fixed callback link. */
    fun receiveCaptchaCallback(state: String, captchaPass: String, turnstileToken: String = "") {
        val purpose = captchaPurpose ?: return
        if (state.isBlank() || state != captchaState) return
        captchaState = null
        captchaPurpose = null
        Prefs.pendingCaptchaState = null
        Prefs.pendingCaptchaPurpose = null
        captchaResult = CaptchaResult(purpose, captchaPass, turnstileToken, state)
    }

    fun clearCaptchaResult() {
        captchaResult = null
    }

    fun cancelCaptcha(purpose: String) {
        if (captchaPurpose == purpose) {
            captchaPurpose = null
            captchaState = null
            Prefs.pendingCaptchaPurpose = null
            Prefs.pendingCaptchaState = null
        }
        if (captchaResult?.purpose == purpose) captchaResult = null
    }

    fun openSettingsFromAuth() {
        returnTo = screen
        go(Screen.PreSettings)
    }

    fun openDev(from: Screen) {
        returnTo = from
        go(Screen.DevSettings)
    }

    fun boot() {
        viewModelScope.launch {
            if (!Prefs.loggedIn) {
                replace(Screen.Login)
                return@launch
            }
            try {
                val u = Api.service().me()
                me = u
                Prefs.user = u
                offline = false
                ensureE2ee()
                WsClient.connect()
                listenCalls()
                refreshBadges()
                replace(Screen.Main)
            } catch (t: Throwable) {
                if (Prefs.user != null) {
                    me = Prefs.user
                    offline = true
                    toast("You're offline. Showing saved messages and profile.")
                    replace(Screen.Main)
                } else {
                    Prefs.clearSession()
                    replace(Screen.Login)
                }
            }
        }
    }

    suspend fun ensureE2ee() {
        val ctx = getApplication<Application>()
        val current = me?.pubkey.orEmpty()
        if (!E2EE.hasLocalKeys(ctx) && current.isNotBlank()) {
            Cache.clearChatData()
            toast("Link this device to a device that already has your encryption key")
            return
        }
        runCatching { processPendingKeyRotation() }
        val rotation = runCatching { Api.service().keyRotationStatus() }.getOrNull()
        if (rotation?.status == "ready" && E2EE.publicPem(ctx) != rotation.newPubkey) return
        val kpPem = E2EE.publicPem(ctx)
        if (current != kpPem) {
            runCatching { Api.service().putPubkey(mapOf("pubkey" to kpPem)) }
            me = me?.copy(pubkey = kpPem)
            Prefs.user = me
        }
        // Register/heartbeat this device with the server so it appears in "Linked devices".
        val label = runCatching { android.os.Build.MODEL }.getOrDefault("Android")
        runCatching {
            Api.service().registerDevice(DeviceRegisterIn(label = label, platform = "android", devicePubkey = kpPem))
        }
        runCatching { processPendingKeyRotation() }
    }

    private suspend fun processPendingKeyRotation() = keyRotationMutex.withLock {
        val ctx = getApplication<Application>()
        val userId = me?.id ?: return@withLock
        val state = runCatching { Api.service().keyRotationStatus() }.getOrNull() ?: return@withLock
        if (state.status == "none") return@withLock
        Cache.clearChatData()
        if (state.status == "no_key") {
            runCatching { Api.service().acknowledgeKeyRotation(KeyRotationAckIn(state.rotationId)) }
            return@withLock
        }

        if (state.status == "pending" || state.status == "claimed") {
            if (!E2EE.hasLocalKeys(ctx)) return@withLock
            val claim = runCatching { Api.service().claimKeyRotation() }.getOrNull()
            if (claim?.status != "claimed" || claim.claimToken.isBlank()) {
                if (claim?.status == "claimed") scheduleKeyRotationRetry()
                return@withLock
            }
            if (E2EE.publicPem(ctx) != claim.oldPubkey) return@withLock
            val prepared = withContext(Dispatchers.Default) {
                E2EE.prepareKeyRotation(ctx, userId, claim.rotationId)
            }
            Api.service().completeKeyRotation(
                KeyRotationCompleteIn(
                    rotationId = claim.rotationId,
                    claimToken = claim.claimToken,
                    newPubkey = prepared.publicPem,
                    ciphertext = prepared.encrypted.ciphertext,
                    nonce = prepared.encrypted.nonce,
                    keysJson = prepared.encrypted.keysJson,
                ),
            )
            E2EE.importPrivate(ctx, prepared.privatePem)
            me = me?.copy(pubkey = prepared.publicPem)
            Prefs.user = me
            runCatching {
                Api.service().registerDevice(
                    DeviceRegisterIn(
                        label = runCatching { android.os.Build.MODEL }.getOrDefault("Android"),
                        platform = "android",
                        devicePubkey = prepared.publicPem,
                    ),
                )
            }
            Api.service().acknowledgeKeyRotation(KeyRotationAckIn(claim.rotationId))
            return@withLock
        }

        if (state.status != "ready") return@withLock

        if (!E2EE.hasLocalKeys(ctx)) return@withLock
        if (E2EE.publicPem(ctx) != state.newPubkey) {
            withContext(Dispatchers.Default) {
                E2EE.importKeyRotation(
                    ctx,
                    userId,
                    state.rotationId,
                    state.newPubkey,
                    state.ciphertext,
                    state.nonce,
                    state.keysJson,
                )
            }
        }
        me = me?.copy(pubkey = state.newPubkey)
        Prefs.user = me
        runCatching {
            Api.service().registerDevice(
                DeviceRegisterIn(
                    label = runCatching { android.os.Build.MODEL }.getOrDefault("Android"),
                    platform = "android",
                    devicePubkey = state.newPubkey,
                ),
            )
        }
        Api.service().acknowledgeKeyRotation(KeyRotationAckIn(state.rotationId))
    }

    private fun scheduleKeyRotationRetry() {
        if (keyRotationRetry?.isActive == true) return
        keyRotationRetry = viewModelScope.launch {
            repeat(7) {
                delay(60_000)
                val status = runCatching { Api.service().keyRotationStatus().status }.getOrNull()
                    ?: return@launch
                if (status !in setOf("pending", "claimed", "ready")) return@launch
                runCatching { processPendingKeyRotation() }
            }
        }
    }

    /** Wire model -> crypto model (E2EE only knows its own tiny envelope type). */
    private fun KeyBackupOut.asEnvelope() = E2EE.KeyBackup(
        version = version,
        kdf = kdf,
        iterations = iterations,
        salt = salt,
        nonce = nonce,
        ciphertext = ciphertext,
        pubkeyFingerprint = pubkeyFingerprint,
        updatedAt = updatedAt,
    )

    /**
     * Fetch the sealed backup's metadata (no blob) so the settings card and the
     * sign-in flow can tell whether this account has one.
     */
    suspend fun refreshKeyBackup() {
        runCatching { keyBackup = Api.service().keyBackup(meta = 1) }
    }

    /**
     * Seal the local private key with [password] and upload the blob.
     *
     * The password is used exactly as typed - never hashed by the server, never
     * sent anywhere - so any device that later signs in with the same password
     * can open the envelope.
     */
    suspend fun backupKey(password: String, notify: Boolean = true): Boolean {
        val ctx = getApplication<Application>()
        val userId = me?.id ?: Prefs.user?.id.orEmpty()
        if (userId.isBlank()) return false
        keyBackupBusy = true
        return try {
            val boxed = withContext(Dispatchers.Default) { E2EE.sealBackup(ctx, password, userId) }
            Api.service().putKeyBackup(
                KeyBackupIn(
                    version = boxed.version,
                    kdf = boxed.kdf,
                    iterations = boxed.iterations,
                    salt = boxed.salt,
                    nonce = boxed.nonce,
                    ciphertext = boxed.ciphertext,
                    pubkeyFingerprint = boxed.pubkeyFingerprint,
                )
            )
            refreshKeyBackup()
            // Silent during sign-in (the user did not ask for it), loud when the
            // settings card triggered it.
            if (notify) toast("Key backup saved. Any device can restore it with your password.")
            true
        } catch (t: Throwable) {
            toast(Api.errorMessage(t))
            false
        } finally {
            keyBackupBusy = false
        }
    }

    /** Replace this device's keys with the ones from the sealed backup. */
    suspend fun restoreKey(password: String): Boolean {
        val ctx = getApplication<Application>()
        val userId = me?.id ?: Prefs.user?.id.orEmpty()
        keyBackupBusy = true
        return try {
            val remote = Api.service().keyBackup()
            if (!remote.exists || remote.ciphertext.isBlank()) {
                toast("This account has no key backup on the server")
                return false
            }
            if (userId.isBlank()) {
                toast("Sign in again to restore your keys")
                return false
            }
            withContext(Dispatchers.Default) { E2EE.restoreBackup(ctx, password, userId, remote.asEnvelope()) }
            // The restored key is now the account key - publish it and heartbeat.
            ensureE2ee()
            refreshKeyBackup()
            toast("Encryption keys restored. Old messages are readable again.")
            true
        } catch (t: Throwable) {
            toast("Could not open the backup - check your password.")
            false
        } finally {
            keyBackupBusy = false
        }
    }

    /** Forget the server copy. The keys on this device stay untouched. */
    suspend fun deleteKeyBackup(): Boolean {
        keyBackupBusy = true
        return try {
            Api.service().deleteKeyBackup()
            refreshKeyBackup()
            toast("Key backup deleted from the server")
            true
        } catch (t: Throwable) {
            toast(Api.errorMessage(t))
            false
        } finally {
            keyBackupBusy = false
        }
    }

    /**
     * The sign-in hook for the key backup.
     *
     * Order matters: restoring must happen *before* [ensureE2ee] publishes a
     * public key, otherwise a fresh install would generate a brand new keypair
     * and overwrite the account key (making older messages unreadable) before
     * it ever looked for the backup.
     */
    suspend fun syncKeyBackup(password: String) {
        val ctx = getApplication<Application>()
        val userId = me?.id ?: Prefs.user?.id.orEmpty()
        if (userId.isBlank() || password.length < 6) return
        val remote = runCatching { Api.service().keyBackup() }.getOrNull() ?: return

        if (!E2EE.hasLocalKeys(ctx)) {
            if (!remote.exists || remote.ciphertext.isBlank()) return
            try {
                withContext(Dispatchers.Default) { E2EE.restoreBackup(ctx, password, userId, remote.asEnvelope()) }
                toast("Encryption keys restored from your backup")
            } catch (t: Throwable) {
                // Usually means the backup was sealed with an older password
                // (e.g. changed on another device). A new key is generated by
                // ensureE2ee() below; we must not clobber the stored blob.
                toast("Could not unlock your key backup - the stored backup uses a different password.")
                return
            }
            return
        }

        // This device already had keys. Only refresh the blob when it is ours:
        // blindly uploading would destroy another key we cannot decrypt.
        val localFp = runCatching { E2EE.pubkeyFingerprint(E2EE.publicPem(ctx)) }.getOrDefault("")
        if (remote.exists && remote.pubkeyFingerprint.isNotBlank() && remote.pubkeyFingerprint != localFp) {
            return
        }
        backupKey(password, notify = false)
    }

    fun onLoggedIn() {
        viewModelScope.launch { onLoggedIn(null) }
    }

    fun onLoggedIn(password: String?) {
        viewModelScope.launch {
            me = Prefs.user
            offline = false
            if (!password.isNullOrEmpty()) {
                runCatching { syncKeyBackup(password) }
            }
            runCatching { ensureE2ee() }
            runCatching { refreshKeyBackup() }
            WsClient.connect()
            listenCalls()
            refreshBadges()
            stack.clear()
            tabHistory.clear()
            replace(Screen.Main)
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching {
                if (Prefs.refreshToken.isNotBlank()) {
                    Api.service().logout(org.uxaoro.app.data.RefreshIn(Prefs.refreshToken))
                }
            }
            WsClient.disconnect()
            endCall(notify = false)
            Prefs.clearSession()
            me = null
            offline = false
            chatUnread = 0
            noteUnread = 0
            stack.clear()
            tabHistory.clear()
            replace(Screen.Login)
        }
    }

    fun refreshBadges() {
        viewModelScope.launch {
            runCatching { Api.service().inboxBadge() }.onSuccess {
                chatUnread = it.chatsUnread
                noteUnread = it.notifications
            }
        }
    }

    fun startCall(chatId: String, peerId: String, peerName: String, video: Boolean) {
        if (peerId.isBlank()) {
            toast("Pick a person to call")
            return
        }
        val id = UUID.randomUUID().toString()
        call = CallUi("outgoing", chatId, peerId, peerName, video, id)
        CallEngine.onPhase = { phase ->
            if (phase == "idle") call = null
            else call = call?.copy(phase = if (phase == "remote-video") "active" else phase)
        }
        viewModelScope.launch {
            val ice = withContext(Dispatchers.IO) {
                runCatching { Api.service().ice().iceServers }.getOrDefault(emptyList())
            }
            val ctx = getApplication<Application>()
            CallEngine.init(ctx)
            WsClient.call(
                JSONObject()
                    .put("kind", "invite")
                    .put("chat_id", chatId)
                    .put("to", peerId)
                    .put("video", video)
                    .put("call_id", id),
            )
            withContext(Dispatchers.IO) {
                CallEngine.startOutgoing(ctx, ice, chatId, peerId, video, id)
            }
        }
    }

    fun acceptCall() {
        val c = call ?: return
        call = c.copy(phase = "active")
        viewModelScope.launch {
            val ice = withContext(Dispatchers.IO) {
                runCatching { Api.service().ice().iceServers }.getOrDefault(emptyList())
            }
            val ctx = getApplication<Application>()
            CallEngine.init(ctx)
            CallEngine.onPhase = { phase ->
                if (phase == "idle") call = null
                else call = call?.copy(phase = if (phase == "remote-video") "active" else phase)
            }
            withContext(Dispatchers.IO) {
                CallEngine.accept(ctx, ice, c.chatId, c.peerId, c.video, c.callId)
            }
        }
    }

    fun endCall(notify: Boolean = true) {
        CallEngine.hangup(notify)
        call = null
    }

    private fun listenCalls() {
        if (listeningCalls) return
        listeningCalls = true
        CallEngine.init(getApplication())
        viewModelScope.launch {
            WsClient.events.collect { ev ->
                when (ev.event) {
                    "call" -> if (ev.data != null && ev.data.isJsonObject) handleCall(ev.data.asJsonObject)
                    "message", "message_deleted" -> refreshBadges()
                    "e2ee_key_reroll_required", "e2ee_key_rotation_ready" -> {
                        runCatching { processPendingKeyRotation() }
                    }
                    "presence" -> {
                        val d = ev.data
                        if (d != null && d.isJsonObject) {
                            val o = d.asJsonObject
                            val uid = runCatching { o.get("user_id")?.asString }.getOrNull().orEmpty()
                            if (uid.isNotBlank()) {
                                val online = runCatching { o.get("online")?.asBoolean }.getOrNull() ?: false
                                val seen = runCatching {
                                    val el = o.get("last_seen")
                                    if (el == null || el.isJsonNull) null else el.asString
                                }.getOrNull()
                                presenceOverrides[uid] = Presence(online, seen)
                            }
                        }
                    }
                    "ux" -> {
                        val d = ev.data
                        if (d != null && d.isJsonObject) {
                            val kind = runCatching { d.asJsonObject.get("kind")?.asString }.getOrNull().orEmpty()
                            val amt = runCatching { d.asJsonObject.get("amount")?.asDouble }.getOrNull() ?: 0.0
                            when (kind) {
                                "gift_in" -> toast("UX gift received: +${fmtAmount(amt)} UX")
                                "admin" -> if (amt >= 0) toast("UX granted: +${fmtAmount(amt)} UX") else toast("UX deducted: ${fmtAmount(amt)} UX")
                                "purchase" -> toast("UX purchase credited")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fmtAmount(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun handleCall(obj: JsonObject) {
        fun s(key: String): String {
            val el = obj.get(key) ?: return ""
            return runCatching { el.asString }.getOrDefault("")
        }
        fun b(key: String): Boolean {
            val el = obj.get(key) ?: return false
            return runCatching { el.asBoolean }.getOrDefault(false)
        }
        val kind = s("kind")
        val from = s("from_id").ifBlank { s("fromId") }
        val meId = me?.id.orEmpty()
        if (from == meId) return
        val chatId = s("chat_id").ifBlank { s("chatId") }
        val callId = s("call_id").ifBlank { s("callId") }
        val video = b("video")
        val name = s("from_name").ifBlank { s("fromName") }.ifBlank { "Incoming call" }
        when (kind) {
            "invite" -> {
                if (call != null) return
                call = CallUi("incoming", chatId, from, name, video, callId)
                CallEngine.onPhase = { phase ->
                    if (phase == "idle") call = null
                    else call = call?.copy(phase = if (phase == "remote-video") "active" else phase)
                }
            }
            "hangup", "reject" -> {
                if (call == null) return
                CallEngine.hangup(notify = false)
                call = null
            }
            "offer", "answer", "ice" -> {
                val data = JSONObject(obj.toString())
                CallEngine.onRemoteSignal(kind, data)
            }
        }
    }
}
