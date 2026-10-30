package com.workhub.app.data

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pings `/health` every [INTERVAL_MS] while the app is foregrounded so Render's
 * free tier does not spin down after ~15 minutes of idle HTTP traffic.
 */
class ServerKeepAlive(private val api: WorkHubApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val running = AtomicBoolean(false)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        loop = scope.launch {
            // Wake immediately (covers cold start when user opens the app).
            runCatching { api.ping() }
            while (isActive && running.get()) {
                delay(INTERVAL_MS)
                if (!running.get()) break
                runCatching { api.ping() }
            }
        }
    }

    fun stop() {
        running.set(false)
        loop?.cancel()
        loop = null
    }

    companion object {
        const val INTERVAL_MS = 5 * 60_000L
    }
}
