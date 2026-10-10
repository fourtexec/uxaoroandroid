package org.uxaoro.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.uxaoro.app.ui.theme.glassBlurBar
import org.uxaoro.app.ui.theme.liquidGlass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.PostOut
import org.uxaoro.app.data.ReactionOut
import org.uxaoro.app.data.Reacts
import org.uxaoro.app.data.UserPublic
import org.uxaoro.app.ui.premium.PremiumBadges
import org.uxaoro.app.ui.premium.PremiumName
import org.uxaoro.app.ui.motion.HeartBurst
import org.uxaoro.app.ui.motion.shake
import org.uxaoro.app.ui.motion.TypingDots
import org.uxaoro.app.ui.motion.BurstOverlay
import org.uxaoro.app.ui.motion.AnimatedCounter
import org.uxaoro.app.ui.motion.pressScale
import org.uxaoro.app.ui.motion.pulse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UxaoroLogo(modifier: Modifier = Modifier) {
    val dark = when (org.uxaoro.app.data.Prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val resId = if (dark) org.uxaoro.app.R.drawable.logo_dark else org.uxaoro.app.R.drawable.logo_light
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(resId),
        contentDescription = "Uxaoro",
        modifier = modifier,
    )
}

@Composable
fun BetaTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "BETA",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DATopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    /** Optional second line, e.g. the peer's presence in a chat. */
    subtitle: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .height(44.dp)
            .glassBlurBar(RoundedCornerShape(14.dp), elevation = 6.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (leadingContent != null) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { leadingContent() }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.weight(1f)) {
            if (title == "Uxaoro") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UxaoroLogo(Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Uxaoro",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(6.dp))
                    BetaTag()
                }
            } else {
                Column {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Int = 44, onClick: (() -> Unit)? = null) {
    val initials = name.trim().take(1).uppercase().ifBlank { "?" }
    val mod = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    Box(mod, contentAlignment = Alignment.Center) {
        val abs = Api.mediaUrl(url)
        if (abs.isNotBlank()) {
            AsyncImage(
                model = abs,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                initials,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.4).sp,
            )
        }
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.pulse(0.88f, 1.08f, 900))
            Spacer(Modifier.height(14.dp))
            TypingDots()
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorText(text: String?) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn(tween(180)) + expandVertically(),
        exit = fadeOut(tween(120)) + shrinkVertically(),
    ) {
        Text(text.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp).shake(text?.hashCode() ?: 0))
    }
}

fun formatTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val inst = Instant.parse(iso)
        val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())
        fmt.format(inst)
    } catch (_: Exception) {
        iso.take(16).replace("T", " ")
    }
}

/**
 * "Last seen" the way a chat expects it: relative while that is meaningful,
 * an absolute date once "3 weeks ago" stops being useful.
 */
