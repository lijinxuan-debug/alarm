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

        repositoryScope.launch {
            // 关闭闹钟通知（服务已经在receiver和service里面关闭）
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(alarm.id)
            // 修改数据库
            alarmDao?.updateEnabledStatus(alarm.id,false,nextTriggerTime)
            Log.e("关闭闹钟id为", alarm.id.toString())
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

    /**
     * 查询该时间戳下已开启的所有闹钟，并对于同一时间戳的闹铃进行修改
     */
    fun updateAllAlarmsByNextTriggerTime(alarm: AlarmEntity,context: Context,isSnooze: Boolean,oldTriggerTime: Long?= null) {
        repositoryScope.launch {
            var alarmEntities: List<AlarmEntity>?
            // 获取当前时间戳下所有已经开启的闹铃
            if (isSnooze) {
                // 如果是小睡模式，那么将除了该小睡模式的闹钟全部关闭
                alarmEntities = alarmDao?.getAlarmsByNextTriggerTime(oldTriggerTime ?: alarm.nextTriggerTime)?.filter { it.id != alarm.id }
            } else {
                // 直接将该时间戳下的全部闹钟关闭即可
                alarmEntities = alarmDao?.getAlarmsByNextTriggerTime(oldTriggerTime ?: alarm.nextTriggerTime)
            }

            // 获取这些闹铃的下一次时间戳，进行比对是否应该关闭闹钟
            Log.e("全部数据",alarmDao?.getAllEnabledAlarms().toString())
            alarmEntities?.forEach {
                if (it.repeatText == "不重复") {
                    // 不重复的话直接取消即可
                    dismissAlarm(it, context)
                } else {
                    // 那就说明为重复，需要创建alarmManager设置下一次的闹钟
                    setAlarm(context, it)
                }
            }
        }
    }



    /**
     * 关闭闹铃后还要设置下一次的alarmManager
     */
    private fun setAlarm(context: Context, alarm: AlarmEntity) {
        val triggerTime = AlarmManagerUtils.calculateNextTriggerTime(alarm)
        // 数据库也要更新它的下一次响铃时间
        updateAlarmNextTriggerTime(alarm, triggerTime)
        // 设置下一次的闹铃
        AlarmManagerUtils.setAlarm(context, alarm, triggerTime)
    }
}