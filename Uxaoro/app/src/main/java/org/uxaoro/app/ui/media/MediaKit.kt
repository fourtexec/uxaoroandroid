package org.uxaoro.app.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.Prefs
import org.uxaoro.app.data.StickerFavIn
import org.uxaoro.app.data.StickerOut
import org.uxaoro.app.data.StickerPackOut
import org.uxaoro.app.data.toPlainBody
import org.uxaoro.app.ui.components.inkFieldColors
import org.uxaoro.app.ui.components.AssetSvgIcon
import java.io.ByteArrayOutputStream

/* ---------- inline audio / music player ---------- */

@Composable
fun InlineAudio(url: String, title: String = "Audio", modifier: Modifier = Modifier) {
    if (url.isBlank()) return
    val abs = Api.mediaUrl(url)
    val ctx = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0f) }
    var dur by remember { mutableFloatStateOf(1f) }
    DisposableEffect(abs) {
        onDispose {
            try { player?.release() } catch (_: Exception) {}
            player = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player
            if (p != null) {
                try {
                    dur = (if (p.duration > 0) p.duration else 1).toFloat()
                    pos = p.currentPosition.toFloat()
                } catch (_: Exception) {}
            }
            delay(500)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            try {
                if (playing) {
                    player?.pause()
                    playing = false
                } else {
                    var mp = player
                    if (mp == null) {
                        mp = MediaPlayer().apply {
                            try {
                                if (abs.startsWith("content://")) {
                                    setDataSource(ctx, Uri.parse(abs))
                                } else {
                                    setDataSource(abs)
                                }
                            } catch (_: Exception) {
                                setDataSource(abs)
                            }
                            prepare()
                            setOnCompletionListener { playing = false }
                        }
                        player = mp
                    }
                    mp.start()
                    playing = true
                }
            } catch (_: Exception) {
            }
        }) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playing) "Pause" else "Play")
        }
        Column(Modifier.weight(1f)) {
            Text(title.take(48), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            Slider(
                value = pos.coerceIn(0f, dur),
                onValueChange = {
                    pos = it
                    try { player?.seekTo(it.toInt()) } catch (_: Exception) {}
                },
                valueRange = 0f..dur,
            )
        }
    }
}

/* ---------- Telegram-style photo editor (auto-opens on pick) ---------- */

data class EditedPhoto(val bytes: ByteArray, val mime: String = "image/jpeg")

