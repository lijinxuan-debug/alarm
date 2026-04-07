package com.example.alarm_jinxuan.view.worldClock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.alarm_jinxuan.model.WorldClockEntity
import com.example.alarm_jinxuan.repository.WorldClockRepository
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class WorldClockUiState(
    val selectedClocks: List<WorldClockEntity> = emptyList(),
    val availableCities: List<WorldClockEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class WorldClockViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorldClockRepository(application)

    private val _uiState = MutableLiveData<WorldClockUiState>()
    val uiState: LiveData<WorldClockUiState> = _uiState

    init {
        _uiState.value = WorldClockUiState()
        initializeDatabase()
        loadSelectedClocks()
    }

    /**
     * 初始化数据库
     */
    private fun initializeDatabase() {
        viewModelScope.launch {
            try {
                repository.initializeDatabaseIfEmpty()
            } catch (e: Exception) {
                _uiState.value = _uiState.value?.copy(error = "初始化失败: ${e.message}")
            }
        }
    }

    /**
     * 加载已选择的世界时钟
     */
    fun loadSelectedClocks() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value ?: WorldClockUiState()
                _uiState.value = currentState.copy(isLoading = true)
                val clocks = repository.getAllSelectedWorldClock()
                val updatedClocks = updateClockTime(clocks)
                _uiState.value = currentState.copy(
                    selectedClocks = updatedClocks,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                val currentState = _uiState.value ?: WorldClockUiState()
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 搜索城市
     */
    fun searchCities(keyword: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value ?: WorldClockUiState()
                _uiState.value = currentState.copy(isLoading = true)
                val cities = repository.searchWorldClock(keyword)
                _uiState.value = currentState.copy(
                    availableCities = cities,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                val currentState = _uiState.value ?: WorldClockUiState()
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = "搜索失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 添加城市到世界时钟
     */
    suspend fun addWorldClock(worldClock: WorldClockEntity) {
        val currentState = _uiState.value ?: WorldClockUiState()

        // 立即添加到内存中（即时反馈）
        val currentTimeMills = System.currentTimeMillis()
        val newClock = worldClock.copy(
            id = (currentState.selectedClocks.maxOfOrNull { it.id } ?: 0) + 1,
            currentTimeMills = currentTimeMills,
            dayStatus = 0
        )
        val updatedClocks = currentState.selectedClocks + newClock

        _uiState.value = currentState.copy(
            selectedClocks = updatedClocks,
            isLoading = false
        )

        // 异步持久化并等待完成
        repository.addWorldClock(worldClock)
    }

    /**
     * 删除世界时钟
     */
    fun removeWorldClock(id: Long) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value ?: WorldClockUiState()

                // 立即从内存中删除（即时反馈）
                val updatedClocks = currentState.selectedClocks.filterNot { it.id.toLong() == id }
                _uiState.value = currentState.copy(
                    selectedClocks = updatedClocks,
                    isLoading = false
                )

                // 异步持久化删除
                repository.removeWorldClock(id)
            } catch (e: Exception) {
                val currentState = _uiState.value ?: WorldClockUiState()
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = "删除失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 批量删除世界时钟
     */
    fun removeWorldClockBatch(ids: List<Long>) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value ?: WorldClockUiState()
                _uiState.value = currentState.copy(isLoading = true)
                repository.removeWorldClockBatch(ids)
                loadSelectedClocks()
                // 刷新可用城市列表
                searchCities("")
            } catch (e: Exception) {
                val currentState = _uiState.value ?: WorldClockUiState()
                _uiState.value = currentState.copy(
                    isLoading = false,
                    error = "删除失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 更新时钟时间（用于实时更新）
     */
    fun updateTime() {
        val currentState = _uiState.value ?: return
        val currentClocks = currentState.selectedClocks
        val updatedClocks = updateClockTime(currentClocks)
        _uiState.value = currentState.copy(selectedClocks = updatedClocks)
    }

    /**
     * 计算时钟的当前时间
     */
    private fun updateClockTime(clocks: List<WorldClockEntity>): List<WorldClockEntity> {
        val now = System.currentTimeMillis()
        val localZoneId = ZoneId.systemDefault()

        return clocks.map { clock ->
            try {
                val zoneId = ZoneId.of(clock.zoneId)

                // 直接使用当前时间戳转换为目标时区的时间
                val cityTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zoneId)
                val localTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), localZoneId)

                // 计算天数差异 - 修正跨年问题
                val localDate = localTime.toLocalDate()
                val cityDate = cityTime.toLocalDate()
                val dayStatus = java.time.temporal.ChronoUnit.DAYS.between(localDate, cityDate).toInt()

                clock.copy(
                    currentTimeMills = now,
                    dayStatus = dayStatus
                )
            } catch (e: Exception) {
                clock // 如果时区解析失败，返回原始数据
            }
        }
    }

    /**
     * 获取格式化的时间字符串
     */
    fun formatTime(worldClock: WorldClockEntity): String {
        try {
            val zoneId = ZoneId.of(worldClock.zoneId)
            val cityTime = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(worldClock.currentTimeMills),
                zoneId
            )
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            return cityTime.format(formatter)
        } catch (e: Exception) {
            return "00:00"
        }
    }

    /**
     * 获取格式化的日期字符串
     */
    fun formatDate(worldClock: WorldClockEntity): String {
        try {
            val zoneId = ZoneId.of(worldClock.zoneId)
            val cityTime = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(worldClock.currentTimeMills),
                zoneId
            )
            val formatter = DateTimeFormatter.ofPattern("M月d日 EEEE")
            return cityTime.format(formatter)
        } catch (e: Exception) {
            return "未知日期"
        }
    }

    /**
     * 获取天数差异描述
     */
    fun getDayStatusText(dayStatus: Int): String {
        return when (dayStatus) {
            -1 -> "昨天"
            1 -> "明天"
            else -> ""
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        val currentState = _uiState.value ?: return
        _uiState.value = currentState.copy(error = null)
    }
}