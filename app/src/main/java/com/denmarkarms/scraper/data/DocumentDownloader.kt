package com.denmarkarms.scraper.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed class DownloadResult {
    object Success : DownloadResult()
    data class Failure(val reason: String) : DownloadResult()
}

class DocumentDownloader(private val context: Context, private val httpClient: OkHttpClient) {

    suspend fun download(url: String, appRef: String, displayName: String): DownloadResult {
        if (url.isBlank()) return DownloadResult.Success
        val folder = appRef.replace("/", "_").replace(" ", "_").ifBlank { "planning" }
        val filename = filenameFromUrl(url, displayName)
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    downloadViaMediaStore(url, filename, folder)
                } else {
                    downloadViaFile(url, filename, folder)
                }
                DownloadResult.Success
            } catch (e: Exception) {
                DownloadResult.Failure(e.message ?: "Unknown error")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadViaMediaStore(url: String, filename: String, folder: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folder"

        val finalFilename = nextAvailableFilename(filename) { name ->
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(name, "$relativePath/"),
                null
            )?.use { it.count > 0 } ?: false
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, finalFilename)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeFor(finalFilename))
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("MediaStore insert failed")
        try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string()
                resolver.delete(uri, null, null)
                throw IllegalStateException(httpErrorMessage(response.code, response.header("Retry-After"), body))
            }
            resolver.openOutputStream(uri)?.use { out ->
                response.body?.byteStream()?.copyTo(out)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun downloadViaFile(url: String, filename: String, folder: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("WRITE_EXTERNAL_STORAGE not granted")

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            folder
        )
        dir.mkdirs()

        val finalFilename = nextAvailableFilename(filename) { name -> File(dir, name).exists() }
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) throw IllegalStateException(httpErrorMessage(response.code, response.header("Retry-After"), response.body?.string()))
        File(dir, finalFilename).outputStream().use { out ->
            response.body?.byteStream()?.copyTo(out)
        }
    }

    private fun nextAvailableFilename(filename: String, exists: (String) -> Boolean): String {
        if (!exists(filename)) return filename
        val ext = if (filename.contains(".")) ".${filename.substringAfterLast(".")}" else ""
        val base = filename.dropLast(ext.length)
        var version = 2
        while (true) {
            val candidate = "${base}_V$version$ext"
            if (!exists(candidate)) return candidate
            version++
        }
    }

    private fun filenameFromUrl(url: String, displayName: String): String {
        val fromUrl = url.substringAfterLast("/").takeIf { it.isNotBlank() && it.contains(".") }
        if (fromUrl != null) return fromUrl
        val safe = displayName.replace("""[\\/:"*?<>|]""".toRegex(), "_")
        return "$safe.pdf"
    }

    private fun mimeTypeFor(filename: String): String = when {
        filename.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        filename.endsWith(".doc", ignoreCase = true) -> "application/msword"
        filename.endsWith(".docx", ignoreCase = true) ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    private fun httpErrorMessage(code: Int, retryAfter: String?, body: String? = null): String {
        val base = when (code) {
            429 -> "HTTP 429 — rate limited by portal"
            403 -> "HTTP 403 — access denied"
            401 -> "HTTP 401 — not authorised"
            404 -> "HTTP 404 — file not found"
            else -> "HTTP $code"
        }
        val withRetry = if (retryAfter != null) {
            val seconds = retryAfter.toLongOrNull()
            if (seconds != null) "$base, retry after ${formatDuration(seconds)}" else "$base, retry after $retryAfter"
        } else base
        if (body.isNullOrBlank()) return withRetry
        val snippet = body
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(250)
        return if (snippet.isBlank()) withRetry else "$withRetry\n$snippet"
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val m = seconds / 60; val s = seconds % 60
            if (s == 0L) "${m}m" else "${m}m ${s}s"
        }
        else -> {
            val h = seconds / 3600; val m = (seconds % 3600) / 60
            if (m == 0L) "${h}h" else "${h}h ${m}m"
        }
    }
}
