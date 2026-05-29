package com.denmarkarms.scraper.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planning_applications")
data class PlanningApplicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "key_val") val keyVal: String,
    @ColumnInfo(name = "search_address") val searchAddress: String,
    @ColumnInfo(name = "reference") val reference: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "received_date") val receivedDate: String,
    @ColumnInfo(name = "last_checked") val lastChecked: Long,
    @ColumnInfo(name = "first_seen") val firstSeen: Long
)

@Entity(tableName = "planning_documents")
data class PlanningDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "application_key_val") val applicationKeyVal: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "first_seen") val firstSeen: Long,
    @ColumnInfo(name = "download_pending") val downloadPending: Boolean = false
)

@Entity(tableName = "monitored_addresses")
data class MonitoredAddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "active") val active: Boolean = true
)

@Entity(tableName = "monitored_persons")
data class MonitoredPersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "active") val active: Boolean = true
)

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "monitored_name") val monitoredName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "officer_id") val officerId: String = "",
    @ColumnInfo(name = "profile_url") val profileUrl: String = "",
    @ColumnInfo(name = "first_seen") val firstSeen: Long
)

@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "person_id") val personId: Long,
    @ColumnInfo(name = "person_display_name") val personDisplayName: String,
    @ColumnInfo(name = "company_number") val companyNumber: String,
    @ColumnInfo(name = "company_name") val companyName: String,
    @ColumnInfo(name = "company_status") val companyStatus: String = "",
    @ColumnInfo(name = "nature_of_business") val natureOfBusiness: String = "",
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "appointed_on") val appointedOn: String,
    @ColumnInfo(name = "resigned_on") val resignedOn: String = "",
    @ColumnInfo(name = "first_seen") val firstSeen: Long
)

@Entity(tableName = "recipients")
data class RecipientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "active") val active: Boolean = true
)

@Entity(tableName = "change_log")
data class ChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "entity_id") val entityId: String = "",
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
