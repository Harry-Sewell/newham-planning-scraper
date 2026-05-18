package com.denmarkarms.scraper.data.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

data class OfficerSearchResult(
    val name: String,
    val officerId: String,
    val appointmentsUrl: String
)

data class AppointmentResult(
    val companyNumber: String,
    val companyName: String,
    val companyStatus: String,
    val role: String,
    val appointedOn: String,
    val resignedOn: String,
    val natureOfBusiness: String
)

class CompaniesHouseService(private val client: OkHttpClient) {

    private val apiBase = "https://api.company-information.service.gov.uk"

    suspend fun searchOfficers(name: String, apiKey: String): List<OfficerSearchResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(name, "UTF-8")
            val json = get("$apiBase/search/officers?q=$encoded&items_per_page=20", apiKey)
            val root = JsonParser.parseString(json).asJsonObject
            val items = root.getAsJsonArray("items") ?: return@withContext emptyList()
            items.mapNotNull { el ->
                val obj = el.asJsonObject
                val links = obj.getAsJsonObject("links") ?: return@mapNotNull null
                val self = links.getString("self") ?: return@mapNotNull null
                val officerId = self.removePrefix("/officers/").removeSuffix("/appointments")
                    .split("/").firstOrNull() ?: return@mapNotNull null
                OfficerSearchResult(
                    name = obj.getString("title") ?: obj.getString("name") ?: name,
                    officerId = officerId,
                    appointmentsUrl = "$apiBase$self"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAppointments(officerId: String, apiKey: String): List<AppointmentResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || officerId.isBlank()) return@withContext emptyList()
        try {
            val json = get("$apiBase/officers/$officerId/appointments", apiKey)
            val root = JsonParser.parseString(json).asJsonObject
            val items = root.getAsJsonArray("items") ?: return@withContext emptyList()
            items.mapNotNull { el ->
                val obj = el.asJsonObject
                val appointedTo = obj.getAsJsonObject("appointed_to") ?: return@mapNotNull null
                AppointmentResult(
                    companyNumber = appointedTo.getString("company_number") ?: "",
                    companyName = appointedTo.getString("company_name") ?: "",
                    companyStatus = appointedTo.getString("company_status") ?: "",
                    role = obj.getString("officer_role") ?: "",
                    appointedOn = obj.getString("appointed_on") ?: "",
                    resignedOn = obj.getString("resigned_on") ?: "",
                    natureOfBusiness = fetchNatureOfBusiness(
                        appointedTo.getString("company_number") ?: "", apiKey
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchNatureOfBusiness(companyNumber: String, apiKey: String): String {
        if (companyNumber.isBlank()) return ""
        return try {
            val json = get("$apiBase/company/$companyNumber", apiKey)
            val root = JsonParser.parseString(json).asJsonObject
            val sics = root.getAsJsonArray("sic_codes")
            sics?.joinToString(", ") { it.asString } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun get(url: String, apiKey: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(apiKey, ""))
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { resp ->
            resp.body?.string() ?: ""
        }
    }

    private fun JsonObject.getString(key: String): String? =
        if (has(key) && !get(key).isJsonNull) get(key).asString else null
}
