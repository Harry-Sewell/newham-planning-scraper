package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.data.db.entity.RecipientEntity
import com.denmarkarms.scraper.domain.*
import com.denmarkarms.scraper.notification.LocalNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as DenmarkArmsApp).container
    private val planningRepo = container.planningRepository
    private val chRepo = container.companiesHouseRepository
    private val prefs = container.prefs
    private val db = container.db

    init {
        // Persist UI defaults so NotificationSender sees them even if the user never edits the field
        val defaults = mapOf(
            PrefsKeys.SMTP_HOST to "smtp.gmail.com",
            PrefsKeys.SMTP_PORT to "587",
            PrefsKeys.SMTP_FROM_NAME to "Denmark Arms Scraper"
        )
        val editor = prefs.edit()
        defaults.forEach { (key, default) ->
            if (prefs.getString(key, "").isNullOrBlank()) editor.putString(key, default)
        }
        editor.apply()
    }

    val monitoredAddresses: StateFlow<List<MonitoredAddress>> =
        planningRepo.monitoredAddresses
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredPersons: StateFlow<List<MonitoredPerson>> =
        chRepo.monitoredPersons
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipients: StateFlow<List<Recipient>> =
        db.recipientDao().getAll()
            .map { list -> list.map { Recipient(it.id, it.type, it.value, it.active) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAddress(address: String) = viewModelScope.launch {
        if (address.isNotBlank()) planningRepo.addMonitoredAddress(address)
    }

    fun removeAddress(address: MonitoredAddress) = viewModelScope.launch {
        planningRepo.removeMonitoredAddress(address)
    }

    fun toggleMonitoredAddress(address: MonitoredAddress) = viewModelScope.launch {
        planningRepo.toggleMonitoredAddress(address)
    }

    fun addPerson(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) chRepo.addMonitoredPerson(name)
    }

    fun removePerson(person: MonitoredPerson) = viewModelScope.launch {
        chRepo.removeMonitoredPerson(person)
    }

    fun toggleMonitoredPerson(person: MonitoredPerson) = viewModelScope.launch {
        chRepo.toggleMonitoredPerson(person)
    }

    fun addRecipient(type: String, value: String) = viewModelScope.launch {
        if (value.isNotBlank()) {
            db.recipientDao().insert(RecipientEntity(type = type, value = value.trim()))
        }
    }

    fun removeRecipient(recipient: Recipient) = viewModelScope.launch {
        db.recipientDao().delete(RecipientEntity(recipient.id, recipient.type, recipient.value, recipient.active))
    }

    fun toggleRecipient(recipient: Recipient) = viewModelScope.launch {
        db.recipientDao().update(RecipientEntity(recipient.id, recipient.type, recipient.value, !recipient.active))
    }

    fun getPref(key: String, default: String = ""): String = prefs.getString(key, default) ?: default

    fun setPref(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private val _testStatus = MutableStateFlow<String?>(null)
    val testStatus: StateFlow<String?> = _testStatus.asStateFlow()

    fun sendTestNotification() = viewModelScope.launch {
        _testStatus.value = "Sending…"
        val results = mutableListOf<String>()

        LocalNotificationHelper.notify(
            getApplication(), id = 9000,
            title = "Test – Denmark Arms Scraper",
            body = "Push notifications are working correctly."
        )
        results.add("Push: sent ✓")

        val allRecipients = db.recipientDao().getActiveOnce()
            .map { Recipient(it.id, it.type, it.value, it.active) }

        val emailRecipients = allRecipients.filter { it.type == RecipientType.EMAIL }
        val waRecipients = allRecipients.filter { it.type == RecipientType.WHATSAPP }

        if (emailRecipients.isEmpty()) {
            results.add("Email: no active recipients configured")
        } else {
            results.add(container.notificationSender.sendTestEmail(emailRecipients.map { it.value }))
        }

        if (waRecipients.isEmpty()) {
            results.add("WhatsApp: no active recipients")
        } else {
            results.add(container.notificationSender.sendTestWhatsApp(waRecipients.map { it.value }))
        }

        _testStatus.value = results.joinToString("\n")
    }

    fun clearTestStatus() { _testStatus.value = null }
}
