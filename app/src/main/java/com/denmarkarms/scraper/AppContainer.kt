package com.denmarkarms.scraper

import android.content.Context
import android.content.SharedPreferences
import com.denmarkarms.scraper.data.DocumentDownloadManager
import com.denmarkarms.scraper.data.DocumentDownloader
import com.denmarkarms.scraper.data.db.AppDatabase
import com.denmarkarms.scraper.data.network.CompaniesHouseService
import com.denmarkarms.scraper.data.network.NewhamPlanningService
import com.denmarkarms.scraper.data.repository.CompaniesHouseRepository
import com.denmarkarms.scraper.data.repository.PlanningRepository
import com.denmarkarms.scraper.notification.NotificationSender
import kotlinx.coroutines.CoroutineScope
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context, private val scope: CoroutineScope) {

    val db: AppDatabase = AppDatabase.getInstance(context)

    val prefs: SharedPreferences = context.getSharedPreferences("denmark_arms_prefs", Context.MODE_PRIVATE)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val store = mutableMapOf<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { store[url.host] = cookies }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val newhamService = NewhamPlanningService(httpClient)
    val companiesHouseService = CompaniesHouseService(httpClient)

    val documentDownloader = DocumentDownloader(context, httpClient)
    val downloadManager = DocumentDownloadManager(db, documentDownloader, scope)

    val planningRepository = PlanningRepository(db, newhamService)
    val companiesHouseRepository = CompaniesHouseRepository(db, companiesHouseService, prefs)

    val notificationSender = NotificationSender(prefs, httpClient)
}
