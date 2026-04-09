package com.example.alarm_jinxuan.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.alarm_jinxuan.model.AlarmEntity
import com.example.alarm_jinxuan.repository.AlarmRepository
import com.example.alarm_jinxuan.service.AlarmService
import com.example.alarm_jinxuan.utils.AlarmManagerUtils

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 先获取对应的action
        val action = intent.action
        // 获取 alarmManager 传递的数据
        val alarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("ALARM_OBJ", AlarmEntity::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("ALARM_OBJ")
        }

        // 对action进行判断执行对应的相关逻辑
        when (action) {
            "ACTION_DISMISS" -> {
                // 关闭闹钟停止服务
                val stopIntent = Intent(context, AlarmService::class.java)
                context.stopService(stopIntent)
                // 这里关闭闹钟后，为了防止多个闹钟的并发问题，需要根据响铃日期计算他们的时间戳
                // 根据当前闹钟获取所有闹钟
                if (alarm != null) {
                    AlarmRepository.updateAllAlarmsByNextTriggerTime(alarm,context,false)
                }
                // 无论如何都要关闭闹钟通知（主要是为了让那些已经存在重复的闹铃也可以正常的关闭）
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                alarm?.let {
                    nm.cancel(it.id)
                }
            }

            "ACTION_SNOOZE" -> {
                // 停止当前闹钟服务
                context.stopService(Intent(context, AlarmService::class.java))
                // 这里调用稍后提醒模式
                if (alarm != null) {
                    AlarmManagerUtils.snoozeAlarm(context, alarm)
                }
            }

            else -> {
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra("ALARM_OBJ", alarm)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

}