package org.uxaoro.app.ui.social

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Cache
import org.uxaoro.app.data.CommunityOut
import org.uxaoro.app.data.CommunityPermissions
import org.uxaoro.app.data.MemberIn
import org.uxaoro.app.data.RoleInCommunity
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.AssetSvgIcon
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.ErrorText
import org.uxaoro.app.ui.components.GlassMenu
import org.uxaoro.app.ui.components.GlassMenuItem
import org.uxaoro.app.ui.components.LoadingBox
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.UserRow
import org.uxaoro.app.ui.components.inkFieldColors
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.theme.BlurScaffold
import java.util.Locale

@Composable
fun CommunityMembersScreen(vm: AppVm, id: String) {
    var community by remember(id) { mutableStateOf<CommunityOut?>(null) }
    var members by remember(id) { mutableStateOf<List<UserPublic>>(emptyList()) }
    var loading by remember(id) { mutableStateOf(true) }
    var revision by remember(id) { mutableIntStateOf(0) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var pendingAction by remember(id) { mutableStateOf<String?>(null) }
    var pendingMemberId by remember(id) { mutableStateOf<String?>(null) }
    var inviteUsername by rememberSaveable(id) { mutableStateOf("") }
    var menuFor by remember(id) { mutableStateOf<String?>(null) }
    var roleFor by remember(id) { mutableStateOf<UserPublic?>(null) }
    var selectedRole by remember(id) { mutableStateOf("member") }
    var removeFor by remember(id) { mutableStateOf<UserPublic?>(null) }
    val busy = pendingAction != null
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, revision) {
        loading = true
        error = null
        try {
            val loaded = Api.service().getCommunity(id)
            val people = Api.service().communityMembers(id)
            community = loaded
            members = people
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = Api.errorMessage(e)
        } finally {
            loading = false
        }
    }

    fun changeRole(member: UserPublic, role: String) {
        val comm: CommunityOut = community ?: return
        if (pendingAction != null || !CommunityPermissions.canChangeRole(comm.myRole, vm.me?.id, member) ||
            role !in CommunityPermissions.assignableRoles(comm.myRole) || role == member.communityRole) return
        val before = members
        error = null
        pendingAction = "Change role"
        pendingMemberId = member.id
        scope.localFirst(
            vm, "Change role",
            apply = { members = members.map { if (it.id == member.id) it.copy(communityRole = role) else it } },
            undo = { members = before; pendingAction = null; pendingMemberId = null },
            send = { Api.service().setCommunityRole(id, member.id, RoleInCommunity(role)) },
            onSuccess = { pendingAction = null; pendingMemberId = null },
            onFailure = { error = it },
        )
    }

    fun removeMember(member: UserPublic) {
        val comm: CommunityOut = community ?: return
        if (pendingAction != null || !CommunityPermissions.canRemoveMember(comm.myRole, vm.me?.id, member)) return
        val before = members
        error = null
        pendingAction = "Remove member"
        scope.localFirst(
            vm, "Remove member",
            apply = {
                members = members.filterNot { it.id == member.id }
                community = comm.copy(membersCount = (comm.membersCount - 1).coerceAtLeast(0))
            },
            undo = { members = before; community = comm; pendingAction = null },
            send = { Api.service().kickCommunityMember(id, member.id) },
            onSuccess = {
                pendingAction = null
                community?.let { Cache.updateCommunity(it) }
            },
            onFailure = { error = it },
        )
    }

    fun inviteMember() {
        val comm: CommunityOut = community ?: return
        if (pendingAction != null || !CommunityPermissions.canInvite(comm)) return
        val username = inviteUsername.trim().trimStart('@').lowercase(Locale.ROOT)
        if (!Regex("^[a-z0-9_]{3,32}$").matches(username)) {
            error = "Enter a username with 3–32 letters, numbers or underscores."
            return
        }
        error = null
        pendingAction = "Find member"
        scope.launch {
            try {
                val user = Api.service().lookupUser(username)
                if (members.any { it.id == user.id }) {
                    error = "@${user.username} is already a member."
                    pendingAction = null
                    return@launch
                }
                val before = members
                pendingAction = "Invite member"
                pendingMemberId = user.id
                // The lookup resolves the username first; then the new member is
                // shown immediately, with rollback if their privacy disallows it.
                scope.localFirst(
                    vm, "Invite member",
                    apply = {
                        members = members + user.copy(communityRole = "member")
                        community = comm.copy(membersCount = comm.membersCount + 1)
                    },
                    undo = { members = before; community = comm; pendingAction = null; pendingMemberId = null },
                    send = { community = Api.service().inviteCommunityMember(id, MemberIn(user.id)) },
                    onSuccess = {
                        pendingAction = null
                        pendingMemberId = null
                        inviteUsername = ""
                        community?.let { Cache.updateCommunity(it) }
                        vm.toast("Member invited")
                    },
                    onFailure = { error = it },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingAction = null
                error = Api.errorMessage(e)
                android.util.Log.e("uxaoro", "Member lookup failed: $error", e)
            }
        }
    }

    fun back() {
        if (busy) vm.toast("Please wait for the member update to finish") else vm.back()
    }
    BackHandler(enabled = busy) { back() }

    val comm = community
    roleFor?.let { member ->
        val roles = CommunityPermissions.assignableRoles(comm?.myRole)
        AlertDialog(
            onDismissRequest = { roleFor = null },
            title = { Text("Role for @${member.username}") },
            text = {
                Column(Modifier.selectableGroup()) {
                    Text("Current role: ${member.communityRole ?: "member"}")
                    if (comm?.myRole != "owner") {
                        Text("Only the owner can grant the admin role.", style = MaterialTheme.typography.bodySmall)
                    }
                    roles.forEach { role ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = selectedRole == role, role = Role.RadioButton,
                                onClick = { selectedRole = role },
                            ).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedRole == role, onClick = null)
                            Text(role.replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && selectedRole in roles && selectedRole != member.communityRole,
                    onClick = { roleFor = null; changeRole(member, selectedRole) },
                ) { Text("Set role") }
            },
            dismissButton = { TextButton(onClick = { roleFor = null }) { Text("Cancel") } },
        )
    }
    removeFor?.let { member ->
        AlertDialog(
            onDismissRequest = { removeFor = null },
            title = { Text("Remove member?") },
            text = { Text("Remove @${member.username} from ${comm?.name ?: "this community"}?") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { removeFor = null; removeMember(member) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removeFor = null }) { Text("Cancel") } },
        )
    }

    BlurScaffold(
        topBar = {
            DATopBar("Members & roles", onBack = { back() }, subtitle = comm?.name, actions = {
                if (CommunityPermissions.canManage(comm?.myRole)) {
                    TextButton(enabled = !busy, onClick = { vm.go(Screen.CommunitySettings(id)) }) { Text("Manage") }
                }
            })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(pendingAction.orEmpty() + "…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            ErrorText(error)
            when {
                loading -> LoadingBox()
                comm == null -> TextButton(onClick = { revision++ }, modifier = Modifier.padding(16.dp)) { Text("Retry") }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (CommunityPermissions.canInvite(comm)) {
                        item {
                            Box(Modifier.padding(16.dp)) {
                                CommunitySection("Invite a member") {
                                    OutlinedTextField(
                                        value = inviteUsername, onValueChange = { inviteUsername = it }, label = { Text("Username (@name)") },
                                        enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = inkFieldColors(),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false),
                                    )
                                    PrimaryButton("Invite", enabled = !busy && inviteUsername.isNotBlank(), icon = "user-plus") { inviteMember() }
                                }
                            }
                        }
                    }
                    item {
                        Text("${comm.membersCount} members", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (members.isEmpty()) item { EmptyBox("No members") }
                    items(members, key = { it.id }) { member ->
                        val canChange = CommunityPermissions.canChangeRole(comm.myRole, vm.me?.id, member)
                        val canRemove = CommunityPermissions.canRemoveMember(comm.myRole, vm.me?.id, member)
                        UserRow(
                            member,
                            subtitle = "@${member.username} · ${member.communityRole ?: "member"}" + if (pendingMemberId == member.id) " · sending…" else "",
                            onClick = { if (!busy) vm.go(Screen.User(member.id)) },
                            trailing = {
                                if (canChange || canRemove) {
                                    Box {
                                        IconButton(enabled = !busy, onClick = { menuFor = member.id }) {
                                            AssetSvgIcon("menu-dots", contentDescription = "Manage @${member.username}", size = 22.dp, tint = LocalContentColor.current)
                                        }
                                        GlassMenu(expanded = menuFor == member.id, onDismissRequest = { menuFor = null }) {
                                            if (canChange) {
                                                GlassMenuItem(text = "Change role", leadingIcon = "user-id", onClick = {
                                                    menuFor = null
                                                    selectedRole = member.communityRole ?: "member"
                                                    roleFor = member
                                                })
                                            }
                                            if (canRemove) {
                                                GlassMenuItem(text = "Remove member", leadingIcon = "user-cross", danger = true, onClick = {
                                                    menuFor = null
                                                    removeFor = member
                                                })
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
