package com.lotus.lptablelook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lotus.lptablelook.model.Platform
import com.lotus.lptablelook.model.Settings
import com.lotus.lptablelook.model.Table

@Database(
    entities = [Platform::class, Table::class, Settings::class],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun platformDao(): PlatformDao
    abstract fun tableDao(): TableDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lptablelook_database"
                )
                    // Destructive fallback is allowed ONLY for the pre-release schemas.
                    // From version 10 on, a bump without a Migration fails loudly instead
                    // of silently wiping the table layout, the appearance settings and the
                    // configured server IP - none of which can be restored from the server.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
