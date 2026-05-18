package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.domain.Appointment
import com.denmarkarms.scraper.domain.Person
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PeopleViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as DenmarkArmsApp).container.companiesHouseRepository

    val persons: StateFlow<List<Person>> =
        repo.persons.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appointments: StateFlow<List<Appointment>> =
        repo.appointments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(Date(ts))

    fun buildExportJson(persons: List<Person>, appointments: List<Appointment>): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val array = JsonArray()
        persons.forEach { person ->
            val personAppts = appointments.filter { it.personId == person.id }
            array.add(JsonObject().apply {
                addProperty("name", person.displayName)
                addProperty("url", "https://find-and-update.company-information.service.gov.uk/officers/${person.officerId}/appointments")
                val apptArray = JsonArray()
                personAppts.forEach { appt ->
                    apptArray.add(JsonObject().apply {
                        addProperty("company", appt.companyName)
                        addProperty("companyNumber", appt.companyNumber)
                        addProperty("role", appt.role)
                        addProperty("appointedOn", appt.appointedOn)
                        addProperty("resignedOn", appt.resignedOn)
                        addProperty("url", "https://find-and-update.company-information.service.gov.uk/company/${appt.companyNumber}")
                    })
                }
                add("appointments", apptArray)
            })
        }
        return gson.toJson(array)
    }
}
