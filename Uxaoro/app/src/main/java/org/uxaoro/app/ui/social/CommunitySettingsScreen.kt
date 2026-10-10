package org.uxaoro.app.ui.social

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Cache
import org.uxaoro.app.data.CommunityOut
import org.uxaoro.app.data.CommunityPatch
import org.uxaoro.app.data.CommunityPermissions
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.AssetSvgIcon
import org.uxaoro.app.ui.components.Avatar
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.ErrorText
import org.uxaoro.app.ui.components.LoadingBox
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.inkFieldColors
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.media.EditedPhoto
import org.uxaoro.app.ui.media.PhotoEditorDialog
import org.uxaoro.app.ui.theme.BlurScaffold
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunitySettingsScreen(vm: AppVm, id: String) {
    var community by remember(id) { mutableStateOf<CommunityOut?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var revision by remember(id) { mutableIntStateOf(0) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var pendingAction by remember(id) { mutableStateOf<String?>(null) }
    val busy = pendingAction != null
    val scope = rememberCoroutineScope()

    // Keep drafts separate from the server row: changing an access rule or
    // uploading a photo must not discard unsaved details (nor a failed save).
    var initialized by rememberSaveable(id) { mutableStateOf(false) }
    var name by rememberSaveable(id) { mutableStateOf("") }
    var description by rememberSaveable(id) { mutableStateOf("") }
    var username by rememberSaveable(id) { mutableStateOf("") }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    var editTarget by rememberSaveable(id) { mutableStateOf("avatar") }
    var editUri by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var avatarPreview by remember(id) { mutableStateOf<ByteArray?>(null) }
    var coverPreview by remember(id) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(id, revision) {
        loading = true
        error = null
        try {
            val loaded = Api.service().getCommunity(id)
            community = loaded
            if (!initialized) {
                name = loaded.name
                description = loaded.description
                username = loaded.username.ifBlank { loaded.slug }
                initialized = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = Api.errorMessage(e)
        } finally {
            loading = false
        }
    }

    fun patch(
        label: String,
        body: CommunityPatch,
        optimistic: (CommunityOut) -> CommunityOut,
        onSaved: (CommunityOut) -> Unit = {},
    ) {
        val before: CommunityOut = community ?: return
        if (pendingAction != null || !CommunityPermissions.canManage(before.myRole)) return
        pendingAction = label
        error = null
        scope.localFirst(
            vm, label,
            apply = { community = optimistic(before) },
            undo = { community = before; pendingAction = null },
            send = { community = Api.service().patchCommunity(id, body) },
            onSuccess = {
                pendingAction = null
                community?.let { saved ->
                    Cache.updateCommunity(saved)
                    onSaved(saved)
                }
                vm.toast("Community updated")
            },
            onFailure = { error = it },
        )
    }

    fun upload(target: String, photo: EditedPhoto) {
        val before: CommunityOut = community ?: return
        if (pendingAction != null || !CommunityPermissions.canManage(before.myRole)) return
        pendingAction = "Upload community $target"
        error = null
        scope.localFirst(
            vm, "Upload community $target",
            apply = {
                if (target == "avatar") avatarPreview = photo.bytes else coverPreview = photo.bytes
            },
            undo = {
                community = before
                avatarPreview = null
                coverPreview = null
                pendingAction = null
            },
            send = {
                val file = MultipartBody.Part.createFormData(
                    "file", "community-$target.jpg", photo.bytes.toRequestBody(photo.mime.toMediaType()),
                )
                val result = if (target == "avatar") Api.service().uploadCommunityAvatar(id, file)
                    else Api.service().uploadCommunityCover(id, file)
                val url = (if (target == "avatar") result.avatarUrl else result.coverUrl)
                    ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("The upload returned no photo URL. Please try again.")
                community = if (target == "avatar") before.copy(avatarUrl = url) else before.copy(coverUrl = url)
            },
            onSuccess = {
                pendingAction = null
                avatarPreview = null
                coverPreview = null
                community?.let { Cache.updateCommunity(it) }
                vm.toast("Community photo updated")
            },
            onFailure = { error = it },
        )
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { editTarget = "avatar"; editUri = uri.toString() }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { editTarget = "cover"; editUri = uri.toString() }
    }
    editUri?.let { uri ->
        PhotoEditorDialog(source = Uri.parse(uri), title = "Edit community $editTarget") { edited ->
            if (edited != null) upload(editTarget, edited)
            editUri = null
        }
    }

    // These mutations own screen-local rollback state. Do not dispose it by
    // navigating away while a save/upload/delete is still in flight.
    fun back() {
        if (busy) vm.toast("Please wait for the community update to finish") else vm.back()
    }
    BackHandler(enabled = busy) { back() }

    val comm = community
    if (confirmDelete && comm?.myRole == "owner") {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete community?") },
            text = { Text("Delete ${comm.name}? All its posts will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmDelete = false
                        pendingAction = "Delete community"
                        error = null
                        scope.localFirst(
                            vm, "Delete community",
                            send = { Api.service().deleteCommunity(id) },
                            undo = { pendingAction = null },
                            onSuccess = {
                                pendingAction = null
                                vm.communityDeleted(id)
                                vm.toast("Community deleted")
                            },
                            onFailure = { error = it },
                        )
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    BlurScaffold(topBar = { DATopBar("Community settings", onBack = { back() }, subtitle = comm?.name) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(pendingAction.orEmpty() + "…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            ErrorText(error)
            when {
                loading -> LoadingBox()
                comm == null -> TextButton(onClick = { revision++ }, modifier = Modifier.padding(16.dp)) { Text("Retry") }
                !CommunityPermissions.canManage(comm.myRole) -> EmptyBox("Only the community owner and admins can manage settings.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    item {
                        CommunitySection("Details") {
                            OutlinedTextField(
                                value = name, onValueChange = { name = it.take(80) }, label = { Text("Name") },
                                enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = inkFieldColors(),
                            )
                            OutlinedTextField(
                                value = description, onValueChange = { description = it.take(2000) }, label = { Text("Description") },
                                enabled = !busy, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), colors = inkFieldColors(),
                            )
                            PrimaryButton("Save details", enabled = !busy && name.isNotBlank(), icon = "check-circle") {
                                patch(
                                    "Save community", CommunityPatch(name = name.trim(), description = description),
                                    optimistic = { it.copy(name = name.trim(), description = description) },
                                    onSaved = { name = it.name; description = it.description },
                                )
                            }
                            if (comm.myRole == "owner") {
                                OutlinedTextField(
                                    value = username, onValueChange = { username = it }, label = { Text("Username (@name)") },
                                    enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = inkFieldColors(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false),
                                )
                                Text("Changing the username changes the public link @name. Use 3–32 letters, numbers or underscores.", style = MaterialTheme.typography.bodySmall)
                                PrimaryButton("Set username", enabled = !busy && username.isNotBlank(), icon = "check-circle") {
                                    val next = username.trim().trimStart('@').lowercase(Locale.ROOT)
                                    if (!Regex("^[a-z0-9_]{3,32}$").matches(next)) {
                                        error = "Username must contain 3–32 letters, numbers or underscores."
                                    } else {
                                        patch(
                                            "Change username", CommunityPatch(username = next),
                                            optimistic = { it.copy(username = next, slug = next) },
                                            onSaved = { username = it.username.ifBlank { it.slug } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        CommunitySection("Look") {
                            Box(
                                Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (coverPreview != null || comm.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = coverPreview ?: Api.mediaUrl(comm.coverUrl), contentDescription = "Community cover photo",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    )
                                } else Text("No cover photo")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (avatarPreview != null) {
                                    AsyncImage(
                                        model = avatarPreview, contentDescription = "New community avatar",
                                        modifier = Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop,
                                    )
                                } else Avatar(comm.avatarUrl, comm.name, size = 64)
                                Spacer(Modifier.width(12.dp))
                                Text(comm.name, fontWeight = FontWeight.SemiBold)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = !busy, onClick = { avatarPicker.launch("image/*") }) { Text("Change avatar") }
                                OutlinedButton(enabled = !busy, onClick = { coverPicker.launch("image/*") }) { Text("Change cover photo") }
                            }
                        }
                    }
                    item {
                        CommunitySection("Access") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Closed community", fontWeight = FontWeight.Medium)
                                    Text("Posts and members are hidden from non-members and joining is blocked. Members can still be invited.", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = comm.isClosed, enabled = !busy,
                                    modifier = Modifier.semantics { contentDescription = "Closed community" },
                                    onCheckedChange = { closed ->
                                        patch(
                                            if (closed) "Close community" else "Open community",
                                            CommunityPatch(isClosed = closed), optimistic = { it.copy(isClosed = closed) },
                                        )
                                    },
                                )
                            }
                            CommunityPolicyPicker("Who can post", comm.whoCanPost, !busy) { policy ->
                                patch("Update posting access", CommunityPatch(whoCanPost = policy), optimistic = { it.copy(whoCanPost = policy) })
                            }
                            CommunityPolicyPicker("Who can invite", comm.whoCanInvite, !busy) { policy ->
                                patch("Update invite access", CommunityPatch(whoCanInvite = policy), optimistic = { it.copy(whoCanInvite = policy) })
                            }
                        }
                    }
                    item {
                        CommunitySection("Members & roles") {
                            Text("Promote, demote, invite and remove members.", style = MaterialTheme.typography.bodySmall)
                            PrimaryButton("Members", enabled = !busy, icon = "users-group-rounded") { vm.go(Screen.CommunityMembers(id)) }
                        }
                    }
                    if (comm.myRole == "owner") {
                        item {
                            CommunitySection("Danger zone") {
                                Text("Deleting the community also removes all its posts.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(
                                    enabled = !busy, onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    AssetSvgIcon("trash-bin-trash", contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Delete community")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommunitySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityPolicyPicker(title: String, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Text(title, fontWeight = FontWeight.Medium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommunityPermissions.accessPolicies.forEach { policy ->
            FilterChip(
                selected = selected == policy, enabled = enabled,
                onClick = { if (selected != policy) onSelect(policy) },
                label = { Text(policy.replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}
