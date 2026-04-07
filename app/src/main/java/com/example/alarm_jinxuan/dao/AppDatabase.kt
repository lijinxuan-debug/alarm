package com.example.alarm_jinxuan.dao

import android.content.Context
import android.os.Build
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.alarm_jinxuan.model.AlarmEntity
import com.example.alarm_jinxuan.model.LapRecord
import com.example.alarm_jinxuan.model.StopwatchState
import com.example.alarm_jinxuan.model.WorldClockEntity

@Database(entities = [LapRecord::class, StopwatchState::class, AlarmEntity::class, WorldClockEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lapDao(): LapDao

    abstract fun stopWatch(): StopwatchStateDao

    abstract fun alarm(): AlarmDao

    abstract fun worldClock(): WorldClockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 从版本2迁移到版本3：添加国家字段
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加国家名和国家拼音字段
                database.execSQL("ALTER TABLE WorldClock ADD COLUMN countryName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE WorldClock ADD COLUMN countryPinyin TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            // 获取设备保护存储上下文。
            val deviceContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else {
                context
            }

            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    deviceContext,
                    AppDatabase::class.java,
                    "stopwatch_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration() // 如果迁移失败，重建表（用于开发阶段）
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}