fun formatLastSeen(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val when_ = Instant.parse(iso)
        val now = Instant.now()
        val mins = java.time.Duration.between(when_, now).toMinutes()
        when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 60 * 24 -> "${mins / 60} h ago"
            mins < 60 * 24 * 2 -> "yesterday"
            else -> {
                val fmt = DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault())
                fmt.format(when_)
            }
        }
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun UserRow(
    user: UserPublic,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.avatarUrl, user.displayName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumName(user, modifier = Modifier.weight(1f, fill = false))
                if (user.premium) {
                    Spacer(Modifier.width(4.dp))
                    PremiumBadges(user, compact = true)
                }
                if (user.online) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(8.dp)
                            .pulse(0.75f, 1.25f, 800)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                subtitle ?: "@${user.username}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun PostCard(
    post: PostOut,
    onOpen: () -> Unit,
    onAuthor: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onRepost: () -> Unit,
    onReact: (String) -> Unit = {},
    onVotePoll: ((String) -> Unit)? = null,
    onTip: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onHashtag: ((String) -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    /** True while the post is on screen but not confirmed by the server yet. */
    pending: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .liquidGlass(RoundedCornerShape(16.dp))
            .pointerInput(post.id, post.liked) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onDoubleTap = { onLike() },
                )
            },
    ) {
        Column(Modifier.padding(14.dp)) {
            if (post.isPinned) {
                Text(
                    "Pinned post",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.author?.avatarUrl, post.author?.displayName ?: "?", 40, onAuthor)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        post.author?.let { author ->
                            if (author.statusEmoji.isNotBlank()) {
                                Text("${author.statusEmoji} ", fontSize = 14.sp)
                            }
                            PremiumName(author, modifier = Modifier.weight(1f, fill = false).clickable(onClick = onAuthor))
                            if (author.premium) {
                                Spacer(Modifier.width(4.dp))
                                PremiumBadges(author, compact = true)
                            }
                        } ?: Text("User", color = MaterialTheme.colorScheme.onBackground)
                    }
                    val meta = buildString {
                        append(formatTime(post.createdAt))
                        if (!post.communityName.isNullOrBlank()) append(" · ${post.communityName}")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (post.viewsCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            AssetSvgIcon("eye", contentDescription = "Views", size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(" ${post.viewsCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (pending) {
                            Spacer(Modifier.width(6.dp))
                            Text("sending…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (post.saved) {
                    // The copy itself lives in Saved Messages; this is the
                    // bookmark that says it was taken.
                    AssetSvgIcon(
                        "bookmark",
                        contentDescription = "Saved to Saved Messages",
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                    GlassMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (onForward != null) {
                            GlassMenuItem(text = "Forward to Saved Messages", leadingIcon = "bookmark", onClick = {
                                menu = false
                                onForward()
                            })
                        }
                        if (onPin != null) {
                            GlassMenuItem(text = if (post.isPinned) "Unpin post" else "Pin post", leadingIcon = "pin", onClick = {
                                menu = false
                                onPin()
                            })
                        }
                        if (onDelete != null) {
                            GlassMenuItem(text = "Delete post", leadingIcon = "trash-bin-trash", danger = true, onClick = {
                                menu = false
                                onDelete()
                            })
                        }
                        if (onReport != null) {
                            GlassMenuItem(text = "Report", leadingIcon = "flag", onClick = {
                                menu = false
                                onReport()
                            })
                        }
                    }
                }
            }
            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(post.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            if (post.hashtags.isNotEmpty() && onHashtag != null) {
                Spacer(Modifier.height(6.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    post.hashtags.forEach { tag ->
                        Text(
                            "#$tag",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { onHashtag(tag) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            post.poll?.let { poll ->
                if (poll.options.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                AssetSvgIcon("chart", contentDescription = "Poll", size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(poll.question, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                            Text(
                                "${poll.totalVoters.coerceAtLeast(poll.totalVotes)} votes",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        poll.options.forEach { opt ->
                            val pctFloat = (opt.pct.toFloat() / 100f).coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        1.dp,
                                        if (opt.voted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable(enabled = onVotePoll != null) { onVotePoll?.invoke(opt.id) },
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(pctFloat)
                                        .height(38.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = if (opt.voted) 0.25f else 0.12f,
                                            ),
                                        ),
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .padding(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        (if (opt.voted) "✓ " else "") + opt.text,
                                        fontSize = 13.sp,
                                        fontWeight = if (opt.voted) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${opt.pct.toInt()}% (${opt.votes})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (post.imageUrl.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = Api.mediaUrl(post.imageUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { zoom = true },
                    contentScale = ContentScale.FillWidth,
                )
            }
            if (post.videoUrl.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                org.uxaoro.app.ui.media.InlineVideo(post.videoUrl)
            } else if (post.mediaType == "video" && post.imageUrl.isNotBlank()) {
                // Back-compat: older rows store video in image_url.
                Spacer(Modifier.height(10.dp))
                org.uxaoro.app.ui.media.InlineVideo(post.imageUrl)
            }
            if (post.audioUrl.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                org.uxaoro.app.ui.media.InlineAudio(post.audioUrl, post.text.ifBlank { "Audio" })
            } else if (post.mediaType == "audio" && post.imageUrl.isNotBlank() && post.videoUrl.isBlank()) {
                Spacer(Modifier.height(10.dp))
                org.uxaoro.app.ui.media.InlineAudio(post.imageUrl, post.text.ifBlank { "Audio" })
            }
            post.originalPost?.let { orig ->
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            orig.author?.displayName ?: "Post",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (orig.text.isNotBlank()) {
                            Text(orig.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                var likeBurst by remember { mutableStateOf(0) }
                val heart = HeartBurst(post.liked)
                val heartTint by animateColorAsState(
                    if (post.liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(220),
                    label = "likeTint",
                )
                BurstOverlay(trigger = likeBurst, color = MaterialTheme.colorScheme.error) {
                    IconButton(onClick = { likeBurst += 1; onLike() }) {
                        AssetSvgIcon(
                            "heart",
                            contentDescription = "Like",
                            tint = heartTint,
                            modifier = Modifier.graphicsLayer {
                                scaleX = heart
                                scaleY = heart
                            },
                        )
                    }
                }
                AnimatedCounter(post.likesCount.toLong(), fontSize = 13.sp)
                val ink = MaterialTheme.colorScheme.onBackground
                IconButton(onClick = onComment) {
                    AssetSvgIcon("chat-round-dots", contentDescription = "Comments", tint = ink)
                }
                Text("${post.commentsCount}", fontSize = 13.sp, color = ink)
                IconButton(onClick = onRepost) {
                    AssetSvgIcon("repeat", contentDescription = "Repost", tint = ink)
                }
                if (post.repostsCount > 0) Text("${post.repostsCount}", fontSize = 13.sp, color = ink)
                if (onTip != null) {
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = onTip, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        AssetSvgIcon("wallet", contentDescription = "Tip", size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text(if (post.tipsUx > 0) "${post.tipsUx} UX" else "Tip", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                } else if (post.tipsUx > 0) {
                    Spacer(Modifier.width(8.dp))
                    AssetSvgIcon("wallet", contentDescription = "Tip amount", size = 14.dp, tint = MaterialTheme.colorScheme.primary)
                    Text(" ${post.tipsUx} UX", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            ReactionStrip(post.reactions, post.myReaction, onReact)
        }
    }
    if (zoom && post.imageUrl.isNotBlank()) {
        Dialog(onDismissRequest = { zoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable { zoom = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = Api.mediaUrl(post.imageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
fun ReactionPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var preview by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .glassSurface(RoundedCornerShape(22.dp), elevation = 18.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)),
            )
            Text(
                "Reactions",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
            )
            // Enlarged emoji preview while a reaction is held down.
            Text(
                preview ?: "",
                fontSize = 44.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(58.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Reacts.quick.forEach { e ->
                    Text(
                        e,
                        fontSize = 32.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .pointerInput(e) {
                                detectTapGestures(
                                    onPress = {
                                        preview = e
                                        tryAwaitRelease()
                                        preview = null
                                    },
                                    onTap = { onPick(e) },
                                )
                            }
                            .padding(6.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(44.dp),
                modifier = Modifier.height(320.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(Reacts.all) { e ->
                    Text(
                        e,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .pointerInput(e) {
                                detectTapGestures(
                                    onPress = {
                                        preview = e
                                        tryAwaitRelease()
                                        preview = null
                                    },
                                    onTap = { onPick(e) },
                                )
                            }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ReactionStrip(
    reactions: List<ReactionOut>,
    mine: String?,
    onReact: (String) -> Unit,
    showAdd: Boolean = true,
) {
    var pick by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.filter { it.count > 0 }.forEach { hit ->
            val on = Reacts.same(mine, hit.emoji) || hit.me
            // Pill chip: accent-filled when it is my reaction, quiet
            // translucent one otherwise. Pops when the count changes.
            val pop = remember { Animatable(0.6f) }
            var chipBurst by remember { mutableStateOf(0) }
            LaunchedEffect(hit.count, on) {
                pop.animateTo(0.85f, tween(60))
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            BurstOverlay(trigger = chipBurst, color = MaterialTheme.colorScheme.primary) {
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { chipBurst += 1; onReact(hit.emoji) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(hit.emoji, fontSize = 15.sp)
                        if (hit.count > 1) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${hit.count}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
        }
        if (showAdd) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { pick = true }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    "+",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
    if (pick) {
        ReactionPicker(
            onPick = { onReact(it); pick = false },
            onDismiss = { pick = false },
        )
    }
}

@Composable
fun inkFieldColors() = OutlinedTextFieldDefaults.colors(
    // Keep text fields opaque so animated/patterned backdrops never leak
    // through their rounded surfaces.
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    errorContainerColor = MaterialTheme.colorScheme.surface,
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
    cursorColor = MaterialTheme.colorScheme.onBackground,
    focusedLabelColor = MaterialTheme.colorScheme.onBackground,
    unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
    disabledLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
    focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
    focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f),
    focusedLeadingIconColor = MaterialTheme.colorScheme.onBackground,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onBackground,
    focusedTrailingIconColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onBackground,
)

@Composable
fun PrimaryField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visual: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val shape = RoundedCornerShape(16.dp)
    val isPassword = visual is PasswordVisualTransformation
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, color = MaterialTheme.colorScheme.onBackground) },
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
        singleLine = singleLine,
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            autoCorrect = !isPassword,
        ),
        shape = shape,
        colors = inkFieldColors(),
    )
}

/**
 * In-app keypad for one-time numeric codes. It intentionally contains no
 * focusable text field, so Android never opens the system numeric keyboard.
 * Each newly entered digit gives a short confirmation pop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumericCodePad(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    digits: Int = 6,
    openOnShow: Boolean = true,
) {
    val value = code.filter(Char::isDigit).take(digits)
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    var keypadOpen by remember { mutableStateOf(openOnShow) }

    // Email and login verification open from the bottom immediately; settings
    // keep the same control closed until it is tapped.
    LaunchedEffect(openOnShow) {
        if (openOnShow) keypadOpen = true
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { keypadOpen = true }
            .padding(horizontal = 14.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CodeDigits(value = value, digits = digits, ink = ink, accent = accent)
            Spacer(Modifier.height(8.dp))
            Text(
                if (value.isEmpty()) "Tap to enter code" else "Tap to edit code",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (keypadOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { keypadOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.56f),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
                )
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Enter $digits-digit code", fontWeight = FontWeight.SemiBold, color = ink)
                Spacer(Modifier.height(14.dp))
                // The same animated cells stay visible above the app keyboard.
                CodeDigits(value = value, digits = digits, ink = ink, accent = accent)
                Spacer(Modifier.height(20.dp))
                CodeKeypad(
                    value = value,
                    digits = digits,
                    onCodeChange = onCodeChange,
                )
                TextButton(onClick = { keypadOpen = false }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun CodeDigits(value: String, digits: Int, ink: Color, accent: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(digits) { index ->
            CodeDigitCell(
                digit = value.getOrNull(index),
                active = index == value.length && value.length < digits,
                ink = ink,
                accent = accent,
            )
        }
    }
}

@Composable
private fun CodeKeypad(value: String, digits: Int, onCodeChange: (String) -> Unit) {
    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { digit ->
                CodeKey(
                    label = digit,
                    modifier = Modifier.weight(1f),
                    onClick = { if (value.length < digits) onCodeChange(value + digit) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Keeps zero centered under the three-column grid.
        Spacer(Modifier.weight(1f))
        CodeKey(
            label = "0",
            modifier = Modifier.weight(1f),
            onClick = { if (value.length < digits) onCodeChange(value + "0") },
        )
        CodeKey(
            label = "⌫",
            modifier = Modifier.weight(1f),
            enabled = value.isNotEmpty(),
            onClick = { onCodeChange(value.dropLast(1)) },
        )
    }
}

@Composable
private fun CodeDigitCell(digit: Char?, active: Boolean, ink: Color, accent: Color) {
    // Key by the value: a fresh digit starts small and springs to its resting size.
    var settled by remember(digit) { mutableStateOf(digit == null) }
    LaunchedEffect(digit) {
        if (digit != null) settled = true
    }
    val scale by animateFloatAsState(
        targetValue = if (settled) 1f else 0.72f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "codeDigit",
    )
    val border = if (active) accent else ink.copy(alpha = 0.28f)
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (active) 2.dp else 1.dp, border, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit?.toString().orEmpty(),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}

@Composable
private fun CodeKey(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (label == "⌫") 22.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
    }
}

/** Choose whichever foreground has the higher WCAG contrast against the button fill. */
fun inkButtonContentColor(containerColor: Color): Color {
    val luminance = containerColor.luminance()
    val blackContrast = (luminance + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (luminance + 0.05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

@Composable
fun inkButtonColors(): ButtonColors {
    val cs = MaterialTheme.colorScheme
    val ink = inkButtonContentColor(cs.onBackground)
    return ButtonDefaults.buttonColors(
        containerColor = cs.onBackground,
        contentColor = ink,
        disabledContainerColor = cs.onBackground.copy(alpha = 0.28f),
        disabledContentColor = ink.copy(alpha = 0.6f),
    )
}

@Composable
fun inkTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = MaterialTheme.colorScheme.onBackground,
    disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
)

/**
 * The app's main CTA. `icon` names an SVG from the repository's icons/ set
 * (copied into the APK by the syncUxaoroIcons task), so a primary button is
 * never left without an icon — and never fakes one with a text glyph.
 */
@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, icon: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        colors = inkButtonColors(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .pressScale(interaction),
        shape = RoundedCornerShape(16.dp),
    ) {
        val ink = LocalContentColor.current
        if (icon != null) {
            AssetSvgIcon(icon, contentDescription = null, size = 20.dp, tint = ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeAccentPickers(onChanged: () -> Unit) {
    val rev = org.uxaoro.app.ui.theme.AppearanceState.rev
    @Suppress("UNUSED_VARIABLE")
    val seen = rev
    val p = org.uxaoro.app.data.Prefs
    val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    val ink = MaterialTheme.colorScheme.onBackground
    Text("Theme", fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.padding(top = 8.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        themes.forEach { (k, l) ->
            val sel = p.theme == k
            TextButton(
                onClick = {
                    p.theme = k
                    onChanged()
                },
                colors = inkTextButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(l, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    Text("Accent color", fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.padding(top = 8.dp))
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        org.uxaoro.app.ui.theme.Accents.forEach { a ->
            val sel = p.accent == a.key
            Box(
                Modifier
                    .size(36.dp)
                    .border(
                        width = if (sel) 2.dp else 0.dp,
                        color = if (sel) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                        shape = CircleShape,
                    )
                    .padding(4.dp)
                    .size(if (sel) 24.dp else 30.dp)
                    .clip(CircleShape)
                    .background(a.color)
                    .clickable {
                        p.accent = a.key
                        onChanged()
                    },
            )
        }
    }
    Text("Text size", fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.padding(top = 8.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("small" to "Small", "normal" to "Default", "large" to "Large").forEach { (k, l) ->
            val sel = p.fontScale == k
            TextButton(onClick = { p.fontScale = k; onChanged() }, colors = inkTextButtonColors()) {
                Text(l, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    Text("Display", fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.padding(top = 8.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { p.amoled = !p.amoled; onChanged() }, colors = inkTextButtonColors()) {
            Text(if (p.amoled) "AMOLED on" else "AMOLED off")
        }
        TextButton(onClick = { p.compact = !p.compact; onChanged() }, colors = inkTextButtonColors()) {
            Text(if (p.compact) "Compact" else "Comfortable")
        }
    }
    TextButton(onClick = { p.reduceMotion = !p.reduceMotion; onChanged() }, colors = inkTextButtonColors()) {
        Text(if (p.reduceMotion) "Reduce motion: on" else "Reduce motion: off")
    }
}
