package com.denmarkarms.scraper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.domain.ChangeLogEntry
import com.denmarkarms.scraper.domain.ChangeType
import com.denmarkarms.scraper.domain.Recipient
import com.denmarkarms.scraper.notification.LocalNotificationHelper
import com.denmarkarms.scraper.notification.NotificationFormatter

class PlanningCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DenmarkArmsApp
            val container = app.container
            val changes = container.planningRepository.checkAndUpdate()
            if (changes.isNotEmpty()) {
                val subject = NotificationFormatter.planningSubject(changes)
                val body = NotificationFormatter.planningBody(changes)
                val actionUrl = changes.firstOrNull()?.let { planningUrl(it) }
                LocalNotificationHelper.notify(applicationContext, 1001, subject, body, actionUrl)

                val recipients = container.db.recipientDao().getActiveOnce()
                    .map { Recipient(it.id, it.type, it.value, it.active) }
                if (recipients.isNotEmpty()) {
                    container.notificationSender.send(subject, body, recipients,
                        htmlBody = NotificationFormatter.planningHtmlBody(changes))
                }
            }
            app.container.prefs.edit().putLong("last_checked", System.currentTimeMillis()).apply()
            app.container.downloadManager.requeueFailed()
            app.container.downloadManager.trigger()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun planningUrl(change: ChangeLogEntry): String {
        val tab = if (change.type == ChangeType.NEW_DOCUMENT) "documents" else "summary"
        return "https://pa.newham.gov.uk/online-applications/applicationDetails.do?activeTab=$tab&keyVal=${change.entityId}"
    }
}
