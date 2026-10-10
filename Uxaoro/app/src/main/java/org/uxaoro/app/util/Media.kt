package org.uxaoro.app.util

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

fun textBody(s: String): RequestBody = s.toRequestBody("text/plain".toMediaType())

fun mediaKind(url: String?, mediaType: String? = null, mime: String? = null): String {
    if (!mediaType.isNullOrBlank()) {
        val t = mediaType.lowercase()
        if (t in listOf("image", "video", "audio", "sticker")) return t
    }
    if (!mime.isNullOrBlank()) {
        val m = mime.lowercase()
        if (m.startsWith("video/")) return "video"
        if (m.startsWith("audio/")) return "audio"
        if (m.startsWith("image/")) return "image"
    }
    val u = (url ?: "").lowercase().substringBefore("?")
    return when {
        u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".mov") -> "video"
        u.endsWith(".mp3") || u.endsWith(".ogg") || u.endsWith(".oga") ||
            u.endsWith(".wav") || u.endsWith(".m4a") || u.endsWith(".weba") || u.endsWith(".aac") -> "audio"
        u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") ||
            u.endsWith(".webp") || u.endsWith(".gif") -> "image"
        else -> ""
    }
}

/**
 * Where a copy kept in Saved Messages came from. Written into the encrypted
 * payload only, so the server never sees it — the same `forward` object the web
 * client writes when it forwards a post, which is why cleared/saved state can be
 * rebuilt on a new device just by reading the Saved Messages chat back.
 */
data class MsgForward(
    val messageId: String = "",
    val chatId: String = "",
    val postId: String = "",
    val author: String = "",
    val username: String = "",
    val createdAt: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("message_id", messageId)
        .put("chat_id", chatId)
        .put("post_id", postId)
        .put("author", author)
        .put("username", username)
        .put("created_at", createdAt)

    companion object {
        fun from(o: JSONObject?): MsgForward? {
            if (o == null) return null
            val f = MsgForward(
                messageId = o.optString("message_id", ""),
                chatId = o.optString("chat_id", ""),
                postId = o.optString("post_id", ""),
                author = o.optString("author", ""),
                username = o.optString("username", ""),
                createdAt = o.optString("created_at", ""),
            )
            // A forward that names nothing is not a forward.
            return if (f.messageId.isBlank() && f.postId.isBlank()) null else f
        }
    }
}

/** Telegram-style message envelope: E2EE text may carry media JSON. */
data class MsgParts(
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "",
    val mediaMime: String = "",
    val stickerId: String = "",
    val forward: MsgForward? = null,
)

fun parseMsgPayload(decrypted: String?, imageUrl: String? = null, mediaUrl: String? = null, mediaType: String? = null, mediaMime: String? = null, stickerId: String? = null): MsgParts {
    val fbMedia = mediaUrl.orEmpty().ifBlank { imageUrl.orEmpty() }
    if (decrypted.isNullOrEmpty()) {
        return MsgParts(
            text = "",
            mediaUrl = fbMedia,
            mediaType = mediaType.orEmpty(),
            mediaMime = mediaMime.orEmpty(),
            stickerId = stickerId.orEmpty(),
        )
    }
    val s = decrypted.trim()
    if (s.startsWith("{") && s.endsWith("}")) {
        try {
            val o = JSONObject(s)
            if (o.has("text") || o.has("media_url") || o.has("sticker_id") || o.has("forward")) {
                return MsgParts(
                    text = o.optString("text", ""),
                    mediaUrl = o.optString("media_url", "").ifBlank { o.optString("image_url", "").ifBlank { fbMedia } },
                    mediaType = o.optString("media_type", "").ifBlank { mediaType.orEmpty() },
                    mediaMime = o.optString("media_mime", "").ifBlank { mediaMime.orEmpty() },
                    stickerId = o.optString("sticker_id", "").ifBlank { stickerId.orEmpty() },
                    forward = MsgForward.from(o.optJSONObject("forward")),
                )
            }
        } catch (_: Exception) {
        }
    }
    return MsgParts(
        text = s,
        mediaUrl = fbMedia,
        mediaType = mediaType.orEmpty(),
        mediaMime = mediaMime.orEmpty(),
        stickerId = stickerId.orEmpty(),
    )
}

fun packMsgPayload(
    text: String,
    mediaUrl: String = "",
    mediaType: String = "",
    mediaMime: String = "",
    stickerId: String = "",
    forward: MsgForward? = null,
): String {
    // Plain text stays plain text: the JSON envelope is only worth its overhead
    // when there is media, a sticker or a "saved from" reference to carry.
    if (mediaUrl.isBlank() && stickerId.isBlank() && forward == null) return text
    val o = JSONObject()
        .put("text", text)
        .put("media_url", mediaUrl)
        .put("media_type", mediaType)
        .put("media_mime", mediaMime)
        .put("sticker_id", stickerId)
    if (forward != null) o.put("forward", forward.toJson())
    return o.toString()
}

fun uriPart(ctx: Context, uri: Uri, field: String = "image"): MultipartBody.Part {
    val cr = ctx.contentResolver
    val mime = cr.getType(uri) ?: when (field) {
        "video" -> "video/mp4"
        "audio" -> "audio/mpeg"
        else -> "image/jpeg"
    }
    val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    val ext = when {
        mime == "image/png" -> "png"
        mime == "image/webp" -> "webp"
        mime == "image/gif" -> "gif"
        mime == "video/webm" -> "webm"
        mime == "video/quicktime" -> "mov"
        mime.startsWith("video/") -> "mp4"
        mime == "audio/ogg" -> "ogg"
        mime == "audio/wav" || mime == "audio/x-wav" -> "wav"
        mime == "audio/mp4" -> "m4a"
        mime == "audio/webm" -> "weba"
        mime.startsWith("audio/") -> "mp3"
        else -> "jpg"
    }
    val body = bytes.toRequestBody(mime.toMediaType())
    return MultipartBody.Part.createFormData(field, "upload.$ext", body)
}
