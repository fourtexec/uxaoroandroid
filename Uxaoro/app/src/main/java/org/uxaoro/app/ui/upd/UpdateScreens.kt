package org.uxaoro.app.ui.upd

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.uxaoro.app.BuildConfig
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.inkTextButtonColors
import org.uxaoro.app.ui.theme.BlurScaffold
import org.uxaoro.app.ui.theme.GlassPanel
import org.uxaoro.app.upd.ApkInstaller
import org.uxaoro.app.upd.UpdateSeverity
import org.uxaoro.app.upd.Updater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.uxaoro.app.ui.components.AssetSvgIcon

/**
 * Global update prompt. Mounted once, above everything else in [AppRoot].
 *
 * * **Required** update: a blocking dialog that cannot be dismissed — no back
 *   press, no tap outside, no "Later". The user either installs the APK or, in
 *   debug builds only, explicitly ignores it for the session.
 * * **Normal** update: the same dialog, but it can be postponed ("Later", this
 *   session) or skipped ("Skip this version", remembered in [Prefs]).
 */
@Composable
fun UpdateGate() {
    val ctx = LocalContext.current
    val st by Updater.state.collectAsState()
    val update = st.update
    val show = update != null && (st.required || (!st.skipped && !st.postponed))
    if (!show || update == null) return

    val canInstall = ApkInstaller.canInstall(ctx)
    val blocked = st.required

    AlertDialog(
        onDismissRequest = { if (!blocked) Updater.postpone() },
        properties = DialogProperties(
            dismissOnBackPress = !blocked,
            dismissOnClickOutside = !blocked,
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (blocked) "Required update" else "Update Uxaoro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                SeverityChip(update.severity)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Uxaoro ${update.version}" +
                        if (update.versionCode > 0) "  (build ${update.versionCode})" else "",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Installed: ${Prefs.APP_VERSION} (build ${Prefs.APP_VERSION_CODE})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (update.requiredReason.isNotBlank() && blocked) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This update cannot be postponed: ${update.requiredReason}.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (update.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    GlassPanel(shape = RoundedCornerShape(14.dp)) {
                        Text(
                            update.notes,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    apkLine(update),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (st.phase == Updater.Phase.DOWNLOADING) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { st.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${st.progress}%  ·  ${bytes(st.bytesReceived)} / ${bytes(st.bytesTotal)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { Updater.cancel() }, colors = inkTextButtonColors()) {
                      AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                      Spacer(Modifier.width(6.dp))
                      Text("Cancel download")
                    }
                }

                if (!canInstall) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android must allow Uxaoro to install unknown apps. Allow it, then tap Install again.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                st.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (st.hasDownloadedApk && !canInstall) {
                TextButton(
                    onClick = { ApkInstaller.requestInstallPermission(ctx) },
                    colors = inkTextButtonColors(),
                ) { Text("Allow installs") }
            } else if (st.hasDownloadedApk) {
                TextButton(
                    onClick = { Updater.install(ctx) },
                    colors = inkTextButtonColors(),
                ) { Text("Install now") }
            } else {
                TextButton(
                    onClick = { Updater.downloadAndInstall(ctx, installWhenReady = true) },
                    enabled = st.phase != Updater.Phase.DOWNLOADING,
                    colors = inkTextButtonColors(),
                ) {
                  AssetSvgIcon("download", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Download & install")
                }
            }
        },
        dismissButton = {
            when {
                blocked && BuildConfig.DEBUG -> {
                    TextButton(
                        onClick = { Updater.ignoreForSession() },
                        colors = inkTextButtonColors(),
                    ) { Text("Ignore (debug)") }
                }
                blocked -> {}
                else -> Row {
                    TextButton(onClick = { Updater.skip() }, colors = inkTextButtonColors()) {
                        Text("Skip this version")
                    }
                    TextButton(onClick = { Updater.postpone() }, colors = inkTextButtonColors()) {
                      AssetSvgIcon("clock-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                      Spacer(Modifier.width(6.dp))
                      Text("Later")
                    }
                }
            }
        },
    )
}

/**
 * "App updates" screen: version information, automatic checks, update channel
 * and the manual install flow. Reachable from the More tab.
 */
@Composable
fun UpdatesScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val st by Updater.state.collectAsState()
    var autoCheck by remember { mutableStateOf(Prefs.updateAutoCheck) }
    var baseUrl by remember { mutableStateOf(Prefs.updateBase) }

    LaunchedEffect(Unit) {
        Updater.check(ctx.applicationContext, manual = true)
    }
    LaunchedEffect(st.status) {
        st.status?.let { msg ->
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            Updater.clearStatus()
        }
    }

    BlurScaffold(topBar = { DATopBar("App updates", onBack = { vm.back() }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Installed") {
                InfoRow("Uxaoro for Android", "${Prefs.APP_VERSION}  (build ${Prefs.APP_VERSION_CODE})")
                InfoRow("Package", BuildConfig.APPLICATION_ID)
                InfoRow("Update source", Updater.manifestUrl())
                InfoRow("Last check", fmtTime(st.lastCheckAt))
            }

            val update = st.update
            if (update != null) {
                SectionCard(if (st.required) "Required update" else "Available update") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Uxaoro ${update.version}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.width(6.dp))
                        SeverityChip(update.severity)
                    }
                    Text(
                        apkLine(update) + (if (update.publishedAt != null) " · ${update.publishedAt}" else ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (update.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            update.notes,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (st.phase == Updater.Phase.DOWNLOADING) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { st.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${st.progress}%  ·  ${bytes(st.bytesReceived)} / ${bytes(st.bytesTotal)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (st.hasDownloadedApk) {
                            TextButton(
                                onClick = { Updater.install(ctx) },
                                colors = inkTextButtonColors(),
                            ) {
                              AssetSvgIcon("download", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                              Spacer(Modifier.width(6.dp))
                              Text("Install update")
                            }
                        } else {
                            TextButton(
                                onClick = { Updater.downloadAndInstall(ctx, installWhenReady = false) },
                                enabled = st.phase != Updater.Phase.DOWNLOADING,
                                colors = inkTextButtonColors(),
                            ) {
                              AssetSvgIcon("download", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                              Spacer(Modifier.width(6.dp))
                              Text("Download APK")
                            }
                        }
                        if (st.phase == Updater.Phase.DOWNLOADING) {
                            TextButton(onClick = { Updater.cancel() }, colors = inkTextButtonColors()) {
                              AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                              Spacer(Modifier.width(6.dp))
                              Text("Cancel")
                            }
                        } else if (!st.required) {
                            TextButton(onClick = { Updater.skip() }, colors = inkTextButtonColors()) {
                                Text("Skip this version")
                            }
                        }
                    }
                    if (!ApkInstaller.canInstall(ctx)) {
                        TextButton(
                            onClick = { ApkInstaller.requestInstallPermission(ctx) },
                            colors = inkTextButtonColors(),
                        ) { Text("Allow app installs") }
                    }
                }
            } else {
                SectionCard("Status") {
                    Text(
                        when {
                            st.phase == Updater.Phase.CHECKING -> "Checking for updates…"
                            st.skipped -> "Version ${Prefs.updateSkippedVersion} was skipped."
                            st.error != null -> st.error.orEmpty()
                            else -> "Uxaoro ${Prefs.APP_VERSION} is the latest version."
                        },
                        fontSize = 13.sp,
                        color =
                            if (st.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            SectionCard("Checks") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Check automatically",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Checks the manifest at most once every 6 hours.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoCheck,
                        onCheckedChange = {
                            autoCheck = it
                            Prefs.updateAutoCheck = it
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { Updater.check(ctx.applicationContext, manual = true) },
                        enabled = st.phase != Updater.Phase.CHECKING,
                        colors = inkTextButtonColors(),
                    ) { Text(if (st.phase == Updater.Phase.CHECKING) "Checking…" else "Check for updates") }
                    if (st.skipped) {
                        TextButton(onClick = { Updater.unskip() }, colors = inkTextButtonColors()) {
                            Text("Un-skip ${Prefs.updateSkippedVersion}")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Publisher base URL (blank = default)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                org.uxaoro.app.ui.components.PrimaryField(
                    value = baseUrl,
                    onValue = { baseUrl = it },
                    label = "Update base URL",
                )
                Text(
                    "Default: ${Updater.DEFAULT_BASE}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            Prefs.updateBase = baseUrl.trim()
                            Updater.clearError()
                            Updater.check(ctx.applicationContext, manual = true)
                        },
                        colors = inkTextButtonColors(),
                    ) { Text("Save & check") }
                    TextButton(
                        onClick = {
                            Prefs.updateBase = ""
                            baseUrl = ""
                            Updater.check(ctx.applicationContext, manual = true)
                        },
                        colors = inkTextButtonColors(),
                    ) { Text("Reset") }
                }
            }

            if (BuildConfig.DEBUG) {
                SectionCard("Debug build") {
                    Text(
                        "Debug builds cannot be replaced by the published release APK. " +
                            "Automatic checks are disabled here; install a release build to test the update flow.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/* ---------------- small pieces ---------------- */

@Composable
private fun SeverityChip(severity: UpdateSeverity) {
    val required = severity == UpdateSeverity.REQUIRED
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (required) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            if (required) "REQUIRED" else "OPTIONAL",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(Locale.getDefault()),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        GlassPanel(shape = RoundedCornerShape(16.dp), intense = true) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun apkLine(update: org.uxaoro.app.upd.AndroidUpdate): String {
    val size = if (update.apkSize > 0) bytes(update.apkSize) else "size n/a"
    val channel = if (update.channel.isNotBlank()) " · ${update.channel}" else ""
    return "APK $size$channel"
}

private fun bytes(n: Long): String = when {
    n <= 0 -> "0 B"
    n >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", n / 1073741824.0)
    n >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", n / 1048576.0)
    n >= 1024L -> String.format(Locale.US, "%.0f KB", n / 1024.0)
    else -> "$n B"
}

private fun fmtTime(at: Long): String {
    if (at <= 0) return "never"
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
    return fmt.format(Date(at))
}
