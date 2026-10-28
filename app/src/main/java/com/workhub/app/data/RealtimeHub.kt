package com.workhub.app.data

import com.workhub.shared.WsEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
 * Stop on background / restart on foreground so sockets do not hang after
 * switching to WeChat (etc.) then returning.
 */
class RealtimeHub(private val api: WorkHubApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val running = AtomicBoolean(false)

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        loop = scope.launch {
            while (isActive && running.get()) {
                try {
                    _connected.value = true
                    api.listen { event -> _events.emit(event) }
                    _connected.value = false
                } catch (error: kotlinx.coroutines.CancellationException) {
                    _connected.value = false
                    throw error
                } catch (_: Exception) {
                    _connected.value = false
                    if (running.get()) delay(2_000)
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
        _connected.value = false
    }
}
