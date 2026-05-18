package com.denmarkarms.scraper.notification

import com.denmarkarms.scraper.domain.ChangeLogEntry
import com.denmarkarms.scraper.domain.ChangeType

object NotificationFormatter {

    private const val NEWHAM_BASE = "https://pa.newham.gov.uk/online-applications/applicationDetails.do"
    private const val CH_BASE = "https://find-and-update.company-information.service.gov.uk"
    private const val PRIMARY = "#1565C0"

    // ── Subjects ─────────────────────────────────────────────────────────────

    fun planningSubject(changes: List<ChangeLogEntry>): String {
        val appCount = changes.map { it.entityId }.distinct().size
        return "Planning: $appCount application${if (appCount != 1) "s" else ""} updated"
    }

    fun companiesHouseSubject(changes: List<ChangeLogEntry>): String {
        val persons = changes.count { it.type == ChangeType.NEW_PERSON }
        val appts = changes.count { it.type == ChangeType.NEW_APPOINTMENT }
        return buildString {
            if (persons > 0) append("$persons new person${if (persons != 1) "s" else ""}")
            if (persons > 0 && appts > 0) append(", ")
            if (appts > 0) append("$appts new appointment${if (appts != 1) "s" else ""}")
        }
    }

    // ── Plain text (WhatsApp / push) ──────────────────────────────────────────

    fun planningBody(changes: List<ChangeLogEntry>): String {
        val byApp = changes.groupBy { it.entityId }
        return buildString {
            byApp.forEach { (keyVal, appChanges) ->
                val ref = appChanges.mapNotNull { planningRef(it) }.firstOrNull() ?: keyVal
                val parts = mutableListOf<String>()
                if (appChanges.any { it.type == ChangeType.NEW_APPLICATION }) parts.add("new application")
                appChanges.firstOrNull { it.type == ChangeType.STATUS_CHANGE }?.let {
                    parts.add("status → ${it.description.substringAfter("→ ").trim().take(40)}")
                }
                val docs = appChanges.count { it.type == ChangeType.NEW_DOCUMENT }
                if (docs > 0) parts.add("$docs new document${if (docs != 1) "s" else ""}")
                appendLine("$ref: ${parts.joinToString(", ")}")
                val tab = if (appChanges.none { it.type == ChangeType.NEW_APPLICATION || it.type == ChangeType.STATUS_CHANGE }) "documents" else "summary"
                appendLine("$NEWHAM_BASE?activeTab=$tab&keyVal=$keyVal")
                appendLine()
            }
        }.trim()
    }

    fun companiesHouseBody(changes: List<ChangeLogEntry>): String {
        val newPersons = changes.filter { it.type == ChangeType.NEW_PERSON }
        val appointments = changes.filter { it.type == ChangeType.NEW_APPOINTMENT }
        val officerIdByName = newPersons.associate { personName(it) to it.entityId }
        val apptsByPerson = appointments.groupBy { apptPersonName(it.description) }
        return buildString {
            newPersons.forEach { c ->
                appendLine("${personName(c)}: now being tracked")
                appendLine("$CH_BASE/officers/${c.entityId}/appointments")
                appendLine()
            }
            apptsByPerson.forEach { (person, appts) ->
                val summary = appts.take(2).joinToString(", ") { apptRoleSummary(it.description) }
                val extra = if (appts.size > 2) " (+${appts.size - 2} more)" else ""
                appendLine("$person: ${appts.size} new appointment${if (appts.size != 1) "s" else ""}")
                appendLine("$summary$extra")
                val officerId = officerIdByName[person]
                appendLine(if (officerId != null) "$CH_BASE/officers/$officerId/appointments" else "$CH_BASE/company/${appts.first().entityId}")
                appendLine()
            }
        }.trim()
    }

    // ── HTML email ────────────────────────────────────────────────────────────

    fun planningHtmlBody(changes: List<ChangeLogEntry>): String {
        val newApps = changes.filter { it.type == ChangeType.NEW_APPLICATION }
        val statusChanges = changes.filter { it.type == ChangeType.STATUS_CHANGE }
        val docsByApp = changes.filter { it.type == ChangeType.NEW_DOCUMENT }.groupBy { it.entityId }

        return buildString {
            appendLine(htmlHeader("Planning Applications Update"))
            if (newApps.isNotEmpty()) {
                appendLine(sectionHeading("New Applications (${newApps.size})"))
                appendLine(tableOpen("Reference", "Description", "Address", ""))
                newApps.forEach { c ->
                    val ref = planningRef(c) ?: c.entityId
                    val desc = c.description.substringAfter(": ").substringBefore(" at ").trim().take(100)
                    val address = c.description.substringAfter(" at ", "").trim().take(80)
                    val url = "$NEWHAM_BASE?activeTab=summary&keyVal=${c.entityId}"
                    appendLine(tr(td(ref, bold = true), td(desc), td(address), tdLink(url, "View →")))
                }
                appendLine("</table>")
            }
            if (statusChanges.isNotEmpty()) {
                appendLine(sectionHeading("Status Changes (${statusChanges.size})"))
                appendLine(tableOpen("Reference", "Status Change", ""))
                statusChanges.forEach { c ->
                    val ref = planningRef(c) ?: c.entityId
                    val change = c.description.substringAfter(": ").trim().take(80)
                    val url = "$NEWHAM_BASE?activeTab=summary&keyVal=${c.entityId}"
                    appendLine(tr(td(ref, bold = true), td(change), tdLink(url, "View →")))
                }
                appendLine("</table>")
            }
            if (docsByApp.isNotEmpty()) {
                val total = docsByApp.values.sumOf { it.size }
                appendLine(sectionHeading("New Documents ($total)"))
                appendLine(tableOpen("Application", "New Documents", ""))
                docsByApp.forEach { (keyVal, docs) ->
                    val ref = docs.mapNotNull { planningRef(it) }.firstOrNull() ?: keyVal
                    val url = "$NEWHAM_BASE?activeTab=documents&keyVal=$keyVal"
                    appendLine(tr(td(ref, bold = true), td("${docs.size} new document${if (docs.size != 1) "s" else ""}"), tdLink(url, "View →")))
                }
                appendLine("</table>")
            }
            appendLine(htmlFooter())
        }
    }

