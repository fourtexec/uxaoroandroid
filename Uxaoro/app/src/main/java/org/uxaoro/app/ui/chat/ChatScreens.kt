package org.uxaoro.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.uxaoro.app.data.FlagIn
import org.uxaoro.app.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import org.uxaoro.app.ui.theme.BlurScaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.uxaoro.app.crypto.E2EE
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Cache
import org.uxaoro.app.data.CachedMsg
import org.uxaoro.app.data.ChatCreate
import org.uxaoro.app.data.ChatOut
import org.uxaoro.app.data.ChatPatch
import org.uxaoro.app.data.MemberIn
import org.uxaoro.app.data.MessageCreate
import org.uxaoro.app.data.MessageOut
import org.uxaoro.app.data.ReactIn
import org.uxaoro.app.data.ReactionOut
import org.uxaoro.app.data.RoleInCommunity
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.data.WsClient
import org.uxaoro.app.data.toPlainBody
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.sendToSavedChat
import org.uxaoro.app.ui.syncSavedMarkers
import org.uxaoro.app.ui.reactLocally
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.AssetSvgIcon
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.LoadingBox
import org.uxaoro.app.ui.components.GlassMenu
import org.uxaoro.app.ui.components.GlassMenuItem
import org.uxaoro.app.ui.components.ReactionPicker
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.uxaoro.app.ui.components.ReactionStrip
import org.uxaoro.app.ui.components.UserRow
import org.uxaoro.app.ui.components.formatTime
import org.uxaoro.app.ui.theme.GlassPanel
import org.uxaoro.app.ui.theme.liquidGlass
import org.uxaoro.app.ui.components.inkFieldColors
import org.uxaoro.app.util.shareText
import org.uxaoro.app.util.uxaoroWebLink
import org.uxaoro.app.ui.motion.bubbleIn
import org.uxaoro.app.ui.motion.spinWhile
import org.uxaoro.app.ui.motion.TypingDots
import androidx.compose.animation.animateContentSize
import org.uxaoro.app.ui.motion.popIn
import org.uxaoro.app.ui.motion.staggerIn

