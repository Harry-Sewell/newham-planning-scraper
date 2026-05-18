package com.denmarkarms.scraper

import android.content.Context
import android.content.SharedPreferences
import com.denmarkarms.scraper.data.db.AppDatabase
import com.denmarkarms.scraper.data.network.CompaniesHouseService
import com.denmarkarms.scraper.data.network.NewhamPlanningService
import com.denmarkarms.scraper.data.repository.CompaniesHouseRepository
import com.denmarkarms.scraper.data.repository.PlanningRepository
import com.denmarkarms.scraper.notification.NotificationSender
import okhttp3.OkHttpClient
import java.net.CookieManager
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val db: AppDatabase = AppDatabase.getInstance(context)

    val prefs: SharedPreferences = context.getSharedPreferences("denmark_arms_prefs", Context.MODE_PRIVATE)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(okhttp3.JavaNetCookieJar(CookieManager()))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val newhamService = NewhamPlanningService(httpClient)
    val companiesHouseService = CompaniesHouseService(httpClient)

    val planningRepository = PlanningRepository(db, newhamService)
    val companiesHouseRepository = CompaniesHouseRepository(db, companiesHouseService, prefs)

    val notificationSender = NotificationSender(prefs, httpClient)
}
