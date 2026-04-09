package com.example.alarm_jinxuan.dao

import android.util.Log
import androidx.room.*
import com.example.alarm_jinxuan.model.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    // 插入新闹钟
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Query("UPDATE alarms SET isEnabled = :enabled, nextTriggerTime = :nextTriggerTime, computeSnoozeCount = snoozeCount WHERE id = :alarmId")
    suspend fun updateEnabledStatus(
        alarmId: Int,
        enabled: Boolean,
        nextTriggerTime: Long,
    )

    // 只修改闹钟的开关状态
    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = :alarmId")
    suspend fun updateEnabledStatus(alarmId: Int, enabled: Boolean)

    // 删除闹钟
    @Query("DELETE FROM alarms WHERE id = :alarmId")
    suspend fun deleteAlarmById(alarmId: Int): Int

    // 查询所有闹钟（按时间先后排序）
    @Query("SELECT * FROM alarms ORDER BY hour24 ASC, minute ASC, id ASC")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

    // 只查询已开启的闹钟（以下一次响铃时间为主）
    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY nextTriggerTime ASC")
    fun getEnabledAlarms(): Flow<List<AlarmEntity>>

    // 只查询已开启的闹钟（以下一次响铃时间为主），但需要明确不是flow流
    @Query("SELECT * FROM alarms WHERE isEnabled = 1 ORDER BY nextTriggerTime ASC")
    fun getAllEnabledAlarms(): List<AlarmEntity>

    // 根据 ID 查询单个闹钟（用于编辑页面回显）
    @Query("SELECT * FROM alarms WHERE id = :alarmId")
    suspend fun getAlarmById(alarmId: Int): AlarmEntity?

    // 根据下一次响铃时间戳查询所有已开启的闹钟（用于处理同一时间点的多个闹钟）
    @Query("SELECT * FROM alarms WHERE nextTriggerTime = :nextTriggerTime AND isEnabled = true")
    suspend fun getAlarmsByNextTriggerTime(nextTriggerTime: Long): List<AlarmEntity>
}