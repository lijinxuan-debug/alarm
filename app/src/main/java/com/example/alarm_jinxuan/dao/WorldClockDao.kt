package com.example.alarm_jinxuan.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.alarm_jinxuan.model.WorldClockEntity

/**
 * 世界时钟 Dao
 */
@Dao
interface WorldClockDao {
    /**
     * 查询所有世界时钟（按 id 升序）
     */
    @Query("SELECT * FROM WorldClock ORDER BY id ASC")
    suspend fun getAllWorldClock(): List<WorldClockEntity>

    @Query("SELECT * FROM WorldClock WHERE selectedTime != -1 ORDER BY selectedTime ASC")
    suspend fun getAllSelectedWorldClock(): List<WorldClockEntity>

    /**
     * 根据 ID 查询单个世界时钟
     * @param id 时钟唯一标识
     * @return 匹配的实体（无则返回 null）
     */
    @Query("SELECT * FROM WorldClock WHERE id = :id LIMIT 1")
    suspend fun getWorldClockById(id: Long): WorldClockEntity?

    /**
     * 模糊搜索世界时钟（按城市名/英文名/国家名/国家拼音）
     * @param keyword 搜索关键词（支持部分匹配，无需手动拼接 %）
     * @return 匹配的时钟列表（按 id 升序）
     * 说明：支持搜索中文城市名、英文名、国家名、国家拼音
     */
    @Query("""
        SELECT * FROM WorldClock
        WHERE cityName LIKE '%' || :keyword || '%'
           OR cityEnglishName LIKE '%' || :keyword || '%'
           OR countryName LIKE '%' || :keyword || '%'
           OR countryPinyin LIKE '%' || :keyword || '%'
        ORDER BY id ASC
    """)
    suspend fun searchWorldClock(keyword: String): List<WorldClockEntity>

    /**
     * 查询是否存在指定 ID 的时钟
     * @param id 时钟唯一标识
     * @return true=存在，false=不存在
     */
    @Query("SELECT EXISTS(SELECT 1 FROM WorldClock WHERE id = :id)")
    suspend fun existsWorldClockById(id: Long): Boolean

    /**
     * 获取世界时钟总数
     */
    @Query("SELECT COUNT(*) FROM WorldClock")
    suspend fun getWorldClockCount(): Int

    /**
     * 添加单个世界时钟
     * @param entity 时钟实体
     * @param onConflict 冲突策略（默认替换：重复 ID 则覆盖）
     * @return 插入的行 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldClock(entity: WorldClockEntity): Long

    /**
     * 批量添加世界时钟
     * @param entities 时钟实体列表
     * @param onConflict 冲突策略（默认替换：重复 ID 则覆盖）
     * @return 插入的行 ID 列表（与输入列表顺序一致）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldClockBatch(entities: List<WorldClockEntity>): List<Long>

    /**
     * 更新单个世界时钟（全字段更新，需传入完整实体）
     * @param entity 完整的时钟实体（ID 必须存在）
     * @return 受影响的行数（0=未更新，1=更新成功）
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateWorldClock(entity: WorldClockEntity): Int

    /**
     * 批量更新世界时钟（全字段更新）
     * @param entities 时钟实体列表（ID 必须存在）
     * @return 受影响的行数
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateWorldClockBatch(entities: List<WorldClockEntity>): Int

    /**
     * 根据 ID 删除单个世界时钟
     * @param id 时钟唯一标识
     * @return 受影响的行数（0=未删除，1=删除成功）
     */
    @Query("DELETE FROM WorldClock WHERE id = :id")
    suspend fun deleteWorldClockById(id: Long): Int

    /**
     * 批量删除世界时钟（根据 ID 列表）
     * @param ids 要删除的 ID 列表
     * @return 受影响的行数
     */
    @Query("DELETE FROM WorldClock WHERE id IN (:ids)")
    suspend fun deleteWorldClockBatch(ids: List<Long>): Int

    /**
     * 删除所有世界时钟
     * 注意：谨慎使用，建议添加二次确认
     */
    @Query("DELETE FROM WorldClock")
    suspend fun deleteAllWorldClock(): Int

    /**
     * 删除单个世界时钟（通过实体，需保证实体 ID 存在）
     * @param entity 时钟实体
     * @return 受影响的行数
     */
    @Delete
    suspend fun deleteWorldClock(entity: WorldClockEntity): Int
}