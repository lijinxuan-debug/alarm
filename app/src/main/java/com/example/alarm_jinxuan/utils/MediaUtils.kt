package com.example.alarm_jinxuan.utils

import android.content.Context
import android.media.MediaPlayer
import com.example.alarm_jinxuan.model.AlarmEntity

object MediaUtils {
    // 播放器
    private var mediaPlayer: MediaPlayer? = null

    // 当前正在响铃的闹钟
    var currentAlarm: AlarmEntity? = null

    fun isAlarmPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    /**
     * 核心播放方法：传入 raw 资源 ID 即可响起来
     */
    fun startRingtonePreview(resId: Int, context: Context, alarm: AlarmEntity?) {
        try {
            if (alarm != null) {
                currentAlarm = alarm
            }
            // 彻底清理上一个播放器（这是防卡顿、防重叠的关键）
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            mediaPlayer = MediaPlayer.create(context, resId).apply {
                // 开启循环
                isLooping = true

                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop(context: Context) {
        VibrationUtils.stop(context)
        // 停止音乐播放
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

}