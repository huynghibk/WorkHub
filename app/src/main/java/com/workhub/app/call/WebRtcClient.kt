package com.workhub.app.call

import android.content.Context
import android.media.AudioManager
import java.util.concurrent.atomic.AtomicBoolean
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Minimal 1-1 WebRTC peer: STUN only, audio always, video optional.
 */
class WebRtcClient(
    context: Context,
    private val videoEnabled: Boolean,
    private val onIce: (IceCandidate) -> Unit,
    private val onConnected: () -> Unit,
    private val onRemoteVideo: (VideoTrack?) -> Unit,
    private val onFailed: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val eglBase: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private val disposed = AtomicBoolean(false)
    private var micMuted = false
    private var camOff = false

    val eglContext: EglBase.Context get() = eglBase.eglBaseContext

    init {
        ensureFactoryInitialized(appContext)
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
    }

    companion object {
        private val factoryReady = AtomicBoolean(false)

        private fun ensureFactoryInitialized(context: Context) {
            if (factoryReady.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions(),
                )
            }
        }
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        videoTrack?.let { track ->
            localRenderer?.let { track.removeSink(it) }
        }
        localRenderer = local
        remoteRenderer = remote
        if (local != null) {
            runCatching {
                local.init(eglBase.eglBaseContext, null)
                local.setMirror(true)
            }
            videoTrack?.addSink(local)
        }
        if (remote != null) {
            runCatching { remote.init(eglBase.eglBaseContext, null) }
        }
    }

    fun startLocalMedia() {
        ensurePeer()
        val audioConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(audioConstraints)
        audioTrack = factory.createAudioTrack("AUD0", audioSource)
        peer?.addTrack(audioTrack, listOf("stream0"))

        if (videoEnabled) {
            val enumerator = Camera2Enumerator(appContext)
            val device = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
            if (device != null) {
                capturer = enumerator.createCapturer(device, null)
                surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                videoSource = factory.createVideoSource(false)
                capturer?.initialize(surfaceHelper, appContext, videoSource!!.capturerObserver)
                capturer?.startCapture(640, 480, 24)
                videoTrack = factory.createVideoTrack("VID0", videoSource)
                localRenderer?.let { videoTrack?.addSink(it) }
                peer?.addTrack(videoTrack, listOf("stream0"))
            }
        }
        setSpeaker(videoEnabled)
    }

    fun createOffer(onSdp: (SessionDescription) -> Unit) {
        val pc = ensurePeer()
        pc.createOffer(object : SdpAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(object : SdpAdapter() {
                    override fun onSetSuccess() = onSdp(desc)
                    override fun onSetFailure(error: String?) = onFailed(error ?: "setLocal offer failed")
                }, desc)
            }

            override fun onCreateFailure(error: String?) = onFailed(error ?: "createOffer failed")
        }, mediaConstraints())
    }

    fun handleRemoteOffer(sdp: String, onAnswer: (SessionDescription) -> Unit) {
        val pc = ensurePeer()
        val remote = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescription(object : SdpAdapter() {
            override fun onSetSuccess() {
                pc.createAnswer(object : SdpAdapter() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc == null) return
                        pc.setLocalDescription(object : SdpAdapter() {
                            override fun onSetSuccess() = onAnswer(desc)
                            override fun onSetFailure(error: String?) =
                                onFailed(error ?: "setLocal answer failed")
                        }, desc)
                    }

                    override fun onCreateFailure(error: String?) =
                        onFailed(error ?: "createAnswer failed")
                }, mediaConstraints())
            }

            override fun onSetFailure(error: String?) = onFailed(error ?: "setRemote offer failed")
        }, remote)
    }

    fun handleRemoteAnswer(sdp: String) {
        val pc = peer ?: return
        pc.setRemoteDescription(
            object : SdpAdapter() {
                override fun onSetFailure(error: String?) = onFailed(error ?: "setRemote answer failed")
            },
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    fun addIce(candidate: IceCandidate) {
        peer?.addIceCandidate(candidate)
    }

    fun setMicMuted(muted: Boolean) {
        micMuted = muted
        audioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        camOff = !enabled
        videoTrack?.setEnabled(enabled)
        if (!enabled) {
            runCatching { capturer?.stopCapture() }
        } else {
            runCatching { capturer?.startCapture(640, 480, 24) }
        }
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    fun setSpeaker(on: Boolean) {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = on
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        capturer = null
        videoTrack?.removeSink(localRenderer)
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        surfaceHelper?.dispose()
        surfaceHelper = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        peer?.close()
        peer?.dispose()
        peer = null
        // Renderers are owned by Compose AndroidView — only detach sinks.
        localRenderer = null
        remoteRenderer = null
        factory.dispose()
        eglBase.release()
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = false
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun ensurePeer(): PeerConnection {
        peer?.let { return it }
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val created = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED,
                    -> onConnected()
                    PeerConnection.IceConnectionState.FAILED -> onFailed("ICE failed")
                    PeerConnection.IceConnectionState.DISCONNECTED -> Unit
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) onIce(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(dc: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteRenderer?.let { track.addSink(it) }
                    onRemoteVideo(track)
                }
            }
        }) ?: error("PeerConnection create failed")
        peer = created
        return created
    }

    private fun mediaConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (videoEnabled) "true" else "false"))
    }

    private open class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
