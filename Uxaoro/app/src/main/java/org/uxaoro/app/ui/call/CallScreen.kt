package org.uxaoro.app.ui.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.uxaoro.app.call.CallCrypto
import org.uxaoro.app.call.CallEngine
import org.uxaoro.app.ui.AppVm
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

data class CallUi(
    val phase: String,
    val chatId: String,
    val peerId: String,
    val peerName: String,
    val video: Boolean,
    val callId: String,
)

private fun fmtTimer(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s)
    else "%02d:%02d".format(m, s)
}

@Composable
fun CallOverlay(vm: AppVm, ui: CallUi) {
    val ctx = LocalContext.current
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(ui.callId, ui.phase) {
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (ui.video) need.add(Manifest.permission.CAMERA)
        perms.launch(need.toTypedArray())
    }
    DisposableEffect(ui.callId) {
        val win = (ctx as? Activity)?.window
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Telegram-style call timer: counts while phase == active.
    var seconds by remember(ui.callId) { mutableLongStateOf(0L) }
    LaunchedEffect(ui.callId, ui.phase) {
        if (ui.phase == "active") {
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }

    var muted by remember(ui.callId) { mutableStateOf(false) }
    var speaker by remember(ui.callId) { mutableStateOf(false) }
    var sharing by remember(ui.callId) { mutableStateOf(false) }
    // Показ экрана: системный диалог разрешения MediaProjection.
    val sharePerms = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            sharing = CallEngine.startScreenShare(ctx, res.data as Intent)
            if (!sharing) vm.toast("Screen share failed")
        }
    }

    val emojis = remember(ui.callId) { CallCrypto.emojis(ui.callId) }
    val keyHint = remember(ui.callId) { CallCrypto.keyHint(ui.callId) }
    val initial = remember(ui.peerName) {
        ui.peerName.trim().firstOrNull()?.uppercase() ?: "?"
    }

    val statusText = when (ui.phase) {
        "incoming" -> if (ui.video) "Incoming video call" else "Incoming call"
        "outgoing" -> "Calling…"
        "active" -> fmtTimer(seconds)
        else -> "Connecting…"
    }
    // Intermediate state right after accept looks like key exchange in Telegram.
    val subStatus = when (ui.phase) {
        "active" -> "In call"
        "incoming", "outgoing" -> "End-to-end encrypted"
        else -> "Exchanging encryption keys…"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF17212B), Color(0xFF0E1621), Color(0xFF080D13)),
                ),
            ),
    ) {
        // Remote video fullscreen behind everything (Telegram-like).
        if (ui.video && ui.phase == "active") {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    SurfaceViewRenderer(c).also { v ->
                        CallEngine.eglContext()?.let { v.init(it, null) }
                        v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        CallEngine.attachRemote(v)
                    }
                },
                onRelease = {
                    CallEngine.attachRemote(null)
                    runCatching { it.release() }
                },
            )
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        }

        // Local preview PiP.
        if (ui.video && ui.phase == "active") {
            AndroidView(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 54.dp, end = 16.dp)
                    .size(width = 112.dp, height = 160.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(Color.Black),
                factory = { c ->
                    SurfaceViewRenderer(c).also { v ->
                        CallEngine.eglContext()?.let { v.init(it, null) }
                        v.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        CallEngine.attachLocal(v)
                    }
                },
                onRelease = {
                    CallEngine.attachLocal(null)
                    runCatching { it.release() }
                },
            )
        }

        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ---- Top: avatar + name + status + security emojis ----
            // Смайлики безопасности — сверху у ника. При активном видео
            // большой аватар прячем, чтобы было видно человека, а смайлики
            // остаются компактно сверху.
            val videoOn = ui.video && ui.phase == "active"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp),
            ) {
                if (!videoOn) {
                    Box(
                        Modifier
                            .size(108.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF5B8DEF), Color(0xFF2F6FED)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initial,
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    ui.peerName.ifBlank { "Unknown" },
                    color = Color.White,
                    fontSize = if (videoOn) 20.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    statusText,
                    color = Color(0xFF8A9BA8),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF6EE7A0),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        "  $subStatus",
                        color = Color(0xFF6EE7A0),
                        fontSize = 13.sp,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (videoOn) 6.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = if (videoOn) 8.dp else 18.dp),
                ) {
                    emojis.forEach { e ->
                        Text(e, fontSize = if (videoOn) 22.sp else 34.sp)
                    }
                }
                if (keyHint.isNotBlank()) {
                    Text(
                        "Key $keyHint",
                        color = Color(0x558A9BA8),
                        fontSize = if (videoOn) 10.sp else 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (!videoOn) {
                    Text(
                        "Ask the other person what emojis they see.\nIf they match yours, the call is 100% secure.",
                        color = Color(0xFF8A9BA8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // ---- Bottom: Telegram round controls, ближе к граням ----
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (ui.phase == "incoming") {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FloatingActionButton(
                            onClick = { vm.endCall() },
                            containerColor = Color(0xFFE5484D),
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        FloatingActionButton(
                            onClick = { vm.acceptCall() },
                            containerColor = Color(0xFF31A24C),
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = "Answer",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                muted = !muted
                                CallEngine.setMicMuted(muted)
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (muted) Color.White else Color(0x33FFFFFF),
                                ),
                        ) {
                            Icon(
                                if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (muted) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        if (ui.video) {
                            IconButton(
                                onClick = {
                                    if (sharing) {
                                        CallEngine.stopScreenShare()
                                        sharing = false
                                    } else {
                                        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                            as? MediaProjectionManager
                                        val intent = mpm?.createScreenCaptureIntent()
                                        if (intent != null) sharePerms.launch(intent)
                                        else vm.toast("Screen share unavailable")
                                    }
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (sharing) Color.White else Color(0x33FFFFFF),
                                    ),
                            ) {
                                Icon(
                                    Icons.Default.ScreenShare,
                                    contentDescription = "Share screen",
                                    tint = if (sharing) Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        FloatingActionButton(
                            onClick = { vm.endCall() },
                            containerColor = Color(0xFFE5484D),
                            modifier = Modifier.size(68.dp),
                        ) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Hang up",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        IconButton(
                            onClick = { speaker = !speaker },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (speaker) Color.White else Color(0x33FFFFFF),
                                ),
                        ) {
                            Icon(
                                if (ui.video) Icons.Default.Videocam else Icons.Default.VolumeUp,
                                contentDescription = "Speaker",
                                tint = if (speaker) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Text(
                        if (sharing) "Sharing screen" else if (muted) "Microphone off" else "Hang up",
                        color = Color(0xFF8A9BA8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
        }
    }
}
