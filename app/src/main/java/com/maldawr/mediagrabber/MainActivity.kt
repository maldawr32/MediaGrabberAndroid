package com.maldawr.mediagrabber

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaGrabberTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MediaGrabberEditor()
                }
            }
        }
    }
}

@Composable
private fun MediaGrabberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Composable
private fun MediaGrabberEditor() {
    val context = LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    val exporter = remember(context) { ClipExporter(context.applicationContext) }

    var sourceUriString by rememberSaveable { mutableStateOf("") }
    var sourceLabel by rememberSaveable { mutableStateOf("لم يتم اختيار فيديو بعد") }
    var internetUrl by rememberSaveable { mutableStateOf("") }
    var durationMs by rememberSaveable { mutableLongStateOf(0L) }
    var startMs by rememberSaveable { mutableLongStateOf(0L) }
    var endMs by rememberSaveable { mutableLongStateOf(0L) }
    var loopSelection by rememberSaveable { mutableStateOf(false) }
    var outputModeName by rememberSaveable { mutableStateOf(ClipExporter.OutputMode.VIDEO.name) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var exportProgress by rememberSaveable { mutableStateOf(0) }
    var savedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var playerError by rememberSaveable { mutableStateOf<String?>(null) }

    val outputMode = ClipExporter.OutputMode.valueOf(outputModeName)

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            sourceUriString = uri.toString()
            sourceLabel = displayName(context, uri)
            savedUriString = null
            playerError = null
        }
    }

    DisposableEffect(player, exporter) {
        onDispose {
            exporter.cancel()
            player.release()
        }
    }

    LaunchedEffect(sourceUriString) {
        if (sourceUriString.isBlank()) return@LaunchedEffect
        durationMs = 0L
        startMs = 0L
        endMs = 0L
        loopSelection = false
        playerError = null
        player.setMediaItem(MediaItem.fromUri(Uri.parse(sourceUriString)))
        player.prepare()
    }

    LaunchedEffect(player, sourceUriString, loopSelection, startMs, endMs) {
        while (sourceUriString.isNotBlank()) {
            val duration = player.duration
            if (duration != C.TIME_UNSET && duration > 0L && duration != durationMs) {
                durationMs = duration
                if (endMs <= 0L || endMs > duration) endMs = duration
            }

            if (loopSelection && player.isPlaying && endMs > startMs && player.currentPosition >= endMs) {
                player.seekTo(startMs)
                player.play()
            }

            player.playerError?.let { error ->
                playerError = error.localizedMessage ?: "تعذر تشغيل هذا المصدر"
            }
            delay(120L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MediaGrabber Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "عاين الفيديو، حدّد البداية والنهاية، ثم صدّر الجزء المطلوب كفيديو أو صوت فقط.",
            style = MaterialTheme.typography.bodyMedium
        )

        SourceCard(
            sourceLabel = sourceLabel,
            internetUrl = internetUrl,
            onInternetUrlChange = { internetUrl = it },
            onPickLocal = { pickVideo.launch(arrayOf("video/*")) },
            onLoadInternet = {
                val value = internetUrl.trim()
                val uri = Uri.parse(value)
                if (value.isBlank() || (uri.scheme != "https" && uri.scheme != "http")) {
                    context.toast("أدخل رابط HTTP أو HTTPS صالح")
                } else {
                    sourceUriString = value
                    sourceLabel = value
                    savedUriString = null
                    playerError = null
                }
            }
        )

        if (sourceUriString.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    )

                    playerError?.let {
                        Text(
                            text = "تعذر تشغيل المصدر: $it",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (durationMs <= 0L) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("جارٍ قراءة معلومات الفيديو…")
                        }
                    } else {
                        SelectionEditor(
                            durationMs = durationMs,
                            startMs = startMs,
                            endMs = endMs,
                            loopSelection = loopSelection,
                            onRangeChange = { newStart, newEnd ->
                                startMs = newStart.coerceIn(0L, durationMs)
                                endMs = newEnd.coerceIn(startMs, durationMs)
                                if (player.currentPosition < startMs || player.currentPosition > endMs) {
                                    player.seekTo(startMs)
                                }
                            },
                            onAdjustStart = { delta ->
                                startMs = (startMs + delta).coerceIn(0L, max(0L, endMs - 100L))
                                player.seekTo(startMs)
                            },
                            onAdjustEnd = { delta ->
                                endMs = (endMs + delta).coerceIn(startMs + 100L, durationMs)
                            },
                            onPreview = {
                                loopSelection = true
                                player.seekTo(startMs)
                                player.play()
                            },
                            onToggleLoop = { loopSelection = !loopSelection }
                        )
                    }
                }
            }
        }

        if (durationMs > 0L && sourceUriString.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "الإخراج",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (outputMode == ClipExporter.OutputMode.VIDEO) {
                            Button(
                                onClick = { outputModeName = ClipExporter.OutputMode.VIDEO.name },
                                modifier = Modifier.weight(1f)
                            ) { Text("فيديو MP4") }
                        } else {
                            OutlinedButton(
                                onClick = { outputModeName = ClipExporter.OutputMode.VIDEO.name },
                                modifier = Modifier.weight(1f)
                            ) { Text("فيديو MP4") }
                        }

                        if (outputMode == ClipExporter.OutputMode.AUDIO) {
                            Button(
                                onClick = { outputModeName = ClipExporter.OutputMode.AUDIO.name },
                                modifier = Modifier.weight(1f)
                            ) { Text("صوت M4A") }
                        } else {
                            OutlinedButton(
                                onClick = { outputModeName = ClipExporter.OutputMode.AUDIO.name },
                                modifier = Modifier.weight(1f)
                            ) { Text("صوت M4A") }
                        }
                    }

                    Text(
                        text = "مدة المقطع: ${formatTime((endMs - startMs).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            exporting = true
                            exportProgress = 0
                            savedUriString = null
                            exporter.export(
                                source = Uri.parse(sourceUriString),
                                startMs = startMs,
                                endMs = endMs,
                                mode = outputMode,
                                onProgress = { exportProgress = it },
                                onSuccess = { savedUri ->
                                    exporting = false
                                    exportProgress = 100
                                    savedUriString = savedUri.toString()
                                    context.toast("تم حفظ المقطع بنجاح")
                                },
                                onError = { message ->
                                    exporting = false
                                    context.toast(message)
                                }
                            )
                        },
                        enabled = !exporting && endMs > startMs,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (exporting) "جارٍ التصدير… $exportProgress%" else "تصدير الجزء المحدد")
                    }

                    if (exporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("$exportProgress%")
                            OutlinedButton(
                                onClick = {
                                    exporter.cancel()
                                    exporting = false
                                    context.toast("تم إلغاء التصدير")
                                }
                            ) { Text("إلغاء") }
                        }
                    }

                    savedUriString?.let { saved ->
                        OutlinedButton(
                            onClick = {
                                shareMedia(context, Uri.parse(saved), outputMode)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("مشاركة الملف الناتج")
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "مصادر الإنترنت",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "هذه المرحلة تدعم المعاينة للروابط المباشرة وHLS/DASH. روابط صفحات YouTube/Facebook/TikTok تحتاج طبقة استخراج منفصلة، وسنبنيها كطبقة مصادر بعد تثبيت المحرر الأساسي.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SourceCard(
    sourceLabel: String,
    internetUrl: String,
    onInternetUrlChange: (String) -> Unit,
    onPickLocal: () -> Unit,
    onLoadInternet: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "المصدر",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(sourceLabel, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onPickLocal, modifier = Modifier.fillMaxWidth()) {
                Text("اختيار فيديو من الجهاز")
            }
            OutlinedTextField(
                value = internetUrl,
                onValueChange = onInternetUrlChange,
                label = { Text("رابط فيديو / HLS / DASH") },
                placeholder = { Text("https://example.com/video.mp4") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = onLoadInternet, modifier = Modifier.fillMaxWidth()) {
                Text("تحميل الرابط للمعاينة")
            }
        }
    }
}

@Composable
private fun SelectionEditor(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    loopSelection: Boolean,
    onRangeChange: (Long, Long) -> Unit,
    onAdjustStart: (Long) -> Unit,
    onAdjustEnd: (Long) -> Unit,
    onPreview: () -> Unit,
    onToggleLoop: () -> Unit
) {
    val totalSeconds = (durationMs / 1000f).coerceAtLeast(0.1f)
    val startSeconds = (startMs / 1000f).coerceIn(0f, totalSeconds)
    val endSeconds = (endMs / 1000f).coerceIn(startSeconds, totalSeconds)

    Text(
        text = "حدد المقطع",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    RangeSlider(
        value = startSeconds..endSeconds,
        onValueChange = { range ->
            onRangeChange(
                (range.start * 1000f).toLong(),
                (range.endInclusive * 1000f).toLong()
            )
        },
        valueRange = 0f..totalSeconds,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("البداية ${formatTime(startMs)}", fontWeight = FontWeight.Medium)
        Text("النهاية ${formatTime(endMs)}", fontWeight = FontWeight.Medium)
    }

    Text("ضبط البداية", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { onAdjustStart(-1000L) }, modifier = Modifier.weight(1f)) {
            Text("-1s")
        }
        OutlinedButton(onClick = { onAdjustStart(-100L) }, modifier = Modifier.weight(1f)) {
            Text("-0.1s")
        }
        OutlinedButton(onClick = { onAdjustStart(100L) }, modifier = Modifier.weight(1f)) {
            Text("+0.1s")
        }
        OutlinedButton(onClick = { onAdjustStart(1000L) }, modifier = Modifier.weight(1f)) {
            Text("+1s")
        }
    }

    Text("ضبط النهاية", style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { onAdjustEnd(-1000L) }, modifier = Modifier.weight(1f)) {
            Text("-1s")
        }
        OutlinedButton(onClick = { onAdjustEnd(-100L) }, modifier = Modifier.weight(1f)) {
            Text("-0.1s")
        }
        OutlinedButton(onClick = { onAdjustEnd(100L) }, modifier = Modifier.weight(1f)) {
            Text("+0.1s")
        }
        OutlinedButton(onClick = { onAdjustEnd(1000L) }, modifier = Modifier.weight(1f)) {
            Text("+1s")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(onClick = onPreview, modifier = Modifier.weight(1f)) {
            Text("معاينة المقطع")
        }
        OutlinedButton(onClick = onToggleLoop, modifier = Modifier.weight(1f)) {
            Text(if (loopSelection) "إيقاف التكرار" else "تكرار المقطع")
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment ?: "video"
}

private fun formatTime(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    val hours = safe / 3_600_000L
    val minutes = (safe % 3_600_000L) / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    val millis = safe % 1_000L
    return if (hours > 0L) {
        "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%03d".format(minutes, seconds, millis)
    }
}

private fun shareMedia(context: Context, uri: Uri, mode: ClipExporter.OutputMode) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (mode == ClipExporter.OutputMode.AUDIO) "audio/mp4" else "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة المقطع"))
}

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
