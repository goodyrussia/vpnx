package com.vpnx.dope.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vpnx.dope.data.dao.ProfileDao
import com.vpnx.dope.data.entity.ProfileEntity

@Database(entities = [ProfileEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN realityPublicKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN realityShortId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN realitySpiderX TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN grpcServiceName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN httpUpgradePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN httpUpgradeHost TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN hy2PinSha256 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN hy2HopInterval TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // No seeded profiles: the user's real server address, UUID
                // and WS path were never recorded, so nothing is invented
                // here. The user adds their profile via import or the
                // Config editor on first launch.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vpnx.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}