package org.uxaoro.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.uxaoro.app.net.TlsGuard
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object WsClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private val want = AtomicBoolean(false)
    private val retries = AtomicInteger(0)
    private var reconnectJob: Job? = null
    private val _events = MutableSharedFlow<WsEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    fun connect() {
        want.set(true)
        retries.set(0)
        open()
    }

    private fun open() {
        reconnectJob?.cancel()
        runCatching { socket?.cancel() }
        socket = null
        Api.service()
        val ok = Api.http ?: return
        if (Prefs.accessToken.isBlank()) return
        val req = Request.Builder().url(Api.wsUrl()).build()
        socket = ok.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retries.set(0)
                // First-frame auth: the token never rides in the upgrade URL.
                val hello = JSONObject()
                    .put("event", "hello")
                    .put("data", JSONObject().put("token", Prefs.accessToken))
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parse(text)?.let { _events.tryEmit(it) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) socket = null
                // The TLS guard reset this socket because something is trying to
                // intercept it: stop reconnecting instead of feeding a proxy.
                if (TlsGuard.resetReason(t) != null) {
                    want.set(false)
                    reconnectJob?.cancel()
                    return
                }
                scheduleReconnect()
            }
        })
    }

    private fun parse(text: String): WsEvent? {
        return runCatching {
            val obj = JSONObject(text)
            val event = obj.optString("event")
            if (event.isBlank()) return null
            val data = obj.opt("data")
            val el = when (data) {
                null, JSONObject.NULL -> null
                else -> Api.gson.fromJson(data.toString(), com.google.gson.JsonElement::class.java)
            }
            WsEvent(event, el)
        }.getOrNull()
    }

    private fun scheduleReconnect() {
        if (!want.get()) return
        if (reconnectJob?.isActive == true) return
        val n = retries.getAndIncrement().coerceAtMost(5)
        val wait = (1000L shl n).coerceAtMost(20_000L)
        reconnectJob = scope.launch {
            delay(wait)
            if (want.get()) open()
        }
    }

    fun typing(chatId: String) {
        send("typing", JSONObject().put("chat_id", chatId))
    }

    fun send(event: String, data: JSONObject) {
        val o = JSONObject()
        o.put("event", event)
        o.put("data", data)
        socket?.send(o.toString())
    }

    fun call(data: JSONObject) = send("call", data)

    fun disconnect() {
        want.set(false)
        reconnectJob?.cancel()
        runCatching { socket?.close(1000, "bye") }
        socket = null
    }
}
