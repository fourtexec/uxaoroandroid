package org.uxaoro.app.call

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.uxaoro.app.data.IceServerOut
import org.uxaoro.app.data.WsClient
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

object CallEngine {
    private val main = Handler(Looper.getMainLooper())
    private var factory: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    private var pc: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var videoSender: RtpSender? = null
    private var screenCapturer: VideoCapturer? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null
    @Volatile var sharingScreen: Boolean = false
    private var pendingOffer: SessionDescription? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteVideo: VideoTrack? = null
    private var remoteSink: SurfaceViewRenderer? = null

    @Volatile var callId: String = ""
    @Volatile var peerId: String = ""
    @Volatile var chatId: String = ""
    @Volatile var video: Boolean = false
    var onPhase: ((String) -> Unit)? = null

    fun eglContext(): EglBase.Context? = egl?.eglBaseContext

    @Synchronized
    fun init(ctx: Context) {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx.applicationContext)
                .createInitializationOptions(),
        )
        val base = EglBase.create()
        egl = base
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(base.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(base.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun iceFrom(list: List<IceServerOut>): List<PeerConnection.IceServer> {
        val src = list.ifEmpty { listOf(IceServerOut(listOf("stun:stun.l.google.com:19302"))) }
        return src.mapNotNull { s ->
            val urls = s.urls.filter { it.isNotBlank() }
            if (urls.isEmpty()) null
            else {
                val b = PeerConnection.IceServer.builder(urls)
                if (s.username.isNotBlank()) {
                    b.setUsername(s.username)
                    b.setPassword(s.credential)
                }
                b.createIceServer()
            }
        }
    }

    fun startOutgoing(ctx: Context, ice: List<IceServerOut>, chatId: String, peerId: String, video: Boolean, callId: String) {
        init(ctx)
        this.chatId = chatId
        this.peerId = peerId
        this.video = video
        this.callId = callId
        openPc(ctx, ice, video)
        val mc = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (video) "true" else "false"))
        }
        pc?.createOffer(sdpObs { sdp ->
            if (sdp == null) return@sdpObs
            pc?.setLocalDescription(sdpObs {
                signal("offer", JSONObject().put("sdp", sdp.description).put("sdp_type", sdp.type.canonicalForm()))
            }, sdp)
        }, mc)
    }

    fun accept(ctx: Context, ice: List<IceServerOut>, chatId: String, peerId: String, video: Boolean, callId: String) {
        init(ctx)
        this.chatId = chatId
        this.peerId = peerId
        this.video = video
        this.callId = callId
        openPc(ctx, ice, video)
        pendingOffer?.let { applyRemote(it) }
        pendingOffer = null
    }

    fun onRemoteSignal(kind: String, data: JSONObject) {
        when (kind) {
            "offer" -> {
                val sdp = SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp"))
                if (pc == null) pendingOffer = sdp else applyRemote(sdp)
            }
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp"))
                pc?.setRemoteDescription(sdpObs {}, sdp)
                drainIce()
            }
            "ice" -> {
                val mid = data.optString("sdpMid")
                val idx = data.optInt("sdpMLineIndex", 0)
                val cand = data.optString("candidate")
                if (cand.isBlank()) return
                val ice = IceCandidate(mid, idx, cand)
                if (pc?.remoteDescription == null) pendingIce.add(ice) else pc?.addIceCandidate(ice)
            }
        }
    }

    fun attachRemote(view: SurfaceViewRenderer?) {
        remoteSink?.let { remoteVideo?.removeSink(it) }
        remoteSink = view
        if (view != null) remoteVideo?.addSink(view)
    }

    fun attachLocal(view: SurfaceViewRenderer?) {
        videoTrack?.removeSink(view)
        if (view != null) videoTrack?.addSink(view)
    }

    @Volatile var muted: Boolean = false

    /** Local mic mute — disables the outgoing audio track (Telegram-style mic button). */
    fun setMicMuted(m: Boolean) {
        muted = m
        runCatching { audioTrack?.setEnabled(!m) }
    }

    /**
     * Screen share: swaps the camera video track for a screen-capture track
     * on the fly (no SDP renegotiation). Works in video calls only.
     * @return true if sharing started.
     */
    fun startScreenShare(ctx: Context, permissionData: Intent): Boolean {
        val sender = videoSender ?: return false
        val f = factory ?: return false
        val helper = surfaceHelper ?: return false
        return try {
            stopScreenShare()
            val cap = ScreenCapturerAndroid(permissionData, object : MediaProjection.Callback() {})
            val src = f.createVideoSource(true)
            cap.initialize(helper, ctx.applicationContext, src.capturerObserver)
            cap.startCapture(720, 1280, 24)
            val track = f.createVideoTrack("qid_s", src)
            runCatching { capturer?.stopCapture() }
            sender.setTrack(track, true)
            screenCapturer = cap
            screenVideoSource = src
            screenVideoTrack = track
            sharingScreen = true
            true
        } catch (_: Throwable) {
            runCatching { capturer?.startCapture(640, 480, 24) }
            false
        }
    }

    fun stopScreenShare() {
        if (!sharingScreen && screenCapturer == null) return
        sharingScreen = false
        runCatching { screenCapturer?.stopCapture() }
        runCatching { screenCapturer?.dispose() }
        screenCapturer = null
        runCatching { screenVideoSource?.dispose() }
        screenVideoSource = null
        screenVideoTrack = null
        val sender = videoSender
        val cam = videoTrack
        if (sender != null && cam != null) {
            runCatching { sender.setTrack(cam, true) }
            runCatching { capturer?.startCapture(640, 480, 24) }
        }
    }

    fun hangup(notify: Boolean = true) {
        if (notify && peerId.isNotBlank()) {
            signal("hangup", JSONObject())
        }
        sharingScreen = false
        runCatching { screenCapturer?.stopCapture() }
        runCatching { screenCapturer?.dispose() }
        screenCapturer = null
        runCatching { screenVideoSource?.dispose() }
        screenVideoSource = null
        screenVideoTrack = null
        videoSender = null
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { surfaceHelper?.dispose() }
        surfaceHelper = null
        remoteVideo = null
        remoteSink = null
        audioTrack = null
        videoTrack = null
        muted = false
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { pc?.close() }
        pc = null
        pendingOffer = null
        pendingIce.clear()
        callId = ""
        peerId = ""
        chatId = ""
        main.post { onPhase?.invoke("idle") }
    }

    private fun openPc(ctx: Context, ice: List<IceServerOut>, video: Boolean) {
        stopScreenShare()
        videoSender = null
        runCatching { pc?.close() }
        val rtc = PeerConnection.RTCConfiguration(iceFrom(ice)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory?.createPeerConnection(rtc, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state == PeerConnection.IceConnectionState.CONNECTED ||
                    state == PeerConnection.IceConnectionState.COMPLETED
                ) {
                    main.post { onPhase?.invoke("active") }
                }
                if (state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.CLOSED
                ) {
                    // leave UI to hangup
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate?) {
                if (c == null) return
                signal(
                    "ice",
                    JSONObject()
                        .put("candidate", c.sdp)
                        .put("sdpMid", c.sdpMid)
                        .put("sdpMLineIndex", c.sdpMLineIndex),
                )
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track() ?: return
                if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    remoteVideo = track as VideoTrack
                    remoteSink?.let { remoteVideo?.addSink(it) }
                    main.post { onPhase?.invoke("remote-video") }
                }
            }
        })
        val f = factory ?: return
        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack("qid_a", audioSource)
        pc?.addTrack(audioTrack, listOf("qid"))
        if (video) {
            val cap = makeCapturer(ctx)
            if (cap != null && egl != null) {
                capturer = cap
                surfaceHelper = SurfaceTextureHelper.create("qidcap", egl!!.eglBaseContext)
                videoSource = f.createVideoSource(false)
                cap.initialize(surfaceHelper, ctx, videoSource!!.capturerObserver)
                runCatching { cap.startCapture(640, 480, 24) }
                videoTrack = f.createVideoTrack("qid_v", videoSource)
                videoSender = pc?.addTrack(videoTrack, listOf("qid"))
            }
        }
    }

    private fun applyRemote(sdp: SessionDescription) {
        pc?.setRemoteDescription(sdpObs {
            drainIce()
            if (sdp.type == SessionDescription.Type.OFFER) {
                val mc = MediaConstraints()
                pc?.createAnswer(sdpObs { answer ->
                    if (answer == null) return@sdpObs
                    pc?.setLocalDescription(sdpObs {
                        signal("answer", JSONObject().put("sdp", answer.description).put("sdp_type", "answer"))
                    }, answer)
                }, mc)
            }
        }, sdp)
    }

    private fun drainIce() {
        val copy = pendingIce.toList()
        pendingIce.clear()
        copy.forEach { pc?.addIceCandidate(it) }
    }

    private fun makeCapturer(ctx: Context): VideoCapturer? {
        val e = Camera2Enumerator(ctx)
        val name = e.deviceNames.firstOrNull { e.isFrontFacing(it) } ?: e.deviceNames.firstOrNull() ?: return null
        return e.createCapturer(name, null)
    }

    private fun signal(kind: String, extra: JSONObject) {
        extra.put("kind", kind)
        extra.put("chat_id", chatId)
        extra.put("to", peerId)
        extra.put("call_id", callId)
        extra.put("video", video)
        WsClient.call(extra)
    }

    private fun sdpObs(done: (SessionDescription?) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) { main.post { done(sdp) } }
        override fun onSetSuccess() { main.post { done(null) } }
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
