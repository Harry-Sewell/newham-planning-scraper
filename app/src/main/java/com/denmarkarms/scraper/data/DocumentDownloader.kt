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

class DocumentDownloader(private val context: Context, private val httpClient: OkHttpClient) {

    suspend fun download(url: String, appRef: String, displayName: String) {
        if (url.isBlank()) return
        val folder = appRef.replace("/", "_").replace(" ", "_").ifBlank { "planning" }
        val filename = filenameFromUrl(url, displayName)
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    downloadViaMediaStore(url, filename, folder)
                } else {
                    downloadViaFile(url, filename, folder)
                }
            } catch (_: Exception) {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadViaMediaStore(url: String, filename: String, folder: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folder"

        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(filename, "$relativePath/"),
            null
        )?.use { if (it.count > 0) return }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeTypeFor(filename))
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return
        try {
            val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
            if (response.isSuccessful) {
                resolver.openOutputStream(uri)?.use { out ->
                    response.body?.byteStream()?.copyTo(out)
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun downloadViaFile(url: String, filename: String, folder: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) return

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            folder
        )
        dir.mkdirs()
        val file = File(dir, filename)
        if (file.exists()) return

        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (response.isSuccessful) {
            file.outputStream().use { out ->
                response.body?.byteStream()?.copyTo(out)
            }
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
}
