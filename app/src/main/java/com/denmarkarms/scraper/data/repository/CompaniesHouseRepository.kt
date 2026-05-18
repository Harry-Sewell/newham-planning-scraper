package com.denmarkarms.scraper.data.repository

import android.content.SharedPreferences
import com.denmarkarms.scraper.data.db.AppDatabase
import com.denmarkarms.scraper.data.db.entity.*
import com.denmarkarms.scraper.data.network.CompaniesHouseService
import com.denmarkarms.scraper.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CompaniesHouseRepository(
    private val db: AppDatabase,
    private val service: CompaniesHouseService,
    private val prefs: SharedPreferences
) {
    val monitoredPersons: Flow<List<MonitoredPerson>> =
        db.monitoredPersonDao().getAll().map { list -> list.map { it.toDomain() } }

    val persons: Flow<List<Person>> =
        db.personDao().getAll().map { list -> list.map { it.toDomain() } }

    val appointments: Flow<List<Appointment>> =
        db.appointmentDao().getAll().map { list -> list.map { it.toDomain() } }

    suspend fun addMonitoredPerson(name: String) {
        db.monitoredPersonDao().insert(MonitoredPersonEntity(name = name.trim()))
    }

    suspend fun removeMonitoredPerson(person: MonitoredPerson) {
        db.monitoredPersonDao().delete(MonitoredPersonEntity(person.id, person.name, person.active))
    }

    suspend fun toggleMonitoredPerson(person: MonitoredPerson) {
        db.monitoredPersonDao().update(MonitoredPersonEntity(person.id, person.name, !person.active))
    }

    suspend fun checkAndUpdate(): List<ChangeLogEntry> {
        val changes = mutableListOf<ChangeLogEntry>()
        val apiKey = prefs.getString(PrefsKeys.COMPANIES_HOUSE_API_KEY, "") ?: ""
        if (apiKey.isBlank()) return changes

        val monitoredNames = db.monitoredPersonDao().getActiveOnce()
        val now = System.currentTimeMillis()

        for (monitoredPerson in monitoredNames) {
            val officerResults = service.searchOfficers(monitoredPerson.name, apiKey)
            for (officer in officerResults) {
                val existingPerson = db.personDao().findByOfficerId(officer.officerId)
                val personId: Long
                if (existingPerson == null) {
                    personId = db.personDao().insert(
                        PersonEntity(
                            monitoredName = monitoredPerson.name,
                            displayName = officer.name,
                            officerId = officer.officerId,
                            profileUrl = officer.appointmentsUrl,
                            firstSeen = now
                        )
                    )
                    val entry = ChangeLogEntry(
                        type = ChangeType.NEW_PERSON,
                        description = "New person found: ${officer.name} (monitoring: ${monitoredPerson.name})",
                        entityId = officer.officerId,
                        timestamp = now
                    )
                    changes.add(entry)
                    db.changeLogDao().insert(entry.toEntity())
                } else {
                    personId = existingPerson.id
                }

                changes += checkAppointments(personId, officer.officerId, officer.name, apiKey, now)
            }
        }
        return changes
    }

    private suspend fun checkAppointments(
        personId: Long,
        officerId: String,
        personName: String,
        apiKey: String,
        now: Long
    ): List<ChangeLogEntry> {
        val changes = mutableListOf<ChangeLogEntry>()
        val freshAppointments = service.getAppointments(officerId, apiKey)
        val knownAppointments = db.appointmentDao().getForPersonOnce(personId)

        for (apt in freshAppointments) {
            val exists = db.appointmentDao().findExisting(personId, apt.companyNumber, apt.role)
            if (exists == null) {
                db.appointmentDao().insert(
                    AppointmentEntity(
                        personId = personId,
                        personDisplayName = personName,
                        companyNumber = apt.companyNumber,
                        companyName = apt.companyName,
                        companyStatus = apt.companyStatus,
                        natureOfBusiness = apt.natureOfBusiness,
                        role = apt.role,
                        appointedOn = apt.appointedOn,
                        resignedOn = apt.resignedOn,
                        firstSeen = now
                    )
                )
                val entry = ChangeLogEntry(
                    type = ChangeType.NEW_APPOINTMENT,
                    description = "New appointment: $personName as ${apt.role} at ${apt.companyName} (${apt.companyNumber}) from ${apt.appointedOn}",
                    entityId = apt.companyNumber,
                    timestamp = now
                )
                changes.add(entry)
                db.changeLogDao().insert(entry.toEntity())
            } else if (exists.resignedOn != apt.resignedOn && apt.resignedOn.isNotBlank()) {
                db.appointmentDao().update(exists.copy(resignedOn = apt.resignedOn))
            }
        }
        return changes
    }
}

private fun MonitoredPersonEntity.toDomain() = MonitoredPerson(id, name, active)

private fun PersonEntity.toDomain() = Person(id, monitoredName, displayName, officerId, profileUrl, firstSeen)

private fun AppointmentEntity.toDomain() = Appointment(
    id, personId, personDisplayName, companyNumber, companyName, companyStatus, natureOfBusiness, role, appointedOn, resignedOn, firstSeen
)

private fun ChangeLogEntry.toEntity() = ChangeLogEntity(
    type = type, description = description, entityId = entityId, timestamp = timestamp
)
