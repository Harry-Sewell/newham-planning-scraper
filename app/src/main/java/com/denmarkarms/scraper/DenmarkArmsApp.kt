package com.denmarkarms.scraper

import android.app.Application
import androidx.work.*
import com.denmarkarms.scraper.notification.LocalNotificationHelper
import com.denmarkarms.scraper.worker.CompaniesHouseCheckWorker
import com.denmarkarms.scraper.worker.PlanningCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DenmarkArmsApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)
        LocalNotificationHelper.createChannel(this)
        scheduleBackgroundWork()
        applicationScope.launch(Dispatchers.IO) {
            container.db.planningDocumentDao().resetInProgress()
        }
    }

    private fun scheduleBackgroundWork() {
        val workManager = WorkManager.getInstance(this)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val planningWork = PeriodicWorkRequestBuilder<PlanningCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        val companiesHouseWork = PeriodicWorkRequestBuilder<CompaniesHouseCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork("planning_check", ExistingPeriodicWorkPolicy.KEEP, planningWork)
        workManager.enqueueUniquePeriodicWork("companies_house_check", ExistingPeriodicWorkPolicy.KEEP, companiesHouseWork)
    }
}
