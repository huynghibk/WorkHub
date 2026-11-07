package com.workhub.app.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RemoteCameraCapture {
    suspend fun capture(context: Context, facing: String): File? = withContext(Dispatchers.Main) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }
        val app = context.applicationContext
        val provider = ProcessCameraProvider.getInstance(app).get(15, TimeUnit.SECONDS)
        val owner = OneShotLifecycleOwner()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val selector = if (facing.equals("back", ignoreCase = true)) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, imageCapture)
            val dest = File(app.cacheDir, "mdm_cam_${System.currentTimeMillis()}.jpg")
            val latch = CountDownLatch(1)
            var error: Exception? = null
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(dest).build(),
                ContextCompat.getMainExecutor(app),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        latch.countDown()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        error = exception
                        latch.countDown()
                    }
                },
            )
            withContext(Dispatchers.IO) {
                latch.await(20, TimeUnit.SECONDS)
            }
            owner.destroy()
            provider.unbindAll()
            if (error != null || !dest.exists() || dest.length() == 0L) null else dest
        } catch (_: Exception) {
            runCatching { owner.destroy() }
            runCatching { provider.unbindAll() }
            null
        }
    }

    private class OneShotLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        init {
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = registry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
