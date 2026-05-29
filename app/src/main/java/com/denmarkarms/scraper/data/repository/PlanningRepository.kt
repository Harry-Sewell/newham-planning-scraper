package com.denmarkarms.scraper.data.repository

import com.denmarkarms.scraper.data.db.AppDatabase
import com.denmarkarms.scraper.data.db.entity.*
import com.denmarkarms.scraper.data.network.NewhamPlanningService
import com.denmarkarms.scraper.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlanningRepository(
    private val db: AppDatabase,
    private val service: NewhamPlanningService
) {
    val applications: Flow<List<PlanningApplication>> =
        db.planningApplicationDao().getAll().map { list -> list.map { it.toDomain() } }

    val monitoredAddresses: Flow<List<MonitoredAddress>> =
        db.monitoredAddressDao().getAll().map { list -> list.map { it.toDomain() } }

    suspend fun addMonitoredAddress(address: String) {
        db.monitoredAddressDao().insert(MonitoredAddressEntity(address = address.trim()))
    }

    suspend fun removeMonitoredAddress(address: MonitoredAddress) {
        db.monitoredAddressDao().delete(MonitoredAddressEntity(address.id, address.address, address.active))
    }

    suspend fun toggleMonitoredAddress(address: MonitoredAddress) {
        db.monitoredAddressDao().update(MonitoredAddressEntity(address.id, address.address, !address.active))
    }

    suspend fun checkAndUpdate(): List<ChangeLogEntry> {
        val changes = mutableListOf<ChangeLogEntry>()
        val addresses = db.monitoredAddressDao().getActiveOnce()
        val now = System.currentTimeMillis()

        for (addr in addresses) {
            val results = service.searchApplications(addr.address)
            for (result in results) {
                val existing = db.planningApplicationDao().findByKeyVal(result.keyVal)
                if (existing == null) {
                    db.planningApplicationDao().insert(
                        PlanningApplicationEntity(
                            keyVal = result.keyVal,
                            searchAddress = addr.address,
                            reference = result.reference,
                            description = result.description,
                            address = result.address,
                            status = result.status,
                            receivedDate = result.receivedDate,
                            lastChecked = now,
                            firstSeen = now
                        )
                    )
                    val entry = ChangeLogEntry(
                        type = ChangeType.NEW_APPLICATION,
                        description = "New planning application ${result.reference}: ${result.description.take(100)} at ${result.address}",
                        entityId = result.keyVal,
                        timestamp = now
                    )
                    changes.add(entry)
                    db.changeLogDao().insert(entry.toEntity())
                    changes += checkDocuments(result.keyVal, now)
                } else {
                    // Always refresh reference/description so stale/incorrect data self-heals
                    val freshRef = result.reference.ifBlank { existing.reference }
                    val freshDesc = result.description.ifBlank { existing.description }
                    if (existing.status != result.status && result.status.isNotBlank()) {
                        db.planningApplicationDao().update(
                            existing.copy(status = result.status, reference = freshRef, description = freshDesc, lastChecked = now)
                        )
                        val entry = ChangeLogEntry(
                            type = ChangeType.STATUS_CHANGE,
                            description = "Status changed for $freshRef: ${existing.status} → ${result.status}",
                            entityId = result.keyVal,
                            timestamp = now
                        )
                        changes.add(entry)
                        db.changeLogDao().insert(entry.toEntity())
                    } else {
                        db.planningApplicationDao().update(existing.copy(reference = freshRef, description = freshDesc, lastChecked = now))
                    }
                    changes += checkDocuments(result.keyVal, now)
                }
            }
        }
        return changes
    }

    private suspend fun checkDocuments(keyVal: String, now: Long): List<ChangeLogEntry> {
        val changes = mutableListOf<ChangeLogEntry>()
        val freshDocs = service.getDocuments(keyVal)
        val knownDocs = db.planningDocumentDao().getForApplicationOnce(keyVal)
        val knownUrls = knownDocs.map { it.url }.filter { it.isNotBlank() }.toSet()
        val knownNames = knownDocs.map { it.name }.toSet()
        val appRef = db.planningApplicationDao().findByKeyVal(keyVal)?.reference?.takeIf { it.isNotBlank() } ?: keyVal

        for (doc in freshDocs) {
            val alreadyKnown = if (doc.url.isNotBlank()) doc.url in knownUrls else doc.name in knownNames
            if (!alreadyKnown) {
                db.planningDocumentDao().insert(
                    PlanningDocumentEntity(
                        applicationKeyVal = keyVal,
                        name = doc.name,
                        date = doc.date,
                        url = doc.url,
                        firstSeen = now,
                        downloadStatus = if (doc.url.isNotBlank()) DownloadStatus.QUEUED else DownloadStatus.DOWNLOADED
                    )
                )
                val entry = ChangeLogEntry(
                    type = ChangeType.NEW_DOCUMENT,
                    description = "New document for $appRef ($keyVal): ${doc.name} (${doc.date})",
                    entityId = keyVal,
                    timestamp = now
                )
                changes.add(entry)
                db.changeLogDao().insert(entry.toEntity())
            }
        }
        return changes
    }

    fun getDocumentsFor(keyVal: String): Flow<List<PlanningDocument>> =
        db.planningDocumentDao().getForApplication(keyVal).map { list -> list.map { it.toDomain() } }
}

private fun PlanningApplicationEntity.toDomain() = PlanningApplication(
    id, keyVal, searchAddress, reference, description, address, status, receivedDate, lastChecked, firstSeen
)

private fun MonitoredAddressEntity.toDomain() = MonitoredAddress(id, address, active)

private fun PlanningDocumentEntity.toDomain() = PlanningDocument(id, applicationKeyVal, name, date, url, firstSeen)

private fun ChangeLogEntry.toEntity() = com.denmarkarms.scraper.data.db.entity.ChangeLogEntity(
    type = type, description = description, entityId = entityId, timestamp = timestamp
)
