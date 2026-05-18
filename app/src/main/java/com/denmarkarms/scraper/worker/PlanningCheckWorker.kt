package com.denmarkarms.scraper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.domain.ChangeLogEntry
import com.denmarkarms.scraper.notification.LocalNotificationHelper

class PlanningCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DenmarkArmsApp
            val container = app.container
            val changes = container.planningRepository.checkAndUpdate()
            if (changes.isNotEmpty()) {
                sendLocalNotification(changes)
                sendNotification(changes, app)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendLocalNotification(changes: List<ChangeLogEntry>) {
        val body = changes.take(3).joinToString("\n") { "• ${it.description.take(80)}" } +
            if (changes.size > 3) "\n…and ${changes.size - 3} more" else ""
        val actionUrl = changes.firstOrNull()?.let { planningUrl(it) }
        LocalNotificationHelper.notify(
            applicationContext,
            id = 1001,
            title = "Planning: ${changes.size} new change${if (changes.size != 1) "s" else ""}",
            body = body,
            actionUrl = actionUrl
        )
    }

    private fun planningUrl(change: ChangeLogEntry): String {
        val tab = if (change.type == com.denmarkarms.scraper.domain.ChangeType.NEW_DOCUMENT) "documents" else "summary"
        return "https://pa.newham.gov.uk/online-applications/applicationDetails.do?activeTab=$tab&keyVal=${change.entityId}"
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