    fun companiesHouseHtmlBody(changes: List<ChangeLogEntry>): String {
        val newPersons = changes.filter { it.type == ChangeType.NEW_PERSON }
        val appointments = changes.filter { it.type == ChangeType.NEW_APPOINTMENT }
        val officerIdByName = newPersons.associate { personName(it) to it.entityId }
        val apptsByPerson = appointments.groupBy { apptPersonName(it.description) }

        return buildString {
            appendLine(htmlHeader("Companies House Update"))
            if (newPersons.isNotEmpty()) {
                appendLine(sectionHeading("New People Tracked (${newPersons.size})"))
                appendLine(tableOpen("Name", "Monitoring For", ""))
                newPersons.forEach { c ->
                    val name = personName(c)
                    val monitoring = c.description.substringAfter("(monitoring: ").substringBefore(")")
                    val url = "$CH_BASE/officers/${c.entityId}/appointments"
                    appendLine(tr(td(name, bold = true), td(monitoring), tdLink(url, "View appointments →")))
                }
                appendLine("</table>")
            }
            if (appointments.isNotEmpty()) {
                appendLine(sectionHeading("New Appointments (${appointments.size})"))
                appendLine(tableOpen("Person", "Role", "Company", "Appointed", ""))
                apptsByPerson.forEach { (person, appts) ->
                    appts.forEach { c ->
                        val role = c.description.substringAfter(" as ").substringBefore(" at ").trim()
                        val company = c.description.substringAfter(" at ").substringBefore(" (").trim()
                        val appointed = c.description.substringAfter(" from ").trim()
                        val officerId = officerIdByName[person]
                        val url = if (officerId != null) "$CH_BASE/officers/$officerId/appointments"
                                  else "$CH_BASE/company/${c.entityId}"
                        appendLine(tr(td(person, bold = true), td(role), td(company), td(appointed), tdLink(url, "View →")))
                    }
                }
                appendLine("</table>")
            }
            appendLine(htmlFooter())
        }
    }

    // ── HTML helpers ──────────────────────────────────────────────────────────

    private fun htmlHeader(title: String) = """
        <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;color:#333;max-width:650px;margin:0 auto;padding:16px;">
        <h2 style="color:$PRIMARY;border-bottom:2px solid $PRIMARY;padding-bottom:8px;">$title</h2>
    """.trimIndent()

    private fun htmlFooter() = """
        <p style="color:#888;font-size:12px;margin-top:24px;">Denmark Arms Scraper</p>
        </body></html>
    """.trimIndent()

    private fun sectionHeading(text: String) =
        """<h3 style="color:#333;margin-top:20px;margin-bottom:8px;">$text</h3>"""

    private fun tableOpen(vararg headers: String): String {
        val ths = headers.joinToString("") { """<th style="text-align:left;padding:8px;background:$PRIMARY;color:#fff;">$it</th>""" }
        return """<table width="100%" cellpadding="0" cellspacing="0" style="border-collapse:collapse;border:1px solid #ddd;margin-bottom:16px;"><thead><tr>$ths</tr></thead><tbody>"""
    }

    private fun tr(vararg cells: String) =
        """<tr style="border-bottom:1px solid #eee;">${cells.joinToString("")}</tr>"""

    private fun td(text: String, bold: Boolean = false): String {
        val style = "padding:8px;" + if (bold) "font-weight:bold;" else ""
        return """<td style="$style">$text</td>"""
    }

    private fun tdLink(url: String, label: String) =
        """<td style="padding:8px;white-space:nowrap;"><a href="$url" style="color:$PRIMARY;text-decoration:none;">$label</a></td>"""

    // ── Parsers ───────────────────────────────────────────────────────────────

    private fun planningRef(change: ChangeLogEntry): String? = when (change.type) {
        ChangeType.NEW_APPLICATION -> change.description.substringAfter("New planning application ").substringBefore(":").trim().takeIf { it.isNotBlank() }
        ChangeType.STATUS_CHANGE -> change.description.substringAfter("Status changed for ").substringBefore(":").trim().takeIf { it.isNotBlank() }
        ChangeType.NEW_DOCUMENT -> change.description.substringAfter("New document for ").substringBefore(" (").trim().takeIf { it.isNotBlank() && it.contains("/") }
        else -> null
    }

    private fun personName(change: ChangeLogEntry): String =
        change.description.substringAfter("New person found: ").substringBefore(" (monitoring")

    private fun apptPersonName(description: String): String =
        description.removePrefix("New appointment: ").substringBefore(" as ").trim()

    private fun apptRoleSummary(description: String): String {
        val role = description.substringAfter(" as ").substringBefore(" at ").trim()
        val company = description.substringAfter(" at ").substringBefore(" (").trim()
        return "$role at $company"
    }
}
