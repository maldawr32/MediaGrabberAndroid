package com.maldawr.mediagrabber

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
class ClipExporter(private val context: Context) {
    enum class OutputMode { VIDEO, AUDIO }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var tempFile: File? = null
    private val exporting = AtomicBoolean(false)

    fun export(
        source: Uri,
        startMs: Long,
        endMs: Long,
        mode: OutputMode,
        onProgress: (Int) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        if (exporting.getAndSet(true)) {
            onError("يوجد تصدير قيد التنفيذ")
            return
        }
        if (endMs <= startMs) {
            exporting.set(false)
            onError("وقت النهاية يجب أن يكون بعد البداية")
            return
        }

        val extension = if (mode == OutputMode.AUDIO) "m4a" else "mp4"
        val output = File.createTempFile("mediagrabber_", ".$extension", context.cacheDir)
        tempFile = output

        val clippedMediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem)
            .setRemoveVideo(mode == OutputMode.AUDIO)
            .build()

        val builder = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)

        if (mode == OutputMode.VIDEO) {
            builder.setVideoMimeType(MimeTypes.VIDEO_H264)
        }

        val currentTransformer = builder
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        stopProgressPolling()
                        persistOutput(
                            output = output,
                            mode = mode,
                            onSuccess = onSuccess,
                            onError = onError
                        )
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        stopProgressPolling()
                        exporting.set(false)
                        output.delete()
                        onError(exportException.localizedMessage ?: "فشل تصدير المقطع")
                    }
                }
            )
            .build()

        transformer = currentTransformer
        onProgress(0)
        currentTransformer.start(editedMediaItem, output.absolutePath)
        startProgressPolling(currentTransformer, onProgress)
    }

    fun cancel() {
        mainHandler.removeCallbacksAndMessages(null)
        transformer?.cancel()
        transformer = null
        exporting.set(false)
        tempFile?.delete()
        tempFile = null
    }

    private fun startProgressPolling(transformer: Transformer, onProgress: (Int) -> Unit) {
        val holder = ProgressHolder()
        mainHandler.post(
            object : Runnable {
                override fun run() {
                    if (!exporting.get()) return
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 350L)
                    }
                }
            }
        )
    }

    private fun stopProgressPolling() {
        mainHandler.removeCallbacksAndMessages(null)
        transformer = null
    }

    private fun persistOutput(
        output: File,
        mode: OutputMode,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            runCatching { copyToMediaStore(output, mode) }
                .onSuccess { savedUri ->
                    output.delete()
                    tempFile = null
                    exporting.set(false)
                    mainHandler.post { onSuccess(savedUri) }
                }
                .onFailure { error ->
                    output.delete()
                    tempFile = null
                    exporting.set(false)
                    mainHandler.post {
                        onError(error.localizedMessage ?: "تم القص لكن تعذر حفظ الملف")
                    }
                }
        }.start()
    }

    private fun copyToMediaStore(file: File, mode: OutputMode): Uri {
        val resolver = context.contentResolver
        val timestamp = System.currentTimeMillis()
        val isAudio = mode == OutputMode.AUDIO
        val displayName = if (isAudio) {
            "MediaGrabber_$timestamp.m4a"
        } else {
            "MediaGrabber_$timestamp.mp4"
        }

        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mp4" else "video/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (isAudio) {
                    "${Environment.DIRECTORY_MUSIC}/MediaGrabber"
                } else {
                    "${Environment.DIRECTORY_MOVIES}/MediaGrabber"
                }
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val target = resolver.insert(collection, values)
            ?: error("تعذر إنشاء ملف الإخراج")

        try {
            resolver.openOutputStream(target, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("تعذر فتح ملف الإخراج")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            return target
        } catch (error: Throwable) {
            resolver.delete(target, null, null)
            throw error
        }
    }
}
