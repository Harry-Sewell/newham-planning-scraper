package com.denmarkarms.scraper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.domain.ChangeLogEntry
import com.denmarkarms.scraper.notification.LocalNotificationHelper

class CompaniesHouseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DenmarkArmsApp
            val container = app.container
            val changes = container.companiesHouseRepository.checkAndUpdate()
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
        LocalNotificationHelper.notify(
            applicationContext,
            id = 1002,
            title = "Companies House: ${changes.size} new change${if (changes.size != 1) "s" else ""}",
            body = body
        )
    }

    private suspend fun sendNotification(changes: List<ChangeLogEntry>, app: DenmarkArmsApp) {
        val container = app.container
        val recipients = container.db.recipientDao().getActiveOnce()
            .map { com.denmarkarms.scraper.domain.Recipient(it.id, it.type, it.value, it.active) }
        if (recipients.isEmpty()) return

        val body = buildString {
            appendLine("Denmark Arms Scraper - Companies House Update")
            appendLine("=" .repeat(40))
            for (change in changes) {
                appendLine()
                appendLine(change.description)
            }
        }
        container.notificationSender.send(
            subject = "Companies House Update: ${changes.size} change(s) detected",
            body = body,
            recipients = recipients
        )
    }
}
