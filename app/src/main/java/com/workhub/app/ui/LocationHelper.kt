package com.workhub.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

fun isLikelyEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT
    val model = Build.MODEL
    val product = Build.PRODUCT
    return fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK", ignoreCase = true) ||
        product.contains("sdk", ignoreCase = true) ||
        product.contains("emulator", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
}

fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    return providers.mapNotNull { provider ->
        runCatching {
            if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
        }.getOrNull()
    }.maxByOrNull { it.time }
}

fun mapsUrlFrom(lat: Double, lng: Double): String =
    "https://maps.google.com/?q=$lat,$lng"

/**
 * Tries last-known first, then waits briefly for a fresh fix (helps emulator after setting a point).
 */
suspend fun awaitBestLocation(context: Context, timeoutMs: Long = 8_000): Location? {
    if (!hasLocationPermission(context)) return null
    lastKnownLocation(context)?.let { cached ->
        // Prefer a reasonably fresh reading.
        if (System.currentTimeMillis() - cached.time < 2 * 60 * 1000) return cached
    }
    val fresh = withTimeoutOrNull(timeoutMs) { requestSingleLocation(context) }
    return fresh ?: lastKnownLocation(context)
}

private suspend fun requestSingleLocation(context: Context): Location? =
    suspendCancellableCoroutine { cont ->
        if (!hasLocationPermission(context)) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { manager.removeUpdates(this) }
                if (cont.isActive) cont.resume(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        cont.invokeOnCancellation {
            runCatching { manager.removeUpdates(listener) }
        }
        runCatching {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        }.onFailure {
            cont.resume(null)
        }
    }