@Composable
fun ChatsScreen(vm: AppVm) {
    val savedChats = remember { Cache.loadChats() }
    var items by remember { mutableStateOf(savedChats) }
    var loading by remember { mutableStateOf(savedChats.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    fun load() {
        scope.launch {
            refreshing = true
            loading = items.isEmpty()
            try {
                // Saved Messages is this account's own chat with itself. GET
                // chats/saved creates it on first access, so ask for it before the list.
                val saved = runCatching { Api.service().savedChat() }.getOrNull()
                val list = runCatching { Api.service().chats() }.getOrDefault(Cache.loadChats())
                items = if (saved != null && list.none { it.id == saved.id }) listOf(saved) + list else list
                Cache.saveChats(items)
                // Rebuild bookmarks from Saved Messages after refreshing the chat list.
                syncSavedMarkers(ctx, vm, vm.me?.id.orEmpty())
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(Unit) {
        WsClient.events.collect { ev ->
            if (ev.event == "chats_reset") {
                Cache.clearChatData()
                items = emptyList()
                load()
            }
        }
    }
    var filter by remember { mutableStateOf("") }
    BlurScaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            DATopBar("Wire", actions = {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.go(Screen.NewChat) },
                modifier = Modifier.popIn(),
            ) {
                Icon(Icons.Default.Add, contentDescription = "New chat")
            }
        },
    ) { pad ->
        val shown = items.filter {
            filter.isBlank() || it.title.contains(filter, true) || it.members.any { m ->
                m.displayName.contains(filter, true) || m.username.contains(filter, true)
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize().padding(pad)) { LoadingBox() }
            else -> Column(Modifier.fillMaxSize().padding(pad)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = { Text("Search chats") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = inkFieldColors(),
                    singleLine = true,
                )
                LazyColumn(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    item { EmptyBox("No chats yet. Start a secret conversation.") }
                }
                itemsIndexed(shown, key = { _, c -> c.id }) { i, c ->
                    // Saved Messages: a private chat with yourself. There is no
                    // peer, so no presence and no "last seen" to show.
                    val isSaved = c.kind == "saved"
                    val preview = c.lastMessage?.let { "Locked · ${formatTime(it.createdAt)}" } ?: "E2EE chat"
                    val other = c.members.firstOrNull { it.id != vm.me?.id }
                    val presence = if (!isSaved && !c.isGroup && other != null) vm.presenceLine(other) else ""
                    val badge = buildString {
                        if (c.pinned) append("Pinned · ")
                        if (c.muted) append("Muted · ")
                        if (isSaved) append("Your private chat with yourself · ")
                        if (presence.isNotBlank()) append("$presence · ")
                        append(preview)
                        if (c.unread > 0) append("  ${c.unread}")
                    }
                    val name = when {
                        isSaved -> "\uD83D\uDD16 " + c.title.ifBlank { "Saved Messages" }
                        c.isGroup -> c.title.ifBlank { "Group" }
                        else -> other?.displayName?.ifBlank { other.username }.orEmpty().ifBlank { c.title.ifBlank { "Chat" } }
                    }
                    Box(Modifier.staggerIn(i)) {
                        UserRow(
                            user = if (!c.isGroup && other != null) other.copy(
                                displayName = name,
                                online = vm.onlineOf(other),
                            ) else UserPublic(
                                id = c.id,
                                username = "group",
                                displayName = name,
                            ),
                            subtitle = badge,
                            onClick = { vm.go(Screen.Chat(c.id)) },
                            trailing = {
                                if (c.pinned) Icon(Icons.Default.PushPin, contentDescription = "Pinned", modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
fun NewChatScreen(vm: AppVm) {
    var friends by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        friends = runCatching { Api.service().friends() }.getOrDefault(emptyList())
    }
    BlurScaffold(topBar = { DATopBar("New message", onBack = { vm.back() }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                TextButton(onClick = { vm.go(Screen.NewGroup) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Create a group")
                }
            }
            item { Text("End-to-end encrypted. Pick a friend.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(friends, key = { it.id }) { u ->
                UserRow(u, onClick = {
                    scope.launch {
                        val chat = Api.service().createChat(ChatCreate(listOf(u.id)))
                        vm.back()
                        vm.go(Screen.Chat(chat.id))
                    }
                })
            }
        }
    }
}

data class DecryptedMsg(
    val raw: MessageOut,
    val text: String,
    val failed: Boolean,
    val localMedia: String = "",
    /** True while the bubble is on screen but the server has not stored it. */
    val pending: Boolean = false,
)

/** Locally picked chat attachment. Kept on-device until Send — only
 *  AES-GCM ciphertext is uploaded (see E2EE.encryptBundle). */
data class PendingAttach(val uri: android.net.Uri, val kind: String, val mime: String)

@Composable
fun ChatThreadScreen(vm: AppVm, chatId: String) {
    val ctx = LocalContext.current
    val meId = vm.me?.id.orEmpty()
    var chat by remember(chatId) { mutableStateOf(Cache.loadChats().firstOrNull { it.id == chatId }) }
    var refreshing by remember(chatId) { mutableStateOf(true) }
    val msgs = remember(chatId) {
        mutableStateListOf<DecryptedMsg>().apply {
            addAll(Cache.loadMessages(chatId).map { cached ->
                DecryptedMsg(
                    cached.raw ?: MessageOut(
                        id = cached.id,
                        chatId = cached.chatId,
                        senderId = cached.senderId,
                        createdAt = cached.createdAt,
                    ),
                    cached.text,
                    cached.failed,
                )
            })
        }
    }
    var input by remember { mutableStateOf(Prefs.draft(chatId)) }
    var sending by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clip = LocalClipboardManager.current

    suspend fun decryptMediaToFile(m: MessageOut): String = withContext(Dispatchers.IO) {
        runCatching {
            val url = Api.mediaUrl(m.mediaUrl.ifBlank { m.imageUrl })
            val ct = Api.service().fetchRaw(url).bytes()
            val pt = E2EE.decryptBytes(ctx, ct, m.mediaNonce, m.keysJson, meId) ?: return@runCatching ""
            val dir = File(ctx.cacheDir, "chat_media").apply { mkdirs() }
            val kind = org.uxaoro.app.util.mediaKind(m.mediaUrl, m.mediaType, m.mediaMime)
            val mime = m.mediaMime.lowercase()
            val ext = when (kind) {
                "video" -> if ("webm" in mime) ".webm" else ".mp4"
                "audio" -> when {
                    "ogg" in mime -> ".ogg"
                    "wav" in mime -> ".wav"
                    "mpeg" in mime || "mp3" in mime -> ".mp3"
                    else -> ".m4a"
                }
                else -> when {
                    "png" in mime -> ".png"
                    "webp" in mime -> ".webp"
                    "gif" in mime -> ".gif"
                    else -> ".jpg"
                }
            }
            val f = File(dir, "${m.id}$ext")
            if (!f.exists() || f.length() != pt.size.toLong()) f.writeBytes(pt)
            android.net.Uri.fromFile(f).toString()
        }.getOrDefault("")
    }

    suspend fun decrypt(m: MessageOut): DecryptedMsg {
        val plain = E2EE.decrypt(ctx, m.ciphertext, m.nonce, m.keysJson, meId)
        if (plain == null) {
            // Media/sticker rows may have empty ciphertext on legacy data — render attachments.
            if (m.mediaUrl.isNotBlank() || m.imageUrl.isNotBlank() || m.stickerId.isNotBlank() || m.sticker != null) {
                val parts = org.uxaoro.app.util.parseMsgPayload("", m.imageUrl, m.mediaUrl, m.mediaType, m.mediaMime, m.stickerId)
                return DecryptedMsg(m, parts.text, false)
            }
            return DecryptedMsg(m, "Unable to decrypt. Missing key.", true)
        }
        if (m.mediaNonce.isNotBlank()) {
            val local = decryptMediaToFile(m)
            return DecryptedMsg(m, plain, false, local)
        }
        val parts = org.uxaoro.app.util.parseMsgPayload(plain, m.imageUrl, m.mediaUrl, m.mediaType, m.mediaMime, m.stickerId)
        // Rebuild display text from envelope so bubbles show caption + media.
        return DecryptedMsg(m, plain, false)
    }

    fun load() {
        scope.launch {
            refreshing = true
            try {
                chat = runCatching { Api.service().getChat(chatId) }.getOrNull() ?: chat
                val page = runCatching { Api.service().messages(chatId) }.getOrNull()
                if (page != null) {
                    msgs.clear()
                    msgs.addAll(page.items.map { decrypt(it) })
                    // In Saved Messages the copies are the record of what this account saved.
                    if (chat?.kind == "saved") {
                        vm.syncSavedRefs(
                            msgs.mapNotNull { dm ->
                                org.uxaoro.app.util.parseMsgPayload(
                                    dm.text,
                                    dm.raw.imageUrl,
                                    dm.raw.mediaUrl,
                                    dm.raw.mediaType,
                                    dm.raw.mediaMime,
                                    dm.raw.stickerId,
                                ).forward
                            },
                        )
                    }
                }
                vm.refreshBadges()
            } finally {
                refreshing = false
            }
        }
    }
    LaunchedEffect(chatId) { load() }
    LaunchedEffect(chatId) {
        snapshotFlow { msgs.toList() }.collect { current ->
            withContext(Dispatchers.IO) {
                Cache.saveMessages(
                    chatId,
                    current.map { CachedMsg(it.raw.id, chatId, it.raw.senderId, it.text, it.failed, it.raw.createdAt, it.raw) },
                )
            }
        }
    }
    LaunchedEffect(chatId) {
        WsClient.events.collect { ev ->
            when (ev.event) {
                "chats_reset" -> {
                    Cache.clearChatData()
                    msgs.clear()
                    chat = null
                }
                "message" -> {
                    val el = ev.data ?: return@collect
                    val m = runCatching { Api.gson.fromJson(el, MessageOut::class.java) }.getOrNull()
                        ?: return@collect
                    if (m.chatId == chatId && msgs.none { it.raw.id == m.id }) {
                        msgs.add(decrypt(m))
                    }
                }
                "message_deleted" -> {
                    val obj = ev.data?.takeIf { it.isJsonObject }?.asJsonObject ?: return@collect
                    val id = runCatching { obj.get("id").asString }.getOrNull() ?: return@collect
                    val cid = runCatching { obj.get("chat_id").asString }.getOrNull()
                    if (cid == null || cid == chatId) {
                        val idx = msgs.indexOfFirst { it.raw.id == id }
                        if (idx >= 0) msgs[idx] = msgs[idx].copy(text = "Message deleted", failed = true)
                    }
                }
                "message_react" -> {
                    val obj = ev.data?.takeIf { it.isJsonObject }?.asJsonObject ?: return@collect
                    val id = runCatching { obj.get("id").asString }.getOrNull() ?: return@collect
                    val cid = runCatching { obj.get("chat_id").asString }.getOrNull()
                    if (cid != null && cid != chatId) return@collect
                    val reacts = runCatching {
                        Api.gson.fromJson(obj.get("reactions"), Array<ReactionOut>::class.java).toList()
                    }.getOrNull() ?: return@collect
                    val idx = msgs.indexOfFirst { it.raw.id == id }
                    if (idx >= 0) {
                        val old = msgs[idx]
                        msgs[idx] = old.copy(raw = old.raw.copy(reactions = reacts))
                    }
                }
                "typing" -> {
                    val obj = ev.data?.takeIf { it.isJsonObject }?.asJsonObject ?: return@collect
                    val cid = runCatching { obj.get("chat_id").asString }.getOrNull()
                    val uid = runCatching { obj.get("user_id").asString }.getOrNull()
                    if (cid == chatId && uid != meId) {
                        typing = true
                    }
                }
            }
        }
    }
    var showSmiles by remember { mutableStateOf(false) }
    var preregLock by remember { mutableStateOf(Prefs.regMode == "preregistration") }
    var attachMenu by remember { mutableStateOf(false) }
    var chatActions by remember { mutableStateOf(false) }
    var pendingMedia by remember { mutableStateOf<PendingAttach?>(null) }
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }
    LaunchedEffect(typing) {
        if (typing) {
            delay(2500)
            typing = false
        }
    }
    val pickMedia = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep the file local; only ciphertext is uploaded on Send.
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val kind = org.uxaoro.app.util.mediaKind(null, null, mime).ifBlank {
            when {
                uri.toString().contains("video") -> "video"
                uri.toString().contains("audio") -> "audio"
                else -> "image"
            }
        }
        pendingMedia = PendingAttach(uri, kind, mime)
        vm.toast(
            when (kind) {
                "video" -> "Video attached — press Send"
                "audio" -> "Music attached — press Send"
                else -> "Photo attached — press Send"
            },
        )
    }

    suspend fun collectPubs(): MutableMap<String, String> {
        val c = chat ?: Api.service().getChat(chatId).also { chat = it }
        val pubs = mutableMapOf<String, String>()
        pubs[meId] = E2EE.publicPem(ctx)
        for (u in c.members) {
            val pk = runCatching { Api.service().pubkey(u.id).pubkey }.getOrNull().orEmpty()
            if (pk.isNotBlank()) pubs[u.id] = pk
        }
        return pubs
    }

    /**
     * One-tap save: an E2EE copy that carries a `forward` reference goes into
     * the personal Saved Messages chat. Text only — an attachment's ciphertext is
     * bound to the key of the chat it was sent in, so photos/videos are saved by
     * sending them to Saved Messages directly.
     *
     * Local-first: the bookmark lands on the bubble with the tap and is taken
     * back (with a toast) if the server refuses the copy.
     */
    fun saveToSaved(m: MessageOut, text: String) {
        val author = chat?.members?.firstOrNull { it.id == m.senderId }
        val me = vm.me?.asPublic()
        val authorName = author?.displayName?.ifBlank { author.username }
            ?: me?.displayName?.ifBlank { me.username }.orEmpty()
        scope.localFirst(
            vm,
            "Save to Saved Messages",
            apply = { vm.markSavedMessage(m.id, true) },
            undo = { vm.markSavedMessage(m.id, false) },
            send = {
                sendToSavedChat(
                    ctx = ctx,
                    meId = meId,
                    payload = org.uxaoro.app.util.packMsgPayload(
                        text,
                        forward = org.uxaoro.app.util.MsgForward(
                            messageId = m.id,
                            chatId = chatId,
                            author = authorName,
                            username = author?.username.orEmpty(),
                            createdAt = m.createdAt.orEmpty(),
                        ),
                    ),
                )
            },
            onSuccess = { vm.toast("\uD83D\uDD16 Saved to Saved Messages") },
        )
    }

    if (showSmiles) {
        org.uxaoro.app.ui.media.StickerSheet(
            onPick = { pick ->
                scope.launch {
                    sending = true
                    try {
                        val pubs = collectPubs()
                        val (payload, mtype, extra) = when (pick) {
                            is org.uxaoro.app.ui.media.StickerPick.Custom ->
                                Triple(
                                    org.uxaoro.app.util.packMsgPayload("", pick.imageUrl, "sticker", "image/webp", pick.id),
                                    "sticker",
                                    MessageCreate("sticker", "", "", "{}", "", pick.imageUrl, "sticker", "image/webp", pick.id),
                                )
                            is org.uxaoro.app.ui.media.StickerPick.Emoji ->
                                Triple(pick.emoji, "text", null)
                        }
                        val enc = E2EE.encrypt(payload, pubs)
                        val sent = if (extra != null) {
                            Api.service().sendMessage(
                                chatId,
                                extra.copy(ciphertext = enc.ciphertext, nonce = enc.nonce, keysJson = enc.keysJson),
                            )
                        } else {
                            Api.service().sendMessage(chatId, MessageCreate("text", enc.ciphertext, enc.nonce, enc.keysJson))
                        }
                        if (msgs.none { it.raw.id == sent.id }) msgs.add(decrypt(sent))
                    } catch (t: Throwable) {
                        android.util.Log.e("uxaoro", "Send sticker failed: ${Api.errorMessage(t)}", t)
                        vm.toast("Send sticker failed — the change was reverted.")
                    } finally {
                        sending = false
                    }
                }
            },
            onDismiss = { showSmiles = false },
        )
    }

    val other = chat?.members?.firstOrNull { it.id != meId }
    // Saved Messages is a chat with yourself: no peer, no calls, and the copy
    // that reaches it *is* the save. Everything else behaves like any chat.
    val isSavedChat = chat?.kind == "saved"
    val title = when {
        isSavedChat -> chat?.title?.ifBlank { "Saved Messages" } ?: "Saved Messages"
        chat?.isGroup == true -> chat?.title?.ifBlank { "Group" } ?: "Group"
        else -> other?.displayName?.ifBlank { other.username } ?: chat?.title ?: "Chat"
    }
    BlurScaffold(
        topBar = {
            // Keep the chat header focused like Telegram: encryption state and
            // one overflow affordance rather than a row of competing actions.
            DATopBar(
                title,
                onBack = { vm.back() },
                subtitle = when {
                    isSavedChat -> "Your private chat with yourself · E2EE"
                    chat?.isGroup != true && other != null -> vm.presenceLine(other)
                    else -> null
                },
                leadingContent = {
                    when {
                        isSavedChat -> Icon(Icons.Default.Bookmark, contentDescription = "Saved Messages", tint = MaterialTheme.colorScheme.primary)
                        chat?.isGroup == true -> org.uxaoro.app.ui.components.AssetSvgIcon(
                            "users-group-rounded",
                            contentDescription = "Group chat",
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        other != null -> org.uxaoro.app.ui.components.Avatar(other.avatarUrl, title, 30)
                        else -> org.uxaoro.app.ui.components.AssetSvgIcon(
                            "user-rounded",
                            contentDescription = "Chat",
                            size = 24.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Icon(
                    if (isSavedChat) Icons.Default.Bookmark else Icons.Default.Lock,
                    contentDescription = if (isSavedChat) "Saved Messages" else "End-to-end encrypted",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Box {
                    IconButton(onClick = { chatActions = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                    }
                    GlassMenu(expanded = chatActions, onDismissRequest = { chatActions = false }) {
                        if (!isSavedChat && chat?.isGroup != true && other != null) {
                            GlassMenuItem(text = "Voice call", leadingIcon = "phone-calling", onClick = {
                                chatActions = false
                                vm.startCall(chatId, other.id, title, false)
                            })
                            GlassMenuItem(text = "Video call", leadingIcon = "videocamera", onClick = {
                                chatActions = false
                                vm.startCall(chatId, other.id, title, true)
                            })
                        }
                        GlassMenuItem(text = "Chat info", leadingIcon = "info-circle", onClick = {
                            chatActions = false
                            vm.go(Screen.ChatInfo(chatId))
                        })
                    }
                }
            })
        },
    ) { pad ->
        ChatEmojiPattern()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 8.dp),
                ) {
                    itemsIndexed(msgs, key = { _, dm -> dm.raw.id }) { i, dm ->
                        ChatBubble(
                            dm = dm,
                            mine = dm.raw.senderId == meId,
                            bubbleStyle = (if (dm.raw.senderId == meId) vm.me?.asPublic()
                                else chat?.members?.firstOrNull { it.id == dm.raw.senderId })
                                ?.let { if (it.premium) it.customization.bubble else "" }.orEmpty(),
                            animateEnter = i >= msgs.size - 8,
                            read = chat?.isGroup != true && chat?.peerLastRead != null && (dm.raw.createdAt ?: "") <= (chat?.peerLastRead ?: ""),
                            onCopy = { clip.setText(AnnotatedString(dm.text)) },
                            onReact = { emoji ->
                                // The chip answers the tap; the server confirms behind it.
                                val idx = msgs.indexOfFirst { it.raw.id == dm.raw.id }
                                if (idx >= 0) {
                                    val old = msgs[idx]
                                    val mine = old.raw.myReaction.orEmpty()
                                    scope.localFirst(
                                        vm,
                                        "React",
                                        send = {
                                            val r = Api.service().reactMessage(chatId, dm.raw.id, ReactIn(emoji))
                                            val at = msgs.indexOfFirst { it.raw.id == dm.raw.id }
                                            if (at >= 0) {
                                                msgs[at] = msgs[at].copy(raw = msgs[at].raw.copy(reactions = r.reactions, myReaction = r.myReaction))
                                            }
                                        },
                                        apply = {
                                            msgs[idx] = old.copy(raw = old.raw.copy(
                                                reactions = reactLocally(old.raw.reactions, mine, emoji),
                                                myReaction = if (mine == emoji) "" else emoji,
                                            ))
                                        },
                                        undo = { if (idx < msgs.size) msgs[idx] = old },
                                    )
                                }
                            },
                            onDelete = if (dm.raw.senderId == meId) {
                                {
                                    val idx = msgs.indexOfFirst { it.raw.id == dm.raw.id }
                                    if (idx >= 0) {
                                        val old = msgs[idx]
                                        scope.localFirst(
                                            vm,
                                            "Delete message",
                                            send = { Api.service().deleteMessage(chatId, dm.raw.id) },
                                            apply = { msgs[idx] = old.copy(text = "Message deleted", failed = true) },
                                            undo = { if (idx < msgs.size) msgs[idx] = old },
                                        )
                                    }
                                }
                            } else null,
                            saved = vm.isSavedMessage(dm.raw.id),
                            // No saving inside Saved Messages itself, and only
                            // text can be copied (see saveToSaved).
                            onSave = if (isSavedChat) null else ({ t -> saveToSaved(dm.raw, t) }),
                            onOpenForwarded = { pid -> vm.go(Screen.Post(pid)) },
                        )
                    }
                }
                TypingHint(
                    visible = typing,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                )
            }
            if (pendingMedia != null) {
                val att = pendingMedia!!
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (att.kind) {
                            "video" -> "videocamera"
                            "audio" -> "music-note"
                            else -> "gallery"
                        }
                        AssetSvgIcon(icon, contentDescription = "${att.kind} attachment", size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("${att.kind.replaceFirstChar { it.uppercase() }} attached — press Send", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { pendingMedia = null }) {
                      AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                      Spacer(Modifier.width(6.dp))
                      Text("Remove")
                    }
                }
            }
            // One quiet rounded composer surface keeps controls legible over
            // wallpaper without competing with the frosted app chrome.
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(8.dp)
                    .liquidGlass(shape = RoundedCornerShape(16.dp), elevation = 6.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach photo / video / music")
                    }
                    GlassMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        GlassMenuItem(text = "Photo", leadingIcon = "gallery", onClick = { attachMenu = false; pickMedia.launch("image/*") })
                        GlassMenuItem(text = "Video", leadingIcon = "videocamera", onClick = { attachMenu = false; pickMedia.launch("video/*") })
                        GlassMenuItem(text = "Music", leadingIcon = "music-note", onClick = { attachMenu = false; pickMedia.launch("audio/*") })
                    }
                }
                IconButton(onClick = { showSmiles = true }) {
                    AssetSvgIcon("sticker-smile-circle", contentDescription = "Reactions", tint = MaterialTheme.colorScheme.onSurface)
                }
                if (preregLock) {
                    Text("Beta preregistration — access opens Oct 10 12:00 UTC", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        Prefs.setDraft(chatId, it)
                        WsClient.typing(chatId)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize(),
                    placeholder = {
                        Text(
                            when {
                                pendingMedia != null -> "Caption (optional)"
                                isSavedChat -> "Note to self"
                                else -> "Message"
                            },
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = chatComposerColors(),
                )
                val canSend = (input.isNotBlank() || pendingMedia != null) && !sending
                IconButton(
                    enabled = canSend,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (canSend) MaterialTheme.colorScheme.primary else Color.Transparent),
                    onClick = {
                        val text = input.trim()
                        val att = pendingMedia
                        // Local-first: the bubble is in the thread before the
                        // encryption and the upload finish. It carries a local
                        // id (see DecryptedMsg.pending) and is replaced by the
                        // server row, or removed with a snack if the send fails.
                        val lid = org.uxaoro.app.ui.localId("m")
                        val draft = DecryptedMsg(
                            raw = MessageOut(id = lid, chatId = chatId, senderId = meId),
                            text = text,
                            failed = false,
                            localMedia = att?.uri?.toString().orEmpty(),
                            pending = true,
                        )
                        msgs.add(draft)
                        input = ""
                        Prefs.setDraft(chatId, "")
                        vm.markPending(lid)
                        scope.launch {
                            sending = true
                            try {
                                val pubs = collectPubs()
                                val payload: String
                                val mtype: String
                                var extra = MessageCreate()
                                var fileBytes: ByteArray? = null
                                if (att != null) {
                                    // Media URL is filled in AFTER the ciphertext upload below.
                                    payload = org.uxaoro.app.util.packMsgPayload(text, "", att.kind, att.mime)
                                    mtype = when (att.kind) {
                                        "video" -> "video"
                                        "audio" -> "audio"
                                        else -> "image"
                                    }
                                    fileBytes = ctx.contentResolver.openInputStream(att.uri)?.use { it.readBytes() }
                                    extra = MessageCreate(mtype, "", "", "{}", "", "", att.kind, att.mime)
                                } else {
                                    payload = text
                                    mtype = "text"
                                }
                                val enc = E2EE.encryptBundle(payload, pubs, fileBytes)
                                if (att != null && enc.mediaCiphertext.isNotBlank()) {
                                    // Upload ciphertext only — the server stores an opaque blob.
                                    val ctBytes = android.util.Base64.decode(enc.mediaCiphertext, android.util.Base64.NO_WRAP)
                                    val part = MultipartBody.Part.createFormData(
                                        "file",
                                        "enc.bin",
                                        ctBytes.toRequestBody("application/octet-stream".toMediaType()),
                                    )
                                    val saved = Api.service().uploadMediaEnc(
                                        part,
                                        "chat".toPlainBody(),
                                        enc = "1".toPlainBody(),
                                        kind = mtype.toPlainBody(),
                                        mime = att.mime.ifBlank { null }?.toPlainBody(),
                                    )
                                    extra = extra.copy(mediaUrl = saved.url)
                                }
                                val sent = Api.service().sendMessage(
                                    chatId,
                                    extra.copy(
                                        msgType = mtype,
                                        ciphertext = enc.ciphertext,
                                        nonce = enc.nonce,
                                        keysJson = enc.keysJson,
                                        mediaNonce = enc.mediaNonce,
                                    ),
                                )
                                msgs.removeAll { it.raw.id == lid }
                                vm.clearPending(lid)
                                if (msgs.none { it.raw.id == sent.id }) {
                                    msgs.add(decrypt(sent))
                                }
                                pendingMedia = null
                            } catch (t: Throwable) {
                                // The bubble goes away and the message comes back
                                // to the composer, so nothing typed is lost.
                                msgs.removeAll { it.raw.id == lid }
                                vm.clearPending(lid)
                                input = text
                                Prefs.setDraft(chatId, text)
                                android.util.Log.e("uxaoro", "Send message failed: ${Api.errorMessage(t)}", t)
                                vm.toast("Send message failed — the change was reverted.")
                            } finally {
                                sending = false
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.spinWhile(sending),
                    )
                }
            }
        }
    }
}

@Composable
private fun chatComposerColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun TypingHint(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypingDots()
            Spacer(Modifier.width(6.dp))
            Text("typing…", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatBubble(
    dm: DecryptedMsg,
    mine: Boolean,
    bubbleStyle: String = "default",
    read: Boolean = false,
    animateEnter: Boolean = true,
    onCopy: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onDelete: (() -> Unit)? = null,
    /** True when this message is kept in Saved Messages (bookmark shown). */
    saved: Boolean = false,
    /** Save the *displayed* text to Saved Messages; null hides the action. */
    onSave: ((String) -> Unit)? = null,
    /** Open the original post of a forwarded copy, when there is one. */
    onOpenForwarded: ((String) -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    val parts = remember(dm.raw.id, dm.text) {
        org.uxaoro.app.util.parseMsgPayload(dm.text, dm.raw.imageUrl, dm.raw.mediaUrl, dm.raw.mediaType, dm.raw.mediaMime, dm.raw.stickerId)
    }
    val kind = remember(parts) {
        org.uxaoro.app.util.mediaKind(
            parts.mediaUrl.ifBlank { dm.raw.mediaUrl.ifBlank { dm.raw.imageUrl } },
            parts.mediaType.ifBlank { dm.raw.mediaType },
            dm.raw.mediaMime,
        )
    }
    val stickerUrl = dm.raw.sticker?.imageUrl.orEmpty().ifBlank { if (kind == "sticker") parts.mediaUrl else "" }
    val mediaUrl = parts.mediaUrl.ifBlank { dm.raw.mediaUrl.ifBlank { dm.raw.imageUrl } }
    // E2EE attachments render from the decrypted local cache file (file:// URI);
    // legacy plaintext media falls back to the server URL.
    val displayUrl = dm.localMedia.ifBlank { Api.mediaUrl(mediaUrl) }
    val caption = parts.text
    val isBigEmoji = caption.isNotBlank() && mediaUrl.isBlank() && stickerUrl.isBlank() &&
        caption.trim().codePointCount(0, caption.trim().length) == 1
    // Apply the sender's server-approved style, not the viewer's own setting.
    // An expired subscription falls back to the default in the caller.
    val bubbleShape = when (bubbleStyle) {
        "rounded" -> RoundedCornerShape(28.dp)
        "square" -> RoundedCornerShape(4.dp)
        else -> RoundedCornerShape(16.dp)
    }
    val outlined = bubbleStyle == "outline"
    val bubbleColor = if (outlined) MaterialTheme.colorScheme.surface else if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val bubbleInk = if (outlined) MaterialTheme.colorScheme.onSurface else if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .bubbleIn(mine, animateEnter),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                Box(
                    // A bubble that is still uploading is dimmed, so a slow
                    // network is obvious instead of looking like a lost message.
                    Modifier
                        .alpha(if (dm.pending) 0.62f else 1f)
                        .widthIn(max = 300.dp)
                        .clip(bubbleShape)
                        .background(bubbleColor, bubbleShape)
                        .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, bubbleShape) else Modifier)
                        .pointerInput(dm.raw.id) { detectTapGestures(onLongPress = { menu = true }) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Column {
                        // A copy kept in Saved Messages says where it came from.
                        // Same wording as the web client, so the two read alike.
                        parts.forward?.let { fwd ->
                            val post = fwd.postId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .then(
                                        if (post.isNotBlank() && onOpenForwarded != null) {
                                            Modifier.clickable { onOpenForwarded(post) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(bottom = 4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "\u21AA Forwarded from " + fwd.author.ifBlank { fwd.username }.ifBlank { "Uxaoro" },
                                    fontSize = 12.sp,
                                    color = bubbleInk.copy(alpha = 0.85f),
                                )
                            }
                        }
                        if (stickerUrl.isNotBlank()) {
                            // Stickers: WebM/WebP, no standard bubble background
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                if (stickerUrl.endsWith(".webm", ignoreCase = true) || stickerUrl.contains("webm")) {
                                    org.uxaoro.app.ui.media.InlineVideo(displayUrl, modifier = Modifier.size(140.dp))
                                } else {
                                    coil.compose.AsyncImage(
                                        model = Api.mediaUrl(stickerUrl),
                                        contentDescription = "sticker",
                                        modifier = Modifier.size(140.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    )
                                }
                            }
                        } else if (mediaUrl.isNotBlank() && kind == "video") {
                            org.uxaoro.app.ui.media.InlineVideo(displayUrl)
                        } else if (mediaUrl.isNotBlank() && kind == "audio") {
                            org.uxaoro.app.ui.media.InlineAudio(displayUrl, caption.ifBlank { "Voice message" })
                        } else if (mediaUrl.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = displayUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { zoom = true },
                                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                            )
                        }
                        if (isBigEmoji) {
                            Text(caption, fontSize = 56.sp, color = bubbleInk)
                        } else if (caption.isNotBlank()) {
                            if (mediaUrl.isNotBlank() || stickerUrl.isNotBlank()) androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                            Text(caption.ifBlank { dm.text }, color = bubbleInk)
                        } else if (mediaUrl.isBlank() && stickerUrl.isBlank()) {
                            Text(dm.text, color = bubbleInk)
                        }
                        Text(
                            buildString {
                                if (saved) append("\uD83D\uDD16  ")
                                append(formatTime(dm.raw.createdAt))
                                if (mine) append(if (read) "  ·  Read" else "  ·  Sent")
                            },
                            fontSize = 10.sp,
                            color = bubbleInk.copy(alpha = 0.65f),
                        )
                    }
                }
                ReactionStrip(dm.raw.reactions, dm.raw.myReaction, onReact)
            }
            GlassMenu(expanded = menu, onDismissRequest = { menu = false }) {
                GlassMenuItem(text = "Copy", leadingIcon = "copy", onClick = { menu = false; onCopy() })
                GlassMenuItem(text = "React", leadingIcon = "sticker-smile-circle", onClick = { menu = false; pick = true })
                // Text only: an attachment's ciphertext is bound to the key of
                // the chat it was sent in, so a copy could never be decrypted.
                if (onSave != null && !saved && caption.isNotBlank()) {
                    GlassMenuItem(
                        text = "Save to Saved Messages",
                        leadingIcon = "bookmark",
                        onClick = { menu = false; onSave(caption) },
                    )
                }
                if (dm.raw.stickerId.isNotBlank() || parts.stickerId.isNotBlank()) {
                    val sid = dm.raw.stickerId.ifBlank { parts.stickerId }
                    val favScope = rememberCoroutineScope()
                    GlassMenuItem(text = "Save sticker to favorites", leadingIcon = "star-1", onClick = {
                        menu = false
                        Prefs.favoriteStickers = Prefs.favoriteStickers + sid
                        favScope.launch {
                            runCatching { Api.service().addFavoriteSticker(org.uxaoro.app.data.StickerFavIn(sid)) }
                        }
                    })
                }
                if (onDelete != null) {
                    GlassMenuItem(text = "Delete", leadingIcon = "trash-bin-trash", danger = true, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
    if (pick) {
        ReactionPicker(onPick = { onReact(it); pick = false }, onDismiss = { pick = false })
    }
    if (zoom && mediaUrl.isNotBlank() && kind == "image") {
        androidx.compose.ui.window.Dialog(onDismissRequest = { zoom = false }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.88f))
                    .clickable { zoom = false },
                contentAlignment = Alignment.Center,
            ) {
                coil.compose.AsyncImage(
                    model = displayUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
fun NewGroupScreen(vm: AppVm) {
    var friends by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var title by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        friends = runCatching { Api.service().friends() }.getOrDefault(Cache.loadFriends())
    }
    BlurScaffold(topBar = { DATopBar("New group", onBack = { vm.back() }) }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Group name") },
                shape = RoundedCornerShape(16.dp),
                        colors = inkFieldColors(),
            )
            TextButton(
                enabled = title.isNotBlank() && selected.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val chat = Api.service().createChat(ChatCreate(selected.toList(), title.trim()))
                        vm.back()
                        vm.go(Screen.Chat(chat.id))
                    }
                },
            ) { Text("Create") }
            LazyColumn {
                items(friends, key = { it.id }) { u ->
                    val on = u.id in selected
                    UserRow(u, subtitle = if (on) "Selected" else "@${u.username}", onClick = {
                        selected = if (on) selected - u.id else selected + u.id
                    })
                }
            }
        }
    }
}

@Composable
fun ChatInfoScreen(vm: AppVm, chatId: String) {
    var chat by remember { mutableStateOf<ChatOut?>(null) }
    var title by remember { mutableStateOf("") }
    var addName by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val meId = vm.me?.id.orEmpty()

    fun load() {
        scope.launch {
            chat = runCatching { Api.service().getChat(chatId) }.onFailure {
                err = Api.errorMessage(it)
            }.getOrNull()
            title = chat?.title.orEmpty()
        }
    }
    LaunchedEffect(chatId) { load() }

    val c = chat
    val canAdmin = c?.myRole == "owner" || c?.myRole == "admin"
    val canInvite = canAdmin || (c?.isGroup == true && c.membersCanInvite)
    val heading = when {
        c == null -> "Chat info"
        c.kind == "saved" -> "Saved Messages"
        c.isGroup -> c.title.ifBlank { "Group" }
        else -> {
            val other = c.members.firstOrNull { it.id != meId }
            other?.displayName?.ifBlank { other.username } ?: "Chat"
        }
    }

    BlurScaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { DATopBar(heading, onBack = { vm.back() }) },
    ) { pad ->
        if (c == null) {
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (err != null) org.uxaoro.app.ui.components.ErrorText(err) else LoadingBox()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                item {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        intense = true,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Pin chat", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Switch(
                                    checked = c.pinned,
                                    onCheckedChange = { on ->
                                        // The switch flips at once; a refusal flips it back.
                                        val was = c.pinned
                                        scope.localFirst(
                                            vm,
                                            if (on) "Pin chat" else "Unpin chat",
                                            apply = {
                                                chat = c.copy(pinned = on)
                                            },
                                            undo = { chat = c.copy(pinned = was) },
                                            send = { chat = Api.service().pinChat(chatId, FlagIn(pinned = on)) },
                                        )
                                    },
                                )
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Mute notifications", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                                Switch(
                                    checked = c.muted,
                                    onCheckedChange = { on ->
                                        val was = c.muted
                                        scope.localFirst(
                                            vm,
                                            if (on) "Mute chat" else "Unmute chat",
                                            apply = {
                                                chat = c.copy(muted = on)
                                            },
                                            undo = { chat = c.copy(muted = was) },
                                            send = { chat = Api.service().muteChat(chatId, FlagIn(muted = on)) },
                                        )
                                    },
                                )
                            }
                            Text(
                                when {
                                    // Same line the web client shows in this card.
                                    c.kind == "saved" -> "Your private chat with yourself · E2EE"
                                    c.isGroup -> "Group · E2EE · you: ${c.myRole ?: "member"}"
                                    else -> "Direct message · E2EE"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                if (c.isGroup && canAdmin) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            placeholder = { Text("Group name") },
                            shape = RoundedCornerShape(16.dp),
                        colors = inkFieldColors(),
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    chat = runCatching { Api.service().patchChat(chatId, ChatPatch(title = title.trim())) }.getOrNull() ?: chat
                                }
                            },
                            enabled = title.isNotBlank(),
                        ) { Text("Rename group") }
                        TextButton(onClick = {
                            scope.launch {
                                chat = runCatching {
                                    Api.service().patchChat(chatId, ChatPatch(membersCanInvite = !c.membersCanInvite))
                                }.getOrNull() ?: chat
                            }
                        }) { Text(if (c.membersCanInvite) "Members can invite · on" else "Members can invite · off") }
                    }
                }
                if (c.isGroup && canInvite) {
                    item {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        runCatching { Api.service().chatInviteLink(chatId) }.onSuccess { inv ->
                                            val link = inv.url.ifBlank { uxaoroWebLink("/join?token=${inv.token}") }
                                            shareText(ctx, c.title.ifBlank { "Group" }, "Join my group on Uxaoro: $link")
                                        }.onFailure { err = Api.errorMessage(it) }
                                    }
                                }) { Text("Share invite link") }
                                if (canAdmin) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { Api.service().revokeChatInviteLink(chatId) }.onSuccess {
                                                vm.toast("Invite link revoked")
                                            }.onFailure { err = Api.errorMessage(it) }
                                        }
                                    }) { Text("Revoke link") }
                                }
                            }
                            if (err != null) {
                                Text(err!!, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                scope.launch {
                                    try {
                                        val u = Api.service().lookupUser(n)
                                        val before = chat
                                        val ghost = u.copy(communityRole = "member")
                                        scope.localFirst(
                                            vm,
                                            "Add member",
                                            send = { chat = Api.service().addChatMember(chatId, MemberIn(u.id)) },
                                            apply = {
                                                chat = chat?.copy(members = chat!!.members + ghost)
                                                addName = ""
                                                err = null
                                            },
                                            undo = { chat = before },
                                        )
                                    } catch (t: Throwable) {
                                        err = Api.errorMessage(t)
                                    }
                                }
                            }) { Text("Add") }
                        }
                        if (err != null) {
                            Text(err!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
                item {
                    Text("Members", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(16.dp))
                }
                items(c.members, key = { it.id }) { u ->
                    val role = u.communityRole ?: "member"
                    UserRow(
                        u,
                        subtitle = "@${u.username} · $role",
                        onClick = { vm.go(Screen.User(u.id)) },
                        trailing = {
                            if (c.isGroup && canAdmin && u.id != meId && role != "owner") {
                                Row {
                                    if (c.myRole == "owner") {
                                        TextButton(onClick = {
                                            val next = if (role == "admin") "member" else "admin"
                                            val before = chat
                                            scope.localFirst(
                                                vm,
                                                "Change role",
                                                send = { Api.service().setChatRole(chatId, u.id, RoleInCommunity(next)) },
                                                apply = {
                                                    chat = chat?.copy(members = chat!!.members.map {
                                                        if (it.id == u.id) it.copy(communityRole = next) else it
                                                    })
                                                },
                                                undo = { chat = before },
                                                onSuccess = { load() },
                                            )
                                        }) { Text(if (role == "admin") "Demote" else "Admin") }
                                    }
                                    TextButton(onClick = {
                                        // The member disappears from the list on the tap.
                                        val was = chat
                                        scope.localFirst(
                                            vm,
                                            "Remove member",
                                            apply = {
                                                chat = chat?.copy(members = chat!!.members.filter { it.id != u.id })
                                            },
                                            undo = { chat = was },
                                            send = { Api.service().removeChatMember(chatId, u.id) },
                                            onSuccess = { load() },
                                        )
                                    }) { Text("Remove") }
                                }
                            }
                        },
                    )
                }
                if (c.isGroup && c.myRole != "owner") {
                    item {
                        TextButton(onClick = {
                            scope.localFirst(
                                vm,
                                "Leave group",
                                apply = { vm.toast("Leaving…") },
                                send = {
                                    Api.service().removeChatMember(chatId, meId)
                                    vm.back()
                                    vm.back()
                                },
                            )
                        }) { Text("Leave group") }
                    }
                }
            }
        }
    }
}
