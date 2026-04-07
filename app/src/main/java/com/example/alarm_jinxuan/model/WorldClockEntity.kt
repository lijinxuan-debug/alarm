package com.example.alarm_jinxuan.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "WorldClock",)
data class WorldClockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val cityName: String, // 中文名称
    val cityEnglishName: String, // 英文名称
    val countryName: String, // 国家/地区中文名称
    val countryPinyin: String, // 国家/地区拼音
    val zoneId: String,
    val timeOffset: String, // 时差
    val currentTimeMills: Long, // 时间戳
    val dayStatus: Int, // -1-昨天 0-今天 1-明天
    val selectedTime: Long // true-用户选择
)
