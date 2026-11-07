package com.workhub.app.data

import com.workhub.shared.WsEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One shared WebSocket for the whole app.
 * It stays up while the screen is locked so incoming messages can notify.
 * Coming back to the foreground reconnects, in case the socket died in the background.
 */
class RealtimeHub(private val api: WorkHubApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val running = AtomicBoolean(false)
    private val outbound = AtomicReference<suspend (WsEvent) -> Unit>({})
    private val pendingOut = Channel<WsEvent>(Channel.UNLIMITED)

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun send(event: WsEvent) {
        scope.launch {
            val sendFn = outbound.get()
            if (_connected.value && sendFn !== emptySend) {
                runCatching { sendFn(event) }
            } else {
                pendingOut.trySend(event)
            }
        }
    }

    fun sendTyping(channelId: String) {
        send(WsEvent(type = "typing", channelId = channelId))
    }

    fun sendRead(channelId: String, lastReadAt: Long, lastMessageId: String?) {
        send(
            WsEvent(
                type = "read",
                channelId = channelId,
                lastReadAt = lastReadAt,
                lastMessageId = lastMessageId,
            ),
        )
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        loop = scope.launch {
            var backoffMs = 500L
            while (isActive && running.get()) {
                try {
                    api.openRealtimeSession(
                        onConnected = { send ->
                            outbound.set(send)
                            _connected.value = true
                            // Flush anything queued while the socket was down (e.g. call_invite).
                            scope.launch {
                                while (true) {
                                    val queued = pendingOut.tryReceive().getOrNull() ?: break
                                    runCatching { send(queued) }
                                }
                            }
                        },
                        onEvent = { event -> _events.emit(event) },
                    )
                    _connected.value = false
                    outbound.set(emptySend)
                    backoffMs = 500L
                } catch (error: kotlinx.coroutines.CancellationException) {
                    _connected.value = false
                    outbound.set(emptySend)
                    throw error
                } catch (_: Exception) {
                    _connected.value = false
                    outbound.set(emptySend)
                    if (running.get()) {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(8_000)
                    }
                }
            }
        }
    }

    fun restart() {
        stop()
        start()
    }

    fun stop() {
        running.set(false)
        loop?.cancel()
        loop = null
        outbound.set(emptySend)
        _connected.value = false
    }

    companion object {
        private val emptySend: suspend (WsEvent) -> Unit = {}
    }
}
