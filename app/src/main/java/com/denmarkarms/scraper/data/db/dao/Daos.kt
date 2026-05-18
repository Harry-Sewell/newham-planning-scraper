package com.denmarkarms.scraper.data.db.dao

import androidx.room.*
import com.denmarkarms.scraper.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanningApplicationDao {
    @Query("SELECT * FROM planning_applications ORDER BY first_seen DESC")
    fun getAll(): Flow<List<PlanningApplicationEntity>>

    @Query("SELECT * FROM planning_applications ORDER BY first_seen DESC")
    suspend fun getAllOnce(): List<PlanningApplicationEntity>

    @Query("SELECT * FROM planning_applications WHERE key_val = :keyVal LIMIT 1")
    suspend fun findByKeyVal(keyVal: String): PlanningApplicationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: PlanningApplicationEntity): Long

    @Update
    suspend fun update(app: PlanningApplicationEntity)

    @Delete
    suspend fun delete(app: PlanningApplicationEntity)

    @Query("DELETE FROM planning_applications")
    suspend fun deleteAll()
}

@Dao
interface PlanningDocumentDao {
    @Query("SELECT * FROM planning_documents WHERE application_key_val = :keyVal ORDER BY first_seen DESC")
    fun getForApplication(keyVal: String): Flow<List<PlanningDocumentEntity>>

    @Query("SELECT * FROM planning_documents WHERE application_key_val = :keyVal ORDER BY first_seen DESC")
    suspend fun getForApplicationOnce(keyVal: String): List<PlanningDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(doc: PlanningDocumentEntity): Long

    @Delete
    suspend fun delete(doc: PlanningDocumentEntity)

    @Query("DELETE FROM planning_documents WHERE application_key_val = :keyVal")
    suspend fun deleteForApplication(keyVal: String)

    @Query("DELETE FROM planning_documents")
    suspend fun deleteAll()
}

@Dao
interface MonitoredAddressDao {
    @Query("SELECT * FROM monitored_addresses ORDER BY id ASC")
    fun getAll(): Flow<List<MonitoredAddressEntity>>

    @Query("SELECT * FROM monitored_addresses WHERE active = 1 ORDER BY id ASC")
    suspend fun getActiveOnce(): List<MonitoredAddressEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(address: MonitoredAddressEntity): Long

    @Update
    suspend fun update(address: MonitoredAddressEntity)

    @Delete
    suspend fun delete(address: MonitoredAddressEntity)
}

@Dao
interface MonitoredPersonDao {
    @Query("SELECT * FROM monitored_persons ORDER BY id ASC")
    fun getAll(): Flow<List<MonitoredPersonEntity>>

    @Query("SELECT * FROM monitored_persons WHERE active = 1 ORDER BY id ASC")
    suspend fun getActiveOnce(): List<MonitoredPersonEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: MonitoredPersonEntity): Long

    @Update
    suspend fun update(person: MonitoredPersonEntity)

    @Delete
    suspend fun delete(person: MonitoredPersonEntity)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY first_seen DESC")
    fun getAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE monitored_name = :monitoredName")
    suspend fun getByMonitoredName(monitoredName: String): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE officer_id = :officerId LIMIT 1")
    suspend fun findByOfficerId(officerId: String): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY first_seen DESC")
    fun getAll(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE person_id = :personId ORDER BY appointed_on DESC")
    suspend fun getForPersonOnce(personId: Long): List<AppointmentEntity>

    @Query("SELECT * FROM appointments WHERE person_id = :personId AND company_number = :companyNumber AND role = :role LIMIT 1")
    suspend fun findExisting(personId: Long, companyNumber: String, role: String): AppointmentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(appointment: AppointmentEntity): Long

    @Update
    suspend fun update(appointment: AppointmentEntity)

    @Delete
    suspend fun delete(appointment: AppointmentEntity)

    @Query("DELETE FROM appointments WHERE person_id = :personId")
    suspend fun deleteForPerson(personId: Long)

    @Query("DELETE FROM appointments")
    suspend fun deleteAll()
}

@Dao
interface RecipientDao {
    @Query("SELECT * FROM recipients ORDER BY id ASC")
    fun getAll(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE active = 1 ORDER BY id ASC")
    suspend fun getActiveOnce(): List<RecipientEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(recipient: RecipientEntity): Long

    @Update
    suspend fun update(recipient: RecipientEntity)

    @Delete
    suspend fun delete(recipient: RecipientEntity)
}

@Dao
interface ChangeLogDao {
    @Query("SELECT * FROM change_log ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<ChangeLogEntity>>

    @Query("SELECT * FROM change_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentOnce(limit: Int = 50): List<ChangeLogEntity>

    @Insert
    suspend fun insert(entry: ChangeLogEntity): Long

    @Query("DELETE FROM change_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Delete
    suspend fun delete(entry: ChangeLogEntity)

    @Query("DELETE FROM change_log")
    suspend fun deleteAll()
}
