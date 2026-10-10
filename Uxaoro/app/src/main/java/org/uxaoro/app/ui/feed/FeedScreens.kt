package org.uxaoro.app.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import org.uxaoro.app.ui.theme.BlurScaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Cache
import org.uxaoro.app.data.CommentCreate
import org.uxaoro.app.data.CommentOut
import org.uxaoro.app.data.PostOut
import org.uxaoro.app.data.ReactIn
import org.uxaoro.app.data.ReportIn
import org.uxaoro.app.data.StoryGroup
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.sendToSavedChat
import org.uxaoro.app.ui.localId
import org.uxaoro.app.ui.reactLocally
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.Avatar
import org.uxaoro.app.ui.components.AssetSvgIcon
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.ErrorText
import org.uxaoro.app.ui.components.LoadingBox
import org.uxaoro.app.ui.components.PostCard
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.UserRow
import org.uxaoro.app.util.textBody
import org.uxaoro.app.util.uriPart
import org.uxaoro.app.ui.components.inkFieldColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalContentColor
import org.uxaoro.app.data.PollVoteIn
import org.uxaoro.app.data.PostTipIn
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.ui.motion.popIn
import org.uxaoro.app.ui.motion.staggerIn
import java.time.ZonedDateTime

@Composable
fun FeedScreen(vm: AppVm) {
    var items by remember { mutableStateOf(Cache.loadFeed()) }
    var stories by remember { mutableStateOf(Cache.loadStories().items) }
    var motd by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(items.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            refreshing = true
            loading = items.isEmpty()
            try {
                val page = Api.service().feed()
                items = page.items
                Cache.saveFeed(page.items)
                // A published post is no longer a draft once the server lists it.
                val ids = page.items.map { it.id }.toSet()
                vm.draftPosts.filter { ids.contains(it.id) }.forEach { vm.removeDraftPost(it.id) }
                val st = runCatching { Api.service().stories() }.getOrNull()
                if (st != null) {
                    Cache.saveStories(st)
                    stories = st.items
                }
                motd = runCatching { Api.service().version().motd }.getOrNull().orEmpty()
                err = null
            } catch (t: Throwable) {
                if (items.isEmpty()) items = Cache.loadFeed()
                if (stories.isEmpty()) stories = Cache.loadStories().items
                err = if (items.isEmpty()) Api.errorMessage(t) else "Offline — showing saved feed"
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
    LaunchedEffect(Unit) { reload() }
    // Drafts (posts still uploading) always sit on top of the server page.
    val withDrafts = remember(items, vm.draftPosts.size) { vm.draftPosts + items }

    val greeting = when (ZonedDateTime.now().hour) {
        in 0..6 -> "Can't sleep?"
        in 7..10 -> "Good morning"
        in 11..16 -> "Good afternoon"
        else -> "Good evening"
    }
    BlurScaffold(
        topBar = {
            DATopBar(greeting, actions = {
                IconButton(onClick = { reload() }) {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        AssetSvgIcon("refresh", contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                IconButton(onClick = { vm.go(Screen.Search) }) {
                    AssetSvgIcon("magnifer", contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { vm.go(Screen.Notifications); vm.noteUnread = 0 }) {
                    BadgedBox(badge = {
                        if (vm.noteUnread > 0) Badge { Text(if (vm.noteUnread > 99) "99+" else "${vm.noteUnread}") }
                    }) {
                        AssetSvgIcon("bell", contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.go(Screen.CreatePost()) },
                modifier = Modifier.popIn(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = org.uxaoro.app.ui.components.inkButtonContentColor(MaterialTheme.colorScheme.primaryContainer),
            ) {
                AssetSvgIcon("add-circle", contentDescription = "New post", tint = org.uxaoro.app.ui.components.inkButtonContentColor(MaterialTheme.colorScheme.primaryContainer))
            }
        },
    ) { pad ->
        when {
            loading -> BoxPad(pad) { LoadingBox() }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                // A story that is still uploading is drawn like any other ring,
                // with its own "sending…" marker.
                item {
                    StoryStrip(
                        vm,
                        vm.draftStories.map { draft ->
                            StoryGroup(user = vm.draftAuthor(), unseen = true, stories = listOf(draft))
                        } + stories,
                    )
                }
                if (motd.isNotBlank()) {
                    item {
                        Text(
                            motd,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (err != null) item { ErrorText(err) }
                if (withDrafts.isEmpty()) item { EmptyBox("Your feed is empty. Add friends, join communities, or post something.") }
                itemsIndexed(withDrafts, key = { _, p -> p.id }) { i, p ->
                    Box(Modifier.staggerIn(i)) {
                        PostItem(vm, p) { updated ->
                            items = items.map { if (it.id == updated.id) updated else it }
                            vm.draftPosts.replaceAll { if (it.id == updated.id) updated else it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryStrip(vm: AppVm, groups: List<StoryGroup>) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { vm.go(Screen.CreateStory) },
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AssetSvgIcon(
                        "add-circle",
                        contentDescription = "Create story",
                        size = 28.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text("Your story", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        itemsIndexed(groups, key = { _, g -> g.user?.id ?: g.stories.firstOrNull()?.id.orEmpty() }) { i, g ->
            val u = g.user ?: return@itemsIndexed
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.staggerIn(i, fromY = 10f).clickable { vm.go(Screen.StoryViewer(u.id)) },
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .border(
                            2.dp,
                            if (g.unseen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(u.avatarUrl, u.displayName, 56)
                }
                Text(
                    u.displayName.ifBlank { u.username }.take(10) +
                        if (g.stories.any { vm.isPending(it.id) }) " · sending…" else "",
                    maxLines = 1,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BoxPad(pad: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(pad)) { content() }
}

@Composable
fun PostItem(vm: AppVm, p: PostOut, onChange: (PostOut) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val me = vm.me
    var showTipDialog by remember { mutableStateOf(false) }
    var tipAmount by remember { mutableStateOf("5") }
    var tipNote by remember { mutableStateOf("") }
    // Cached posts can predate a purchase/edit. Use the fresh self account
    // for its cosmetics without changing the post's identity or interactions.
    val visiblePost = p.author?.let { author ->
        if (me != null && author.id == me.id) {
            p.copy(author = author.copy(premium = me.premium, customization = me.customization))
        } else p
    } ?: p
    val isMine = p.author?.id == me?.id
    if (showTipDialog) {
        AlertDialog(
            onDismissRequest = { showTipDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssetSvgIcon("wallet", contentDescription = null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Tip Post with UX")
                }
            },
            text = {
                Column {
                    Text(
                        "Reward @${p.author?.username ?: "author"} from your UX Wallet (${"%.2f".format(me?.balanceUx ?: 0.0)} UX available).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tipAmount,
                        onValueChange = { tipAmount = it },
                        placeholder = { Text("Amount in UX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tipNote,
                        onValueChange = { tipNote = it },
                        placeholder = { Text("Optional note (e.g. Great post!)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = tipAmount.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) {
                        vm.toast("Enter a positive UX amount")
                    } else {
                        showTipDialog = false
                        scope.localFirst(
                            vm,
                            "Send tip",
                            apply = {
                                vm.me = vm.me?.copy(balanceUx = ((vm.me?.balanceUx ?: 0.0) - amt).coerceAtLeast(0.0))
                                Prefs.user = vm.me
                            },
                            undo = {
                                vm.me = vm.me?.copy(balanceUx = ((vm.me?.balanceUx ?: 0.0) + amt).coerceAtLeast(0.0))
                                Prefs.user = vm.me
                            },
                            send = {
                                val res = Api.service().tipPost(p.id, PostTipIn(amt, tipNote.trim()))
                                vm.me = vm.me?.copy(balanceUx = res.balanceUx)
                                Prefs.user = vm.me
                                res.post?.let(onChange) ?: onChange(p.copy(tipsUx = res.tipsUx, tipsCount = res.tipsCount))
                            },
                            onSuccess = { vm.toast("Tipped $amt UX!") },
                        )
                    }
                }) { Text("Send Tip") }
            },
            dismissButton = {
                TextButton(onClick = { showTipDialog = false }) {
                  AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Cancel")
                }
            },
        )
    }
    PostCard(
        // The bookmark on the card is the local marker; the Saved Messages chat
        // holds the copy itself (see ui/SavedMessages.kt).
        post = visiblePost.copy(saved = vm.isSavedPost(p.id)),
        pending = vm.isPending(p.id),
        onOpen = { vm.go(Screen.Post(p.id)) },
        onAuthor = { p.author?.id?.let { vm.go(Screen.User(it)) } },
        onLike = {
            // Local-first: the heart and its counter move on the tap; if the
            // server refuses, both go back and a snack says why.
            val want = !p.liked
            scope.localFirst(
                vm,
                if (want) "Like" else "Unlike",
                apply = {
                    onChange(p.copy(liked = want, likesCount = (p.likesCount + if (want) 1 else -1).coerceAtLeast(0)))
                },
                undo = { onChange(p) },
                send = {
                    val r = Api.service().like(p.id)
                    onChange(p.copy(liked = r.liked, likesCount = r.likesCount))
                },
            )
        },
        onComment = { vm.go(Screen.Post(p.id)) },
        onRepost = {
            // The counter moves first, the toast follows the server.
            scope.localFirst(
                vm,
                "Repost",
                apply = {
                    onChange(p.copy(repostsCount = p.repostsCount + 1))
                },
                undo = { onChange(p) },
                send = { Api.service().repost(p.id) },
                onSuccess = { vm.toast("Reposted") },
            )
        },
        onReact = { emoji ->
            val mine = p.myReaction.orEmpty()
            scope.localFirst(
                vm,
                "React",
                apply = {
                    onChange(p.copy(
                        reactions = reactLocally(p.reactions, mine, emoji),
                        myReaction = if (mine == emoji) "" else emoji,
                    ))
                },
                undo = { onChange(p) },
                send = {
                    val r = Api.service().reactPost(p.id, ReactIn(emoji))
                    onChange(p.copy(reactions = r.reactions, myReaction = r.myReaction))
                },
            )
        },
        onVotePoll = { optId ->
            val poll = p.poll
            scope.localFirst(
                vm,
                "Vote",
                apply = {
                    if (poll != null) {
                        onChange(p.copy(poll = poll.copy(
                            options = poll.options.map { o ->
                                o.copy(votes = o.votes + if (o.id == optId) 1 else 0, voted = o.id == optId)
                            },
                            totalVoters = poll.totalVoters + 1,
                            totalVotes = poll.totalVotes + 1,
                        )))
                    }
                },
                undo = { onChange(p) },
                send = {
                    val res = Api.service().votePoll(p.id, PollVoteIn(optId))
                    res.post?.let(onChange) ?: onChange(p.copy(poll = res.poll))
                },
            )
        },
        onTip = if (!isMine) ({ showTipDialog = true }) else null,
        onPin = if (isMine || me?.isStaff == true) {
            {
                val want = !p.isPinned
                scope.localFirst(
                    vm,
                    if (want) "Pin" else "Unpin",
                    apply = {
                        onChange(p.copy(isPinned = want))
                    },
                    undo = { onChange(p) },
                    send = {
                        val res = Api.service().pinPost(p.id)
                        res.post?.let(onChange) ?: onChange(p.copy(isPinned = res.pinned))
                    },
                    onSuccess = { vm.toast(if (want) "Post pinned!" else "Post unpinned") },
                )
            }
        } else null,
        onHashtag = { tag -> vm.go(Screen.Explore(tag)) },
        onForward = if (vm.isSavedPost(p.id)) null else {
            {
                // Saved posts were retired server-side in favour of Saved
                // Messages, so forwarding *is* the save: an E2EE copy with a
                // `forward` reference lands in the chat with yourself. The
                // bookmark lands on the card with the tap, and is taken back
                // (with a toast) if the copy cannot be stored.
                scope.localFirst(
                    vm,
                    "Forward to Saved Messages",
                    apply = { vm.markSavedPost(p.id, true) },
                    undo = { vm.markSavedPost(p.id, false) },
                    send = {
                        val mediaUrl = p.imageUrl.ifBlank { p.videoUrl.ifBlank { p.audioUrl } }
                        val mediaType = p.mediaType.ifBlank {
                            when {
                                p.videoUrl.isNotBlank() -> "video"
                                p.audioUrl.isNotBlank() -> "audio"
                                mediaUrl.isNotBlank() -> "image"
                                else -> ""
                            }
                        }
                        sendToSavedChat(
                            ctx = ctx,
                            meId = me?.id.orEmpty(),
                            payload = org.uxaoro.app.util.packMsgPayload(
                                p.text,
                                // Post media is public and already uploaded, so
                                // unlike chat attachments it can be referenced.
                                mediaUrl = mediaUrl,
                                mediaType = mediaType,
                                forward = org.uxaoro.app.util.MsgForward(
                                    postId = p.id,
                                    author = p.author?.displayName.orEmpty().ifBlank { p.author?.username.orEmpty() },
                                    username = p.author?.username.orEmpty(),
                                    createdAt = p.createdAt.orEmpty(),
                                ),
                            ),
                        )
                    },
                    onSuccess = { vm.toast("\uD83D\uDD16 Forwarded to Saved Messages") },
                )
            }
        },
        onDelete = if (p.author?.id == me?.id || me?.isStaff == true) {
            {
                scope.localFirst(
                    vm,
                    "Delete post",
                    apply = {
                        onChange(p.copy(text = "[deleted]"))
                    },
                    undo = { onChange(p) },
                    send = { Api.service().deletePost(p.id) },
                )
            }
        } else null,
        onReport = {
            scope.localFirst(
                vm,
                "Report",
                apply = {},
                send = { Api.service().report(ReportIn("post", p.id, "Inappropriate")) },
                onSuccess = { vm.toast("Reported") },
            )
        },
    )
}

@Composable
fun PostDetailScreen(vm: AppVm, id: String) {
    var post by remember { mutableStateOf<PostOut?>(null) }
    var comments by remember { mutableStateOf<List<CommentOut>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                post = Api.service().getPost(id)
                comments = Api.service().comments(id)
            } catch (t: Throwable) {
                err = Api.errorMessage(t)
            }
        }
    }
    LaunchedEffect(id) { load() }

    BlurScaffold(topBar = { DATopBar("Post", onBack = { vm.back() }) }) { pad ->
        val p = post
        if (p == null) {
            BoxPad(pad) { if (err != null) ErrorText(err) else LoadingBox() }
        } else {
            Column(Modifier.fillMaxSize().padding(pad)) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    item { PostItem(vm, p) { post = it } }
                    item { Text("Comments", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(16.dp)) }
                    itemsIndexed(comments, key = { _, c -> c.id }) { i, c ->
                        Box(Modifier.staggerIn(i)) {
                            UserRow(
                                user = c.author ?: org.uxaoro.app.data.UserPublic(),
                                subtitle = c.text,
                                onClick = { c.author?.id?.let { vm.go(Screen.User(it)) } },
                            )
                        }
                    }
                }
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write a comment") },
                        shape = RoundedCornerShape(16.dp),
                        colors = inkFieldColors(),
                        singleLine = true,
                    )
                    TextButton(onClick = {
                        // Local-first: the comment and the counter move now.
                        val body = text.trim()
                        val lid = localId("c")
                        val mine = CommentOut(
                            id = lid,
                            author = vm.draftAuthor(),
                            text = body,
                            createdAt = null,
                        )
                        scope.localFirst(
                            vm,
                            "Send comment",
                            apply = {
                                comments = comments + mine
                                post = p.copy(commentsCount = p.commentsCount + 1)
                                text = ""
                                vm.markPending(lid)
                            },
                            undo = {
                                comments = comments.filter { it.id != lid }
                                post = p
                                text = body
                                vm.clearPending(lid)
                            },
                            send = {
                                val c = Api.service().addComment(id, CommentCreate(body))
                                // Swap the local row for the server's (real id).
                                comments = comments.map { if (it.id == lid) c else it }
                                vm.clearPending(lid)
                            },
                        )
                    }, enabled = text.isNotBlank()) { Text("Send") }
                }
            }
        }
    }
}

@Composable
fun CreatePostScreen(vm: AppVm, communityId: String?, wallUserId: String?, originalPostId: String?) {
    // A publish that failed returns here with the text still in the box.
    var text by remember { mutableStateOf(vm.composeDraftText) }
    var image by remember { mutableStateOf<Uri?>(null) }
    var video by remember { mutableStateOf<Uri?>(null) }
    var audio by remember { mutableStateOf<Uri?>(null) }
    var editUri by remember { mutableStateOf<Uri?>(null) }
    var showStickers by remember { mutableStateOf(false) }
    var showPollBuilder by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptionsText by remember { mutableStateOf("") }
    var pollMultiple by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Fixed pickers: separate MIME launchers + editor auto-open (Telegram-style).
    // Previously a single image/* picker lost its result on recomposition.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) editUri = it
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { video = it }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { audio = it }

    editUri?.let { uri ->
        org.uxaoro.app.ui.media.PhotoEditorDialog(source = uri, title = "Edit photo") { edited ->
            if (edited != null) {
                // Write edited bytes to cache and use that URI for upload.
                try {
                    val f = java.io.File(ctx.cacheDir, "edited-${System.currentTimeMillis()}.jpg")
                    f.writeBytes(edited.bytes)
                    image = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                } catch (_: Exception) {
                    image = uri
                }
            }
            editUri = null
        }
    }
    if (showStickers) {
        org.uxaoro.app.ui.media.StickerSheet(
            onPick = { pick ->
                when (pick) {
                    is org.uxaoro.app.ui.media.StickerPick.Emoji -> text = (text + " " + pick.emoji).take(2000)
                    is org.uxaoro.app.ui.media.StickerPick.Custom -> {
                        // Custom sticker -> attach as post photo via cache download.
                        scope.launch {
                            try {
                                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    java.net.URL(Api.mediaUrl(pick.imageUrl)).readBytes()
                                }
                                val f = java.io.File(ctx.cacheDir, "sticker-${System.currentTimeMillis()}.webp")
                                f.writeBytes(bytes)
                                image = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                            } catch (t: Throwable) {
                                err = Api.errorMessage(t)
                            }
                        }
                    }
                }
            },
            onDismiss = { showStickers = false },
        )
    }

    BlurScaffold(topBar = { DATopBar("New post", onBack = { vm.back() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            placeholder = { Text("What's new?") },
            shape = RoundedCornerShape(16.dp),
                        colors = inkFieldColors(),
        )
            Text("${text.length}/2000", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { pickImage.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Text("Photo")
                }
                TextButton(onClick = { pickVideo.launch("video/*") }) { Text("Video") }
                TextButton(onClick = { pickAudio.launch("audio/*") }) { Text("Music") }
                TextButton(onClick = { showStickers = true }) {
                  AssetSvgIcon("sticker-smile-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Sticker")
                }
                TextButton(onClick = { showPollBuilder = !showPollBuilder }) {
                    AssetSvgIcon("chart", contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(if (showPollBuilder) "Hide Poll" else "Poll")
                }
            }
            if (showPollBuilder) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pollQuestion,
                    onValueChange = { pollQuestion = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Poll question") },
                    shape = RoundedCornerShape(14.dp),
                    colors = inkFieldColors(),
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = pollOptionsText,
                    onValueChange = { pollOptionsText = it },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    placeholder = { Text("One option per line (minimum 2 options)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = inkFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pollMultiple, onCheckedChange = { pollMultiple = it })
                    Text("Allow multiple answers", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            image?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                TextButton(onClick = { editUri = it }) { Text("Edit photo") }
                TextButton(onClick = { image = null }) {
                  AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Remove")
                }
            }
            video?.let {
                org.uxaoro.app.ui.media.InlineVideo(it.toString())
                TextButton(onClick = { video = null }) {
                  AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Remove video")
                }
            }
            audio?.let {
                org.uxaoro.app.ui.media.InlineAudio(it.toString(), "Attached music")
                TextButton(onClick = { audio = null }) {
                  AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Remove music")
                }
            }
            ErrorText(err)
            Spacer(Modifier.height(12.dp))
            val validPoll = showPollBuilder && pollQuestion.isNotBlank() && pollOptionsText.lines().map { it.trim() }.filter { it.isNotBlank() }.size >= 2
            PrimaryButton("Publish", enabled = !loading && (text.isNotBlank() || image != null || video != null || audio != null || originalPostId != null || validPoll), icon = "send-square") {
                // Local-first publish: a draft row goes up in the timeline and
                // the composer closes at once; the upload happens behind it. If
                // the server refuses, the draft disappears, a snack says why and
                // the composer comes back with the text still in it.
                val draftId = localId("p")
                val draft = PostOut(
                    id = draftId,
                    author = vm.draftAuthor(),
                    text = text,
                    createdAt = null,
                )
                vm.addDraftPost(draft)
                vm.composeDraftText = text
                vm.composeDraftCommunityId = communityId
                vm.composeDraftWallId = wallUserId
                vm.composeDraftOriginalId = originalPostId
                vm.back()
                vm.launchAppTask {
                    loading = true
                    err = null
                    try {
                        val opts = pollOptionsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                        val mediaParts = withContext(Dispatchers.IO) {
                            Triple(
                                image?.let { uriPart(ctx, it, "image") },
                                video?.let { uriPart(ctx, it, "video") },
                                audio?.let { uriPart(ctx, it, "audio") },
                            )
                        }
                        Api.service().createPost(
                            text = textBody(text),
                            communityId = communityId?.let { textBody(it) },
                            wallUserId = wallUserId?.let { textBody(it) },
                            originalPostId = originalPostId?.let { textBody(it) },
                            image = mediaParts.first,
                            video = mediaParts.second,
                            audio = mediaParts.third,
                            pollQuestion = if (validPoll) textBody(pollQuestion.trim()) else null,
                            pollOptions = if (validPoll) textBody(opts.joinToString("\n")) else null,
                            pollMultiple = if (validPoll) textBody(if (pollMultiple) "1" else "0") else null,
                        )
                        vm.removeDraftPost(draftId)
                        vm.clearComposeDraft()
                    } catch (t: Throwable) {
                        vm.removeDraftPost(draftId)
                        val reason = Api.errorMessage(t)
                        android.util.Log.e("uxaoro", "Publish post failed: $reason", t)
                        vm.toast("Publish post failed: ${reason.take(180)}")
                        // Hand the draft back to the composer.
                        vm.go(Screen.CreatePost(communityId = communityId, wallUserId = wallUserId, originalPostId = originalPostId))
                    } finally {
                        loading = false
                    }
                }
            }
        }
    }
}
