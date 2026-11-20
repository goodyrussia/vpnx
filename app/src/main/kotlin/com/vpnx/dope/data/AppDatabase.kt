package com.vpnx.dope.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.vpnx.dope.data.dao.ProfileDao
import com.vpnx.dope.data.entity.ProfileEntity

@Database(entities = [ProfileEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
