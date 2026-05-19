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

class CompaniesHouseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DenmarkArmsApp
            val container = app.container
            val changes = container.companiesHouseRepository.checkAndUpdate()
            if (changes.isNotEmpty()) {
                val subject = NotificationFormatter.companiesHouseSubject(changes)
                val body = NotificationFormatter.companiesHouseBody(changes)
                val actionUrl = changes.firstOrNull()?.let { companiesHouseUrl(it) }
                LocalNotificationHelper.notify(applicationContext, 1002, subject, body, actionUrl)

                val recipients = container.db.recipientDao().getActiveOnce()
                    .map { Recipient(it.id, it.type, it.value, it.active) }
                if (recipients.isNotEmpty()) {
                    container.notificationSender.send(subject, body, recipients,
                        htmlBody = NotificationFormatter.companiesHouseHtmlBody(changes))
                }
            }
            app.container.prefs.edit().putLong("last_checked", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun companiesHouseUrl(change: ChangeLogEntry): String =
        if (change.type == ChangeType.NEW_PERSON)
            "https://find-and-update.company-information.service.gov.uk/officers/${change.entityId}/appointments"
        else
            "https://find-and-update.company-information.service.gov.uk/company/${change.entityId}"
}
