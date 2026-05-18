package com.denmarkarms.scraper.notification

import com.denmarkarms.scraper.domain.ChangeLogEntry
import com.denmarkarms.scraper.domain.ChangeType

object NotificationFormatter {

    private const val NEWHAM_BASE = "https://pa.newham.gov.uk/online-applications/applicationDetails.do"
    private const val CH_BASE = "https://find-and-update.company-information.service.gov.uk"

    fun planningSubject(changes: List<ChangeLogEntry>): String {
        val appCount = changes.map { it.entityId }.distinct().size
        return "Planning: $appCount application${if (appCount != 1) "s" else ""} updated"
    }

    fun planningBody(changes: List<ChangeLogEntry>): String {
        val byApp = changes.groupBy { it.entityId }
        return buildString {
            byApp.forEach { (keyVal, appChanges) ->
                val ref = appChanges.mapNotNull { planningRef(it) }.firstOrNull() ?: keyVal

                val parts = mutableListOf<String>()
                if (appChanges.any { it.type == ChangeType.NEW_APPLICATION }) parts.add("new application")
                appChanges.firstOrNull { it.type == ChangeType.STATUS_CHANGE }?.let { sc ->
                    parts.add("status → ${sc.description.substringAfter("→ ").trim().take(40)}")
                }
                val docCount = appChanges.count { it.type == ChangeType.NEW_DOCUMENT }
                if (docCount > 0) parts.add("$docCount new document${if (docCount != 1) "s" else ""}")

                appendLine("$ref: ${parts.joinToString(", ")}")

                val tab = if (appChanges.none { it.type == ChangeType.NEW_APPLICATION || it.type == ChangeType.STATUS_CHANGE }) "documents" else "summary"
                appendLine("$NEWHAM_BASE?activeTab=$tab&keyVal=$keyVal")
                appendLine()
            }
        }.trim()
    }

    fun companiesHouseSubject(changes: List<ChangeLogEntry>): String {
        val persons = changes.filter { it.type == ChangeType.NEW_PERSON }.size
        val appts = changes.filter { it.type == ChangeType.NEW_APPOINTMENT }.size
        return buildString {
            if (persons > 0) append("$persons new person${if (persons != 1) "s" else ""}")
            if (persons > 0 && appts > 0) append(", ")
            if (appts > 0) append("$appts new appointment${if (appts != 1) "s" else ""}")
        }
    }

    fun companiesHouseBody(changes: List<ChangeLogEntry>): String {
        val newPersons = changes.filter { it.type == ChangeType.NEW_PERSON }
        val appointments = changes.filter { it.type == ChangeType.NEW_APPOINTMENT }

        // Build officerId lookup from any NEW_PERSON changes in this batch
        val officerIdByName = newPersons.associate { c ->
            c.description.substringAfter("New person found: ").substringBefore(" (monitoring") to c.entityId
        }

        val apptsByPerson = appointments.groupBy { apptPersonName(it.description) }

        return buildString {
            newPersons.forEach { c ->
                val name = c.description.substringAfter("New person found: ").substringBefore(" (monitoring")
                appendLine("$name: now being tracked")
                appendLine("$CH_BASE/officers/${c.entityId}/appointments")
                appendLine()
            }
            apptsByPerson.forEach { (person, appts) ->
                val rolesSummary = appts.take(2).joinToString(", ") { apptRoleSummary(it.description) }
                val extra = if (appts.size > 2) " (+${appts.size - 2} more)" else ""
                appendLine("$person: ${appts.size} new appointment${if (appts.size != 1) "s" else ""}")
                appendLine("$rolesSummary$extra")
                val officerId = officerIdByName[person]
                val url = if (officerId != null) "$CH_BASE/officers/$officerId/appointments"
                          else "$CH_BASE/company/${appts.first().entityId}"
                appendLine(url)
                appendLine()
            }
        }.trim()
    }

    private fun planningRef(change: ChangeLogEntry): String? = when (change.type) {
        ChangeType.NEW_APPLICATION ->
            change.description.substringAfter("New planning application ").substringBefore(":").trim().takeIf { it.isNotBlank() }
        ChangeType.STATUS_CHANGE ->
            change.description.substringAfter("Status changed for ").substringBefore(":").trim().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun apptPersonName(description: String): String =
        description.removePrefix("New appointment: ").substringBefore(" as ").trim()

    private fun apptRoleSummary(description: String): String {
        val role = description.substringAfter(" as ").substringBefore(" at ").trim()
        val company = description.substringAfter(" at ").substringBefore(" (").trim()
        return "$role at $company"
    }
}
