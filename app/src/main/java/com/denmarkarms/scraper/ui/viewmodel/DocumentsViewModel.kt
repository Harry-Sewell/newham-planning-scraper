package com.denmarkarms.scraper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.denmarkarms.scraper.DenmarkArmsApp
import com.denmarkarms.scraper.data.db.entity.PlanningApplicationEntity
import com.denmarkarms.scraper.data.db.entity.PlanningDocumentEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as DenmarkArmsApp).container
    private val db = container.db

    val applications: StateFlow<List<PlanningApplicationEntity>> =
        db.planningApplicationDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<PlanningDocumentEntity>> =
        db.planningDocumentDao().getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun downloadDocument(doc: PlanningDocumentEntity) {
        container.downloadManager.downloadDocument(doc.id)
    }
}
