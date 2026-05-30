package com.denmarkarms.scraper.data

import com.denmarkarms.scraper.data.db.AppDatabase
import com.denmarkarms.scraper.data.db.entity.ChangeLogEntity
import com.denmarkarms.scraper.domain.ChangeType
import com.denmarkarms.scraper.domain.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DocumentDownloadManager(
    private val db: AppDatabase,
    private val downloader: DocumentDownloader,
    private val scope: CoroutineScope
) {
    fun downloadDocument(docId: Long) {
        scope.launch(Dispatchers.IO) {
            val doc = db.planningDocumentDao().findById(docId) ?: return@launch
            if (doc.downloadStatus == DownloadStatus.IN_PROGRESS || doc.downloadStatus == DownloadStatus.DOWNLOADED) return@launch
            if (doc.url.isBlank()) return@launch

            db.planningDocumentDao().update(doc.copy(downloadStatus = DownloadStatus.IN_PROGRESS, downloadError = ""))

            val appRef = db.planningApplicationDao().findByKeyVal(doc.applicationKeyVal)
                ?.reference?.takeIf { it.isNotBlank() } ?: doc.applicationKeyVal

            when (val result = downloader.download(doc.url, appRef, doc.name)) {
                is DownloadResult.Success -> {
                    db.planningDocumentDao().update(doc.copy(downloadStatus = DownloadStatus.DOWNLOADED, downloadError = ""))
                }
                is DownloadResult.Failure -> {
                    db.planningDocumentDao().update(doc.copy(downloadStatus = DownloadStatus.FAILED, downloadError = result.reason))
                    db.changeLogDao().insert(
                        ChangeLogEntity(
                            type = ChangeType.DOWNLOAD_FAILED,
                            description = "Download failed for '${doc.name}' ($appRef): ${result.reason}",
                            entityId = doc.applicationKeyVal,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
}
