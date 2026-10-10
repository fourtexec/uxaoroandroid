package org.uxaoro.app.ui

import android.content.Context
import org.uxaoro.app.crypto.E2EE
import org.uxaoro.app.data.Api
import org.uxaoro.app.data.ChatOut
import org.uxaoro.app.data.MessageCreate
import org.uxaoro.app.util.parseMsgPayload

/**
 * Saved Messages.
 *
 * The server keeps one private chat per account (`chats.kind = 'saved'`,
 * `GET /chats/saved`, created on first access) and the web client already uses
 * it — saved posts and Cloud Notes were retired in its favour and now answer
 * `410 Gone` telling clients to come here. Android had both of those dead
 * surfaces and none of the replacement, which is what this file fixes.
 *
 * There is no "save" endpoint on purpose: a saved item is an ordinary E2EE
 * message in that chat, carrying a `forward` reference inside the encrypted
 * payload. The server only ever holds ciphertext, and the reference is what lets
 * the bookmark markers on bubbles and posts be rebuilt from the chat itself on
 * any device.
 */

/** The account's Saved Messages chat, created server-side on first access. */
suspend fun savedChat(): ChatOut = Api.service().savedChat()

/**
 * Put an encrypted copy of [payload] into the user's Saved Messages.
 *
 * The chat has exactly one member, so the AES key is wrapped for this account's
 * own published key — the same envelope as any other chat message. Returns the
 * saved chat's id.
 */
suspend fun sendToSavedChat(
    ctx: Context,
    meId: String,
    payload: String,
    msgType: String = "text",
    extra: MessageCreate = MessageCreate(),
): String {
    val chat = Api.service().savedChat()
    // Whatever this account has published is what every one of its devices can
    // open; the local PEM is only a fallback when nothing is published yet.
    val published = runCatching { Api.service().pubkey(meId).pubkey }.getOrNull().orEmpty()
    val pubkeys = mapOf(meId to published.ifBlank { E2EE.publicPem(ctx) })
    val enc = E2EE.encryptBundle(payload, pubkeys, null)
    Api.service().sendMessage(
        chat.id,
        extra.copy(
            msgType = msgType,
            ciphertext = enc.ciphertext,
            nonce = enc.nonce,
            keysJson = enc.keysJson,
        ),
    )
    return chat.id
}

/**
 * Rebuild the local "saved" markers from this account's Saved Messages.
 *
 * The chat *is* the record: a reinstall or a second device starts with no
 * markers at all, so reading the copies back is what puts the bookmarks on
 * posts and messages again after a relogin.
 */
suspend fun syncSavedMarkers(ctx: Context, vm: AppVm, meId: String) {
    if (meId.isBlank()) return
    val chat = runCatching { Api.service().savedChat() }.getOrNull() ?: return
    val page = runCatching { Api.service().messages(chat.id) }.getOrNull() ?: return
    val refs = page.items.mapNotNull { m ->
        val plain = runCatching { E2EE.decrypt(ctx, m.ciphertext, m.nonce, m.keysJson, meId) }.getOrNull()
        runCatching {
            parseMsgPayload(plain, m.imageUrl, m.mediaUrl, m.mediaType, m.mediaMime, m.stickerId).forward
        }.getOrNull()
    }
    vm.syncSavedRefs(refs)
}
