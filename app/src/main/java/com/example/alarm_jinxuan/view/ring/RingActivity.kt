package com.example.alarm_jinxuan.view.ring

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.alarm_jinxuan.databinding.ActivityRingBinding
import com.example.alarm_jinxuan.model.AlarmEntity
import com.example.alarm_jinxuan.repository.AlarmRepository
import com.example.alarm_jinxuan.service.AlarmService
import com.example.alarm_jinxuan.utils.AlarmManagerUtils
import com.example.alarm_jinxuan.utils.MediaUtils
import com.example.alarm_jinxuan.utils.VibrationUtils

class RingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRingBinding
    private lateinit var alarm: AlarmEntity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFlagsForLockScreen()

        // 初始化数据
        alarm = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 及以上使用新方法
            intent.getParcelableExtra("ALARM_OBJ", AlarmEntity::class.java)
        } else {
            // Android 13 以下使用旧方法
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<AlarmEntity>("ALARM_OBJ")
        })!!
        // 显示最上面的时间
        fillAlarmData()

        // 设置交互
        setupListeners(alarm)
    }

    /**
     * 冲破锁屏
     */
    private fun setupFlagsForLockScreen() {
        // 检查屏幕是否处于熄灭状态
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val isScreenOff = !powerManager.isInteractive

        // 1. 冲破锁屏，只有在屏幕熄灭时才点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            if (isScreenOff) {
                setTurnScreenOn(true)
            }
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            window.addFlags(if (isScreenOff) {
                flags or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            } else {
                flags
            })
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. 💡 解决黑条的关键：适配刘海屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // 3. 实现沉浸式占满
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // 💡 增加这一行，确保布局撑开
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun fillAlarmData() {
        binding.tvTime.text = "${alarm.hour}:${alarm.minute}"
    }

    private fun setupListeners(alarm: AlarmEntity) {
        val intent = Intent(this, AlarmService::class.java)

        binding.btnSnooze.text = "${alarm.snoozeInterval} 分钟后提醒"

        binding.btnSnooze.setOnClickListener {
            Toast.makeText(this, "闹钟将在 ${alarm.snoozeInterval} 分钟后再次响铃", Toast.LENGTH_SHORT).show()
            // 先停止闹钟响铃
            this.stopService(intent)
            // 实现小睡模式
            AlarmManagerUtils.snoozeAlarm(this,alarm)
            // 退出页面
            finish()
        }

        binding.seekbarDismiss.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 如果用户松手时，滑块滑到了 80% 以上，就认为是要关闭
                if (seekBar != null && seekBar.progress > 80) {
                    // 关闭铃声的同时也要关闭振动
                    this@RingActivity.stopService(intent)
                    // 更新数据库中的闹钟状态
                    AlarmRepository.dismissAlarm(alarm, this@RingActivity)
                    // 同时销毁页面
                    finish()
                } else {
                    // 否则，滑块自动弹回起点
                    seekBar?.progress = 0
                }
            }
        })
    }

    // 严谨点，防止Activity意外销毁导致声音一直响
    override fun onDestroy() {
        super.onDestroy()
        MediaUtils.stop(this)
        VibrationUtils.stop(this)
    }
}