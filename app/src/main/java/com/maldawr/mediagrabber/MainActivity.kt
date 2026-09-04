package com.maldawr.mediagrabber

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaGrabberTheme {
                MediaGrabberScreen()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaGrabberScreen() {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var url by rememberSaveable { mutableStateOf("") }
    var downloadId by rememberSaveable { mutableLongStateOf(-1L) }
    var downloadStatus by remember { mutableStateOf<DirectMediaDownloader.Status?>(null) }
    var selectedVideo by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedVideoName by rememberSaveable { mutableStateOf<String?>(null) }

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedVideo = uri.toString()
            selectedVideoName = displayName(context, uri)
        }
    }

    LaunchedEffect(downloadId) {
        if (downloadId <= 0L) return@LaunchedEffect
        while (true) {
            val status = DirectMediaDownloader.query(context, downloadId)
            downloadStatus = status
            if (status.state == DirectMediaDownloader.Status.State.SUCCESS ||
                status.state == DirectMediaDownloader.Status.State.FAILED
            ) break
            delay(1200)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.direct_download_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.direct_download_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.url_label)) },
                        placeholder = { Text("https://cdn.example.com/video.mp4") }
                    )
                    Button(
                        onClick = {
                            when (DirectMediaPolicy.validationError(url)) {
                                "empty" -> context.showToast(context.getString(R.string.error_empty_url))
                                "https_required" -> context.showToast(context.getString(R.string.error_https_only))
                                "blocked_source" -> context.showToast(context.getString(R.string.error_blocked_source))
                                "invalid" -> context.showToast(context.getString(R.string.error_invalid_url))
                                else -> runCatching {
                                    DirectMediaDownloader.enqueue(context, url)
                                }.onSuccess {
                                    downloadId = it
                                    downloadStatus = DirectMediaDownloader.Status(
                                        DirectMediaDownloader.Status.State.QUEUED
                                    )
                                    context.showToast(context.getString(R.string.download_started))
                                }.onFailure {
                                    context.showToast(context.getString(R.string.download_failed_to_start))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.download_button))
                    }

                    downloadStatus?.let { status ->
                        DownloadStatusRow(status)
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.local_media_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.local_media_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { pickVideo.launch(arrayOf("video/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.pick_video))
                    }

                    selectedVideoName?.let { name ->
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }

                    selectedVideo?.let { uriString ->
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(uriString), "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                                    .onFailure {
                                        context.showToast(context.getString(R.string.no_video_app))
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.open_video))
                        }
                    }
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.compliance_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.compliance_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DownloadStatusRow(status: DirectMediaDownloader.Status) {
    val label = when (status.state) {
        DirectMediaDownloader.Status.State.QUEUED -> stringResource(R.string.status_queued)
        DirectMediaDownloader.Status.State.RUNNING -> stringResource(R.string.status_running)
        DirectMediaDownloader.Status.State.PAUSED -> stringResource(R.string.status_paused)
        DirectMediaDownloader.Status.State.SUCCESS -> stringResource(R.string.status_success)
        DirectMediaDownloader.Status.State.FAILED -> stringResource(R.string.status_failed)
        DirectMediaDownloader.Status.State.UNKNOWN -> stringResource(R.string.status_unknown)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status.state == DirectMediaDownloader.Status.State.RUNNING ||
            status.state == DirectMediaDownloader.Status.State.QUEUED
        ) {
            CircularProgressIndicator()
        }
        Column {
            Text(label, fontWeight = FontWeight.Medium)
            status.percent?.let { Text("$it%", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment ?: "video"
}

private fun android.content.Context.showToast(message: String) {
    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
}
