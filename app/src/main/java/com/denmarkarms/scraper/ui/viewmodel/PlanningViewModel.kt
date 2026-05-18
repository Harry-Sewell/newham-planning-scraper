package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.domain.PlanningApplication
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
}
