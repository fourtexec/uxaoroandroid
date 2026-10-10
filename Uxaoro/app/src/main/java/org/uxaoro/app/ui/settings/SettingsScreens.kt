package org.uxaoro.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.PasswordChange
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.TotpCodeIn
import org.uxaoro.app.data.TotpDisableIn
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.data.UserMe
import org.uxaoro.app.data.UserUpdate
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.auth.VersionTap
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.AssetSvgIcon
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.NumericCodePad
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.PrimaryField
import org.uxaoro.app.ui.components.ThemeAccentPickers
import org.uxaoro.app.ui.components.UserRow
import org.uxaoro.app.ui.components.inkTextButtonColors
import org.uxaoro.app.ui.theme.BlurScaffold
import org.uxaoro.app.ui.theme.GlassPanel

private val EveryoneFriendsNobody = listOf(
    "everyone" to "Everyone",
    "friends" to "Friends",
    "nobody" to "Nobody",
)
private val EveryoneNobody = listOf(
    "everyone" to "Everyone",
    "nobody" to "Nobody",
)

@Composable
fun InAppSettingsScreen(vm: AppVm) {
    val ctx = LocalContext.current
    var oldP by remember { mutableStateOf("") }
    var newP by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var totpUrl by remember { mutableStateOf("") }
    var totpPass by remember { mutableStateOf("") }
    var bye by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val me = vm.me

    LaunchedEffect(Unit) {
        runCatching { Api.service().me() }.onSuccess {
            vm.me = it
            Prefs.user = it
        }
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    /**
     * Local-first settings save: `mirror` writes the new value into the
     * signed-in account straight away, so the switch or dropdown never waits for
     * the network; a refusal puts the old account back and toasts why.
     */
    fun patch(update: UserUpdate, mirror: (UserMe) -> UserMe = { it }) {
        val before = vm.me
        scope.localFirst(
            vm,
            "Save setting",
            send = {
                val fresh = Api.service().updateMe(update)
                vm.me = fresh
                Prefs.user = fresh
            },
            apply = {
                val cur = vm.me
                if (cur != null) {
                    vm.me = mirror(cur)
                    Prefs.user = vm.me
                }
            },
            undo = {
                vm.me = before
                Prefs.user = before
            },
        )
    }

    BlurScaffold(topBar = { DATopBar("Settings", onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionCard("Look") {
                ThemeAccentPickers { vm.themeBump() }
                Spacer(Modifier.height(8.dp))
                Text("Smiles style", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text("Emoji/style set used in chats", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("apple", "google", "oneui").forEach { style ->
                        val selected = Prefs.smileStyle == style
                        Text(
                            style.replaceFirstChar { it.uppercase() },
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .clickable { Prefs.smileStyle = style; vm.themeBump() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            SectionCard("Uxaoro Premium") {
                Text(
                    if (me?.premium == true) "✦ Premium active — personalize your profile and messages."
                    else "More stories and communities, plus profile and chat customization. Buy with UX.",
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = { vm.go(Screen.Premium) }, colors = inkTextButtonColors()) {
                    Text(if (me?.premium == true) "Manage Premium" else "Get Premium")
                }
            }
            org.uxaoro.app.ui.premium.BadgesAndSalesSection(vm)
            SectionCard("Privacy") {
                PrivacyRow(
                    "Who can message me",
                    "Direct messages on Wire",
                    me?.whoCanMessage ?: "everyone",
                    EveryoneFriendsNobody,
                ) { patch(UserUpdate(whoCanMessage = it)) { m -> m.copy(whoCanMessage = it) } }
                PrivacyRow(
                    "Who can add me to a community",
                    "Invites into circles",
                    me?.whoCanInviteCommunity ?: "everyone",
                    EveryoneFriendsNobody,
                ) { patch(UserUpdate(whoCanInviteCommunity = it)) { m -> m.copy(whoCanInviteCommunity = it) } }
                PrivacyRow(
                    "Who can add me to a group",
                    "Messenger groups",
                    me?.whoCanAddGroup ?: "everyone",
                    EveryoneFriendsNobody,
                ) { patch(UserUpdate(whoCanAddGroup = it)) { m -> m.copy(whoCanAddGroup = it) } }
                PrivacyRow(
                    "Who can send me a friend request",
                    "People tab",
                    me?.whoCanFriend ?: "everyone",
                    EveryoneNobody,
                ) { patch(UserUpdate(whoCanFriend = it)) { m -> m.copy(whoCanFriend = it) } }
                PrivacyRow(
                    "Who can call me",
                    "Audio and video",
                    me?.whoCanCall ?: "everyone",
                    EveryoneFriendsNobody,
                ) { patch(UserUpdate(whoCanCall = it)) { m -> m.copy(whoCanCall = it) } }
                PrivacyRow(
                    "Who can see last visit time",
                    "When you were last here",
                    me?.whoCanSeeLastSeen ?: "everyone",
                    EveryoneFriendsNobody,
                ) { patch(UserUpdate(whoCanSeeLastSeen = it)) { m -> m.copy(whoCanSeeLastSeen = it) } }
                PrivacyRow(
                    "Who can see when I am online",
                    "Live / offline status",
                    me?.whoCanSeeOnline ?: "everyone",
                    EveryoneFriendsNobody,
                ) { patch(UserUpdate(whoCanSeeOnline = it)) { m -> m.copy(whoCanSeeOnline = it) } }
                PrivacyRow(
                    "Who can see my stories",
                    "24-hour lanterns",
                    me?.whoCanSeeStories ?: "everyone",
                    EveryoneFriendsNobody,
                ) { patch(UserUpdate(whoCanSeeStories = it)) { m -> m.copy(whoCanSeeStories = it) } }
            }
            SectionCard("Sign-in security") {
                SecurityHardeningSection(
                    vm = vm,
                    me = me,
                    onToast = { toast(it) },
                    onPatch = { patch(it) },
                )
            }
            SectionCard("Key backup") {
                KeyBackupSection(vm)
            }
            SectionCard("Proxy") {
                ProxySettingsSection(onToast = { toast(it) })
            }
            SectionCard("Security") {
                var tlsBlocked by remember { mutableStateOf<org.uxaoro.app.net.TlsGuard.Blocked?>(null) }
                var tlsCount by remember { mutableStateOf(0) }
                var guard by remember { mutableStateOf(Prefs.tlsGuard) }
                var strict by remember { mutableStateOf(Prefs.tlsGuardStrict) }
                LaunchedEffect(Unit) {
                    org.uxaoro.app.net.TlsGuard.status.collect { tlsBlocked = it }
                }
                LaunchedEffect(Unit) {
                    org.uxaoro.app.net.TlsGuard.blocked.collect { tlsCount = it }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reset SSL-proxying proxies", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Every HTTPS connection whose TLS is intercepted (mitmproxy, Charles, Burp, corporate or MDM inspection) is reset before anything leaves the app.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = guard,
                        onCheckedChange = {
                            guard = it
                            Prefs.tlsGuard = it
                            toast(if (it) "Interception protection on" else "Interception protection off")
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strict mode", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Also reset two-certificate chains seen through a configured proxy (inspection roots trusted system wide).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = strict,
                        onCheckedChange = {
                            strict = it
                            Prefs.tlsGuardStrict = it
                            toast(if (it) "Strict proxy guard on" else "Strict proxy guard off")
                        },
                    )
                }
                Text(
                    tlsBlocked?.let { "Last reset: ${it.reason} — ${it.host} via ${it.via}" }
                        ?: if (tlsCount > 0) {
                            "$tlsCount intercepted connections were reset on this session."
                        } else {
                            "No intercepted connection on this session."
                        },
                    fontSize = 12.sp,
                    color = if (tlsBlocked != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Text("Linked accounts", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Link Google or GitHub after registration to sign in using either your password, Google, or GitHub.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                fun linkProvider(provider: String) {
                    scope.launch {
                        try {
                            // Ask the API for a signed link-mode authorize URL over
                            // the authenticated Retrofit client. Never put the
                            // bearer token in a browser URL or referrer.
                            val start = Api.service().startOAuth(
                                provider,
                                "json",
                                "uxaoro://oauth",
                                "link",
                            )
                            if (start.url.isBlank()) error("Could not start $provider linking")
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(start.url)))
                        } catch (t: Throwable) {
                            toast(Api.errorMessage(t))
                        }
                    }
                }
                fun unlinkProvider(provider: String) {
                    scope.launch {
                        try {
                            val res = Api.service().unlinkOAuth(org.uxaoro.app.data.OAuthUnlinkIn(provider))
                            vm.me = res.user
                            Prefs.user = res.user
                            toast("${provider.replaceFirstChar { it.uppercase() }} unlinked")
                        } catch (t: Throwable) {
                            toast(Api.errorMessage(t))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(id = org.uxaoro.app.R.drawable.ic_google),
                        contentDescription = "Google",
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                        modifier = Modifier.padding(end = 10.dp).height(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Google", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (me?.googleLinked == true) "Connected ✓ — you can sign in with Google" else "Not connected",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (me?.googleLinked == true) unlinkProvider("google") else linkProvider("google")
                        },
                        colors = inkTextButtonColors(),
                    ) {
                        Text(if (me?.googleLinked == true) "Unlink" else "Link Google", fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(id = org.uxaoro.app.R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(end = 10.dp).height(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text("GitHub", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (me?.githubLinked == true) "Connected ✓ — you can sign in with GitHub" else "Not connected",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (me?.githubLinked == true) unlinkProvider("github") else linkProvider("github")
                        },
                        colors = inkTextButtonColors(),
                    ) {
                        Text(if (me?.githubLinked == true) "Unlink" else "Link GitHub", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val hasPw = me?.hasPassword != false
                Text("Password", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                if (hasPw) {
                    PrimaryField(oldP, { oldP = it }, "Current password", visual = PasswordVisualTransformation())
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryField(newP, { newP = it }, "New password", visual = PasswordVisualTransformation())
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    if (hasPw) "Change password" else "Set password",
                    enabled = (!hasPw || oldP.length >= 6) && newP.length >= 6,
                    icon = "key",
                ) {
                    scope.launch {
                        try {
                            Api.service().changePassword(PasswordChange(oldP, newP))
                            runCatching { vm.me = Api.service().me(); Prefs.user = vm.me }
                            toast(if (hasPw) "Password changed" else "Password set")
                            oldP = ""; newP = ""
                        } catch (t: Throwable) {
                            toast(Api.errorMessage(t))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Authenticator app", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    if (me?.totpEnabled == true) "Two-factor is on. Codes from Google Authenticator, Aegis, or similar."
                    else "Protect sign-in with a 6-digit code from an authenticator app.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                if (me?.totpEnabled == true) {
                    PrimaryField(totpPass, { totpPass = it }, "Password", visual = PasswordVisualTransformation())
                    Spacer(Modifier.height(12.dp))
                    NumericCodePad(
                        code = totpCode,
                        onCodeChange = { totpCode = it },
                        openOnShow = false,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Turn off 2FA", enabled = totpPass.length >= 6 && totpCode.length == 6, icon = "shield-cross") {
                        scope.launch {
                            try {
                                Api.service().totpDisable(TotpDisableIn(totpPass, totpCode))
                                vm.me = Api.service().me(); Prefs.user = vm.me
                                totpPass = ""; totpCode = ""
                                toast("Two-factor off")
                            } catch (t: Throwable) {
                                toast(Api.errorMessage(t))
                            }
                        }
                    }
                } else if (totpSecret.isBlank()) {
                    PrimaryButton("Set up authenticator", icon = "shield-keyhole") {
                        scope.launch {
                            try {
                                val s = Api.service().totpSetup()
                                totpSecret = s.secret
                                totpUrl = s.otpauthUrl
                            } catch (t: Throwable) {
                                toast(Api.errorMessage(t))
                            }
                        }
                    }
                } else {
                    Text("Secret key", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        totpSecret.chunked(4).joinToString(" "),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(totpUrl)))
                            }.onFailure { toast("Add this key in your authenticator app") }
                        },
                        colors = inkTextButtonColors(),
                    ) { Text("Open authenticator app") }
                    Spacer(Modifier.height(12.dp))
                    NumericCodePad(
                        code = totpCode,
                        onCodeChange = { totpCode = it },
                        openOnShow = false,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Confirm and enable", enabled = totpCode.length == 6, icon = "shield-check") {
                        scope.launch {
                            try {
                                Api.service().totpEnable(TotpCodeIn(totpCode))
                                vm.me = Api.service().me(); Prefs.user = vm.me
                                totpSecret = ""; totpUrl = ""; totpCode = ""
                                toast("Two-factor on")
                            } catch (t: Throwable) {
                                toast(Api.errorMessage(t))
                            }
                        }
                    }
                    TextButton(onClick = { totpSecret = ""; totpUrl = ""; totpCode = "" }, colors = inkTextButtonColors()) {
                      AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                      Spacer(Modifier.width(6.dp))
                      Text("Cancel setup")
                    }
                }
            }
            SectionCard("Active Sessions & Devices") {
                var sessions by remember { mutableStateOf<List<org.uxaoro.app.data.SessionItem>>(emptyList()) }
                fun loadSessions() {
                    scope.launch {
                        sessions = runCatching { Api.service().listSessions().items }.getOrDefault(emptyList())
                    }
                }
                LaunchedEffect(Unit) { loadSessions() }
                Text(
                    "Devices currently signed in to your Uxaoro account (${sessions.size}).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                sessions.forEach { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.device + (if (s.isLatest) " · Current" else ""),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                (if (s.ipAddress.isNotBlank()) "${s.ipAddress} · " else "") +
                                    org.uxaoro.app.ui.components.formatTime(s.lastUsedAt ?: s.createdAt),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching { Api.service().revokeSession(s.id) }
                                        .onSuccess { toast("Session revoked"); loadSessions() }
                                        .onFailure { toast(Api.errorMessage(it)) }
                                }
                            },
                            colors = inkTextButtonColors(),
                        ) { Text("Revoke", fontSize = 12.sp) }
                    }
                }
                if (sessions.size > 1) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { Api.service().revokeOtherSessions(org.uxaoro.app.data.RefreshIn(Prefs.refreshToken)) }
                                    .onSuccess { toast("Signed out other devices"); loadSessions() }
                                    .onFailure { toast(Api.errorMessage(it)) }
                            }
                        },
                        colors = inkTextButtonColors(),
                    ) {
                        Text("Sign out all other devices")
                    }
                }
            }
            SectionCard("E2EE & Devices") {
                TextButton(onClick = { vm.go(Screen.Devices) }, colors = inkTextButtonColors()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetSvgIcon("devices", contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Linked devices — transfer E2EE keys between devices")
                    }
                }
                Text(
                    "Your private encryption key never leaves your device unencrypted. Pair a new device to securely copy your keys over ECDH+AES-GCM.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            SectionCard("Account") {
                TextButton(onClick = { vm.go(Screen.Blocked) }, colors = inkTextButtonColors()) {
                    Text("Blocked users")
                }
                TextButton(onClick = { vm.go(Screen.EditProfile) }, colors = inkTextButtonColors()) {
                  AssetSvgIcon("pen", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Edit profile")
                }
                Text(
                    "Messages stay encrypted on this device. The server never sees plaintext.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                VersionTap(onDev = { vm.openDev(Screen.Settings) })
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Log out", icon = "logout") { bye = true }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (bye) {
        AlertDialog(
            onDismissRequest = { bye = false },
            title = { Text("Sign out?") },
            text = { Text("You can come back anytime. Drafts stay on this device.") },
            confirmButton = { TextButton(onClick = { bye = false; vm.logout() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { bye = false }) { Text("Stay") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProxySettingsSection(onToast: (String) -> Unit = {}) {
    var enabled by remember { mutableStateOf(Prefs.proxyEnabled) }
    var bypassOnVpn by remember { mutableStateOf(Prefs.proxyBypassOnVpn) }
    var type by remember { mutableStateOf(Prefs.proxyType) }
    var host by remember { mutableStateOf(Prefs.proxyHost) }
    var portText by remember { mutableStateOf(Prefs.proxyPort.toString()) }
    var username by remember { mutableStateOf(Prefs.proxyUsername) }
    var password by remember { mutableStateOf(Prefs.proxyPassword) }
    val vpnActive by org.uxaoro.app.net.AppProxy.vpnActive.collectAsState()

    LaunchedEffect(Unit) {
        org.uxaoro.app.net.AppProxy.refreshVpnState()
    }

    fun parseAndSave(newEnabled: Boolean = enabled, newBypassVpn: Boolean = bypassOnVpn, silent: Boolean = false) {
        var h = host.trim()
        var p = portText.trim().toIntOrNull() ?: Prefs.proxyPort
        var t = type.trim().uppercase()
        var u = username.trim()
        var pw = password

        // Support pasting full URL like socks5://user:pass@host:port or host:port
        if (h.contains("://")) {
            runCatching {
                val uri = Uri.parse(h)
                val sch = (uri.scheme ?: "").uppercase()
                if (sch.startsWith("SOCKS")) t = "SOCKS5"
                else if (sch.startsWith("HTTP")) t = "HTTP"
                if (!uri.host.isNullOrBlank()) h = uri.host!!
                if (uri.port in 1..65535) p = uri.port
                val ui = uri.userInfo
                if (!ui.isNullOrBlank()) {
                    val parts = ui.split(":", limit = 2)
                    u = parts[0]
                    if (parts.size > 1) pw = parts[1]
                }
            }
        } else if (h.count { it == ':' } == 1 && !h.startsWith("[")) {
            val parts = h.split(":", limit = 2)
            val maybePort = parts[1].toIntOrNull()
            if (maybePort != null && maybePort in 1..65535) {
                h = parts[0].trim()
                p = maybePort
            }
        }

        if (newEnabled && (h.isBlank() || p !in 1..65535)) {
            if (!silent) onToast("Enter a valid proxy host and port (1–65535)")
            return
        }

        type = t
        host = h
        portText = p.coerceIn(1, 65535).toString()
        username = u
        password = pw
        Prefs.saveProxyConfig(
            enabled = newEnabled,
            type = t,
            host = h,
            port = p.coerceIn(1, 65535),
            username = u,
            password = pw,
            bypassOnVpn = newBypassVpn,
        )
        if (!silent) {
            onToast(
                if (newEnabled) "Proxy saved for Uxaoro ($t $h:${p.coerceIn(1, 65535)})"
                else "Uxaoro proxy disabled",
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Use proxy for Uxaoro only",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Routes only Uxaoro app traffic (API, messages, media, news) through this proxy. Other apps on your device are not affected.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                if (!it || host.isNotBlank()) {
                    parseAndSave(newEnabled = it, newBypassVpn = bypassOnVpn)
                }
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Don't use proxy when VPN is on",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Automatically bypass the Uxaoro proxy whenever a VPN connection is active on this device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(
            checked = bypassOnVpn,
            onCheckedChange = {
                bypassOnVpn = it
                Prefs.proxyBypassOnVpn = it
                onToast(
                    if (it) "Don't use proxy when VPN is on: enabled"
                    else "Don't use proxy when VPN is on: disabled",
                )
            },
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        org.uxaoro.app.net.AppProxy.statusSummary(vpnActive),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (enabled && !(bypassOnVpn && vpnActive)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
    Text("Proxy protocol", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(6.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("HTTP" to "HTTP / HTTPS", "SOCKS5" to "SOCKS5").forEach { (proto, label) ->
            val selected = type.equals(proto, ignoreCase = true) || (proto == "SOCKS5" && type.startsWith("SOCKS", ignoreCase = true))
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    )
                    .clickable { type = proto }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    PrimaryField(host, { host = it }, "Proxy host (e.g. 127.0.0.1)")
    Spacer(Modifier.height(8.dp))
    PrimaryField(portText, { portText = it.filter { ch -> ch.isDigit() }.take(5) }, "Proxy port (e.g. 8080 or 1080)")
    Spacer(Modifier.height(8.dp))
    PrimaryField(username, { username = it }, "Proxy username (optional)")
    Spacer(Modifier.height(8.dp))
    PrimaryField(password, { password = it }, "Proxy password (optional)", visual = PasswordVisualTransformation())
    Spacer(Modifier.height(10.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PrimaryButton(
            "Save proxy",
            enabled = host.isNotBlank() && (portText.toIntOrNull() ?: 0) in 1..65535,
            icon = "check-circle",
        ) {
            enabled = true
            parseAndSave(newEnabled = true, newBypassVpn = bypassOnVpn)
        }
        TextButton(
            onClick = {
                Prefs.resetProxyConfig()
                enabled = false
                type = Prefs.proxyType
                host = ""
                portText = "8080"
                username = ""
                password = ""
                onToast("Proxy cleared")
            },
            colors = inkTextButtonColors(),
        ) {
            Text("Reset proxy")
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        intense = true,
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyRow(
    title: String,
    subtitle: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (key, label) ->
                val on = value == key
                Text(
                    label,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        )
                        .clickable { if (!on) onChange(key) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun BlockedScreen(vm: AppVm) {
    var items by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch { items = runCatching { Api.service().blocked() }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(Unit) { load() }
    BlurScaffold(topBar = { DATopBar("Blocked", onBack = { vm.back() }) }) { pad ->
        if (items.isEmpty()) Box(Modifier.padding(pad).fillMaxSize()) { EmptyBox("No blocked users") }
        else androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            items(items.size) { i ->
                val u = items[i]
                UserRow(u, onClick = {}, trailing = {
                    TextButton(onClick = {
                        scope.launch { Api.service().unblock(u.id); load() }
                    }, colors = inkTextButtonColors()) { Text("Unblock") }
                })
            }
        }
    }
}
