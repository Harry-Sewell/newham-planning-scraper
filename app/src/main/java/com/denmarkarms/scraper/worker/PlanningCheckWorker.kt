package com.denmarkarms.scraper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.domain.ChangeLogEntry

class PlanningCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DenmarkArmsApp
            val container = app.container
            val changes = container.planningRepository.checkAndUpdate()
            if (changes.isNotEmpty()) sendNotification(changes, app)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun sendNotification(changes: List<ChangeLogEntry>, app: DenmarkArmsApp) {
        val container = app.container
        val recipients = container.db.recipientDao().getActiveOnce()
            .map { com.denmarkarms.scraper.domain.Recipient(it.id, it.type, it.value, it.active) }
        if (recipients.isEmpty()) return

        val body = buildString {
            appendLine("Denmark Arms Scraper - Planning Update")
            appendLine("=" .repeat(40))
            for (change in changes) {
                appendLine()
                appendLine(change.description)
            }
        }
        container.notificationSender.send(
            subject = "Planning Update: ${changes.size} change(s) detected",
            body = body,
            recipients = recipients
        )
    }
}
