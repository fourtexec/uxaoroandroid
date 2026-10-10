package org.uxaoro.app.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.uxaoro.app.crypto.DeviceTransfer
import org.uxaoro.app.crypto.E2EE
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.DeviceOut
import org.uxaoro.app.data.DeviceRegisterIn
import org.uxaoro.app.data.DeviceRenameIn
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.TransferApproveIn
import org.uxaoro.app.data.TransferCompleteIn
import org.uxaoro.app.data.TransferInitiateIn
import org.uxaoro.app.data.TransferOut
import org.uxaoro.app.data.WsClient
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.AssetSvgIcon

// ---------- ViewModel ----------

class DevicesVm(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {
    var devices by mutableStateOf<List<DeviceOut>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)

    var pendingIncoming by mutableStateOf<TransferOut?>(null)
    var approveSasState by mutableStateOf<ApproveSasState?>(null)

    data class ApproveSasState(
        val transfer: TransferOut,
        val approverEphemeral: DeviceTransfer.EphemeralKeys,
        val sas: String,
        val sealed: DeviceTransfer.Sealed,
    )

    var newFlow by mutableStateOf<NewFlowState?>(null)

    sealed class NewFlowStage {
        object Waiting : NewFlowStage()
        data class SasConfirm(val sas: String, val approverPub: String) : NewFlowStage()
        object Importing : NewFlowStage()
        object Done : NewFlowStage()
        data class Failed(val why: String) : NewFlowStage()
    }

    data class NewFlowState(
        val transfer: TransferOut,
        val ephemeral: DeviceTransfer.EphemeralKeys,
        val stage: NewFlowStage,
    )

    fun load() {
        viewModelScope.launch {
            loading = true; error = null
            runCatching { Api.service().listDevices() }
                .onSuccess { devices = it }
                .onFailure { error = Api.errorMessage(it) }
            loading = false
        }
    }

    fun rename(d: DeviceOut, name: String) {
        viewModelScope.launch {
            runCatching { Api.service().renameDevice(d.id, DeviceRenameIn(name)) }
                .onSuccess { load() }
                .onFailure { message = Api.errorMessage(it) }
        }
    }

    fun revoke(d: DeviceOut) {
        viewModelScope.launch {
            runCatching { Api.service().revokeDevice(d.id) }
                .onSuccess { load(); message = "Device removed" }
                .onFailure { message = Api.errorMessage(it) }
        }
    }

    fun initiateTransfer(label: String) {
        viewModelScope.launch {
            loading = true; error = null
            val ephemeral = DeviceTransfer.generateEphemeral()
            val res = runCatching {
                Api.service().transferInitiate(
                    TransferInitiateIn(
                        label = label.ifBlank { "New device" },
                        platform = "android",
                        ephemeralPubkey = ephemeral.publicKeyPem,
                    )
                )
            }
            res.onSuccess { t ->
                newFlow = NewFlowState(t, ephemeral, NewFlowStage.Waiting)
                loading = false
                startPolling(t.transferId)
            }.onFailure {
                error = Api.errorMessage(it); loading = false
            }
        }
    }

    private fun startPolling(tid: String) {
        viewModelScope.launch {
            repeat(200) { _ ->
                kotlinx.coroutines.delay(1000)
                val f = newFlow ?: return@launch
                if (f.transfer.transferId != tid) return@launch
                if (f.stage !is NewFlowStage.Waiting) return@launch
                val t = runCatching { Api.service().transferPoll(tid) }.getOrNull() ?: return@repeat
                if (t.status == "approved" && t.approverEphemeralPubkey.isNotBlank() && t.encryptedBlob.isNotBlank()) {
                    val sas = runCatching {
                        DeviceTransfer.sasFromPublicKeys(f.ephemeral.keyPair, t.approverEphemeralPubkey)
                    }.getOrDefault("")
                    if (sas.isNotBlank()) {
                        newFlow = f.copy(transfer = t, stage = NewFlowStage.SasConfirm(sas, t.approverEphemeralPubkey))
                    }
                    return@launch
                }
                when (t.status) {
                    "denied" -> { newFlow = f.copy(stage = NewFlowStage.Failed("Request denied")); return@launch }
                    "expired" -> { newFlow = f.copy(stage = NewFlowStage.Failed("Request expired")); return@launch }
                }
            }
        }
    }

    fun onTransferReady(t: TransferOut) {
        val f = newFlow ?: return
        if (f.transfer.transferId != t.transferId) return
        if (t.approverEphemeralPubkey.isBlank() || t.encryptedBlob.isBlank()) return
        val sas = runCatching {
            DeviceTransfer.sasFromPublicKeys(f.ephemeral.keyPair, t.approverEphemeralPubkey)
        }.getOrDefault("")
        if (sas.isBlank()) return
        newFlow = f.copy(transfer = t, stage = NewFlowStage.SasConfirm(sas, t.approverEphemeralPubkey))
    }

    fun confirmNewSasAndImport() {
        val f = newFlow ?: return
        val st = f.stage as? NewFlowStage.SasConfirm ?: return
        viewModelScope.launch {
            newFlow = f.copy(stage = NewFlowStage.Importing)
            val ctx = getApplication<android.app.Application>()
            val res = withContext(Dispatchers.Default) {
                runCatching {
                    DeviceTransfer.unsealBundle(
                        ourEphemeralKp = f.ephemeral.keyPair,
                        theirEphemeralPubPem = st.approverPub,
                        pairingCode = f.transfer.pairingCode,
                        ciphertextB64 = f.transfer.encryptedBlob,
                        nonceB64 = f.transfer.blobNonce,
                    )
                }
            }
            res.onSuccess { unsealed ->
                if (unsealed.sasCode != st.sas) {
                    newFlow = f.copy(stage = NewFlowStage.Failed("Verification code mismatch"))
                    return@onSuccess
                }
                withContext(Dispatchers.IO) {
                    E2EE.importPrivate(ctx, unsealed.bundle.privateKeyPem)
                }
                val pubPem = E2EE.publicPem(ctx)
                val ok = runCatching {
                    Api.service().transferComplete(
                        f.transfer.transferId,
                        TransferCompleteIn(devicePubkey = pubPem),
                    )
                }
                runCatching {
                    val label = runCatching { android.os.Build.MODEL }.getOrDefault("Android")
                    Api.service().registerDevice(
                        DeviceRegisterIn(label = label, platform = "android", devicePubkey = pubPem),
                    )
                    Api.service().putPubkey(mapOf("pubkey" to pubPem))
                }
                if (ok.isSuccess) {
                    newFlow = f.copy(stage = NewFlowStage.Done)
                    load()
                    message = "Device paired successfully"
                } else {
                    newFlow = f.copy(stage = NewFlowStage.Failed(Api.errorMessage(ok.exceptionOrNull() ?: Exception("complete failed"))))
                }
            }.onFailure {
                newFlow = f.copy(stage = NewFlowStage.Failed("Decryption failed: codes may not match"))
            }
        }
    }

    fun cancelNewFlow() { newFlow = null }

    fun consumeIncomingRequest(t: TransferOut) {
        if (pendingIncoming?.transferId == t.transferId) return
        pendingIncoming = t
    }

    fun startApprove() {
        val t = pendingIncoming ?: return
        viewModelScope.launch {
            loading = true
            val fresh = runCatching { Api.service().transferGet(t.transferId) }.getOrDefault(t)
            val approverEphemeral = DeviceTransfer.generateEphemeral()
            val ctx = getApplication<android.app.Application>()
            val bundle = DeviceTransfer.Bundle(
                privateKeyPem = E2EE.exportPrivate(ctx),
                publicKeyPem = E2EE.publicPem(ctx),
            )
            val sealed = withContext(Dispatchers.Default) {
                DeviceTransfer.sealBundle(
                    bundle = bundle,
                    ourEphemeralKp = approverEphemeral.keyPair,
                    theirEphemeralPubPem = fresh.newEphemeralPubkey,
                    pairingCode = fresh.pairingCode,
                )
            }
            approveSasState = ApproveSasState(fresh, approverEphemeral, sealed.sasCode, sealed)
            pendingIncoming = fresh
            loading = false
        }
    }

    fun confirmSasAndUpload() {
        val st = approveSasState ?: return
        viewModelScope.launch {
            loading = true
            runCatching {
                Api.service().transferApprove(
                    st.transfer.transferId,
                    TransferApproveIn(
                        approvingDeviceId = Prefs.deviceId,
                        approverEphemeralPubkey = st.approverEphemeral.publicKeyPem,
                        encryptedBlob = st.sealed.ciphertextB64,
                        blobNonce = st.sealed.nonceB64,
                    ),
                )
            }.onSuccess {
                message = "Keys sent to new device"
                approveSasState = null; pendingIncoming = null; load()
            }.onFailure { message = Api.errorMessage(it) }
            loading = false
        }
    }

    fun cancelApprove() { approveSasState = null }

    fun denyIncoming() {
        val t = pendingIncoming ?: return
        viewModelScope.launch {
            runCatching { Api.service().transferDeny(t.transferId) }
            pendingIncoming = null; approveSasState = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(appVm: AppVm, vm: DevicesVm = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(Unit) {
        WsClient.events.collect { ev ->
            when (ev.event) {
                "device_transfer_request" -> {
                    val data = ev.data ?: return@collect
                    val t = runCatching { Api.gson.fromJson(data, TransferOut::class.java) }.getOrNull()
                    if (t != null) vm.consumeIncomingRequest(t)
                }
                "device_transfer_ready" -> {
                    val data = ev.data ?: return@collect
                    val t = runCatching { Api.gson.fromJson(data, TransferOut::class.java) }.getOrNull()
                    if (t != null) vm.onTransferReady(t)
                }
                "device_transfer_completed" -> vm.load()
                "device_transfer_denied" -> { vm.message = "Transfer denied"; vm.load() }
            }
        }
    }

    Scaffold(
        topBar = { DATopBar(title = "Linked devices", onBack = { appVm.back() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { appVm.go(Screen.DeviceTransferNew) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Pair new device") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(
                Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("End-to-end encrypted key transfer", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Messages are encrypted on your device with a private key that never leaves it unencrypted. " +
                            "Pair a new device by approving the request on a logged-in device and matching a 6-digit " +
                            "verification code. The server cannot read your keys.",
                        fontSize = 13.sp,
                    )
                }
            }

            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(vm.devices, key = { it.id }) { d -> DeviceRow(d, vm) }
                if (vm.devices.isEmpty() && !vm.loading) {
                    item {
                        Text(
                            "No other devices linked yet. Tap “Pair new device” to set up E2EE on another phone or desktop.",
                            Modifier.padding(16.dp), fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    vm.pendingIncoming?.let { t ->
        if (vm.approveSasState == null) {
            IncomingRequestDialog(t, vm)
        }
    }
    vm.approveSasState?.let { st -> SasConfirmDialog(st, vm) }

    vm.message?.let { m ->
        LaunchedEffect(m) { appVm.toast(m); vm.message = null }
    }
}

@Composable
private fun DeviceRow(d: DeviceOut, vm: DevicesVm) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember(d.id) { mutableStateOf(d.label) }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (d.platform) {
                    "android" -> Icons.Default.PhoneAndroid
                    "ios" -> Icons.Default.PhoneIphone
                    "desktop" -> Icons.Default.Computer
                    "web" -> Icons.Default.Language
                    else -> Icons.Default.Devices
                },
                null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (d.label.isBlank()) "Unnamed device" else d.label, fontWeight = FontWeight.SemiBold)
                val badge = when {
                    d.isCurrent -> "this device"
                    else -> "linked"
                }
                Text(
                    "${d.platform.ifBlank { "unknown" }}  •  $badge",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!d.isCurrent) {
                IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
                IconButton(onClick = { vm.revoke(d) }) {
                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename device") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(d, name); renaming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IncomingRequestDialog(t: TransferOut, vm: DevicesVm) {
    AlertDialog(
        onDismissRequest = { vm.denyIncoming() },
        title = { Text("New device wants to pair") },
        text = {
            Column {
                Text("“${t.newDeviceLabel.ifBlank { "A new device" }}” (${t.newDevicePlatform.ifBlank { "unknown" }}) is asking to receive your E2EE keys.", fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text("Pairing code", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    t.pairingCode, fontSize = 32.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp, modifier = Modifier.padding(vertical = 4.dp),
                )
                Text("Make sure this code matches what's shown on the new device. After approving, you'll verify a second 6-digit code.", fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { vm.startApprove() }, enabled = !vm.loading) { Text("Approve") } },
        dismissButton = { TextButton(onClick = { vm.denyIncoming() }) { Text("Deny", color = MaterialTheme.colorScheme.error) } },
    )
}

@Composable
private fun SasConfirmDialog(st: DevicesVm.ApproveSasState, vm: DevicesVm) {
    AlertDialog(
        onDismissRequest = { vm.cancelApprove() },
        title = { Text("Confirm verification code") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Compare this 6-digit code with the one shown on the new device. If they match, the connection is safe.", fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st.sas, fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.confirmSasAndUpload() }, enabled = !vm.loading) { Text("They match — send keys") }
        },
        dismissButton = { TextButton(onClick = { vm.cancelApprove() }) { Text("Cancel") } },
    )
}

// ---------- New-device pairing screen ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceTransferNewScreen(appVm: AppVm, vm: DevicesVm = viewModel()) {
    var label by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            DATopBar(title = "Pair this device", onBack = {
                vm.cancelNewFlow()
                appVm.back()
            })
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(24.dp).fillMaxSize()) {
            val f = vm.newFlow
            if (f == null) {
                Text("Transfer E2EE keys to this device", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Open Uxaoro on a device where you're already logged in. Go to Settings → Linked devices and approve the request " +
                        "for this device when it appears. You'll confirm a 6-digit code on both sides.",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Device label (e.g. My Pixel)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { vm.initiateTransfer(label) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.loading,
                ) { Text("Start pairing") }
                if (vm.loading) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            } else {
                Text("Waiting for approval…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("On your logged-in device, open Settings → Linked devices. A pairing request for “${f.transfer.newDeviceLabel.ifBlank { "new device" }}” should appear.", fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                Text("Pairing code", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(f.transfer.pairingCode, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp)
                }
                Spacer(Modifier.height(18.dp))
                when (val st = f.stage) {
                    is DevicesVm.NewFlowStage.Waiting -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Waiting for approval…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is DevicesVm.NewFlowStage.SasConfirm -> {
                        Text("Verification code", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(st.sas, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("If this matches the code on the other device, tap Confirm to import your keys.", fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.confirmNewSasAndImport() }, modifier = Modifier.fillMaxWidth()) {
                            Text("They match — import keys")
                        }
                    }
                    is DevicesVm.NewFlowStage.Importing -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text("Importing keys…", fontSize = 12.sp)
                    }
                    is DevicesVm.NewFlowStage.Done -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssetSvgIcon("check-circle", contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Device paired! You can now read encrypted messages on this device.", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.cancelNewFlow(); appVm.back() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                    is DevicesVm.NewFlowStage.Failed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssetSvgIcon("danger-triangle", contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(st.why, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { vm.cancelNewFlow() }, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                    }
                }
            }
        }
    }
}