private fun applyEdits(src: Bitmap, rotate: Int, flip: Boolean, filter: String, bright: Float, contrast: Float, sat: Float): Bitmap {
    val m = Matrix()
    m.postRotate((rotate * 90).toFloat())
    if (flip) m.postScale(-1f, 1f)
    var bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    val cm = ColorMatrix()
    // brightness/contrast/saturation
    val c = contrast
    val b = (bright - 1f) * 255f
    cm.set(
        floatArrayOf(
            c, 0f, 0f, 0f, b,
            0f, c, 0f, 0f, b,
            0f, 0f, c, 0f, b,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val satM = ColorMatrix()
    satM.setSaturation(sat)
    satM.postConcat(cm)
    when (filter) {
        "mono" -> {
            val g = ColorMatrix()
            g.setSaturation(0f)
            satM.postConcat(g)
        }
        "sepia" -> {
            val sep = ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            satM.postConcat(sep)
        }
        "vivid" -> {
            val v = ColorMatrix()
            v.setSaturation(1.5f)
            satM.postConcat(v)
        }
        "warm" -> {
            val w = ColorMatrix(
                floatArrayOf(
                    1.1f, 0f, 0f, 0f, 12f,
                    0f, 1.0f, 0f, 0f, 4f,
                    0f, 0f, 0.9f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            satM.postConcat(w)
        }
        "cold" -> {
            val w = ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, -4f,
                    0f, 1.0f, 0f, 0f, 2f,
                    0f, 0f, 1.12f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            satM.postConcat(w)
        }
        "fade" -> {
            val f = ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, 22f,
                    0f, 0.9f, 0f, 0f, 22f,
                    0f, 0f, 0.9f, 0f, 22f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            satM.postConcat(f)
        }
    }
    val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val paint = Paint()
    paint.colorFilter = ColorMatrixColorFilter(satM)
    canvas.drawBitmap(bmp, 0f, 0f, paint)
    return out
}

@Composable
fun PhotoEditorDialog(
    source: Uri,
    title: String = "Edit photo",
    onDone: (EditedPhoto?) -> Unit,
) {
    val ctx = LocalContext.current
    var base by remember(source) { mutableStateOf<Bitmap?>(null) }
    var rotate by remember { mutableStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("none") }
    var bright by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var sat by remember { mutableFloatStateOf(1f) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source) {
        try {
            val bytes = ctx.contentResolver.openInputStream(source)?.use { it.readBytes() }
            if (bytes != null) base = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(base, rotate, flip, filter, bright, contrast, sat) {
        val b = base ?: return@LaunchedEffect
        // Downscale preview for speed.
        val scale = 1024f / maxOf(b.width, b.height).coerceAtLeast(1)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
        preview = runCatching { applyEdits(small, rotate, flip, filter, bright, contrast, sat) }.getOrNull()
    }
    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
        ) {
            // Scrollable editing area; the apply/cancel bar below stays pinned
            // and always visible, whatever the screen size.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                IconButton(onClick = { onDone(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }
            val pv = preview
            if (pv != null) {
                Image(
                    bitmap = pv.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(androidx.compose.ui.graphics.Color.Black),
                    contentScale = ContentScale.Fit,
                )
            } else {
                // While the bitmap decodes, keep the dialog compact so the
                // "Done" button is visible instead of pushed off-screen.
                Text(
                    "Loading photo…",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(48.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("none" to "Normal", "vivid" to "Vivid", "warm" to "Warm", "cold" to "Cold", "mono" to "Mono", "sepia" to "Sepia", "fade" to "Fade").forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label, fontSize = 12.sp) })
                }
            }
            Text("Light", fontSize = 12.sp)
            Slider(value = bright, onValueChange = { bright = it }, valueRange = 0.5f..1.5f)
            Text("Contrast", fontSize = 12.sp)
            Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.5f..1.5f)
            Text("Color", fontSize = 12.sp)
            Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..2f)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { rotate = (rotate + 1) % 4 }) { Text("Rotate 90°") }
                TextButton(onClick = { flip = !flip }) { Text(if (flip) "Unflip" else "Flip") }
                TextButton(onClick = {
                    rotate = 0; flip = false; filter = "none"
                    bright = 1f; contrast = 1f; sat = 1f
                }) { Text("Reset") }
            }
            }

            // Pinned apply bar — replaces the old inline "Done" button that
            // could end up off-screen on smaller displays.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = { onDone(null) },
                    modifier = Modifier.weight(1f),
                ) {
                  AssetSvgIcon("close-circle", contentDescription = null, size = 16.dp, tint = LocalContentColor.current)
                  Spacer(Modifier.width(6.dp))
                  Text("Cancel")
                }
                Button(
                    onClick = {
                        val b = base
                        if (b == null) {
                            onDone(null)
                            return@Button
                        }
                        val pristine = rotate == 0 && !flip && filter == "none" && bright == 1f && contrast == 1f && sat == 1f
                        if (pristine) {
                            try {
                                val raw = ctx.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: ByteArray(0)
                                onDone(EditedPhoto(raw))
                            } catch (_: Exception) {
                                onDone(null)
                            }
                            return@Button
                        }
                        try {
                            val full = applyEdits(b, rotate, flip, filter, bright, contrast, sat)
                            val bos = ByteArrayOutputStream()
                            full.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                            onDone(EditedPhoto(bos.toByteArray()))
                        } catch (_: Exception) {
                            onDone(null)
                        }
                    },
                    modifier = Modifier.weight(2f),
                ) { Text("Add to post") }
            }
        }
    }
}

/* ---------- smiles & stickers ---------- */

