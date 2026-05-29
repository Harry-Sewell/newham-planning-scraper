package com.denmarkarms.scraper.data

import android.content.SharedPreferences
import com.denmarkarms.scraper.data.db.AppDatabase
import com.denmarkarms.scraper.domain.DownloadStatus
import com.denmarkarms.scraper.domain.PrefsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class DocumentDownloadManager(
    private val db: AppDatabase,
    private val downloader: DocumentDownloader,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope
) {
    private val isRunning = AtomicBoolean(false)

    fun trigger() {
        if (!isRunning.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            try {
                processQueue()
            } finally {
                isRunning.set(false)
            }
        }
    }

    private suspend fun processQueue() {
        db.planningDocumentDao().requeueFailed()
        while (true) {
            val queued = db.planningDocumentDao().getQueuedOnce()
            if (queued.isEmpty()) break
            for (doc in queued) {
                db.planningDocumentDao().update(doc.copy(downloadStatus = DownloadStatus.IN_PROGRESS))
                val appRef = db.planningApplicationDao().findByKeyVal(doc.applicationKeyVal)
                    ?.reference?.takeIf { it.isNotBlank() } ?: doc.applicationKeyVal
                val success = downloader.download(doc.url, appRef, doc.name)
                db.planningDocumentDao().update(
                    doc.copy(downloadStatus = if (success) DownloadStatus.DOWNLOADED else DownloadStatus.FAILED)
                )
                val delayMs = prefs.getString(PrefsKeys.DOWNLOAD_DELAY_SECS, "1.5")
                    ?.toDoubleOrNull()?.coerceAtLeast(0.0)?.times(1000)?.toLong() ?: 1500L
                delay(delayMs)
            }
        }
    }
}
