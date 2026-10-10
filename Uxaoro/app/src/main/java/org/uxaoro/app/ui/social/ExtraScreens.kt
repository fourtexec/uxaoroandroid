package org.uxaoro.app.ui.social

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import org.uxaoro.app.ui.theme.BlurScaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Cache
import org.uxaoro.app.data.StoryOut
import org.uxaoro.app.ui.AppVm
import org.uxaoro.app.ui.localFirst
import org.uxaoro.app.ui.Screen
import org.uxaoro.app.ui.components.DATopBar
import org.uxaoro.app.ui.components.EmptyBox
import org.uxaoro.app.ui.components.PrimaryButton
import org.uxaoro.app.ui.components.PrimaryField
import org.uxaoro.app.util.textBody
import org.uxaoro.app.util.uriPart
import org.uxaoro.app.ui.components.AssetSvgIcon
import androidx.compose.foundation.layout.width

@Composable
fun CreateStoryScreen(vm: AppVm) {
    var image by remember { mutableStateOf<Uri?>(null) }
    var video by remember { mutableStateOf<Uri?>(null) }
    var editUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var quota by remember { mutableStateOf<Int?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(vm.me?.premium) {
        quota = runCatching { Api.service().premiumStatus().storiesLimit }.getOrNull()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { editUri = it }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { video = it; image = null }
    editUri?.let { uri ->
        org.uxaoro.app.ui.media.PhotoEditorDialog(source = uri, title = "Edit story") { edited ->
            if (edited != null) {
                try {
                    val f = java.io.File(ctx.cacheDir, "story-${System.currentTimeMillis()}.jpg")
                    f.writeBytes(edited.bytes)
                    image = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                    video = null
                } catch (_: Exception) {
                    image = uri
                }
            }
            editUri = null
        }
    }
    BlurScaffold(topBar = { DATopBar("New story", onBack = { vm.back() }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Daily limit: ${quota ?: if (vm.me?.premium == true) 40 else 4} stories", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.go(Screen.Premium) }) {
              AssetSvgIcon("crown", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
              Spacer(Modifier.width(6.dp))
              Text("✦ View Premium limits")
            }
            Row {
                TextButton(onClick = { pickPhoto.launch("image/*") }) { Text("Photo") }
                TextButton(onClick = { pickVideo.launch("video/*") }) {
                  AssetSvgIcon("videocamera", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Video")
                }
            }
            image?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
                Row {
                    TextButton(onClick = { editUri = it }) { Text("Edit") }
                    TextButton(onClick = { image = null }) {
                      AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                      Spacer(Modifier.width(6.dp))
                      Text("Remove")
                    }
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
            if (image == null && video == null) {
                Text("Pick a photo or video — the editor opens automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            PrimaryField(caption, { caption = it }, "Caption (optional)")
            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Share story", enabled = image != null || video != null, icon = "send-square") {
                val uri = image ?: video ?: return@PrimaryButton
                // Local-first: the story tile is in the tray of the screen the
                // user lands on; a refusal removes it and brings this form back.
                val lid = org.uxaoro.app.ui.localId("story")
                vm.addDraftStory(
                    org.uxaoro.app.data.StoryOut(
                        id = lid,
                        authorId = vm.me?.id.orEmpty(),
                        caption = caption,
                    ),
                )
                vm.back()
                vm.launchAppTask {
                    localFirst(
                        vm,
                        "Share story",
                        send = {
                            val part = withContext(Dispatchers.IO) { uriPart(ctx, uri, "file") }
                            Api.service().createStory(part, textBody(caption))
                            vm.removeDraftStory(lid)
                        },
                        undo = { vm.removeDraftStory(lid) },
                    )
                }
            }
        }
    }
}

@Composable
fun StoryViewerScreen(vm: AppVm, userId: String) {
    var stories by remember { mutableStateOf<List<StoryOut>>(emptyList()) }
    var idx by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(userId) {
        val feed = runCatching { Api.service().stories() }.getOrNull() ?: Cache.loadStories()
        stories = feed.items.firstOrNull { it.user?.id == userId }?.stories.orEmpty()
        stories.firstOrNull()?.let { s -> runCatching { Api.service().viewStory(s.id) } }
    }
    val s = stories.getOrNull(idx)
    BlurScaffold(topBar = { DATopBar(s?.author?.displayName ?: "Story", onBack = { vm.back() }) }) { pad ->
        if (s == null) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { EmptyBox("No stories") }
        else Box(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val isVideo = s.mediaType == "video" || s.imageUrl.lowercase().endsWith(".mp4") || s.imageUrl.lowercase().endsWith(".webm") || s.imageUrl.lowercase().endsWith(".mov")
            if (isVideo) {
                // Stories autoplay, loop and fill the screen (no fullscreen
                // button: they already are fullscreen).
                org.uxaoro.app.ui.media.InlineVideo(
                    s.imageUrl,
                    Modifier.fillMaxSize(),
                    autoPlay = true,
                    loop = true,
                    immersive = true,
                    allowFullscreen = false,
                )
            } else {
                AsyncImage(
                    model = Api.mediaUrl(s.imageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                if (s.caption.isNotBlank()) Text(s.caption, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                Row {
                    if (idx < stories.lastIndex) {
                        TextButton(onClick = {
                            idx++
                            stories.getOrNull(idx)?.let { n -> scope.launch { runCatching { Api.service().viewStory(n.id) } } }
                        }) { Text("Next") }
                    }
                    if (s.authorId == vm.me?.id) {
                        TextButton(onClick = {
                            scope.localFirst(
                                vm,
                                "Delete story",
                                send = {
                                    Api.service().deleteStory(s.id)
                                    vm.back()
                                },
                            )
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
