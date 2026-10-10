package org.uxaoro.app.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.uxaoro.app.data.LoginEvent
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.UserMe
import org.uxaoro.app.data.UserUpdate
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.NumericCodePad
import org.uxaoro.app.ui.components.UxaoroLogo
import org.uxaoro.app.ui.components.formatLastSeen
import org.uxaoro.app.ui.components.formatTime
import org.uxaoro.app.ui.components.inkTextButtonColors
import org.uxaoro.app.ui.theme.BlurScaffold
import org.uxaoro.app.ui.theme.GlassPanel
import org.uxaoro.app.ui.components.AssetSvgIcon
import androidx.compose.foundation.layout.width

/*
 * Sign-in security.
 *
 * Three separate protections live here:
 *
 * * the **security activity** log and its alert switch (server side: every
 *   sign-in is recorded, unrecognised devices raise a notification),
 * * the **app lock**, which keeps someone holding an unlocked phone out of the
 *   app entirely — device-local, the PIN never leaves the phone,
 * * **screenshot protection**, which also hides the app in the recents
 *   thumbnail.
 */

private val AUTO_LOCK_CHOICES = listOf(
    0 to "Immediately",
    1 to "1 min",
    5 to "5 min",
    15 to "15 min",
    60 to "1 hour",
)

/** Security rows for the Settings screen. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SecurityHardeningSection(
    vm: AppVm,
    me: UserMe?,
    onToast: (String) -> Unit,
    onPatch: (UserUpdate) -> Unit,
) {
    val ctx = LocalContext.current
    var autoLock by remember { mutableStateOf(Prefs.appLockTimeoutMin) }
    var blockShots by remember { mutableStateOf(Prefs.blockScreenshots) }
    var lockEnabled by remember { mutableStateOf(Prefs.appLockEnabled) }
    var settingPin by remember { mutableStateOf(false) }
    var disabling by remember { mutableStateOf(false) }

    fun applyScreenshots(on: Boolean) {
        // Apply at once instead of waiting for the next resume.
        runCatching {
            val act = ctx as? Activity ?: return
            if (on) {
                act.window.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                    android.view.WindowManager.LayoutParams.FLAG_SECURE,
                )
            } else {
                act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    if (settingPin) {
        SetPinDialog(
            onCancel = { settingPin = false },
            onDone = { pin ->
                Prefs.setAppLockPin(pin)
                Prefs.appLockEnabled = true
                lockEnabled = true
                settingPin = false
                onToast("App lock is on")
            },
        )
    }
    if (disabling) {
        AlertDialog(
            onDismissRequest = { disabling = false },
            title = { Text("Turn off app lock?", fontWeight = FontWeight.Bold) },
            text = { Text("The app will open without asking for a PIN.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Prefs.appLockEnabled = false
                        Prefs.setAppLockPin("")
                        vm.onAppLockRemoved()
                        lockEnabled = false
                        disabling = false
                        onToast("App lock is off")
                    },
                    colors = inkTextButtonColors(),
                ) { Text("Turn off") }
            },
            dismissButton = {
                TextButton(onClick = { disabling = false }, colors = inkTextButtonColors()) {
                  AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Cancel")
                }
            },
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Alert me about new sign-ins", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "A notification (and an email, when the server can send one) the first time a new device signs in.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = me?.securityAlerts ?: true,
            onCheckedChange = { onPatch(UserUpdate(securityAlerts = it)) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        TextButton(onClick = { vm.go(org.uxaoro.app.ui.Screen.SecurityActivity) }, colors = inkTextButtonColors()) {
            Text("Security activity")
        }
        TextButton(onClick = { vm.go(org.uxaoro.app.ui.Screen.Devices) }, colors = inkTextButtonColors()) {
            Text("Active sessions & devices")
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("App lock", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Ask for a PIN before the app opens. Stored on this device only.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = lockEnabled,
            onCheckedChange = { want ->
                if (want) settingPin = true else disabling = true
            },
        )
    }
    if (lockEnabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { settingPin = true }, colors = inkTextButtonColors()) { Text("Change PIN") }
            TextButton(onClick = { vm.lockAppNow() }, colors = inkTextButtonColors()) { Text("Lock now") }
        }
        Text(
            "Lock after leaving the app",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AUTO_LOCK_CHOICES.forEach { (minutes, label) ->
                val on = autoLock == minutes
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
                        .clickable {
                            autoLock = minutes
                            Prefs.appLockTimeoutMin = minutes
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Block screenshots", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Blocks screenshots, screen recording and the recents preview (Android).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = blockShots,
            onCheckedChange = {
                blockShots = it
                Prefs.blockScreenshots = it
                applyScreenshots(it)
            },
        )
    }
}

/** The lock screen itself: full-screen, nothing of the app behind it. */
@Composable
fun AppLockScreen(vm: AppVm) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UxaoroLogo(Modifier.height(42.dp))
            Spacer(Modifier.height(10.dp))
            Text("Uxaoro is locked", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Enter your PIN to continue.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            NumericCodePad(
                code = pin,
                digits = 4,
                onCodeChange = { value ->
                    pin = value
                    if (value.length == 4) {
                        if (!vm.unlockWith(value)) {
                            error = "Wrong PIN"
                            pin = ""
                        } else {
                            error = null
                        }
                    }
                },
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

/** Ask for the PIN twice; only the second entry stores it. */
@Composable
private fun SetPinDialog(onCancel: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val step2 = first.length == 4
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (step2) "Repeat the PIN" else "Choose a 4-digit PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (!step2) {
                    Text(
                        "Used to open Uxaoro on this device. It is stored hashed and never sent to the server.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    NumericCodePad(code = first, digits = 4, onCodeChange = { first = it })
                } else {
                    NumericCodePad(
                        code = second,
                        digits = 4,
                        onCodeChange = { value ->
                            second = value
                            if (value.length == 4) {
                                if (value == first) onDone(value) else {
                                    error = "The two entries do not match"
                                    second = ""
                                    first = ""
                                }
                            }
                        },
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        // The pad advances to the second entry on its own once 4 digits are in.
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    if (step2) {
                        second = ""
                        first = ""
                    } else {
                        onCancel()
                    }
                },
                colors = inkTextButtonColors(),
            ) { Text(if (step2) "Back" else "Cancel") }
        },
    )
}

