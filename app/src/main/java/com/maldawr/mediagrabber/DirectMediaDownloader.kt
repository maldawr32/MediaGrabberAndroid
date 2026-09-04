package com.maldawr.mediagrabber

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.net.URLConnection
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DirectMediaDownloader {
    data class Status(
        val state: State,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val reason: Int = 0
    ) {
        enum class State { QUEUED, RUNNING, PAUSED, SUCCESS, FAILED, UNKNOWN }

        val percent: Int?
            get() = if (totalBytes > 0L && downloadedBytes >= 0L) {
                ((downloadedBytes * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
            } else null
    }

    fun enqueue(context: Context, rawUrl: String): Long {
        val url = rawUrl.trim()
        require(DirectMediaPolicy.validationError(url) == null) { "Unsupported URL" }

        val uri = Uri.parse(url)
        val fileName = safeFileName(uri)
        val mime = URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"

        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setDescription(context.getString(R.string.download_in_progress))
            .setMimeType(mime)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "MediaGrabber/$fileName"
            )

        val manager = context.getSystemService(DownloadManager::class.java)
        return manager.enqueue(request)
    }

    fun query(context: Context, downloadId: Long): Status {
        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(downloadId)

        manager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return Status(Status.State.UNKNOWN)
            return cursor.toStatus()
        }
        return Status(Status.State.UNKNOWN)
    }

    private fun Cursor.toStatus(): Status {
        val status = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val downloaded = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val reason = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))

        val state = when (status) {
            DownloadManager.STATUS_PENDING -> Status.State.QUEUED
            DownloadManager.STATUS_RUNNING -> Status.State.RUNNING
            DownloadManager.STATUS_PAUSED -> Status.State.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> Status.State.SUCCESS
            DownloadManager.STATUS_FAILED -> Status.State.FAILED
            else -> Status.State.UNKNOWN
        }
        return Status(state, downloaded, total, reason)
    }

    private fun safeFileName(uri: Uri): String {
        val segment = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        val decoded = runCatching {
            URLDecoder.decode(segment, StandardCharsets.UTF_8.name())
        }.getOrDefault(segment)

        val sanitized = decoded
            .substringBefore('?')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
            .take(96)

        return if (sanitized.isNotBlank() && sanitized.contains('.')) {
            sanitized
        } else {
            "media_${System.currentTimeMillis()}.bin"
        }
    }
}
