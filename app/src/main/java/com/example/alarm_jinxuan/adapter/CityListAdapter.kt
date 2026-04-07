package com.example.alarm_jinxuan.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.alarm_jinxuan.databinding.ItemCityListBinding
import com.example.alarm_jinxuan.model.WorldClockEntity
import java.time.ZoneId
import java.time.ZonedDateTime

class CityListAdapter(
    private val onItemClick: (WorldClockEntity) -> Unit
) : RecyclerView.Adapter<CityListAdapter.CityViewHolder>() {

    private var cities = listOf<WorldClockEntity>()

    inner class CityViewHolder(private val binding: ItemCityListBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(cities[position])
                }
            }
        }

        fun bind(city: WorldClockEntity) {
            try {
                val zoneId = ZoneId.of(city.zoneId)
                val cityTime = ZonedDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(System.currentTimeMillis()),
                    zoneId
                )

                // 设置城市名（带国家）
                binding.cityName.text = "${city.cityName}（${city.countryName}）"

                // 设置时间（带时段）
                val timeInfo = formatTimeWithPeriod(cityTime)
                binding.cityInfo.text = timeInfo

            } catch (e: Exception) {
                binding.cityName.text = city.cityName
                binding.cityInfo.text = city.cityEnglishName
            }
        }

        /**
         * 根据小时数获取时段文字
         */
        private fun getTimePeriodText(hour: Int): String {
            return when (hour) {
                in 5..6 -> "清晨"
                in 7..11 -> "上午"
                in 12..13 -> "中午"
                in 14..17 -> "下午"
                in 18..22 -> "晚上"
                in 23..24, in 0..4 -> "半夜"
                else -> ""
            }
        }

        /**
         * 格式化时间（带时段）
         */
        private fun formatTimeWithPeriod(cityTime: ZonedDateTime): String {
            val localNow = ZonedDateTime.now()
            val period = getTimePeriodText(cityTime.hour)

            // 判断是否是同一天
            val isSameDay = cityTime.toLocalDate() == localNow.toLocalDate()

            val timeStr = "${cityTime.hour}:${String.format("%02d", cityTime.minute)}"

            return if (isSameDay) {
                // 同一天：显示"上午1:55"
                "$period$timeStr"
            } else {
                // 不同天：显示"4月3号下午8:55"
                "${cityTime.monthValue}月${cityTime.dayOfMonth}号$period$timeStr"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CityViewHolder {
        val binding = ItemCityListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CityViewHolder, position: Int) {
        holder.bind(cities[position])
    }

    override fun getItemCount(): Int = cities.size

    fun updateCities(newCities: List<WorldClockEntity>) {
        val diffCallback = CityDiffCallback(cities, newCities)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        cities = newCities
        diffResult.dispatchUpdatesTo(this)
    }
}

class CityDiffCallback(
    private val oldList: List<WorldClockEntity>,
    private val newList: List<WorldClockEntity>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].cityEnglishName == newList[newItemPosition].cityEnglishName
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldCity = oldList[oldItemPosition]
        val newCity = newList[newItemPosition]

        return oldCity.cityName == newCity.cityName &&
                oldCity.timeOffset == newCity.timeOffset &&
                oldCity.cityEnglishName == newCity.cityEnglishName
    }
}