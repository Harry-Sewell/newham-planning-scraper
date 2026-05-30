package com.denmarkarms.scraper.ui.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.denmarkarms.scraper.data.db.entity.PlanningApplicationEntity
import com.denmarkarms.scraper.data.db.entity.PlanningDocumentEntity
import com.denmarkarms.scraper.domain.DownloadStatus
import com.denmarkarms.scraper.ui.viewmodel.DocumentsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(vm: DocumentsViewModel = viewModel()) {
    val applications by vm.applications.collectAsState()
    val documents by vm.documents.collectAsState()

    val appMap = remember(applications) { applications.associateBy { it.keyVal } }
    val grouped = remember(documents) {
        documents.groupBy { it.applicationKeyVal }
            .entries.sortedByDescending { (_, docs) -> docs.maxOf { it.firstSeen } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No documents found yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Documents appear here when new planning applications are discovered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        "${documents.size} document${if (documents.size != 1) "s" else ""} across ${grouped.size} application${if (grouped.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                grouped.forEach { (keyVal, appDocs) ->
                    val app = appMap[keyVal]
                    val appRef = app?.reference?.takeIf { it.isNotBlank() } ?: keyVal
                    item(key = "header_$keyVal") {
                        ApplicationHeader(app = app, keyVal = keyVal, docCount = appDocs.size)
                    }
                    items(appDocs, key = { it.id }) { doc ->
                        DocumentRow(
                            doc = doc,
                            appRef = appRef,
                            onDownload = { vm.downloadDocument(doc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationHeader(
    app: PlanningApplicationEntity?,
    keyVal: String,
    docCount: Int
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                app?.reference?.takeIf { it.isNotBlank() } ?: keyVal,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$docCount doc${if (docCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        if (app != null && app.description.isNotBlank()) {
            Text(
                app.description.take(80) + if (app.description.length > 80) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun DocumentRow(
    doc: PlanningDocumentEntity,
    appRef: String,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openDocument(context, doc, appRef) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                if (doc.downloadStatus == DownloadStatus.DOWNLOADED) Icons.Default.InsertDriveFile
                else Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                tint = if (doc.downloadStatus == DownloadStatus.DOWNLOADED)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                val filename = docFilename(doc.url, doc.name)
                if (filename != null) {
                    Text(
                        filename,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (doc.date.isNotBlank()) {
                    Text(
                        doc.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(Modifier.height(6.dp))
                DocumentStatusRow(doc = doc, onDownload = onDownload)
            }
        }
    }
}

@Composable
private fun DocumentStatusRow(doc: PlanningDocumentEntity, onDownload: () -> Unit) {
    when (doc.downloadStatus) {
        DownloadStatus.QUEUED -> {
            if (doc.url.isNotBlank()) {
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Download", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                StatusChip(color = MaterialTheme.colorScheme.tertiary, label = "No file")
            }
        }
        DownloadStatus.IN_PROGRESS -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Downloading…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        DownloadStatus.DOWNLOADED -> {
            StatusChip(color = MaterialTheme.colorScheme.tertiary, label = "Downloaded · tap to open", icon = Icons.Default.CheckCircle)
        }
        DownloadStatus.FAILED -> {
            Column {
                StatusChip(color = MaterialTheme.colorScheme.error, label = "Failed", icon = Icons.Default.ErrorOutline)
                if (doc.downloadError.isNotBlank()) {
                    Text(
                        doc.downloadError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun openDocument(context: Context, doc: PlanningDocumentEntity, appRef: String) {
    if (doc.downloadStatus == DownloadStatus.DOWNLOADED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val filename = docFilename(doc.url, doc.name)
        if (filename != null) {
            val folder = appRef.replace("/", "_").replace(" ", "_").ifBlank { "planning" }
            val localUri = findInMediaStore(context, folder, filename)
            if (localUri != null) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(localUri, mimeTypeFor(filename))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                    return
                } catch (_: Exception) {}
            }
        }
    }
    if (doc.url.isNotBlank()) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc.url)))
    }
}

private fun findInMediaStore(context: Context, folder: String, filename: String): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$folder/"
    val baseName = filename.substringBeforeLast(".")
    context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
        "${MediaStore.Downloads.RELATIVE_PATH} = ?",
        arrayOf(relativePath),
        "${MediaStore.Downloads.DATE_ADDED} DESC"
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
            if (name == filename || name.startsWith("${baseName}_V")) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return ContentUris.withAppendedId(collection, id)
            }
        }
    }
    return null
}

private fun docFilename(url: String, name: String): String? {
    if (url.isBlank()) return null
    val fromUrl = url.substringAfterLast("/").takeIf { it.isNotBlank() && it.contains(".") }
    val filename = fromUrl ?: "${name.replace("""[\\/:"*?<>|]""".toRegex(), "_")}.pdf"
    return filename.takeIf { it != name }
}

private fun mimeTypeFor(filename: String): String = when {
    filename.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
    filename.endsWith(".doc", ignoreCase = true) -> "application/msword"
    filename.endsWith(".docx", ignoreCase = true) ->
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    else -> "application/octet-stream"
}

@Composable
private fun StatusChip(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
