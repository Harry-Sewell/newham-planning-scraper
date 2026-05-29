package com.denmarkarms.scraper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.denmarkarms.scraper.data.db.dao.*
import com.denmarkarms.scraper.data.db.entity.*

@Database(
    entities = [
        PlanningApplicationEntity::class,
        PlanningDocumentEntity::class,
        MonitoredAddressEntity::class,
        MonitoredPersonEntity::class,
        PersonEntity::class,
        AppointmentEntity::class,
        RecipientEntity::class,
        ChangeLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planningApplicationDao(): PlanningApplicationDao
    abstract fun planningDocumentDao(): PlanningDocumentDao
    abstract fun monitoredAddressDao(): MonitoredAddressDao
    abstract fun monitoredPersonDao(): MonitoredPersonDao
    abstract fun personDao(): PersonDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun recipientDao(): RecipientDao
    abstract fun changeLogDao(): ChangeLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE planning_documents ADD COLUMN download_pending INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "denmark_arms.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { INSTANCE = it }
        }
    }
}
