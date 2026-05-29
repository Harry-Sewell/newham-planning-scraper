package com.denmarkarms.scraper.domain

data class PlanningApplication(
    val id: Long = 0,
    val keyVal: String,
    val searchAddress: String,
    val reference: String,
    val description: String,
    val address: String,
    val status: String,
    val receivedDate: String,
    val lastChecked: Long = System.currentTimeMillis(),
    val firstSeen: Long = System.currentTimeMillis()
)

data class PlanningDocument(
    val id: Long = 0,
    val applicationKeyVal: String,
    val name: String,
    val date: String,
    val url: String = "",
    val firstSeen: Long = System.currentTimeMillis()
)

data class MonitoredAddress(
    val id: Long = 0,
    val address: String,
    val active: Boolean = true
)

data class MonitoredPerson(
    val id: Long = 0,
    val name: String,
    val active: Boolean = true
)

data class Person(
    val id: Long = 0,
    val monitoredName: String,
    val displayName: String,
    val officerId: String = "",
    val profileUrl: String = "",
    val firstSeen: Long = System.currentTimeMillis()
)

data class Appointment(
    val id: Long = 0,
    val personId: Long,
    val personDisplayName: String,
    val companyNumber: String,
    val companyName: String,
    val companyStatus: String = "",
    val natureOfBusiness: String = "",
    val role: String,
    val appointedOn: String,
    val resignedOn: String = "",
    val firstSeen: Long = System.currentTimeMillis()
)

data class Recipient(
    val id: Long = 0,
    val type: String,
    val value: String,
    val active: Boolean = true
)

data class ChangeLogEntry(
    val id: Long = 0,
    val type: String,
    val description: String,
    val entityId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object ChangeType {
    const val NEW_APPLICATION = "NEW_APPLICATION"
    const val NEW_DOCUMENT = "NEW_DOCUMENT"
    const val STATUS_CHANGE = "STATUS_CHANGE"
    const val NEW_PERSON = "NEW_PERSON"
    const val NEW_APPOINTMENT = "NEW_APPOINTMENT"
}

object RecipientType {
    const val EMAIL = "email"
    const val WHATSAPP = "whatsapp"
}

object PrefsKeys {
    const val SMTP_HOST = "smtp_host"
    const val SMTP_PORT = "smtp_port"
    const val SMTP_USERNAME = "smtp_username"
    const val SMTP_PASSWORD = "smtp_password"
    const val SMTP_FROM_NAME = "smtp_from_name"
    const val TWILIO_ACCOUNT_SID = "twilio_account_sid"
    const val TWILIO_AUTH_TOKEN = "twilio_auth_token"
    const val TWILIO_FROM_NUMBER = "twilio_from_number"
    const val COMPANIES_HOUSE_API_KEY = "ch_api_key"
    const val DOWNLOAD_DELAY_SECS = "download_delay_secs"
}

object DownloadStatus {
    const val QUEUED = "queued"
    const val IN_PROGRESS = "in_progress"
    const val DOWNLOADED = "downloaded"
    const val FAILED = "failed"
}
