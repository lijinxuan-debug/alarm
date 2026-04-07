package com.example.alarm_jinxuan.repository

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.example.alarm_jinxuan.dao.AlarmDao
import com.example.alarm_jinxuan.dao.AppDatabase
import com.example.alarm_jinxuan.model.AlarmEntity
import com.example.alarm_jinxuan.utils.AlarmManagerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// 主要用于在receiver、service里面对数据库进行操作
object AlarmRepository {
    private var alarmDao: AlarmDao? = null
    // 使用SupervisorJob确保一个协程失败不会影响其他协程
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 在 Application 类的 onCreate 里初始化一次即可
    fun init(context: Context) {
        Log.e("AlarmRepository", "开始初始化，当前alarmDao: ${alarmDao != null}")
        if (alarmDao == null) {
            val db = AppDatabase.getDatabase(context)
            alarmDao = db.alarm()
            Log.e("AlarmRepository", "初始化完成，alarmDao: ${alarmDao != null}")
        }
    }

    /**
     * 关闭闹钟时修改闹钟状态（仅在不重复时关闭）
      */
    fun dismissAlarm(alarm: AlarmEntity,context: Context) {
        // 获得下一次闹钟响铃的时间戳
        val nextTriggerTime = AlarmManagerUtils.calculateNextTriggerTime(alarm)

        Log.e("关闭闹钟", "准备写入时间戳: $nextTriggerTime")
        Log.e("AlarmRepository", "alarmDao是否为null: ${alarmDao == null}")

        repositoryScope.launch {
            // 关闭闹钟通知（服务已经在receiver和service里面关闭）
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(alarm.id)
            // 修改数据库
            alarmDao?.updateEnabledStatus(alarm.id,false,nextTriggerTime)
            Log.e("关闭闹钟", "数据库更新完成")
            // 同时不要忘记去修改闹钟状态
            AlarmManagerUtils.cancelAlarm(context,alarm.id)
        }
    }

    /**
     * 只是修改闹钟的时间戳（不改变闹钟状态，主要是闹钟是重复的）
     */
    fun updateAlarmNextTriggerTime(alarm: AlarmEntity,nextTriggerTime: Long) {
        repositoryScope.launch {
            // 在调用这个方法的地方打印
            Log.e("修改闹钟时间戳", "准备写入时间戳: $nextTriggerTime")
            alarmDao?.updateEnabledStatus(alarm.id,true,nextTriggerTime)
        }
    }

    /**
     * 修改闹钟状态，直接覆盖
     */
    fun updateAlarm(alarm: AlarmEntity) {
        repositoryScope.launch {
            alarmDao?.insertAlarm(alarm)
        }
    }

    /**
     * 查询所有已经开启的闹铃（开机自启动所需要）
     */
    suspend fun getAllAlarms(): List<AlarmEntity>? {
        return alarmDao?.getAllEnabledAlarms()
    }
}