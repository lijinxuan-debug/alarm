package com.example.alarm_jinxuan.repository

import android.content.Context
import com.example.alarm_jinxuan.dao.AppDatabase
import com.example.alarm_jinxuan.dao.WorldClockDao
import com.example.alarm_jinxuan.model.WorldClockEntity
import com.example.alarm_jinxuan.utils.WorldClockUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class WorldClockRepository(context: Context) {
    private val worldClockDao: WorldClockDao = AppDatabase.getDatabase(context).worldClock()

    /**
     * 获取所有已选择的世界时钟
     */
    suspend fun getAllSelectedWorldClock(): List<WorldClockEntity> =
        withContext(Dispatchers.IO) {
            worldClockDao.getAllSelectedWorldClock()
        }

    /**
     * 搜索城市
     */
    suspend fun searchWorldClock(keyword: String): List<WorldClockEntity> =
        withContext(Dispatchers.IO) {
            val results = if (keyword.isEmpty()) {
                getAvailableCities()
            } else {
                worldClockDao.searchWorldClock(keyword)
            }
            // 按中文拼音排序
            results.sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.cityName })
        }

    /**
     * 获取所有可用城市（未选择的）
     */
    suspend fun getAvailableCities(): List<WorldClockEntity> = withContext(Dispatchers.IO) {
        // 先获取数据库中所有已选择的城市
        val selectedClocks = worldClockDao.getAllSelectedWorldClock()
        val selectedIds = selectedClocks.map { it.cityEnglishName }.toSet()

        // 获取所有预置城市（已按字母排序）
        val allCities = WorldClockUtils.generateAccurate500WorldClocks()

        // 过滤掉已选择的城市，同时保持字母排序
        allCities.filterNot { it.cityEnglishName in selectedIds }
    }

    /**
     * 添加城市到世界时钟
     */
    suspend fun addWorldClock(worldClock: WorldClockEntity): Long = withContext(Dispatchers.IO) {
        // 获取当前已选择城市的最大 selectedTime
        val selectedClocks = worldClockDao.getAllSelectedWorldClock()
        val maxSelectedTime = selectedClocks.maxOfOrNull { it.selectedTime } ?: -1

        // 设置新的 selectedTime
        val newWorldClock = worldClock.copy(
            selectedTime = maxSelectedTime + 1
        )

        worldClockDao.insertWorldClock(newWorldClock)
    }

    /**
     * 删除世界时钟
     */
    suspend fun removeWorldClock(id: Long): Int = withContext(Dispatchers.IO) {
        worldClockDao.deleteWorldClockById(id)
    }

    /**
     * 批量删除世界时钟
     */
    suspend fun removeWorldClockBatch(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        worldClockDao.deleteWorldClockBatch(ids)
    }

    /**
     * 更新世界时钟顺序
     */
    suspend fun updateWorldClockOrder(worldClocks: List<WorldClockEntity>) =
        withContext(Dispatchers.IO) {
            worldClockDao.updateWorldClockBatch(worldClocks)
        }

    /**
     * 初始化数据库中的城市数据
     */
    suspend fun initializeDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        val allClocks = worldClockDao.getAllWorldClock()

        // 检查是否需要重新初始化（数据库为空 或者 有数据但缺少国家字段）
        val needsReinit = allClocks.isEmpty() ||
                allClocks.any { it.countryName.isEmpty() || it.countryPinyin.isEmpty() }

        if (needsReinit) {
            // 删除旧数据（如果有）
            if (allClocks.isNotEmpty()) {
                worldClockDao.deleteAllWorldClock()
            }
            // 初始化所有城市数据（已按拼音排序）
            val allCities = WorldClockUtils.generateAccurate500WorldClocks()
            worldClockDao.insertWorldClockBatch(allCities)
        }
    }
}