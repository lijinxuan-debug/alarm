package com.example.alarm_jinxuan.view.worldClock

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alarm_jinxuan.adapter.WorldClockAdapter
import com.example.alarm_jinxuan.databinding.FragmentWorldClockBinding
import com.example.alarm_jinxuan.utils.SharedClockComponents
import com.example.alarm_jinxuan.view.addCity.AddCityActivity
import java.time.format.DateTimeFormatter

class WorldClockFragment : Fragment() {
    private var _binding: FragmentWorldClockBinding? = null

    private val binding get() = _binding!!
    private val viewModel: WorldClockViewModel by activityViewModels()
    private lateinit var adapter: WorldClockAdapter
    private val timeUpdateHandler = Handler(Looper.getMainLooper())
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            viewModel.updateTime()
            timeUpdateHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorldClockBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupComposeView()
        setupRecyclerView()
        setupAddButton()
        setupSettingsButton()
        observeViewModel()

        // 启动时间更新
        startUpdateTime()
    }

    private fun setupComposeView() {
        binding.composeView.setContent {
            com.example.alarm_jinxuan.utils.SharedClockComponents.ClockView()
        }
    }

    private fun setupRecyclerView() {
        adapter = WorldClockAdapter(viewModel) { clock ->
            viewModel.removeWorldClock(clock.id.toLong())
        }

        binding.worldClockRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@WorldClockFragment.adapter
        }
    }

    private fun setupAddButton() {
        binding.add.setOnClickListener {
            val intent = Intent(requireContext(), AddCityActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupSettingsButton() {
        binding.edit.setOnClickListener {
            val intent = Intent(requireContext(), com.example.alarm_jinxuan.view.permission.PermissionSettingActivity::class.java)
            intent.putExtra("show_back_button", true)
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { uiState ->
            // 更新列表
            adapter.updateClocks(uiState.selectedClocks)

            // 显示/隐藏空提示
            binding.emptyHint.visibility = if (uiState.selectedClocks.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 更新日期时间
            updateDateTime(uiState.selectedClocks)

            // 处理错误
            uiState.error?.let { error ->
                // 可以添加错误提示
                viewModel.clearError()
            }
        }
    }

    private fun updateDateTime(clocks: List<com.example.alarm_jinxuan.model.WorldClockEntity>) {
        if (clocks.isNotEmpty()) {
            // 查找北京时间
            val beijingClock = clocks.firstOrNull { it.zoneId.contains("Shanghai") }
            if (beijingClock != null) {
                try {
                    val zoneId = java.time.ZoneId.of(beijingClock.zoneId)
                    val cityTime = java.time.ZonedDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(beijingClock.currentTimeMills),
                        zoneId
                    )
                    val formatter = DateTimeFormatter.ofPattern("M月d日 EEEE")
                    binding.dataTime.text = cityTime.format(formatter)
                } catch (e: Exception) {
                    binding.dataTime.text = "3月17日星期二"
                }
            }
        }
    }

    private fun startUpdateTime() {
        timeUpdateHandler.post(timeUpdateRunnable)
    }

    override fun onResume() {
        super.onResume()
        // 重新加载数据
        viewModel.loadSelectedClocks()
        startUpdateTime()
    }

    override fun onPause() {
        super.onPause()
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        _binding = null
    }
}