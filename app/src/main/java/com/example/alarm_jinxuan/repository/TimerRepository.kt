package com.example.alarm_jinxuan.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object TimerRepository {
    var totalNanos: Long = 0L
    var remainingNanos: Long = 0L

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    /**
     * 将纳秒转换为人性化的剩余时间字符串
     * 逻辑：
     * 1. 超过1天：显示 X天X小时
     * 2. 超过1小时：显示 X小时X分钟
     * 3. 超过1分钟：显示 X分钟
     * 4. 低于1分钟：显示 不足1分钟
     * 5. 归零或负数：显示 计时器超时（仅用于超时通知，不在正常计时中显示）
     */
    fun formatRemainingTime(nanos: Long): String {
        // 使用纳秒进行判断，避免向下取整导致小于1秒时显示错误
        if (nanos <= 0) return "计时器超时"
        if (nanos < 60 * 1_000_000_000L) return "还剩不到 1 分钟"

        // 换算成总秒数，方便计算
        val totalSeconds = nanos / 1_000_000_000L

        // 计算各个单位
        val days = totalSeconds / (24 * 3600)
        val hours = (totalSeconds % (24 * 3600)) / 3600
        val minutes = (totalSeconds % 3600) / 60

        // 根据时间跨度返回不同的格式
        return when {
            days > 0 -> {
                // 如果有天数，只显示 天 + 小时
                if (hours > 0) "还剩${days}天 ${hours}小时" else "${days}天"
            }
            hours > 0 -> {
                // 如果有小时，只显示 小时 + 分钟
                "还剩${hours}小时 ${minutes}分钟"
            }
            else -> {
                // 只有分钟
                "还剩${minutes}分钟"
            }
        }
    }

    /**
     * 计时器超时格式化 (输入秒数，输出 00:00 格式)
     */
    fun formatTimeOut(timeoutSeconds: Long): String {
        val minutes = timeoutSeconds / 60
        val seconds = timeoutSeconds % 60

        // %02d 表示保留两位数字，不足两位补 0
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    /**
     * 更改运行状态
     */
    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    /**
     * 超时回调：当计时器时间到0时触发
     */
    var onTimeOutCallback: (() -> Unit)? = null
}