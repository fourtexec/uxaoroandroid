package org.uxaoro.app.ui

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.uxaoro.app.data.Api

/**
 * Local-first mutations for the Android client.
 *
 * Every user-facing write goes through [localFirst]: the in-memory state is
 * changed **before** the request leaves, so the tap is answered instantly, and
 * if the server refuses the change the state is put back and the user is told.
 *
 * This mirrors `backend/web/app.js` (`localFirst` / the optim overlay): show the
 * change now, roll it back and log it if the server disagrees. Nothing is ever
 * saved locally - a reload always shows the server's truth.
 *
 * Failure reporting is a snack (toast) plus a `Log.e` line under the `uxaoro`
 * tag; nothing persistent is written to Settings.
 *
 * `apply` and `undo` are separate parameters on purpose: a trailing
 * `{ ... }` after a call inside a single lambda would be parsed as a trailing
 * lambda argument, which is a compile error the static checker cannot see
 * (it only shows up in the real classpath build).
 */
fun CoroutineScope.localFirst(
    vm: AppVm,
    label: String,
    send: suspend () -> Unit,
    apply: () -> Unit = {},
    undo: () -> Unit = {},
    onSuccess: suspend () -> Unit = {},
    // Forms can surface validation/privacy errors without consuming the HTTP
    // error body a second time (Api.errorMessage reads it once).
    onFailure: (String) -> Unit = {},
) = launch {
    try {
        apply()
    } catch (t: Throwable) {
        Log.e(TAG, "$label: local change failed", t)
    }
    try {
        send()
        try {
            onSuccess()
        } catch (t: Throwable) {
            Log.e(TAG, "$label: follow-up step failed", t)
        }
    } catch (t: Throwable) {
        try {
            undo()
        } catch (u: Throwable) {
            Log.e(TAG, "$label: rollback failed", u)
        }
        val message = Api.errorMessage(t)
        Log.e(TAG, "$label failed: $message", t)
        vm.toast("$label failed — the change was reverted.")
        try {
            onFailure(message)
        } catch (f: Throwable) {
            Log.e(TAG, "$label: failure reporting failed", f)
        }
    }
}

/**
 * Client-side reaction tally, in the same shape the server uses
 * ({emoji, count, me}), so a chip can answer the tap immediately.
 */
fun reactLocally(
    reactions: List<org.uxaoro.app.data.ReactionOut>,
    myReaction: String,
    emoji: String,
): List<org.uxaoro.app.data.ReactionOut> {
    val out = mutableListOf<org.uxaoro.app.data.ReactionOut>()
    for (r in reactions) {
        var count = r.count
        if (r.emoji == myReaction) count -= 1
        if (r.emoji == emoji) count += 1
        if (count > 0) out.add(r.copy(count = count, me = r.emoji == emoji))
    }
    if (out.none { it.emoji == emoji }) {
        out.add(org.uxaoro.app.data.ReactionOut(emoji = emoji, count = 1, me = true))
    }
    if (myReaction == emoji) {
        // Tapping the same emoji again takes it back.
        return out.mapNotNull { r -> if (r.emoji == emoji) null else r }
    }
    return out
}

/** Row replacement helper: swap the row with [id] for [row] (or insert it). */
fun <T> List<T>.upsert(id: String, row: T, idOf: (T) -> String): List<T> {
    var replaced = false
    val out = map {
        if (idOf(it) == id) {
            replaced = true
            row
        } else {
            it
        }
    }
    return if (replaced) out else listOf(row) + out
}

/** Put a row back (rollback helper). */
fun <T> List<T>.withRow(row: T): List<T> = this + row

/** Drop the row with [id]. */
fun <T> List<T>.without(id: String, idOf: (T) -> String): List<T> = filter { idOf(it) != id }

/** The last local id that was handed out, so optimistic rows stay recognisable. */
private var localSeq: Long = 0

fun localId(prefix: String = "local"): String {
    localSeq += 1
    return "$prefix:optim:$localSeq"
}

private const val TAG = "uxaoro"
