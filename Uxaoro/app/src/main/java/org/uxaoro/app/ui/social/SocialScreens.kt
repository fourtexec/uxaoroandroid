package org.uxaoro.app.ui.social

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import org.uxaoro.app.ui.theme.BlurScaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.ChatCreate
import org.uxaoro.app.data.Cache
import org.uxaoro.app.data.CommunityCreate
import org.uxaoro.app.data.UsernameIn
import org.uxaoro.app.data.CommunityOut
import org.uxaoro.app.data.CommunityPermissions
import org.uxaoro.app.data.NotificationOut
import org.uxaoro.app.data.PostOut
import org.uxaoro.app.data.SearchOut
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.data.UserUpdate
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.localId
import org.uxaoro.app.ui.without
import org.uxaoro.app.ui.withRow
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.Avatar
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.ErrorText
import org.uxaoro.app.ui.components.LoadingBox
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.PrimaryField
import org.uxaoro.app.ui.components.UserRow
import org.uxaoro.app.util.shareText
import org.uxaoro.app.util.uxaoroWebLink
import org.uxaoro.app.ui.components.inkButtonColors
import org.uxaoro.app.ui.feed.PostItem
import org.uxaoro.app.ui.premium.PremiumBadges
import org.uxaoro.app.ui.premium.PremiumName
import org.uxaoro.app.ui.premium.premiumProfileBrush
import org.uxaoro.app.ui.premium.safePremiumColor
import org.uxaoro.app.ui.premium.visibleCustomization
import androidx.compose.ui.graphics.Color
import org.uxaoro.app.util.uriPart
import org.uxaoro.app.ui.components.inkFieldColors
import org.uxaoro.app.ui.motion.popIn
import org.uxaoro.app.ui.motion.zoomPop
import org.uxaoro.app.ui.motion.sheenSweep
import org.uxaoro.app.ui.motion.staggerIn
import org.uxaoro.app.ui.components.AssetSvgIcon

@Composable
fun FriendsScreen(vm: AppVm) {
    var tab by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val titles = listOf("Friends", "Requests", "Suggested")

    fun load() {
        scope.launch {
            loading = true
            list = runCatching {
                when (tab) {
                    0 -> Api.service().friends().also { Cache.saveFriends(it) }
                    1 -> Api.service().friendRequests()
                    else -> Api.service().suggestions()
                }
            }.getOrElse { if (tab == 0) Cache.loadFriends() else emptyList() }
            loading = false
        }
    }
    LaunchedEffect(tab) { load() }

    var addName by remember { mutableStateOf("") }
    BlurScaffold(topBar = { DATopBar("Friends") }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addName,
                    onValueChange = { addName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add by username") },
                    shape = RoundedCornerShape(16.dp),
                    colors = inkFieldColors(),
                    singleLine = true,
                )
                TextButton(onClick = {
                    val n = addName.trim().trimStart('@')
                    if (n.isBlank()) return@TextButton
                    // Local-first: a pending row appears immediately (the server
                    // may replace it with the real account or take it away).
                    val lid = localId("fr")
                    val ghost = UserPublic(id = lid, username = n, displayName = n)
                    scope.localFirst(
                        vm,
                        "Send friend request",
                        apply = {
                            list = list + ghost
                            addName = ""
                            vm.markPending(lid)
                        },
                        undo = {
                            list = list.filter { it.id != lid }
                            addName = n
                            vm.clearPending(lid)
                        },
                        send = { Api.service().friendRequestByUsername(UsernameIn(n)) },
                        onSuccess = { load() },
                    )
                }) { Text("Add") }
            }
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, modifier = Modifier.height(36.dp)) {
                titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.height(36.dp), text = { Text(t, fontSize = 12.sp) }) }
            }
            LazyColumn(Modifier.fillMaxSize()) {
            if (loading) {
                item { LoadingBox() }
            } else if (list.isEmpty()) {
                item { EmptyBox("Nobody here yet") }
            } else {
                itemsIndexed(list, key = { _, u -> u.id }) { i, u ->
                    Box(Modifier.staggerIn(i)) {
                    UserRow(u, onClick = { vm.go(Screen.User(u.id)) }, trailing = {
                        when (tab) {
                            1 -> Row {
                                TextButton(onClick = {
                                    // The request leaves the list on the tap.
                                    scope.localFirst(
                                        vm,
                                        "Accept friend request",
                                        apply = {
                                            list = list.without(u.id) { it.id }
                                        },
                                        undo = { list = list.withRow(u) },
                                        send = { Api.service().friendAccept(u.id) },
                                        onSuccess = { load() },
                                    )
                                }) { Text("Accept") }
                                TextButton(onClick = {
                                    scope.localFirst(
                                        vm,
                                        "Decline friend request",
                                        apply = {
                                            list = list.without(u.id) { it.id }
                                        },
                                        undo = { list = list.withRow(u) },
                                        send = { Api.service().friendDecline(u.id) },
                                        onSuccess = { load() },
                                    )
                                }) { Text("Decline") }
                            }
                            2 -> TextButton(onClick = {
                                scope.localFirst(
                                    vm,
                                    "Friend request",
                                    apply = {
                                        list = list.map { if (it.id == u.id) u.copy(friendStatus = "pending_out") else it }
                                    },
                                    undo = { list = list.map { if (it.id == u.id) u else it } },
                                    send = { Api.service().friendRequest(u.id) },
                                    onSuccess = { load() },
                                )
                            }) { Text("Add") }
                            else -> {}
                        }
                    })
                    }
                }
            }
            }
        }
    }
}

