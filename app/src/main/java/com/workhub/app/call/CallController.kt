package com.workhub.app.call

import android.content.Context
import com.workhub.app.data.ChatStore
import com.workhub.app.data.RealtimeHub
import com.workhub.shared.WsEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.webrtc.IceCandidate
import org.webrtc.SurfaceViewRenderer

enum class CallPhase {
    Idle,
    OutgoingRinging,
    IncomingRinging,
    Connecting,
    Connected,
}

data class CallUiState(
    val phase: CallPhase = CallPhase.Idle,
    val callId: String? = null,
    val channelId: String? = null,
    val peerName: String = "",
    val video: Boolean = false,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val cameraOn: Boolean = true,
    val recording: Boolean = false,
    /** Peer phone is ringing (server/callee ack). */
    val peerRinging: Boolean = false,
    val connectedForMs: Long = 0L,
    val error: String? = null,
    val notice: String? = null,
)

class CallController(
    context: Context,
    private val realtime: RealtimeHub,
    private val chat: ChatStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private var rtc: WebRtcClient? = null
    private val recorder = CallRecorder(appContext)
    private val ringtone = CallRingtone(appContext)
    private var ringTimeout: Job? = null
    private var durationJob: Job? = null
    private var isCaller = false
    private var pendingRemoteIce = mutableListOf<IceCandidate>()
    private var remoteDescSet = false
    private var connectedAt = 0L
    private var outcome: CallOutcome = CallOutcome.Cancelled
    private val historyPosted = AtomicBoolean(false)

    private enum class CallOutcome { Cancelled, Rejected, Missed, Completed }

    init {
        scope.launch {
            realtime.events.collect { event -> handleSignal(event) }
        }
    }

    fun startCall(channelId: String, peerName: String, video: Boolean) {
        if (_state.value.phase != CallPhase.Idle) return
        if (!realtime.connected.value) {
            // Still queue the invite — RealtimeHub buffers until socket is up.
            _state.value = CallUiState(notice = "Đang nối máy chủ…")
        }
        val callId = UUID.randomUUID().toString()
        isCaller = true
        remoteDescSet = false
        pendingRemoteIce.clear()
        historyPosted.set(false)
        outcome = CallOutcome.Missed
        connectedAt = 0L
        _state.value = CallUiState(
            phase = CallPhase.OutgoingRinging,
            callId = callId,
            channelId = channelId,
            peerName = peerName,
            video = video,
            speakerOn = video,
            cameraOn = video,
            peerRinging = false,
        )
        prepareRtc(video)
        realtime.send(
            WsEvent(
                type = "call_invite",
                channelId = channelId,
                callId = callId,
                callMedia = if (video) "video" else "audio",
            ),
        )
        ringTimeout?.cancel()
        ringTimeout = scope.launch {
            delay(8_000)
            if (_state.value.phase == CallPhase.OutgoingRinging &&
                _state.value.callId == callId &&
                !_state.value.peerRinging
            ) {
                _state.value = _state.value.copy(
                    notice = "Chưa nhận được phản hồi — kiểm tra máy kia đã mở app chưa",
                )
            }
            delay(37_000)
            if (_state.value.phase == CallPhase.OutgoingRinging && _state.value.callId == callId) {
                outcome = CallOutcome.Missed
                hangup(local = true)
            }
        }
    }

    fun accept() {
        val s = _state.value
        if (s.phase != CallPhase.IncomingRinging) return
        val callId = s.callId ?: return
        val channelId = s.channelId ?: return
        ringtone.stop()
        isCaller = false
        remoteDescSet = false
        pendingRemoteIce.clear()
        outcome = CallOutcome.Cancelled
        prepareRtc(s.video)
        _state.value = s.copy(phase = CallPhase.Connecting, error = null)
        realtime.send(
            WsEvent(
                type = "call_accept",
                channelId = channelId,
                callId = callId,
                callMedia = if (s.video) "video" else "audio",
            ),
        )
    }

    fun reject() {
        val s = _state.value
        if (s.phase != CallPhase.IncomingRinging) return
        outcome = CallOutcome.Rejected
        realtime.send(
            WsEvent(
                type = "call_reject",
                channelId = s.channelId,
                callId = s.callId,
                callMedia = if (s.video) "video" else "audio",
            ),
        )
        clear(error = null, postHistory = true)
    }

    fun hangup(local: Boolean = true) {
        val s = _state.value
        if (s.phase == CallPhase.Idle) return
        if (s.phase == CallPhase.Connected) outcome = CallOutcome.Completed
        else if (isCaller && outcome != CallOutcome.Rejected) outcome = CallOutcome.Missed
        if (local && s.callId != null) {
            realtime.send(
                WsEvent(
                    type = "call_hangup",
                    channelId = s.channelId,
                    callId = s.callId,
                    callMedia = if (s.video) "video" else "audio",
                ),
            )
        }
        clear(error = null, postHistory = true)
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        rtc?.setMicMuted(muted)
        _state.value = _state.value.copy(muted = muted)
    }

    fun toggleSpeaker() {
        val on = !_state.value.speakerOn
        rtc?.setSpeaker(on)
        _state.value = _state.value.copy(speakerOn = on)
    }

    fun toggleCamera() {
        if (!_state.value.video) return
        val on = !_state.value.cameraOn
        rtc?.setCameraEnabled(on)
        _state.value = _state.value.copy(cameraOn = on)
    }

    fun switchCamera() {
        if (_state.value.video) rtc?.switchCamera()
    }

    fun toggleRecording() {
        val s = _state.value
        if (s.phase != CallPhase.Connected && s.phase != CallPhase.Connecting) return
        if (s.recording) {
            val path = recorder.stop()
            _state.value = s.copy(
                recording = false,
                notice = path?.let { "Đã lưu ghi âm:\n$it" } ?: "Không lưu được file ghi âm",
            )
        } else {
            runCatching { recorder.start() }
                .onSuccess {
                    _state.value = s.copy(recording = true, notice = "Đang ghi âm…")
                }
                .onFailure { e ->
                    _state.value = s.copy(
                        recording = false,
                        error = e.message ?: "Không bắt đầu được ghi âm",
                    )
                }
        }
    }

    fun bindRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        rtc?.attachRenderers(local, remote)
    }

    private fun prepareRtc(video: Boolean) {
        rtc?.dispose()
        rtc = WebRtcClient(
            context = appContext,
            videoEnabled = video,
            onIce = { cand -> sendIce(cand) },
            onConnected = {
                scope.launch {
                    val s = _state.value
                    if (s.phase == CallPhase.Connecting || s.phase == CallPhase.OutgoingRinging) {
                        if (connectedAt == 0L) connectedAt = System.currentTimeMillis()
                        outcome = CallOutcome.Completed
                        _state.value = s.copy(phase = CallPhase.Connected, peerRinging = false)
                        ringTimeout?.cancel()
                        startDurationTicker()
                    }
                }
            },
            onRemoteVideo = { },
            onFailed = { msg ->
                scope.launch {
                    hangup(local = true)
                    _state.value = _state.value.copy(error = msg)
                }
            },
        )
        rtc?.startLocalMedia()
    }

    private fun startDurationTicker() {
        durationJob?.cancel()
        durationJob = scope.launch {
            while (true) {
                delay(1_000)
                val start = connectedAt
                if (start <= 0L || _state.value.phase != CallPhase.Connected) break
                _state.value = _state.value.copy(connectedForMs = System.currentTimeMillis() - start)
            }
        }
    }

    private fun sendIce(candidate: IceCandidate) {
        val s = _state.value
        val payload = buildJsonObject {
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }.toString()
        realtime.send(
            WsEvent(
                type = "call_ice",
                channelId = s.channelId,
                callId = s.callId,
                iceCandidate = payload,
            ),
        )
    }

    private fun handleSignal(event: WsEvent) {
        when (event.type) {
            "call_invite" -> onInvite(event)
            "call_ringing" -> onRinging(event)
            "call_accept" -> onAccept(event)
            "call_reject" -> onReject(event)
            "call_hangup" -> onRemoteHangup(event)
            "call_offer" -> onOffer(event)
            "call_answer" -> onAnswer(event)
            "call_ice" -> onIce(event)
        }
    }

    private fun onInvite(event: WsEvent) {
        if (_state.value.phase != CallPhase.Idle) {
            realtime.send(
                WsEvent(
                    type = "call_reject",
                    channelId = event.channelId,
                    callId = event.callId,
                    callMedia = event.callMedia,
                ),
            )
            return
        }
        val video = event.callMedia.equals("video", ignoreCase = true)
        historyPosted.set(false)
        outcome = CallOutcome.Missed
        connectedAt = 0L
        isCaller = false
        _state.value = CallUiState(
            phase = CallPhase.IncomingRinging,
            callId = event.callId,
            channelId = event.channelId,
            peerName = event.userName.orEmpty().ifBlank { "Cuộc gọi" },
            video = video,
            speakerOn = true,
            cameraOn = video,
        )
        ringtone.start()
        realtime.send(
            WsEvent(
                type = "call_ringing",
                channelId = event.channelId,
                callId = event.callId,
                callMedia = event.callMedia,
            ),
        )
        ringTimeout?.cancel()
        ringTimeout = scope.launch {
            delay(45_000)
            if (_state.value.phase == CallPhase.IncomingRinging &&
                _state.value.callId == event.callId
            ) {
                outcome = CallOutcome.Missed
                reject()
            }
        }
    }

    private fun onRinging(event: WsEvent) {
        val s = _state.value
        if (!isCaller || s.callId == null || event.callId != s.callId) return
        if (s.phase == CallPhase.OutgoingRinging) {
            _state.value = s.copy(peerRinging = true)
        }
    }

    private fun onAccept(event: WsEvent) {
        val s = _state.value
        if (!isCaller || s.callId == null || event.callId != s.callId) return
        if (s.phase != CallPhase.OutgoingRinging && s.phase != CallPhase.Connecting) return
        _state.value = s.copy(phase = CallPhase.Connecting, peerRinging = false)
        ringTimeout?.cancel()
        rtc?.createOffer { desc ->
            realtime.send(
                WsEvent(
                    type = "call_offer",
                    channelId = s.channelId,
                    callId = s.callId,
                    sdp = desc.description,
                    callMedia = if (s.video) "video" else "audio",
                ),
            )
        }
    }

    private fun onReject(event: WsEvent) {
        val s = _state.value
        if (s.callId != null && event.callId != s.callId) return
        outcome = CallOutcome.Rejected
        val msg = when (event.alertText) {
            "offline" -> "Đối phương đang offline"
            "invalid" -> "Không gọi được kênh này"
            else -> if (s.phase == CallPhase.OutgoingRinging) "Cuộc gọi bị từ chối" else null
        }
        clear(error = msg, postHistory = true)
    }

    private fun onRemoteHangup(event: WsEvent) {
        val s = _state.value
        if (s.callId != null && event.callId != s.callId) return
        if (s.phase == CallPhase.Idle) return
        if (s.phase == CallPhase.Connected) outcome = CallOutcome.Completed
        else if (!isCaller) outcome = CallOutcome.Missed
        clear(error = null, postHistory = true)
    }

    private fun onOffer(event: WsEvent) {
        val s = _state.value
        if (isCaller || s.callId == null || event.callId != s.callId) return
        val sdp = event.sdp ?: return
        if (rtc == null) prepareRtc(s.video)
        _state.value = s.copy(phase = CallPhase.Connecting)
        rtc?.handleRemoteOffer(sdp) { answer ->
            remoteDescSet = true
            flushIce()
            realtime.send(
                WsEvent(
                    type = "call_answer",
                    channelId = s.channelId,
                    callId = s.callId,
                    sdp = answer.description,
                    callMedia = if (s.video) "video" else "audio",
                ),
            )
        }
    }

    private fun onAnswer(event: WsEvent) {
        val s = _state.value
        if (!isCaller || s.callId == null || event.callId != s.callId) return
        val sdp = event.sdp ?: return
        rtc?.handleRemoteAnswer(sdp)
        remoteDescSet = true
        flushIce()
    }

    private fun onIce(event: WsEvent) {
        val s = _state.value
        if (s.callId == null || event.callId != s.callId) return
        val raw = event.iceCandidate ?: return
        val cand = parseIce(raw) ?: return
        if (!remoteDescSet) pendingRemoteIce.add(cand) else rtc?.addIce(cand)
    }

    private fun flushIce() {
        pendingRemoteIce.forEach { rtc?.addIce(it) }
        pendingRemoteIce.clear()
    }

    private fun parseIce(raw: String): IceCandidate? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        IceCandidate(
            obj["sdpMid"]?.jsonPrimitive?.contentOrNull,
            obj["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
            obj["candidate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.getOrNull()

    private fun clear(error: String?, postHistory: Boolean) {
        ringTimeout?.cancel()
        ringTimeout = null
        durationJob?.cancel()
        durationJob = null
        ringtone.stop()
        val s = _state.value
        val saved = if (recorder.isRecording) recorder.stop() else null
        val durationMs = if (connectedAt > 0L) System.currentTimeMillis() - connectedAt else 0L
        val channelId = s.channelId
        val callId = s.callId
        val video = s.video
        rtc?.dispose()
        rtc = null
        isCaller = false
        remoteDescSet = false
        pendingRemoteIce.clear()
        connectedAt = 0L
        _state.value = CallUiState(
            error = error,
            notice = saved?.let { "Đã lưu ghi âm:\n$it" },
        )
        if (postHistory && channelId != null && callId != null && historyPosted.compareAndSet(false, true)) {
            val body = callHistoryBody(video, outcome, durationMs)
            scope.launch(Dispatchers.IO) {
                runCatching { chat.sendCallLog(channelId, callId, body) }
            }
        }
    }

    private fun callHistoryBody(video: Boolean, outcome: CallOutcome, durationMs: Long): String {
        val kind = if (video) "Cuộc gọi video" else "Cuộc gọi thoại"
        return when (outcome) {
            CallOutcome.Completed -> "📞 $kind · ${formatDuration(durationMs)}"
            CallOutcome.Rejected -> "📞 $kind · đã từ chối"
            CallOutcome.Missed -> "📞 $kind · nhỡ máy"
            CallOutcome.Cancelled -> "📞 $kind · đã hủy"
        }
    }

    private fun formatDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }
}
