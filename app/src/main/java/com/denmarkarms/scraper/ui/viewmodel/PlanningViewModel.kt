package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.domain.PlanningApplication
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlanningViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as DenmarkArmsApp).container.planningRepository

    val applications: StateFlow<List<PlanningApplication>> =
        repo.applications
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(Date(ts))

    fun buildExportJson(applications: List<PlanningApplication>): String {
        val array = JsonArray()
        applications.forEach { app ->
            array.add(JsonObject().apply {
                addProperty("reference", app.reference)
                addProperty("description", app.description)
                addProperty("address", app.address)
                addProperty("status", app.status)
                addProperty("receivedDate", app.receivedDate)
                addProperty("url", "https://pa.newham.gov.uk/online-applications/applicationDetails.do?activeTab=summary&keyVal=${app.keyVal}")
            })
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(array)
    }
}
