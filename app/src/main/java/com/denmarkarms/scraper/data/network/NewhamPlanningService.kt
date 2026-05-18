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

            // Load search page to get session cookie and CSRF token
            val pageHtml = get(searchPageUrl)
            val csrfToken = Jsoup.parse(pageHtml).select("input[name=_csrf]").attr("value")

            // POST to the correct Newham/Idox endpoint with the correct field names
            val formBody = FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("searchType", "Application")
                .add("searchCriteria.caseStatus", "")
                .add("searchCriteria.simpleSearchString", address)
                .add("searchCriteria.simpleSearch", "true")
                .build()

            val postRequest = Request.Builder()
                .url("$baseUrl/simpleSearchResults.do?action=firstPage")
                .post(formBody)
                .header("Referer", searchPageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val html = client.newCall(postRequest).execute().use { it.body?.string() ?: "" }
            parseSearchResults(Jsoup.parse(html, baseUrl))
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

        // Primary: Idox standard list format
        doc.select("li.searchresult").forEach { li ->
            val link = li.selectFirst("a.summaryLink, a[href*=keyVal]") ?: return@forEach
            val keyVal = extractKeyVal(link.attr("abs:href").ifBlank { link.attr("href") }) ?: return@forEach

            // Idox often encodes both reference and description in the link text: "23/01234/FUL - Change of use..."
            val linkText = link.text().trim()
            val reference = linkText.substringBefore(" - ").trim()
                .ifBlank { li.selectFirst("h2, h3")?.text()?.trim() ?: "" }
            val descFromLink = if (linkText.contains(" - ")) linkText.substringAfter(" - ").trim() else ""
            val description = descFromLink.ifBlank {
                li.select(".description, p.description, .descriptionWrap").firstOrNull()?.text()?.trim() ?: ""
            }

            val address = li.select(".address, p.address").firstOrNull()?.text()?.trim() ?: ""
            val metaText = li.select(".metaInfo, p.metaInfo, .searchresult-footer, .metaData").text()
            val status = extractField(metaText, "Status:") ?: extractField(metaText, "Case Status:") ?: ""
            val receivedDate = extractField(metaText, "Received:") ?: extractField(metaText, "Registered:") ?: extractField(metaText, "Valid:") ?: ""
            results.add(PlanningApplicationResult(keyVal, reference, description, address, status.trim(), receivedDate.trim()))
        }

        // Fallback: any anchor whose href contains keyVal (catches alternate Idox layouts)
        if (results.isEmpty()) {
            doc.select("a[href*=keyVal]").forEach { link ->
                val keyVal = extractKeyVal(link.attr("abs:href").ifBlank { link.attr("href") }) ?: return@forEach
                if (results.any { it.keyVal == keyVal }) return@forEach
                val row = link.closest("tr") ?: link.closest("li") ?: link.parent() ?: return@forEach
                val cells = row.select("td")
                val linkText = link.text().trim()
                val reference = linkText.substringBefore(" - ").trim().ifBlank { linkText }
                val description = if (linkText.contains(" - ")) linkText.substringAfter(" - ").trim()
                    else cells.getOrNull(1)?.text()?.trim() ?: row.select(".description").text().trim()
                val address = cells.getOrNull(2)?.text()?.trim() ?: row.select(".address").text().trim()
                val status = cells.getOrNull(3)?.text()?.trim() ?: ""
                val receivedDate = cells.getOrNull(4)?.text()?.trim() ?: ""
                if (reference.isNotBlank() || description.isNotBlank()) {
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