@Composable
fun CommunitiesScreen(vm: AppVm) {
    var q by remember { mutableStateOf("") }
    var mine by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<CommunityOut>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch {
            items = runCatching { Api.service().communities(q, mine).items }
                .onSuccess { Cache.saveCommunities(it) }
                .getOrElse { Cache.loadCommunities() }
        }
    }
    LaunchedEffect(mine) { load() }

    BlurScaffold(
        topBar = { DATopBar("Communities") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.go(Screen.CreateCommunity) },
                modifier = Modifier.popIn(),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Search communities") },
                shape = RoundedCornerShape(16.dp),
                colors = inkFieldColors(),
                singleLine = true,
            )
            Row(Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { mine = false; }) { Text(if (!mine) "All ▾" else "All") }
                TextButton(onClick = { mine = true }) { Text(if (mine) "My communities ▾" else "My communities") }
                TextButton(onClick = { load() }) { Text("Search") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (items.isEmpty()) {
                    item { EmptyBox("No communities") }
                } else {
                    itemsIndexed(items, key = { _, c -> c.id }) { i, c ->
                        Box(Modifier.staggerIn(i)) {
                            UserRow(
                                user = UserPublic(id = c.id, username = c.username.ifBlank { c.slug }, displayName = c.name, avatarUrl = c.avatarUrl),
                                subtitle = "@${c.username.ifBlank { c.slug }} · ${c.membersCount} members",
                                onClick = { vm.go(Screen.Community(c.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateCommunityScreen(vm: AppVm) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var quota by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm.me?.premium) {
        quota = runCatching { Api.service().premiumStatus().communitiesLimit }.getOrNull()
    }
    BlurScaffold(topBar = { DATopBar("New community", onBack = { vm.back() }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Community limit: ${quota ?: if (vm.me?.premium == true) 4000 else 40} owned", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.go(Screen.Premium) }) {
              AssetSvgIcon("crown", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
              Spacer(Modifier.width(6.dp))
              Text("✦ View Premium limits")
            }
            PrimaryField(name, { name = it }, "Name (can match another community)")
            Spacer(Modifier.height(8.dp))
            PrimaryField(username, { username = it }, "Username (unique, like photos_club)")
            Spacer(Modifier.height(8.dp))
            PrimaryField(desc, { desc = it }, "Description", singleLine = false)
            ErrorText(err)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Create", enabled = name.length >= 2 && username.length >= 3, icon = "add-circle") {
                // The community opens straight away; the create call happens
                // behind it. A refusal brings this form back with the fields
                // still filled in.
                val body = CommunityCreate(name = name.trim(), username = username.trim().trimStart('@'), description = desc)
                vm.pendingCommunity = CommunityOut(
                    id = localId("c"), name = body.name, username = body.username, description = body.description,
                    membersCount = 1, isMember = true, myRole = "owner",
                )
                vm.replaceWait(Screen.Community(vm.pendingCommunity!!.id))
                scope.launch {
                    try {
                        val c = Api.service().createCommunity(body)
                        vm.pendingCommunity = null
                        vm.replace(Screen.Community(c.id))
                    } catch (t: Throwable) {
                        vm.pendingCommunity = null
                        android.util.Log.e("uxaoro", "Create community failed: ${Api.errorMessage(t)}", t)
                        vm.toast("Create community failed — nothing was created.")
                        vm.go(Screen.CreateCommunity)
                    }
                }
            }
        }
    }
}

private fun AppVm.replaceWait(s: Screen) {
    back()
    go(s)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityDetailScreen(vm: AppVm, id: String) {
    // Never issue management requests for an optimistic, not-yet-created id.
    var c by remember(id) { mutableStateOf(vm.pendingCommunity?.takeIf { it.id == id }) }
    var posts by remember(id) { mutableStateOf<List<PostOut>>(emptyList()) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var membershipBusy by remember(id) { mutableStateOf(false) }
    val pending = vm.pendingCommunity?.id == id
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun load() {
        if (vm.pendingCommunity?.id == id) return
        scope.launch {
            error = null
            try {
                val loaded = Api.service().getCommunity(id)
                c = loaded
                posts = if (loaded.isClosed && !loaded.isMember) emptyList()
                    else Api.service().communityPosts(id).items
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = Api.errorMessage(e)
            }
        }
    }
    LaunchedEffect(id) { load() }
    val comm = c
    BlurScaffold(
        topBar = {
            DATopBar(comm?.name ?: "Community", onBack = { vm.back() }, actions = {
                if (comm != null && !pending) {
                    IconButton(onClick = {
                        val handle = comm.username.ifBlank { comm.slug }
                        val link = if (handle.isNotBlank()) uxaoroWebLink("/community?u=$handle")
                        else uxaoroWebLink("/community?id=${comm.id}")
                        shareText(ctx, comm.name, "Join my community on Uxaoro: $link")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share community")
                    }
                }
            })
        },
        floatingActionButton = {
            if (comm != null && !pending && !membershipBusy && CommunityPermissions.canPost(comm)) {
                FloatingActionButton(
                    onClick = { vm.go(Screen.CreatePost(communityId = id)) },
                    modifier = Modifier.popIn(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Post in community")
                }
            }
        },
    ) { pad ->
        if (comm == null) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (error == null) LoadingBox()
                else Column(Modifier.padding(16.dp)) {
                    ErrorText(error)
                    TextButton(onClick = { load() }) { Text("Retry") }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                CoverHeader(comm.coverUrl, comm.avatarUrl, comm.name, comm.description, "@${comm.username.ifBlank { comm.slug }} · ${comm.membersCount} members · you: ${comm.myRole ?: "guest"}")
                if (comm.isClosed) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssetSvgIcon("lock-keyhole", contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Closed community", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pending) Text("Creating community…", modifier = Modifier.padding(16.dp))
                ErrorText(error)
                if (error != null) TextButton(onClick = { load() }, enabled = !membershipBusy) { Text("Retry") }
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comm.isMember && comm.myRole != "owner") {
                        OutlinedButton(enabled = !pending && !membershipBusy, onClick = {
                            membershipBusy = true
                            val beforePosts = posts
                            scope.localFirst(
                                vm, "Leave community",
                                apply = {
                                    c = comm.copy(isMember = false, myRole = null, membersCount = (comm.membersCount - 1).coerceAtLeast(0))
                                    if (comm.isClosed) posts = emptyList()
                                },
                                undo = { c = comm; posts = beforePosts; membershipBusy = false },
                                send = { Api.service().leaveCommunity(id) },
                                onSuccess = { membershipBusy = false; load() },
                                onFailure = { error = it },
                            )
                        }) { Text("Leave") }
                    } else if (!comm.isMember) {
                        Button(
                            enabled = !pending && !membershipBusy && !comm.isClosed,
                            onClick = {
                                membershipBusy = true
                                scope.localFirst(
                                    vm, "Join community",
                                    apply = { c = comm.copy(isMember = true, myRole = "member", membersCount = comm.membersCount + 1) },
                                    undo = { c = comm; membershipBusy = false },
                                    send = { c = Api.service().joinCommunity(id) },
                                    onSuccess = { membershipBusy = false; load() },
                                    onFailure = { error = it },
                                )
                            },
                            colors = inkButtonColors(),
                        ) { Text("Join") }
                    }
                    OutlinedButton(
                        enabled = !pending && !membershipBusy && (!comm.isClosed || comm.isMember),
                        onClick = { vm.go(Screen.CommunityMembers(id)) },
                    ) { Text("Members") }
                    if (CommunityPermissions.canManage(comm.myRole)) {
                        OutlinedButton(enabled = !pending && !membershipBusy, onClick = { vm.go(Screen.CommunitySettings(id)) }) {
                            AssetSvgIcon("settings-minimalistic", contentDescription = null, size = 18.dp, tint = LocalContentColor.current)
                            Spacer(Modifier.width(8.dp))
                            Text("Manage")
                        }
                    }
                }
                if (comm.isClosed && !comm.isMember) {
                    Text(
                        "Posts and members are hidden. Ask a community admin for an invitation.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(posts, key = { _, p -> p.id }) { i, p ->
                Box(Modifier.staggerIn(i)) { PostItem(vm, p) { } }
            }
        }
    }
}

@Composable
fun CoverHeader(cover: String, avatar: String, title: String, subtitle: String, meta: String, user: UserPublic? = null, viewsCount: Int? = null) {
    val c = user?.let(::visibleCustomization)
    val brush = c?.profileTheme?.let(::premiumProfileBrush)
    val ink = if (brush != null) Color.White else MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxWidth().then(if (brush != null) Modifier.background(brush) else Modifier)) {
        Box(
            Modifier.fillMaxWidth().height(140.dp)
                .then(if (brush == null) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
                .sheenSweep(3200, strength = 0.10f),
        ) {
            if (cover.isNotBlank()) {
                AsyncImage(
                    model = Api.mediaUrl(cover),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.offset(y = (-24).dp).zoomPop()) {
                Avatar(avatar, title, 80)
            }
            Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                if (user != null) {
                    PremiumName(user, fontSize = 20.sp, defaultColor = ink)
                    PremiumBadges(user)
                } else {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meta, color = if (brush != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (viewsCount != null) {
                        Spacer(Modifier.width(6.dp))
                        org.uxaoro.app.ui.components.AssetSvgIcon("eye", contentDescription = "Profile views", size = 14.dp, tint = if (brush != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" $viewsCount", color = if (brush != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
        if (subtitle.isNotBlank()) Text(
            subtitle,
            color = c?.bioColor?.let(::safePremiumColor) ?: ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun ProfileScreen(vm: AppVm) {
    val me = vm.me
    if (me == null) return
    UserProfileBody(vm, me.id, isSelf = true, showBack = false)
}

@Composable
fun UserScreen(vm: AppVm, id: String) {
    UserProfileBody(vm, id, isSelf = id == vm.me?.id, showBack = true)
}

@Composable
private fun UserProfileBody(vm: AppVm, id: String, isSelf: Boolean, showBack: Boolean) {
    var u by remember { mutableStateOf<UserPublic?>(null) }
    var posts by remember { mutableStateOf<List<PostOut>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun load() {
        scope.launch {
            if (isSelf) {
                runCatching { Api.service().me() }.onSuccess {
                    vm.me = it
                    org.uxaoro.app.data.Prefs.user = it
                }
            }
            u = runCatching { Api.service().getUser(id) }.getOrNull()
            posts = runCatching { Api.service().userPosts(id).items }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(id) { load() }
    // /auth/me is refreshed after a purchase or customization; don't leave
    // a previously fetched self-profile showing stale cosmetics on Back.
    val user = if (isSelf) vm.me?.let { me ->
        u?.copy(
            displayName = me.displayName, bio = me.bio, status = me.status,
            city = me.city, avatarUrl = me.avatarUrl, coverUrl = me.coverUrl,
            premium = me.premium, customization = me.customization, balanceUx = me.balanceUx,
        ) ?: me.asPublic()
    } else u
    BlurScaffold(
        topBar = {
            DATopBar(if (isSelf) "Profile" else (user?.displayName ?: "User"), onBack = if (showBack) ({ vm.back() }) else null, actions = {
                if (isSelf) {
                    if (vm.me?.isStaff == true) {
                        IconButton(onClick = { vm.go(Screen.Admin) }) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Control panel")
                        }
                    }
                    IconButton(onClick = { vm.go(Screen.EditProfile) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
                IconButton(onClick = {
                    val handle = user?.username.orEmpty()
                    val link = if (handle.isNotBlank()) uxaoroWebLink("/user?u=$handle")
                    else uxaoroWebLink("/user?id=$id")
                    val pitch = if (isSelf) "Find me on Uxaoro: $link" else "Check out this Uxaoro profile: $link"
                    shareText(ctx, user?.displayName ?: "Uxaoro", pitch)
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share profile")
                }
            })
        },
        floatingActionButton = {
            if (isSelf) {
                FloatingActionButton(
                    onClick = { vm.go(Screen.CreatePost()) },
                    modifier = Modifier.popIn(),
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        },
    ) { pad ->
        if (user == null) Box(Modifier.padding(pad).fillMaxSize()) { LoadingBox() }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                CoverHeader(
                    user.coverUrl,
                    user.avatarUrl,
                    user.displayName,
                    user.bio,
                    buildString {
                        append("@${user.username}")
                        if (user.pronouns.isNotBlank()) append(" (${user.pronouns})")
                        append(" · ${user.friendsCount} friends")
                        // Live presence (socket) wins over the fetched profile.
                        val presence = vm.presenceLine(user)
                        if (presence.isNotBlank()) append(" · $presence")
                    },
                    user = user,
                    viewsCount = user.profileViewsCount,
                )
                if (user.status.isNotBlank() || user.statusEmoji.isNotBlank()) {
                    val stLine = listOf(user.statusEmoji, user.status).filter { it.isNotBlank() }.joinToString(" ")
                    Text("“$stLine”", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary)
                }
                if (user.website.isNotBlank()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        org.uxaoro.app.ui.components.AssetSvgIcon("link", contentDescription = "Website", size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(user.website, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (!isSelf) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // The friend button changes on the tap; a refusal puts
                        // the old label back and says why.
                        fun setStatus(next: String?) {
                            val previous = user.friendStatus
                            val current = u
                            scope.localFirst(
                                vm,
                                "Friend",
                                apply = {
                                    // `user` is derived (self profile mirrors vm.me),
                                    // so the optimistic edit lands on the backing state.
                                    val base = u
                                    if (base != null) u = base.copy(friendStatus = next)
                                },
                                undo = { u = current },
                                send = {
                                    when (next) {
                                        null -> Api.service().unfriend(id)
                                        "friends" -> Api.service().friendAccept(id)
                                        else -> Api.service().friendRequest(id)
                                    }
                                },
                                onSuccess = { load() },
                            )
                        }
                        when (user.friendStatus) {
                            "friends" -> OutlinedButton(onClick = { setStatus(null) }) { Text("Friends") }
                            "pending_out" -> OutlinedButton(onClick = {}) { Text("Request sent") }
                            "pending_in" -> Button(
                                onClick = { setStatus("friends") },
                                colors = inkButtonColors(),
                            ) { Text("Accept request") }
                            else -> Button(
                                onClick = { setStatus("pending_out") },
                                colors = inkButtonColors(),
                            ) { Text("Add friend") }
                        }
                        OutlinedButton(onClick = {
                            scope.localFirst(
                                vm,
                                "Open chat",
                                apply = {},
                                send = {
                                    val chat = Api.service().createChat(ChatCreate(listOf(id)))
                                    vm.go(Screen.Chat(chat.id))
                                },
                            )
                        }) { Text("Message") }
                        OutlinedButton(onClick = { vm.go(Screen.CreatePost(wallUserId = id)) }) {
                            Text("Wall")
                        }
                        OutlinedButton(onClick = { vm.go(Screen.Wallet) }) {
                            Text("Gift UX")
                        }
                    }
                } else {
                    Text(
                        "Role: ${vm.me?.role}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        if (vm.me?.isStaff == true) {
                            TextButton(onClick = { vm.go(Screen.Admin) }) { Text("Control panel") }
                        }
                    }
                }
            }
            itemsIndexed(posts, key = { _, p -> p.id }) { i, p ->
                Box(Modifier.staggerIn(i)) { PostItem(vm, p) {} }
            }
        }
    }
}

@Composable
fun EditProfileScreen(vm: AppVm) {
    val me = vm.me ?: return
    var name by remember { mutableStateOf(me.displayName) }
    var bio by remember { mutableStateOf(me.bio) }
    var status by remember { mutableStateOf(me.status) }
    var statusEmoji by remember { mutableStateOf(me.statusEmoji) }
    var pronouns by remember { mutableStateOf(me.pronouns) }
    var website by remember { mutableStateOf(me.website) }
    var city by remember { mutableStateOf(me.city) }
    var err by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun doUpload(target: String, uri: Uri) {
        scope.launch {
            // Built-in editor auto-opens for avatars/covers (Telegram-style).
            // For simplicity the picker below already routes through the editor.
            runCatching {
                if (target == "avatar") Api.service().uploadAvatar(uriPart(ctx, uri, "file"))
                else Api.service().uploadCover(uriPart(ctx, uri, "file"))
            }
            runCatching { vm.me = Api.service().me(); org.uxaoro.app.data.Prefs.user = vm.me }
        }
    }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val avatarPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            editTarget = "avatar"
            pendingUri = uri
        }
    }
    val coverPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            editTarget = "cover"
            pendingUri = uri
        }
    }
    pendingUri?.let { uri ->
        org.uxaoro.app.ui.media.PhotoEditorDialog(source = uri, title = "Edit photo") { edited ->
            val target = editTarget
            if (edited != null && target != null) {
                try {
                    val f = java.io.File(ctx.cacheDir, "avatar-${System.currentTimeMillis()}.jpg")
                    f.writeBytes(edited.bytes)
                    val eu = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                    doUpload(target, eu)
                } catch (_: Exception) {
                    doUpload(target, uri)
                }
            }
            pendingUri = null
            editTarget = null
        }
    }
    BlurScaffold(topBar = { DATopBar("Edit profile", onBack = { vm.back() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            TextButton(onClick = { avatarPick.launch("image/*") }) { Text("Change avatar") }
            TextButton(onClick = { coverPick.launch("image/*") }) { Text("Change cover") }
            PrimaryField(name, { name = it }, "Display name")
            Spacer(Modifier.height(8.dp))
            PrimaryField(statusEmoji, { statusEmoji = it }, "Status emoji (e.g. 🚀)")
            Spacer(Modifier.height(8.dp))
            PrimaryField(pronouns, { pronouns = it }, "Pronouns (e.g. they/them)")
            Spacer(Modifier.height(8.dp))
            PrimaryField(status, { status = it }, "Status")
            Spacer(Modifier.height(8.dp))
            PrimaryField(city, { city = it }, "City")
            Spacer(Modifier.height(8.dp))
            PrimaryField(website, { website = it }, "Website (https://...)")
            Spacer(Modifier.height(8.dp))
            PrimaryField(bio, { bio = it }, "Bio", singleLine = false)
            ErrorText(err)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Save", icon = "check-circle") {
                // Local-first: the new name/bio is on the profile (and in the
                // signed-in account) before the request is answered. A refusal
                // brings the old profile back and keeps the form open.
                val body = UserUpdate(
                    displayName = name,
                    bio = bio,
                    status = status,
                    city = city,
                    statusEmoji = statusEmoji,
                    pronouns = pronouns,
                    website = website,
                )
                val before = vm.me
                scope.localFirst(
                    vm,
                    "Save profile",
                    send = {
                        val u = Api.service().updateMe(body)
                        vm.me = u
                        org.uxaoro.app.data.Prefs.user = u
                        vm.back()
                    },
                    apply = {
                        val cur = vm.me
                        if (cur != null) {
                            vm.me = cur.copy(
                                displayName = name,
                                bio = bio,
                                status = status,
                                city = city,
                                statusEmoji = statusEmoji,
                                pronouns = pronouns,
                                website = website,
                            )
                            org.uxaoro.app.data.Prefs.user = vm.me
                        }
                    },
                    undo = {
                        vm.me = before
                        org.uxaoro.app.data.Prefs.user = before
                        err = "Not saved — your old profile is still in place."
                    },
                )
            }
        }
    }
}

@Composable
fun SearchScreen(vm: AppVm) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<SearchOut?>(null) }
    val scope = rememberCoroutineScope()
    BlurScaffold(topBar = { DATopBar("Search", onBack = { vm.back() }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                OutlinedTextField(
                    value = q,
                    onValueChange = {
                        q = it
                        if (it.length >= 2) scope.launch { res = runCatching { Api.service().search(it) }.getOrNull() }
                    },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    placeholder = { Text("People and communities") },
                    shape = RoundedCornerShape(16.dp),
                        colors = inkFieldColors(),
                )
            }
            res?.users?.let { users ->
                item { Text("People", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(16.dp)) }
                items(users, key = { it.id }) { u ->
                    UserRow(u, onClick = { vm.go(Screen.User(u.id)) })
                }
            }
            res?.communities?.let { comms ->
                item { Text("Communities", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(16.dp)) }
                items(comms, key = { it.id }) { c ->
                    UserRow(
                        UserPublic(id = c.id, username = c.slug, displayName = c.name, avatarUrl = c.avatarUrl),
                        subtitle = "${c.membersCount} members",
                        onClick = { vm.go(Screen.Community(c.id)) },
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: AppVm) {
    var items by remember { mutableStateOf<List<NotificationOut>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // Opening the list clears the badge at once; the server catches up in
        // the background and the unread count only comes back if it refuses.
        vm.noteUnread = 0
        val n = runCatching { Api.service().notifications() }.getOrNull()
        items = n?.items.orEmpty()
        runCatching { Api.service().readAllNotifications() }
            .onFailure { vm.refreshBadges() }
    }
    BlurScaffold(topBar = { DATopBar("Notifications", onBack = { vm.back() }) }) { pad ->
        if (items.isEmpty()) Box(Modifier.padding(pad).fillMaxSize()) { EmptyBox("No notifications") }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            itemsIndexed(items, key = { _, n -> n.id }) { i, n ->
                Column(Modifier.fillMaxWidth().padding(16.dp).staggerIn(i)) {
                    Text(n.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(n.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
