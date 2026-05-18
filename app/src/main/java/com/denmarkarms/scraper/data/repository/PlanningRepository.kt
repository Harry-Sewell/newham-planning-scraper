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
                } else {
                    if (existing.status != result.status && result.status.isNotBlank()) {
                        db.planningApplicationDao().update(
                            existing.copy(status = result.status, lastChecked = now)
                        )
                        val entry = ChangeLogEntry(
                            type = ChangeType.STATUS_CHANGE,
                            description = "Status changed for ${result.reference}: ${existing.status} → ${result.status}",
                            entityId = result.keyVal,
                            timestamp = now
                        )
                        changes.add(entry)
                        db.changeLogDao().insert(entry.toEntity())
                    } else {
                        db.planningApplicationDao().update(existing.copy(lastChecked = now))
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
        val knownNames = knownDocs.map { it.name }.toSet()

        for (doc in freshDocs) {
            if (doc.name !in knownNames) {
                db.planningDocumentDao().insert(
                    PlanningDocumentEntity(
                        applicationKeyVal = keyVal,
                        name = doc.name,
                        date = doc.date,
                        url = doc.url,
                        firstSeen = now
                    )
                )
                val entry = ChangeLogEntry(
                    type = ChangeType.NEW_DOCUMENT,
                    description = "New document for $keyVal: ${doc.name} (${doc.date})",
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
