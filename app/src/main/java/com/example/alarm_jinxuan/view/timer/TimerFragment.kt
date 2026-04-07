package com.example.alarm_jinxuan.view.timer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import com.aigestudio.wheelpicker.WheelPicker
import com.example.alarm_jinxuan.databinding.FragmentTimerBinding
import com.example.alarm_jinxuan.repository.TimerRepository
import com.example.alarm_jinxuan.service.TimerService
import com.example.alarm_jinxuan.utils.SharedClockComponents

class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null

    private val binding get() = _binding!!

    private val viewModel: TimerViewModel by activityViewModels()

    private val TAG = "TimerFragment"

    // 小时数据: 00 到 99
    private val hourData = (0..99).map { String.format("%02d", it) }

    // 分钟和秒钟数据: 00 到 59
    private val minSecData = (0..59).map { String.format("%02d", it) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimerBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置按钮
        binding.edit.setOnClickListener {
            val intent = Intent(requireContext(), com.example.alarm_jinxuan.view.permission.PermissionSettingActivity::class.java)
            intent.putExtra("show_back_button", true)
            startActivity(intent)
        }

        // 设置超时回调：当 Service 检测到时间到0时，同步 ViewModel 的剩余时间为0
        TimerRepository.onTimeOutCallback = {
            viewModel.syncRemainingToZero()
        }

        // 监听剩余时间变化，当时间到0时合并按钮为启动按钮
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.remainingSeconds.collect { remaining ->
                // 只有在设置了计时器（repoTotal > 0）且时间到0时才合并按钮
                if (TimerRepository.totalNanos > 0L && remaining <= 0L) {
                    // 隐藏左侧按钮，显示中间启动按钮
                    binding.wheelHour.visibility = View.VISIBLE
                    binding.wheelMinute.visibility = View.VISIBLE
                    binding.wheelSecond.visibility = View.VISIBLE
                    binding.btnLeftTimer.visibility = View.GONE
                    binding.btnRightTimer.visibility = View.GONE
                    binding.add.visibility = View.VISIBLE
                    binding.composeView.visibility = View.GONE
                    binding.add.alpha = 1f
                    binding.add.scaleX = 1f
                    binding.add.scaleY = 1f
                } else {
                    Log.d(TAG, "🔓 正常状态：显示左右按钮")
                }
            }
        }

        // 设置滚轮监听器
        val wheelListener = object : WheelPicker.OnItemSelectedListener {
            override fun onItemSelected(
                picker: WheelPicker,
                data: Any?,
                position: Int
            ) {
                viewModel.updatePickerValues(
                    binding.wheelHour.currentItemPosition,
                    binding.wheelMinute.currentItemPosition,
                    binding.wheelSecond.currentItemPosition
                )
            }
        }

        // 设置图表动画
        // 1. 初始化 Compose 视图
        binding.composeView.apply {
            // 主要用于view生命周期销毁则compose生命周期也应当销毁
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val total by viewModel.totalSeconds.collectAsState()
                val remaining by viewModel.remainingSeconds.collectAsState()
                val isRunning by viewModel.isRunning.collectAsState()

                // 计时所用的协程
                LaunchedEffect(isRunning) {
                    if (isRunning) {
                        var lastFrameTime = System.nanoTime()
                        while (remaining > 0L) {
                            withFrameNanos { frameTimeNanos ->
                                val diff = frameTimeNanos - lastFrameTime
                                viewModel.reduceNanos(diff)
                                lastFrameTime = frameTimeNanos
                            }
                        }
                    }
                }

                // 调用封装好的渲染函数
                SharedClockComponents.TimerRenderScreen(
                    total = total,
                    remaining = remaining,
                    isRunning = isRunning,
                    formatTime = { viewModel.formatTime(it) } // 传入格式化逻辑
                )
            }
        }
        // 小时滚轮
        binding.wheelHour.apply {
            data = hourData
            selectedItemPosition = 0 // 默认选中 00
            isCurved = true          // 开启弧度感
            isCyclic = true
            setOnItemSelectedListener(wheelListener)
        }

        // 分钟滚轮
        binding.wheelMinute.apply {
            data = minSecData
            selectedItemPosition = 0
            isCurved = true
            isCyclic = true
            setOnItemSelectedListener(wheelListener)
        }

        // 秒钟滚轮
        binding.wheelSecond.apply {
            data = minSecData
            selectedItemPosition = 0
            isCurved = true
            isCyclic = true
            setOnItemSelectedListener(wheelListener)
        }

        // 启动按钮
        binding.add.setOnClickListener {
            val bool = viewModel.toggle()
            if (!bool) return@setOnClickListener
            // 分裂动画
            startSplitAnimation()
            // 隐藏滚轴
            binding.wheelHour.visibility = View.GONE
            binding.wheelMinute.visibility = View.GONE
            binding.wheelSecond.visibility = View.GONE
            binding.tvColon1.visibility = View.GONE
            binding.tvColon2.visibility = View.GONE
            // 显示图表
            binding.composeView.visibility = View.VISIBLE
            // 发送通知service
            createService("ACTION_START")
        }

        // 重置按钮（时间到时禁用）
        binding.btnLeftTimer.setOnClickListener {
            if (TimerRepository.totalNanos > 0L && viewModel.remainingSeconds.value <= 0L) {
                return@setOnClickListener
            }
            viewModel.reset()
            // 停止服务和关闭通知
            createService("ACTION_STOP_SERVICE")
            // 收回动画
            startMergeAnimation()
            // 滚轮数据也全部清0
            binding.wheelHour.selectedItemPosition = 0
            binding.wheelMinute.selectedItemPosition = 0
            binding.wheelSecond.selectedItemPosition = 0

            // 显示滚轴
            binding.wheelHour.visibility = View.VISIBLE
            binding.wheelMinute.visibility = View.VISIBLE
            binding.wheelSecond.visibility = View.VISIBLE
            binding.tvColon1.visibility = View.VISIBLE
            binding.tvColon2.visibility = View.VISIBLE
            // 隐藏图表
            binding.composeView.visibility = View.GONE
        }

        // 暂停/开始按钮（时间到时允许重新开始）
        binding.btnRightTimer.setOnClickListener {
            val wasRunning = TimerRepository.isRunning.value
            viewModel.toggle()

            // 根据状态变化发送正确的 action 到 Service
            if (wasRunning) {
                // 从运行变为暂停
                createService("ACTION_PAUSE")
            } else {
                // 从暂停变为运行，检查是否是首次启动
                if (TimerRepository.totalNanos > 0L) {
                    // 恢复计时（之前暂停过）
                    createService("ACTION_RESUME")
                }
                // 首次启动的逻辑在 add 按钮中处理
            }
        }
    }

    /**
     * 创建相关服务
     */
    private fun createService(actionIntent: String) {
        // 这里可以直接指向service
        val intent = Intent(requireContext(), TimerService::class.java).apply {
            action = actionIntent
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(requireContext(),intent)
        } else {
            requireContext().startService(intent)
        }
    }

    /**
     * 分裂动画
     */
    private fun startSplitAnimation() {
        val distance = 200f // 分裂出去的距离（像素或转为dp）
        val duration = 400L // 动画时长

        // add按钮缩小变透明
        binding.add.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(duration)
            .withEndAction { binding.add.visibility = View.GONE }
            .start()

        // 2. 左侧按钮：先显示，再向左平移
        binding.btnLeftTimer.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0f
            scaleY = 0f
            animate()
                .translationX(-distance) // 向左移动
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(OvershootInterpolator()) // 增加回弹效果，更有张力
                .setDuration(duration)
                .start()
        }

        // 3. 右侧按钮：先显示，再向右平移
        binding.btnRightTimer.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0f
            scaleY = 0f
            animate()
                .translationX(distance) // 向右移动
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(OvershootInterpolator())
                .setDuration(duration)
                .start()
        }
    }

    /**
     * 缩回动画
     */
    private fun startMergeAnimation() {
        val duration = 400L

        // 1. 中间按钮：显现并放大
        binding.add.apply {
            visibility = View.VISIBLE
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(duration)
                .start()
        }

        // 2. 左右按钮：缩回中间并消失
        listOf(binding.btnLeftTimer, binding.btnRightTimer).forEach { btn ->
            btn.animate()
                .translationX(0f) // 回到坐标原点（即父容器中心）
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(duration)
                .withEndAction { btn.visibility = View.GONE }
                .start()
        }
    }

     override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}