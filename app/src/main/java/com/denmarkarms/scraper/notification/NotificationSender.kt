package com.denmarkarms.scraper.notification

import android.content.SharedPreferences
import com.denmarkarms.scraper.domain.PrefsKeys
import com.denmarkarms.scraper.domain.Recipient
import com.denmarkarms.scraper.domain.RecipientType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class NotificationSender(
    private val prefs: SharedPreferences,
    private val httpClient: OkHttpClient
) {
    suspend fun send(subject: String, body: String, recipients: List<Recipient>) {
        if (recipients.isEmpty()) return
        val emailRecipients = recipients.filter { it.active && it.type == RecipientType.EMAIL }
        val whatsappRecipients = recipients.filter { it.active && it.type == RecipientType.WHATSAPP }

        if (emailRecipients.isNotEmpty()) sendEmail(subject, body, emailRecipients.map { it.value })
        if (whatsappRecipients.isNotEmpty()) sendWhatsApp(body, whatsappRecipients.map { it.value })
    }

    private suspend fun sendEmail(subject: String, body: String, toAddresses: List<String>) = withContext(Dispatchers.IO) {
        val host = prefs.getString(PrefsKeys.SMTP_HOST, "") ?: ""
        val port = prefs.getString(PrefsKeys.SMTP_PORT, "587") ?: "587"
        val username = prefs.getString(PrefsKeys.SMTP_USERNAME, "") ?: ""
        val password = prefs.getString(PrefsKeys.SMTP_PASSWORD, "") ?: ""
        val fromName = prefs.getString(PrefsKeys.SMTP_FROM_NAME, "Denmark Arms Scraper") ?: "Denmark Arms Scraper"

        if (host.isBlank() || username.isBlank() || password.isBlank()) return@withContext

        try {
            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.ssl.trust", host)
                put("mail.smtp.timeout", "15000")
                put("mail.smtp.connectiontimeout", "15000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username, fromName))
                setRecipients(Message.RecipientType.TO, toAddresses.joinToString(","))
                setSubject(subject, "UTF-8")
                setText(body, "UTF-8")
            }

            Transport.send(message)
        } catch (e: Exception) {
            // Log failure silently — next check will retry
        }
    }

    private suspend fun sendWhatsApp(body: String, toNumbers: List<String>) = withContext(Dispatchers.IO) {
        val accountSid = prefs.getString(PrefsKeys.TWILIO_ACCOUNT_SID, "") ?: ""
        val authToken = prefs.getString(PrefsKeys.TWILIO_AUTH_TOKEN, "") ?: ""
        val fromNumber = prefs.getString(PrefsKeys.TWILIO_FROM_NUMBER, "") ?: ""

        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) return@withContext

        val url = "https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json"
        val credential = Credentials.basic(accountSid, authToken)

        for (to in toNumbers) {
            try {
                val formBody = FormBody.Builder()
                    .add("From", "whatsapp:$fromNumber")
                    .add("To", "whatsapp:$to")
                    .add("Body", body)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .header("Authorization", credential)
                    .build()
                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                // Log failure silently
            }
        }
    }
}
