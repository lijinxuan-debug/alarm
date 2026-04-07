package com.example.alarm_jinxuan.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.alarm_jinxuan.R
import com.example.alarm_jinxuan.databinding.ItemWorldClockBinding
import com.example.alarm_jinxuan.model.WorldClockEntity
import com.example.alarm_jinxuan.view.worldClock.WorldClockViewModel
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class WorldClockAdapter(
    private val viewModel: WorldClockViewModel,
    private val onLongPress: (WorldClockEntity) -> Unit
) : RecyclerView.Adapter<WorldClockAdapter.WorldClockViewHolder>() {

    private var clocks = listOf<WorldClockEntity>()

    inner class WorldClockViewHolder(private val binding: ItemWorldClockBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // 长按删除
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLongPress(clocks[position])
                }
                true
            }
        }

        fun bind(clock: WorldClockEntity) {
            // 更新时间
            try {
                val zoneId = ZoneId.of(clock.zoneId)
                val cityTime = ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(clock.currentTimeMills),
                    zoneId
                )

                // 12小时制
                val hour24 = cityTime.hour
                val hour12 = when {
                    hour24 == 0 -> 12
                    hour24 <= 12 -> hour24
                    else -> hour24 - 12
                }
                binding.timeText.text = String.format("%d:%02d", hour12, cityTime.minute)

                // 设置时段
                val timePeriodText = getTimePeriodText(cityTime.hour)
                binding.timePeriod.text = timePeriodText

                // 设置城市名
                binding.cityName.text = clock.cityName

                // 设置天数状态 + 时间差
                val dayStatusText = getDayStatusText(clock.dayStatus, clock.timeOffset)
                binding.cityInfo.text = dayStatusText

            } catch (e: Exception) {
                binding.timeText.text = "12:00"
                binding.timePeriod.text = "上午"
                binding.cityName.text = clock.cityName
                binding.cityInfo.text = "今天"
            }
        }

        /**
         * 根据小时数获取时段文字（12小时制）
         */
        private fun getTimePeriodText(hour: Int): String {
            return when (hour) {
                in 5..8 -> "早上"
                in 9..11 -> "上午"
                in 12..13 -> "中午"
                in 14..16 -> "下午"
                in 17..18 -> "傍晚"
                in 19..22 -> "晚上"
                in 23..24, in 0..4 -> "半夜"
                else -> ""
            }
        }

        /**
         * 根据天数状态和时差获取文字
         * @param dayStatus 天数状态，-1-昨天 0-今天 1-明天
         * @param timeOffset 时区偏移，如 "+08:00" 或 "-05:00"
         * @return 格式化后的文字，如 "今天早6小时" 或 "明天晚8小时30分钟"
         */
        private fun getDayStatusText(dayStatus: Int, timeOffset: String): String {
            // 获取天数文字
            val dayText = when (dayStatus) {
                -1 -> "昨天"
                0 -> "今天"
                1 -> "明天"
                else -> ""
            }

            // 解析时区偏移计算时间差
            val timeDiffText = getTimeDiffText(timeOffset)

            // 组合显示
            return if (timeDiffText.isNotEmpty()) {
                "$dayText$timeDiffText"
            } else {
                dayText
            }
        }

        /**
         * 计算时间差文字
         * @param timeOffset 时区偏移，如 "+08:00" 或 "-05:00"
         * @return 时间差文字，如 "早6小时" 或 "晚8小时30分钟"
         */
        private fun getTimeDiffText(timeOffset: String): String {
            try {
                // 解析时区偏移
                val sign = if (timeOffset.startsWith("+")) 1 else -1
                val parts = timeOffset.substring(1).split(":")
                val hours = parts[0].toInt() * sign
                val minutes = parts[1].toInt()

                // 根据时差决定是"早"还是"晚"
                val diffType = if (hours > 0 || (hours == 0 && minutes > 0)) "早" else "晚"
                val absHours = kotlin.math.abs(hours)
                val absMinutes = kotlin.math.abs(minutes)

                // 格式化输出
                return if (absMinutes > 0) {
                    "${diffType}${absHours}小时${absMinutes}分钟"
                } else if (absHours > 0) {
                    "${diffType}${absHours}小时"
                } else {
                    ""
                }
            } catch (e: Exception) {
                return ""
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorldClockViewHolder {
        val binding = ItemWorldClockBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WorldClockViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorldClockViewHolder, position: Int) {
        holder.bind(clocks[position])
    }

    override fun getItemCount(): Int = clocks.size

    fun updateClocks(newClocks: List<WorldClockEntity>) {
        val diffCallback = WorldClockDiffCallback(clocks, newClocks)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        clocks = newClocks
        diffResult.dispatchUpdatesTo(this)
    }
}

class WorldClockDiffCallback(
        private val oldList: List<WorldClockEntity>,
        private val newList: List<WorldClockEntity>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldClock = oldList[oldItemPosition]
            val newClock = newList[newItemPosition]

            return oldClock.cityName == newClock.cityName &&
                    oldClock.timeOffset == newClock.timeOffset &&
                    oldClock.currentTimeMills == newClock.currentTimeMills &&
                    oldClock.dayStatus == newClock.dayStatus
        }
    }