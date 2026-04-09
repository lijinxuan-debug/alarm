package com.example.alarm_jinxuan.utils

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.alarm_jinxuan.model.AlarmEntity
import com.example.alarm_jinxuan.receiver.AlarmReceiver
import com.example.alarm_jinxuan.repository.AlarmRepository
import com.example.alarm_jinxuan.service.AlarmService
import java.util.Calendar

object AlarmManagerUtils {
    const val ALARM_CHANNEL_ID = "ALARM_channelId"

    /**
     * 设置闹钟管理
     */
    @SuppressLint("ScheduleExactAlarm")
    fun setAlarm(context: Context, alarm: AlarmEntity, timeInMills: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 创建一个 Intent，传递对象数据所使用
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_OBJ", alarm)
        }

        // PendingIntent 是交给系统托管的 Intent
        // 使用 FLAG_UPDATE_CURRENT 确保可以更新现有的 PendingIntent
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        // 设置精准闹钟 (setExactAndAllowWhileIdle 保证省电模式也准时)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMills,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMills, pendingIntent)
        }
    }

    /**
     * 主要是删除闹钟使用
     */
    fun cancelAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. 创建一个目的地完全相同的 Intent
        val intent = Intent(context, AlarmReceiver::class.java)

        // 2. 这里的 requestCode 必须和你当初 setAlarm 时传入的 alarmId 一模一样！
        // 标志位使用 FLAG_NO_CREATE：如果这个闹钟不存在，就别创建新的，直接返回 null
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 如果找到了这个待执行的闹钟，就取消它
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            // 记得还要把这个 PendingIntent 彻底废弃
            pendingIntent.cancel()
        }
    }

    /**
     * 闹钟下次的响铃时间
     */
    fun calculateNextTriggerTime(alarm: AlarmEntity): Long {
        return calculateNextTriggerTimeByTime(alarm.hour24,alarm.minute,alarm.repeatData)
    }

    /**
     * 主要是为了save()初始化直接调用的
     */
    fun calculateNextTriggerTimeByTime(hour24: Int,minute: Int,repeatData: String): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val repeatDays = repeatData.split(",").map { it == "1" }
        val isRepeat = repeatDays.contains(true)

        if (!isRepeat) {
            // 不重复的闹钟
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            // 重复闹钟
            for (i in 0..7) {
                // 获取 target 当前是周几（周日1，周六7）
                val dayOfWeek = target.get(Calendar.DAY_OF_WEEK)

                // 需要映射到我的数组索引
                val index = dayOfWeek - 1

                // 如果这一天是选中的重复日，时间必须在now之后
                if (repeatDays[index] && target.after(now)) {
                    break
                }

                // 没找到就加一天
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return target.timeInMillis
    }

    /**
     * 稍后提醒模式
     */
     fun snoozeAlarm(context: Context, alarm: AlarmEntity) {
        // 先停止振动和铃声
        MediaUtils.stop(context)
        VibrationUtils.stop(context)
        // 获取小睡模式前的时间戳
        val oldTriggerTime = alarm.nextTriggerTime

        // 这里的稍后提醒没有任何的次数限制，只要用户愿意可以一直稍后提醒
        // alarm.computeSnoozeCount--

        val triggerTime = getSnoozeTriggerTime(alarm)
        alarm.nextTriggerTime = triggerTime
        // 修改数据库更改下一次响铃的时间（这里修改了响应更换次数，后续关闭闹钟需要修改回来）
        AlarmRepository.updateAlarm(alarm)
        Log.e("当前的闹钟数据",alarm.toString())
        // 只需要再设置一个再响间隔的闹铃即可
        setAlarm(context, alarm, triggerTime)

        // 修改当前闹钟的状态
        AlarmRepository.updateAllAlarmsByNextTriggerTime(alarm,context,true,oldTriggerTime)

        // 设置稍后提醒的闹钟日志
        val dismissPI = AlarmNotificationUtils.getBroadcastIntent(context, alarm, "ACTION_DISMISS", 1000)
        val snoozeBuilder = AlarmNotificationUtils.getSnoozeBuilder(context, alarm, dismissPI, ALARM_CHANNEL_ID)

        // 获取 NotificationManager 并更新通知
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(alarm.id, snoozeBuilder.build())

        // 清除前台响铃通知
        context.stopService(Intent(context, AlarmService::class.java))
    }

    /**
     * 和当前时间（now）计算还有多久
     */
    fun getRemainingTime(triggerTime: Long): Triple<Int, Int, Int> {
        val diff = triggerTime - System.currentTimeMillis()

        // 如果时间已经过了，直接返回全 0
        if (diff <= 0) return Triple(0, 0, 0)

        // 先算出总分钟数
        val totalMinutes = diff / (1000 * 60)

        // 算出总小时数
        val totalHours = totalMinutes / 60

        // 级联取余计算
        val remainMinutes = (totalMinutes % 60).toInt()
        val remainHours = (totalHours % 24).toInt() // 小时对 24 取余，保证不超过 24
        val days = (totalHours / 24).toInt()        // 总小时除以 24 得到天数

        return Triple(days, remainHours, remainMinutes)
    }

    /**
     * 在当前的时间上添加响铃间隔
     */
    fun getSnoozeTriggerTime(alarm: AlarmEntity): Long {
        val calendar = Calendar.getInstance()

        // 在当前时间的基础上，增加指定的分钟数
        calendar.add(Calendar.MINUTE, alarm.snoozeInterval)

        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.timeInMillis
    }

}