/**
 * "Key backup" section: the account's E2EE private key, sealed with the account
 * password and parked on the server so a new device can pick it up.
 *
 * The password typed here is used locally (PBKDF2-HMAC-SHA256, 600k rounds,
 * AES-256-GCM) and is never sent: the server stores an opaque blob it cannot
 * open. That is also why a wrong password shows up as "could not open" instead
 * of a server error.
 */
@Composable
fun KeyBackupSection(vm: AppVm) {
    val backup = vm.keyBackup
    var forBackup by remember { mutableStateOf(false) }
    var forRestore by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { vm.refreshKeyBackup() }

    if (forBackup || forRestore) {
        PasswordPromptDialog(
            title = if (forBackup) "Encrypt the backup" else "Unlock your backup",
            explain = if (forBackup) {
                "Your account password turns the key file into a blob only you can open. It is not sent to the server."
            } else {
                "Enter the account password this backup was created with."
            },
            onCancel = { forBackup = false; forRestore = false },
            onConfirm = { pw ->
                val sealing = forBackup
                forBackup = false
                forRestore = false
                scope.launch { if (sealing) vm.backupKey(pw) else vm.restoreKey(pw) }
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete the server copy?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The encrypted blob is removed from the server. Keys already on your devices keep working, but a new device will not be able to restore them.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        scope.launch { vm.deleteKeyBackup() }
                    },
                    colors = inkTextButtonColors(),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }, colors = inkTextButtonColors()) { Text("Keep") }
            },
        )
    }

    Column(Modifier.padding(vertical = 4.dp)) {
        Text("Private key backup", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Your end-to-end encryption keys are sealed with your password and stored on the server, so signing in on a " +
                "new device restores them. The server only holds the encrypted blob.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            vm.keyBackupBusy -> Text("Working…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            backup == null -> Text("Checking the server…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            !backup.exists -> Text(
                "No backup on the server yet.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Text(
                    "Backed up " + formatTime(backup.updatedAt) + " · sealed with your password",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!backup.matchesAccountKey && backup.pubkeyFingerprint.isNotBlank()) {
                    Text(
                        "This backup was made from a different key than the one this device publishes. Restoring it would replace your current key.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { forBackup = true },
                enabled = !vm.keyBackupBusy,
                colors = inkTextButtonColors(),
            ) { Text(if (backup?.exists == true) "Update backup" else "Back up now") }
            TextButton(
                onClick = { forRestore = true },
                enabled = !vm.keyBackupBusy && backup?.exists == true,
                colors = inkTextButtonColors(),
            ) { Text("Restore") }
            TextButton(
                onClick = { confirmingDelete = true },
                enabled = !vm.keyBackupBusy && backup?.exists == true,
                colors = inkTextButtonColors(),
            ) {
              AssetSvgIcon("trash-bin-trash", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
              Spacer(Modifier.width(6.dp))
              Text("Delete")
            }
        }
    }
}

/** One-off password entry used by the key backup actions. */
@Composable
private fun PasswordPromptDialog(
    title: String,
    explain: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(explain, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    singleLine = true,
                    label = { Text("Account password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pw) }, enabled = pw.length >= 6, colors = inkTextButtonColors()) {
                Text("Continue")
            }
        },
        dismissButton = { TextButton(onClick = onCancel, colors = inkTextButtonColors()) { Text("Cancel") } },
    )
}

/** Recent sign-ins for this account, newest first. */
@Composable
fun SecurityActivityScreen(vm: AppVm) {
    var items by remember { mutableStateOf<List<LoginEvent>>(emptyList()) }
    var alertsEnabled by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scale = rememberCoroutineScope()

    fun load() {
        scale.launch {
            loading = true
            runCatching { Api.service().securityActivity(100) }
                .onSuccess {
                    items = it.items
                    alertsEnabled = it.alertsEnabled
                    error = null
                }
                .onFailure { error = Api.errorMessage(it) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    BlurScaffold(topBar = { DATopBar("Security activity", onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Where your account was signed in from. If something here is not yours, change your password " +
                    "and sign out the other sessions.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            if (!loading && items.isEmpty() && error == null) {
                Text("No sign-ins recorded yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEach { event ->
                GlassPanel(shape = RoundedCornerShape(16.dp), intense = true) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                event.device.ifBlank { "Unknown device" },
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            if (event.newDevice) {
                                Text(
                                    "NEW DEVICE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            buildString {
                                append(describeMethod(event.method))
                                if (event.ipAddress.isNotBlank()) append(" · ${event.ipAddress}")
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${formatTime(event.at)} (${formatLastSeen(event.at)})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (items.isNotEmpty()) {
                Text(
                    if (alertsEnabled) {
                        "Alerts for new devices are on — turn them off in Settings."
                    } else {
                        "Alerts for new devices are off — turn them on in Settings."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { load() }, colors = inkTextButtonColors()) {
              AssetSvgIcon("refresh", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
              Spacer(Modifier.width(6.dp))
              Text("Refresh")
            }
        }
    }
}

private fun describeMethod(method: String): String = when (method.lowercase()) {
    "password" -> "Password sign-in"
    "totp" -> "Two-factor code"
    "email" -> "Email link"
    "register" -> "New account"
    "oauth" -> "Google / GitHub"
    "" -> "Sign-in"
    else -> method.replaceFirstChar { it.uppercase() }
}
