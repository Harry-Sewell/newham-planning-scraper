package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.data.db.entity.ChangeLogEntity
import com.denmarkarms.scraper.data.db.entity.MonitoredAddressEntity
import com.denmarkarms.scraper.data.db.entity.MonitoredPersonEntity
import com.denmarkarms.scraper.domain.ChangeLogEntry
import com.denmarkarms.scraper.domain.ChangeType
import com.denmarkarms.scraper.domain.Recipient
import com.denmarkarms.scraper.notification.LocalNotificationHelper
import com.denmarkarms.scraper.notification.NotificationFormatter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as DenmarkArmsApp).container
    private val prefs = container.prefs

    val recentChanges: StateFlow<List<ChangeLogEntity>> =
        container.db.changeLogDao().getRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredAddresses: StateFlow<List<MonitoredAddressEntity>> =
        container.db.monitoredAddressDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredPersons: StateFlow<List<MonitoredPersonEntity>> =
        container.db.monitoredPersonDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _lastChecked = MutableStateFlow<Long?>(
        prefs.getLong("last_checked", 0L).takeIf { it > 0L }
    )
    val lastChecked: StateFlow<Long?> = _lastChecked.asStateFlow()

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "last_checked") {
            _lastChecked.value = prefs.getLong("last_checked", 0L).takeIf { it > 0L }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }

    fun runChecks() {
        viewModelScope.launch {
            _isChecking.value = true
            try {
                coroutineScope {
                    launch { runPlanningCheck() }
                    launch { runCompaniesHouseCheck() }
                }
            } finally {
                _isChecking.value = false
                val now = System.currentTimeMillis()
                _lastChecked.value = now
                prefs.edit().putLong("last_checked", now).apply()
            }
        }
    }

    private suspend fun runPlanningCheck() {
        try {
            val changes = container.planningRepository.checkAndUpdate()
            if (changes.isNotEmpty()) {
                val subject = NotificationFormatter.planningSubject(changes)
                val body = NotificationFormatter.planningBody(changes)
                val url = changes.firstOrNull()?.let { planningUrl(it) }
                LocalNotificationHelper.notify(getApplication(), 1001, subject, body, url)
                sendRemote(subject, body, NotificationFormatter.planningHtmlBody(changes))
            }
        } catch (_: Exception) {}
    }

    private suspend fun runCompaniesHouseCheck() {
        try {
            val changes = container.companiesHouseRepository.checkAndUpdate()
            if (changes.isNotEmpty()) {
                val subject = NotificationFormatter.companiesHouseSubject(changes)
                val body = NotificationFormatter.companiesHouseBody(changes)
                val url = changes.firstOrNull()?.let { companiesHouseUrl(it) }
                LocalNotificationHelper.notify(getApplication(), 1002, subject, body, url)
                sendRemote(subject, body, NotificationFormatter.companiesHouseHtmlBody(changes))
            }
        } catch (_: Exception) {}
    }

    fun dismissEntry(entry: ChangeLogEntity) {
        viewModelScope.launch { container.db.changeLogDao().delete(entry) }
    }

    fun dismissAll() {
        viewModelScope.launch { container.db.changeLogDao().deleteAll() }
    }

    fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(Date(ts))

    private suspend fun sendRemote(subject: String, body: String, htmlBody: String? = null) {
        val recipients = container.db.recipientDao().getActiveOnce()
            .map { Recipient(it.id, it.type, it.value, it.active) }
        if (recipients.isEmpty()) return
        container.notificationSender.send(subject, body, recipients, htmlBody)
    }

    private fun planningUrl(c: ChangeLogEntry): String {
        val tab = if (c.type == ChangeType.NEW_DOCUMENT) "documents" else "summary"
        return "https://pa.newham.gov.uk/online-applications/applicationDetails.do?activeTab=$tab&keyVal=${c.entityId}"
    }

    private fun companiesHouseUrl(c: ChangeLogEntry): String =
        if (c.type == ChangeType.NEW_PERSON)
            "https://find-and-update.company-information.service.gov.uk/officers/${c.entityId}/appointments"
        else
            "https://find-and-update.company-information.service.gov.uk/company/${c.entityId}"
}
