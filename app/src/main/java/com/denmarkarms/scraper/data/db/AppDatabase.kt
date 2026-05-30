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
    version = 5,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE planning_documents_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        application_key_val TEXT NOT NULL,
                        name TEXT NOT NULL,
                        date TEXT NOT NULL,
                        url TEXT NOT NULL,
                        first_seen INTEGER NOT NULL,
                        download_status TEXT NOT NULL DEFAULT 'queued'
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO planning_documents_new (id, application_key_val, name, date, url, first_seen, download_status)
                    SELECT id, application_key_val, name, date, url, first_seen, 'queued'
                    FROM planning_documents
                """.trimIndent())
                database.execSQL("DROP TABLE planning_documents")
                database.execSQL("ALTER TABLE planning_documents_new RENAME TO planning_documents")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE planning_documents ADD COLUMN download_error TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE change_log ADD COLUMN document_id INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "denmark_arms.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { INSTANCE = it }
        }
    }
}
