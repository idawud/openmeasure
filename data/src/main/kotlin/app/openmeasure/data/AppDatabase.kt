package app.openmeasure.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MeasurementEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 -> v2: nullable `volumeCubicMeters` for BOX entries (F14). Existing rows keep NULL. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_1_2)
            }
        }
        const val SQL_1_2 = "ALTER TABLE `measurements` ADD COLUMN `volumeCubicMeters` REAL"

        /** Process-wide singleton (no DI library). */
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "openmeasure.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
