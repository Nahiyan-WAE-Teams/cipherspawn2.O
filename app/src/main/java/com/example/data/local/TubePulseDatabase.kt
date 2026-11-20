package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedChannelEntity::class,
        CachedVideoEntity::class,
        CachedSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TubePulseDatabase : RoomDatabase() {
    abstract fun tubePulseDao(): TubePulseDao

    companion object {
        @Volatile
        private var INSTANCE: TubePulseDatabase? = null

        fun getInstance(context: Context): TubePulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TubePulseDatabase::class.java,
                    "tubepulse_cache.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
