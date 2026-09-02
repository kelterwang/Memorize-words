package com.morningwords.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.morningwords.data.dao.MorningWordsDao
import com.morningwords.data.entity.*

@Database(
    entities = [WordEntity::class, WordBatchEntity::class, BatchWordEntity::class,
        TestSessionEntity::class, SessionBatchEntity::class, SessionWordEntity::class,
        TestRecordEntity::class, WrongWordEntity::class, WrongRecordEntity::class,
        UndoSnapshotEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MorningWordsDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun create(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "morning_words.db")
                .build().also { instance = it }
        }
    }
}

