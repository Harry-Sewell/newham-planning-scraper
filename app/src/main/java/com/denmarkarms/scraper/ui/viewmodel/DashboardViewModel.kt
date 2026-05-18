package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.data.db.entity.ChangeLogEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as DenmarkArmsApp).container

    val recentChanges: StateFlow<List<ChangeLogEntity>> =
        container.db.changeLogDao().getRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun runPlanningCheck() {
        viewModelScope.launch {
            try { container.planningRepository.checkAndUpdate() } catch (_: Exception) {}
        }
    }

    fun runCompaniesHouseCheck() {
        viewModelScope.launch {
            try { container.companiesHouseRepository.checkAndUpdate() } catch (_: Exception) {}
        }
    }

    fun dismissEntry(entry: com.denmarkarms.scraper.data.db.entity.ChangeLogEntity) {
        viewModelScope.launch {
            container.db.changeLogDao().delete(entry)
        }
    }

    fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(Date(ts))
}
