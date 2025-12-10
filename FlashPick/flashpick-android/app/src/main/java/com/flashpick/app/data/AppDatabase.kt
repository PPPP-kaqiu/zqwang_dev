package com.flashpick.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flashpick.app.data.dao.VideoRecordDao
import com.flashpick.app.data.dao.DailySummaryDao
import com.flashpick.app.data.model.DailySummary
import com.flashpick.app.data.model.VideoRecord

@Database(entities = [VideoRecord::class, DailySummary::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoRecordDao(): VideoRecordDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flashpick_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

