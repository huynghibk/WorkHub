package com.workhub.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.workhub.app.R
import com.workhub.app.call.CallPhase
import com.workhub.app.call.CallUiState
import com.workhub.app.data.LocalAppGraph
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallOverlay() {
    val graph = LocalAppGraph.current
    val state by graph.calls.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.error) {
        state.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    if (state.phase != CallPhase.Idle) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            CallScreenContent(
                state = state,
                onAccept = { graph.calls.accept() },
                onReject = { graph.calls.reject() },
                onHangup = { graph.calls.hangup() },
                onToggleMute = { graph.calls.toggleMute() },
                onToggleSpeaker = { graph.calls.toggleSpeaker() },
                onToggleCamera = { graph.calls.toggleCamera() },
                onSwitchCamera = { graph.calls.switchCamera() },
                onToggleRecording = { graph.calls.toggleRecording() },
                onBindRenderers = { local, remote -> graph.calls.bindRenderers(local, remote) },
            )
        }
    }
}

@Composable
private fun CallScreenContent(
    state: CallUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleRecording: () -> Unit,
    onBindRenderers: (SurfaceViewRenderer?, SurfaceViewRenderer?) -> Unit,
) {
    val localRef = remember { arrayOfNulls<SurfaceViewRenderer>(1) }
    val remoteRef = remember { arrayOfNulls<SurfaceViewRenderer>(1) }
    DisposableEffect(state.callId, state.video) {
        onDispose {
            onBindRenderers(null, null)
            localRef[0]?.release()
            remoteRef[0]?.release()
            localRef[0] = null
            remoteRef[0] = null
        }
    }
    val ringing = state.phase == CallPhase.OutgoingRinging || state.phase == CallPhase.IncomingRinging
    val pulse = rememberInfiniteTransition(label = "callPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (ringing) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1220), Color(0xFF111827), Color(0xFF1E1B4B)),
                ),
            ),
    ) {
        if (state.video && state.phase == CallPhase.Connected) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        val remote = SurfaceViewRenderer(ctx).also {
                            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            addView(
                                it,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                            remoteRef[0] = it
                        }
                        val local = SurfaceViewRenderer(ctx).also {
                            it.setZOrderMediaOverlay(true)
                            it.setMirror(true)
                            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            val lp = FrameLayout.LayoutParams(132, 198)
                            lp.marginEnd = 16
                            lp.topMargin = 96
                            lp.gravity = android.view.Gravity.END or android.view.Gravity.TOP
                            addView(it, lp)
                            localRef[0] = it
                        }
                        onBindRenderers(local, remote)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                state.peerName.ifBlank { stringResource(R.string.call_unknown) },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                callStatusText(state),
                color = if (state.recording) Color(0xFFF87171) else Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleSmall,
            )

            Spacer(Modifier.weight(1f))

            if (!state.video || state.phase != CallPhase.Connected) {
                Box(contentAlignment = Alignment.Center) {
                    if (ringing) {
                        Box(
                            modifier = Modifier
                                .size(168.dp)
                                .scale(pulseScale)
                                .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4))),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.peerName.take(1).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            when (state.phase) {
                CallPhase.IncomingRinging -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        CallAction(
                            label = stringResource(R.string.call_reject),
                            color = Color(0xFFDC2626),
                            onClick = onReject,
                        ) {
                            Icon(Icons.Filled.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        CallAction(
                            label = stringResource(R.string.call_accept),
                            color = Color(0xFF16A34A),
                            onClick = onAccept,
                        ) {
                            Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                CallPhase.OutgoingRinging,
                CallPhase.Connecting,
                CallPhase.Connected,
                -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        CallChip(
                            active = state.muted,
                            onClick = onToggleMute,
                            label = stringResource(R.string.call_mute),
                        ) {
                            Icon(
                                if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                null,
                                tint = Color.White,
                            )
                        }
                        CallChip(
                            active = state.speakerOn,
                            onClick = onToggleSpeaker,
                            label = stringResource(R.string.call_speaker),
                        ) {
                            Icon(
                                if (state.speakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                null,
                                tint = Color.White,
                            )
                        }
                        if (state.phase == CallPhase.Connected) {
                            CallChip(
                                active = state.recording,
                                danger = state.recording,
                                onClick = onToggleRecording,
                                label = stringResource(
                                    if (state.recording) R.string.call_record_stop else R.string.call_record,
                                ),
                            ) {
                                Icon(
                                    if (state.recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                    null,
                                    tint = Color.White,
                                )
                            }
                        }
                        if (state.video) {
                            CallChip(
                                active = !state.cameraOn,
                                onClick = onToggleCamera,
                                label = stringResource(R.string.call_camera),
                            ) {
                                Icon(
                                    if (state.cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                    null,
                                    tint = Color.White,
                                )
                            }
                            if (state.phase == CallPhase.Connected) {
                                CallChip(
                                    active = false,
                                    onClick = onSwitchCamera,
                                    label = stringResource(R.string.call_flip_camera),
                                ) {
                                    Icon(Icons.Filled.Cameraswitch, null, tint = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    CallAction(
                        label = stringResource(R.string.call_hangup),
                        color = Color(0xFFDC2626),
                        onClick = onHangup,
                    ) {
                        Icon(Icons.Filled.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                }
                CallPhase.Idle -> Unit
            }
        }
    }
}

@Composable
private fun callStatusText(state: CallUiState): String = when {
    state.recording -> stringResource(R.string.call_recording)
    state.phase == CallPhase.OutgoingRinging && state.peerRinging ->
        stringResource(R.string.call_ringing_peer)
    state.phase == CallPhase.OutgoingRinging -> stringResource(R.string.call_ringing_out)
    state.phase == CallPhase.IncomingRinging -> stringResource(
        if (state.video) R.string.call_incoming_video else R.string.call_incoming_audio,
    )
    state.phase == CallPhase.Connecting -> stringResource(R.string.call_connecting)
    state.phase == CallPhase.Connected -> formatCallClock(state.connectedForMs)
    else -> ""
}

private fun formatCallClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun CallChip(
    active: Boolean,
    onClick: () -> Unit,
    label: String,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    when {
                        danger -> Color(0xFFDC2626)
                        active -> Color.White.copy(alpha = 0.28f)
                        else -> Color.White.copy(alpha = 0.12f)
                    },
                    CircleShape,
                ),
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CallAction(
    label: String,
    color: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .background(color, CircleShape),
        ) { content() }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun rememberStartDirectCall(): (channelId: String, peerName: String, video: Boolean) -> Unit {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<Triple<String, String, Boolean>>(1) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val req = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        val needCam = req.third
        val okAudio = granted[Manifest.permission.RECORD_AUDIO] == true
        val okCam = !needCam || granted[Manifest.permission.CAMERA] == true
        if (okAudio && okCam) {
            graph.calls.startCall(req.first, req.second, req.third)
        }
    }
    return remember(graph, launcher) {
        { channelId, peerName, video ->
            val need = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (video) add(Manifest.permission.CAMERA)
            }
            val missing = need.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                graph.calls.startCall(channelId, peerName, video)
            } else {
                pending[0] = Triple(channelId, peerName, video)
                launcher.launch(missing.toTypedArray())
            }
            (context as? Activity)?.window?.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }
}
