package com.example.alarm_jinxuan.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.util.Log
import com.example.alarm_jinxuan.model.AddAlarmClockManager
import com.example.alarm_jinxuan.model.AlarmEntity
import com.example.alarm_jinxuan.repository.AlarmRepository
import com.example.alarm_jinxuan.utils.AlarmManagerUtils
import com.example.alarm_jinxuan.utils.AlarmNotificationUtils
import com.example.alarm_jinxuan.utils.MediaUtils
import com.example.alarm_jinxuan.utils.VibrationUtils
import com.example.alarm_jinxuan.view.ring.RingActivity

class AlarmService : Service() {

    val channelId: String = "ALARM_channelId"

    // 存储当前响铃的闹钟
    private var currentAlarm: AlarmEntity? = null

    // 处理灭屏逻辑
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // 如果当前闹钟实例不为空才会执行
                currentAlarm?.let {
                    handleScreenOffDuringAlarm(it)
                }
            }
        }
    }

    // 主要处理用户未响应逻辑（睡着模式）
    private val autoDismissHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val alarm = msg.obj as AlarmEntity
            autoHandleNoResponse(alarm)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val intentFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, intentFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 获取闹钟数据
        val alarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("ALARM_OBJ", AlarmEntity::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("ALARM_OBJ")
        }

        if (alarm == null) {
            Log.e("service出现问题", "接收广播传服务的是空数据")
            return START_STICKY_COMPATIBILITY
        }
        currentAlarm = alarm

        // 显示对应通知
        showNotification(alarm)
        // 开始响铃振动
        startForegroundResource(alarm)
        // 主动启动 RingActivity 唤醒屏幕
        startRingActivity(alarm)

        handlerSendMeg(alarm)
        Log.e("service执行成功了", alarm.toString())

        return START_STICKY
    }

    /**
     * 使用handler来发送消息
     */
    private fun handlerSendMeg(alarm: AlarmEntity) {
        // 使用meg传递对象
        val message = autoDismissHandler.obtainMessage().apply {
            obj = alarm
        }

        // 开始倒计时，如果在指定的响铃时间内没有关闭闹钟需要自动实现稍后提醒功能
        autoDismissHandler.sendMessageDelayed(message, alarm.ringDuration * 60 * 1000L)
    }

    /**
     * 主动启动 RingActivity 唤醒屏幕
     */
    private fun startRingActivity(alarm: AlarmEntity) {
        // 屏幕亮度编辑器
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            return
        } else {
            try {
                val intent = Intent(this, RingActivity::class.java).apply {
                    putExtra("ALARM_OBJ", alarm)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("AlarmService", "启动 RingActivity 失败: ${e.message}")
            }
        }
    }

    private fun startForegroundResource(alarm: AlarmEntity) {
        // 先开启振动
        val vibrationOption = AddAlarmClockManager.vibrationList[alarm.vibrationId]
        VibrationUtils.vibrate(this, vibrationOption.pattern, 0)
        // 再播放铃声
        val resId =
            this.resources.getIdentifier(alarm.ringtoneFileName, "raw", this.packageName)

        MediaUtils.startRingtonePreview(resId, this)
    }

    private fun showNotification(alarm: AlarmEntity) {
        // 全屏跳转
        val fullScreenPI = AlarmNotificationUtils.getFullScreenIntent(this, alarm)

        // 关闭闹钟功能
        val dismissPI =
            AlarmNotificationUtils.getBroadcastIntent(this, alarm, "ACTION_DISMISS", 1000)

        // 稍后提醒功能
        val snoozePI =
            AlarmNotificationUtils.getBroadcastIntent(this, alarm, "ACTION_SNOOZE", 2000)

        // 构建通知
        val builder = AlarmNotificationUtils.getNotificationBuilder(
            this,
            alarm,
            fullScreenPI,
            dismissPI,
            snoozePI,
            channelId
        )

        // 启动前台服务通知
        startForeground(119, builder.build())
    }

    /**
     * 主要调用重复响铃次数
     */
    private fun autoHandleNoResponse(alarm: AlarmEntity) {
        Log.e(
            "AlarmService",
            "自动处理无响应: ${alarm.label}, computeSnoozeCount = ${alarm.computeSnoozeCount}"
        )

        // 手动清理铃声和振动资源（不要调用 onDestroy()）
        MediaUtils.stop(this)
        VibrationUtils.stop(this)

        if (alarm.computeSnoozeCount > 0) {
            // 还有重复次数，准备稍后提醒
            alarm.computeSnoozeCount--

            // 更新下次响应时间
            alarm.nextTriggerTime = AlarmManagerUtils.getSnoozeTriggerTime(alarm)
            // 更新数据库中的闹钟状态
            AlarmRepository.updateAlarm(alarm)

            // 设置下次响铃时间
            AlarmManagerUtils.setAlarm(this, alarm, alarm.nextTriggerTime)

            // 更新通知为"稍后提醒"状态
            val dismissPI =
                AlarmNotificationUtils.getBroadcastIntent(this, alarm, "ACTION_DISMISS", 1000)
            val snoozeBuilder =
                AlarmNotificationUtils.getSnoozeBuilder(this, alarm, dismissPI, channelId)

            // 获取 NotificationManager 并更新通知
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(119, snoozeBuilder.build())

            Log.d("AlarmService", "已设置稍后提醒: ${alarm.snoozeInterval}分钟后")
        } else {
            // 重复响铃次数耗尽，关闭闹钟（等待下一次的闹钟响（如果重复的话））
            Log.e("AlarmService", "重复次数耗尽，关闭闹钟")
            if (alarm.repeatText == "不重复") {
                AlarmRepository.dismissAlarm(alarm, this)
            } else {
                setAlarm(this, alarm)
            }
            // 停止服务
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 服务关闭时，务必释放资源
        MediaUtils.stop(this)
        VibrationUtils.stop(this)

        // 显式移除前台通知（参数 true 表示移除通知）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // 同时销毁handler任务（目前有问题，如果当前有两个闹钟都在响，那么一下子就都清除了。）
        autoDismissHandler.removeMessages(0)
        // 销毁灭屏广播服务
        unregisterReceiver(screenReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 关闭闹铃后还要设置下一次的alarmManager
     */
    private fun setAlarm(context: Context, alarm: AlarmEntity) {
        // 设置下一次的闹铃
        AlarmManagerUtils.setAlarm(context, alarm, alarm.nextTriggerTime)
    }

    /**
     * 灭屏后的相关逻辑（其实就是执行稍后提醒模式）
     */
    private fun handleScreenOffDuringAlarm(alarm: AlarmEntity) {
        // 添加windowFlag的目的是为了
        AlarmManagerUtils.snoozeAlarm(this, alarm)
    }

}