val BuiltinEmojiStickers = listOf(
    // Smileys & Emotion
    "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩","😘","😗","☺️","😚","😙","🥲",
    "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥","😏","😒","🙄","😬","😮‍💨",
    "🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","😵‍💫","🤯","🤠","🥳","🥸","😎",
    "🤓","🧐","😕","🫤","😟","🙁","☹️","😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰","😥","😢","😭","😱","😖",
    "😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩","🤡","👹","👺","👻","👽","👾","🤖",
    "😺","😸","😹","😻","😼","😽","🙀","😿","😾",
    // Gestures & People
    "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉","👆","🖕","👇","☝️","🫵","👍",
    "👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅","🤳","💪","🦾","🦵","🦶","👂","👃","🧠",
    "👀","👅","👄","🫦","👶","🧒","👦","👧","🧑","👨","👩","🧓","👴","👵","💃","🕺","🧘",
    // Hearts & Symbols
    "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝","💟","💯",
    "💢","💥","💫","💦","💨","💣","💬","💭","💤","✨","⭐","🌟","⚡","🔥","🌈","☀️","🌤️","🌙","❄️","☄️",
    // Animals & Nature
    "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧",
    "🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐴","🦄","🐝","🦋","🐌","🐞","🐢","🐍","🦖","🐙","🦀","🐠","🐬","🐳",
    "🦈","🐊","🐘","🦒","🦘","🐎","🐕","🐈","🦜","🦢","🦩","🕊️","🐇","🦦","🦥","🐿️","🦔","🌸","🌹","🌺","🌻","🌷","🌱","🌲","🌴","🌵","🍀","🍁","🍃",
    // Food & Drink
    "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🥑","🥦","🌶️","🌽","🥕",
    "🥐","🥯","🍞","🧀","🥚","🍳","🥞","🧇","🥓","🥩","🍗","🌭","🍔","🍟","🍕","🥪","🌮","🌯","🥗","🍝","🍜","🍲",
    "🍣","🍱","🥟","🍤","🍙","🍚","🍦","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","☕","🍵","🧃","🥤","🧋","🍺","🍻","🥂","🍷","🍸","🍹","🍾","🧊",
    // Activities & Objects
    "⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏓","🏸","🥊","🛹","🎯","🎮","🕹️","🎲","🧩","🧸","🪩","🎭","🎨","🎬","🎤","🎧","🎹","🥁","🎷","🎺","🎸","🎻","🎉","🎊","🎈","🎁","🏆","🥇","🥈","🥉","💎","👑","🚀","🛸","💡","🔒","🔑","🔔"
)

private fun renderTextStickerBytes(label: String): ByteArray {
    val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5A5F.toInt()
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }
    val rect = android.graphics.RectF(40f, 40f, 472f, 472f)
    c.drawRoundRect(rect, 96f, 96f, bgPaint)
    c.drawRoundRect(rect, 96f, 96f, strokePaint)
    val clean = label.trim().ifBlank { "✨" }.take(14)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = when {
            clean.length <= 2 -> 180f
            clean.length <= 6 -> 110f
            else -> 72f
        }
        isFakeBoldText = true
    }
    val y = 256f - (textPaint.descent() + textPaint.ascent()) / 2f
    c.drawText(clean, 256f, y, textPaint)
    val bos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 95, bos)
    return bos.toByteArray()
}

