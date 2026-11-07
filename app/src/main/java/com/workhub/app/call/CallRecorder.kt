package com.workhub.app.call

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records call audio to app-private local storage (M4A/AAC).
 * Uses VOICE_COMMUNICATION so it fits an active VoIP call; remote side is captured
 * best when speakerphone is on (device mixes into the mic).
 */
class CallRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun dir(): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(base, "call_recordings").also { it.mkdirs() }
    }

    /** Starts recording; returns output path or throws. */
    fun start(): String {
        stop()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir(), "call_$stamp.m4a")
        // Prefer VOICE_COMMUNICATION during VoIP; fall back to MIC if the device rejects it.
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
        )
        var lastError: Exception? = null
        for (source in sources) {
            val mr = createRecorder()
            try {
                mr.setAudioSource(source)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioEncodingBitRate(128_000)
                mr.setAudioSamplingRate(44_100)
                mr.setOutputFile(file.absolutePath)
                mr.prepare()
                mr.start()
                recorder = mr
                outputFile = file
                return file.absolutePath
            } catch (e: Exception) {
                lastError = e
                runCatching { mr.release() }
            }
        }
        throw lastError ?: IllegalStateException("MediaRecorder start failed")
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    /** Stops and returns the saved file path, or null if nothing was recording. */
    fun stop(): String? {
        val mr = recorder ?: return null
        recorder = null
        val path = outputFile?.absolutePath
        outputFile = null
        return runCatching {
            mr.stop()
            mr.release()
            path
        }.getOrElse {
            runCatching { mr.release() }
            // Incomplete file — delete
            path?.let { runCatching { File(it).delete() } }
            null
        }
    }
}
