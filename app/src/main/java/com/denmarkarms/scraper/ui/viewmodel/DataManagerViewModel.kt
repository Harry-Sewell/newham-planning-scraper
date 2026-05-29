package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.data.db.entity.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as DenmarkArmsApp).container.db

    val applications: StateFlow<List<PlanningApplicationEntity>> =
        db.planningApplicationDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<PlanningDocumentEntity>> =
        db.planningDocumentDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val persons: StateFlow<List<PersonEntity>> =
        db.personDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appointments: StateFlow<List<AppointmentEntity>> =
        db.appointmentDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val changeLog: StateFlow<List<ChangeLogEntity>> =
        db.changeLogDao().getRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Planning applications + documents
    fun deleteApplication(app: PlanningApplicationEntity) = viewModelScope.launch {
        db.planningDocumentDao().deleteForApplication(app.keyVal)
        db.planningApplicationDao().delete(app)
    }

    fun deleteDocument(doc: PlanningDocumentEntity) = viewModelScope.launch {
        db.planningDocumentDao().delete(doc)
    }

    fun clearAllApplications() = viewModelScope.launch {
        db.planningDocumentDao().deleteAll()
        db.planningApplicationDao().deleteAll()
    }

    // Persons + their appointments
    fun deletePerson(person: PersonEntity) = viewModelScope.launch {
        db.appointmentDao().deleteForPerson(person.id)
        db.personDao().delete(person)
    }

    fun clearAllPersons() = viewModelScope.launch {
        db.appointmentDao().deleteAll()
        db.personDao().deleteAll()
    }

    // Individual appointments
    fun deleteAppointment(appointment: AppointmentEntity) = viewModelScope.launch {
        db.appointmentDao().delete(appointment)
    }

    // Change log
    fun deleteLogEntry(entry: ChangeLogEntity) = viewModelScope.launch {
        db.changeLogDao().delete(entry)
    }

    fun clearChangeLog() = viewModelScope.launch {
        db.changeLogDao().deleteAll()
    }

    fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(Date(ts))

    fun retryFailedDownloads() = viewModelScope.launch {
        val app = getApplication<DenmarkArmsApp>()
        app.container.downloadManager.requeueFailed()
        app.container.downloadManager.trigger()
    }
}