sealed interface StickerPick {
    data class Custom(val id: String, val imageUrl: String) : StickerPick
    data class Emoji(val emoji: String) : StickerPick
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSheet(onPick: (StickerPick) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableIntStateOf(0) }
    var packs by remember { mutableStateOf<List<StickerPackOut>>(emptyList()) }
    var favStickers by remember { mutableStateOf<List<StickerOut>>(emptyList()) }
    var favIds by remember { mutableStateOf(Prefs.favoriteStickers) }
    var createText by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }

    fun reloadStickers() {
        scope.launch {
            val loadedPacks = runCatching { Api.service().stickerPacks() }.getOrDefault(emptyList())
            packs = loadedPacks
            val serverFavs = runCatching { Api.service().favoriteStickers().items }.getOrDefault(emptyList())
            val byId = mutableMapOf<String, StickerOut>()
            loadedPacks.forEach { p -> p.stickers.forEach { s -> byId[s.id] = s } }
            serverFavs.forEach { s -> byId[s.id] = s }
            val mergedIds = (serverFavs.map { it.id } + Prefs.favoriteStickers).toSet()
            Prefs.favoriteStickers = mergedIds
            favIds = mergedIds
            favStickers = mergedIds.mapNotNull { byId[it] }.filter { it.imageUrl.isNotBlank() }
        }
    }

    val pickStickerPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            statusMsg = "Uploading sticker…"
            runCatching {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read image")
                val mime = ctx.contentResolver.getType(uri) ?: "image/png"
                val part = MultipartBody.Part.createFormData("file", "sticker.png", bytes.toRequestBody(mime.toMediaType()))
                val created = Api.service().createSticker(part, "✨".toPlainBody())
                Prefs.favoriteStickers = Prefs.favoriteStickers + created.id
            }.onSuccess {
                statusMsg = "Sticker created & added to favorites!"
                reloadStickers()
            }.onFailure {
                statusMsg = Api.errorMessage(it)
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadStickers()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(16.dp)) {
            TabRow(selectedTabIndex = activeTab, modifier = Modifier.height(36.dp)) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier.height(36.dp),
                    text = { Text("😊 Smiles (${BuiltinEmojiStickers.size})", fontSize = 12.sp) },
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1; reloadStickers() },
                    modifier = Modifier.height(36.dp),
                    text = { Text("✨ Stickers", fontSize = 12.sp) },
                )
            }
            Spacer(Modifier.height(10.dp))

            if (activeTab == 0) {
                Text(
                    "All smiles (${BuiltinEmojiStickers.size}) · Apple / Google / OneUI style set.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Adaptive(52.dp), modifier = Modifier.height(380.dp)) {
                    items(BuiltinEmojiStickers) { e ->
                        Text(
                            e,
                            fontSize = 30.sp,
                            modifier = Modifier
                                .padding(6.dp)
                                .clickable {
                                    scope.launch {
                                        onPick(StickerPick.Emoji(e))
                                        onDismiss()
                                    }
                                },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .height(390.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "★ Favorite Stickers (${favStickers.size})",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (favStickers.isEmpty()) {
                        Text(
                            "No favorite stickers yet. Create one below or tap ★ on any sticker!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyVerticalGrid(columns = GridCells.Adaptive(68.dp), modifier = Modifier.height(110.dp)) {
                            items(favStickers, key = { "fav_" + it.id }) { s ->
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .padding(4.dp)
                                        .clickable {
                                            scope.launch {
                                                onPick(StickerPick.Custom(s.id, s.imageUrl))
                                                onDismiss()
                                            }
                                        },
                                ) {
                                    AsyncImage(
                                        model = Api.mediaUrl(s.imageUrl),
                                        contentDescription = s.emoji,
                                        modifier = Modifier.size(60.dp).align(Alignment.Center),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Text(
                                        "✕",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .clickable {
                                                scope.launch {
                                                    Prefs.favoriteStickers = Prefs.favoriteStickers - s.id
                                                    runCatching { Api.service().removeFavoriteSticker(s.id) }
                                                    reloadStickers()
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "+ Create Sticker",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = createText,
                            onValueChange = { createText = it },
                            placeholder = { Text("Text or emoji (e.g. 🔥 GG)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = inkFieldColors(),
                        )
                        Button(onClick = {
                            scope.launch {
                                statusMsg = "Creating sticker…"
                                runCatching {
                                    val txt = createText.trim().ifBlank { "✨ UX" }
                                    val bytes = renderTextStickerBytes(txt)
                                    val part = MultipartBody.Part.createFormData(
                                        "file",
                                        "sticker.png",
                                        bytes.toRequestBody("image/png".toMediaType()),
                                    )
                                    val created = Api.service().createSticker(part, txt.take(4).toPlainBody())
                                    Prefs.favoriteStickers = Prefs.favoriteStickers + created.id
                                    createText = ""
                                }.onSuccess {
                                    statusMsg = "Sticker created & saved to favorites!"
                                    reloadStickers()
                                }.onFailure {
                                    statusMsg = Api.errorMessage(it)
                                }
                            }
                        }) {
                            Text("Create")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickStickerPhoto.launch("image/*") }) {
                            AssetSvgIcon("camera", contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text("Create sticker from photo")
                        }
                        if (statusMsg.isNotBlank()) {
                            Text(statusMsg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    packs.forEach { p ->
                        if (p.stickers.isEmpty()) return@forEach
                        Text(
                            "${p.title.ifBlank { p.name }} (${p.stickers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyVerticalGrid(columns = GridCells.Adaptive(68.dp), modifier = Modifier.height(140.dp)) {
                            items(p.stickers, key = { it.id }) { s ->
                                val isWebm = s.imageUrl.contains(".webm", ignoreCase = true) || s.imageUrl.contains("webm")
                                val isFav = favIds.contains(s.id)
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .padding(4.dp)
                                        .clickable {
                                            scope.launch {
                                                onPick(StickerPick.Custom(s.id, s.imageUrl))
                                                onDismiss()
                                            }
                                        },
                                ) {
                                    if (isWebm) {
                                        AssetSvgIcon(
                                            "videocamera",
                                            contentDescription = "Video sticker",
                                            modifier = Modifier.align(Alignment.Center),
                                            size = 28.dp,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        AsyncImage(
                                            model = Api.mediaUrl(s.imageUrl),
                                            contentDescription = s.emoji,
                                            modifier = Modifier.size(60.dp).align(Alignment.Center),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .clickable {
                                                scope.launch {
                                                    if (isFav) {
                                                        Prefs.favoriteStickers = Prefs.favoriteStickers - s.id
                                                        runCatching { Api.service().removeFavoriteSticker(s.id) }
                                                    } else {
                                                        Prefs.favoriteStickers = Prefs.favoriteStickers + s.id
                                                        runCatching { Api.service().addFavoriteSticker(StickerFavIn(s.id)) }
                                                    }
                                                    reloadStickers()
                                                }
                                            },
                                    ) {
                                        AssetSvgIcon(
                                            "star-1",
                                            contentDescription = if (isFav) "Remove favorite" else "Add favorite",
                                            size = 18.dp,
                                            tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


