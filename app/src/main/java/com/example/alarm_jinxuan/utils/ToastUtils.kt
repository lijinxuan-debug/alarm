package com.example.alarm_jinxuan.utils

import android.content.Context
import android.widget.Toast

/**
 * Toast 工具类，支持防抖处理
 */
object ToastUtils {

    private var toast: Toast? = null
    private var lastShowTime = 0L
    private const val MIN_INTERVAL = 1000L // 最小显示间隔 1 秒

    /**
     * 显示 Toast，支持防抖
     * @param context 上下文
     * @param message 消息内容
     * @param duration 显示时长
     */
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val currentTime = System.currentTimeMillis()

        // 如果当前正在显示的 Toast 和新消息相同，且时间间隔小于最小间隔，则不显示
        if (toast != null && lastShowTime > 0 && currentTime - lastShowTime < MIN_INTERVAL) {
            // 检查消息是否相同（通过 toast 视图的文本）
            val currentMessage = toast?.view?.findViewById<android.widget.TextView>(android.R.id.message)?.text
            if (currentMessage == message) {
                return
            }
        }

        // 取消之前的 Toast
        toast?.cancel()

        // 创建新的 Toast
        toast = Toast.makeText(context.applicationContext, message, duration)
        toast?.show()
        lastShowTime = currentTime
    }

    /**
     * 显示短时间 Toast
     */
    fun showShort(context: Context, message: String) {
        show(context, message, Toast.LENGTH_SHORT)
    }

    /**
     * 显示长时间 Toast
     */
    fun showLong(context: Context, message: String) {
        show(context, message, Toast.LENGTH_LONG)
    }

    /**
     * 取消当前显示的 Toast
     */
    fun cancel() {
        toast?.cancel()
        toast = null
        lastShowTime = 0
    }
}