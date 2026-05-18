package com.denmarkarms.scraper.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class PlanningApplicationResult(
    val keyVal: String,
    val reference: String,
    val description: String,
    val address: String,
    val status: String,
    val receivedDate: String
)

data class PlanningDocumentResult(
    val name: String,
    val date: String,
    val url: String
)

class NewhamPlanningService(private val client: OkHttpClient) {

    private val baseUrl = "https://pa.newham.gov.uk/online-applications"

    suspend fun searchApplications(address: String): List<PlanningApplicationResult> = withContext(Dispatchers.IO) {
        try {
            val searchPageUrl = "$baseUrl/search.do?action=simple&searchType=Application"
            val pageHtml = get(searchPageUrl)
            val doc = Jsoup.parse(pageHtml)
            val csrfToken = doc.select("input[name=_csrf]").attr("value")

            val formBody = FormBody.Builder()
                .add("searchCriteria.description", address)
                .add("searchCriteria.searchType", "Application")
                .add("action", "Search")
                .apply { if (csrfToken.isNotEmpty()) add("_csrf", csrfToken) }
                .build()

            val request = Request.Builder()
                .url("$baseUrl/search.do")
                .post(formBody)
                .header("Referer", searchPageUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; DenmarkArmsScraper/1.0)")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext emptyList()
            parseSearchResults(Jsoup.parse(html))
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getDocuments(keyVal: String): List<PlanningDocumentResult> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/applicationDetails.do?activeTab=documents&keyVal=$keyVal"
            val html = get(url)
            val doc = Jsoup.parse(html)
            parseDocuments(doc, keyVal)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getApplicationStatus(keyVal: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/applicationDetails.do?activeTab=summary&keyVal=$keyVal"
            val html = get(url)
            val doc = Jsoup.parse(html)
            doc.select("span.appealStatus, td:contains(Status) + td, .keyinfo td:contains(Status) + td")
                .firstOrNull()?.text()?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseSearchResults(doc: Document): List<PlanningApplicationResult> {
        val results = mutableListOf<PlanningApplicationResult>()
        // Idox planning portal result format
        doc.select("li.searchresult").forEach { li ->
            val link = li.selectFirst("a.summaryLink") ?: return@forEach
            val href = link.attr("href")
            val keyVal = extractKeyVal(href) ?: return@forEach
            val reference = link.selectFirst("h3")?.text()?.trim() ?: ""
            val description = link.select("p.description, .description").firstOrNull()?.text()?.trim() ?: ""
            val address = link.select("p.address, .address").firstOrNull()?.text()?.trim() ?: ""
            val metaText = li.select("p.metaInfo, .metaInfo, .status").text()
            val status = extractField(metaText, "Status:") ?: extractField(metaText, "status") ?: ""
            val receivedDate = extractField(metaText, "Received:") ?: extractField(metaText, "received") ?: ""
            results.add(PlanningApplicationResult(keyVal, reference, description, address, status.trim(), receivedDate.trim()))
        }
        // Fallback: try table rows if list not found
        if (results.isEmpty()) {
            doc.select("table.searchresults tr, table#searchresults tr").drop(1).forEach { row ->
                val cells = row.select("td")
                if (cells.size >= 3) {
                    val linkEl = row.selectFirst("a") ?: return@forEach
                    val keyVal = extractKeyVal(linkEl.attr("href")) ?: return@forEach
                    val reference = cells.getOrNull(0)?.text()?.trim() ?: ""
                    val address = cells.getOrNull(1)?.text()?.trim() ?: ""
                    val description = cells.getOrNull(2)?.text()?.trim() ?: ""
                    val status = cells.getOrNull(3)?.text()?.trim() ?: ""
                    val receivedDate = cells.getOrNull(4)?.text()?.trim() ?: ""
                    results.add(PlanningApplicationResult(keyVal, reference, description, address, status, receivedDate))
                }
            }
        }
        return results
    }

    private fun parseDocuments(doc: Document, keyVal: String): List<PlanningDocumentResult> {
        val docs = mutableListOf<PlanningDocumentResult>()
        doc.select("table.display tbody tr, table#Documents tbody tr").forEach { row ->
            val cells = row.select("td")
            if (cells.isEmpty()) return@forEach
            val nameEl = row.selectFirst("a") ?: cells.firstOrNull()
            val name = nameEl?.text()?.trim() ?: return@forEach
            if (name.isBlank()) return@forEach
            val url = row.selectFirst("a")?.absUrl("href") ?: ""
            val date = cells.lastOrNull { it.text().matches(Regex(".*\\d{2}/\\d{2}/\\d{4}.*|.*\\d{4}-\\d{2}-\\d{2}.*")) }
                ?.text()?.trim() ?: ""
            docs.add(PlanningDocumentResult(name, date, url))
        }
        return docs
    }

    private fun extractKeyVal(href: String): String? {
        val regex = Regex("[?&]keyVal=([^&]+)")
        return regex.find(href)?.groupValues?.get(1)
    }

    private fun extractField(text: String, label: String): String? {
        val idx = text.indexOf(label, ignoreCase = true)
        if (idx == -1) return null
        return text.substring(idx + label.length).split("\n", ",", "|").firstOrNull()?.trim()
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; DenmarkArmsScraper/1.0)")
            .build()
        return client.newCall(request).execute().use { resp ->
            resp.body?.string() ?: ""
        }
    }
}
