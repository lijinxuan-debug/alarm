package com.example.alarm_jinxuan.view.stopWatch

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.alarm_jinxuan.R
import com.example.alarm_jinxuan.databinding.FragmentStopWatchBinding
import com.example.alarm_jinxuan.utils.GlideUtil
import com.example.alarm_jinxuan.utils.StopWatchManager
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class StopWatchFragment : Fragment() {

    private var _binding : FragmentStopWatchBinding?= null

    private val binding get() = _binding!!

    private val viewModel : StopWatchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStopWatchBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.formattedTime.observe(viewLifecycleOwner) { timeString ->
            binding.stopWatch.text = timeString
        }

        viewModel.formattedInterval.observe(viewLifecycleOwner) { value ->
            binding.breakPoint.text = value
        }

        viewModel.firstInterval.observe(viewLifecycleOwner) { value ->
            if (value) {
                binding.breakPoint.visibility = View.VISIBLE
            } else {
                binding.breakPoint.visibility = View.INVISIBLE
            }
        }

        val lapAdapter = LapAdapter()

        binding.laps.apply {
            adapter = lapAdapter
            setHasFixedSize(true)
        }

        // 设置按钮
        binding.edit.setOnClickListener {
            val intent = Intent(requireContext(), com.example.alarm_jinxuan.view.permission.PermissionSettingActivity::class.java)
            intent.putExtra("show_back_button", true)
            startActivity(intent)
        }

        // 启动秒表
        binding.add.setOnClickListener {
            viewModel.start()
            // 分裂动画
            startSplitAnimation()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.laps.collect { newList ->
                        lapAdapter.submitList(newList) {
                            binding.laps.scrollToPosition(0)
                        }
                    }
                }

                launch {
                    // 监听运行状态
                    viewModel.isRunning.collect { value ->
                        if (value) {
                            GlideUtil.loadImage(requireContext(),R.drawable.ic_stop_watch,binding.btnLeftStopWatch)
                            GlideUtil.loadImage(requireContext(),R.drawable.ic_pause,binding.btnRightStopWatch)

                            // 快记功能
                            binding.btnLeftStopWatch.setOnClickListener {
                                // 添加快记数据列表
                                viewModel.addLap()
                                // 将间隔时间清零重来
                                viewModel.intervalReset()
                                // 显示间隔时间
                                viewModel.firstInterval.value = true
                            }
                            // 暂停功能
                            binding.btnRightStopWatch.setOnClickListener {
                                viewModel.stop()
                            }
                        } else {
                            GlideUtil.loadImage(requireContext(),R.drawable.ic_reopen,binding.btnLeftStopWatch)
                            GlideUtil.loadImage(requireContext(),R.drawable.ic_begin,binding.btnRightStopWatch)

                            binding.btnLeftStopWatch.setOnClickListener {
                                // 重置功能
                                viewModel.reset()
                                // 删除所有快记时间
                                viewModel.deleteLapRecord()
                                // 将间隔时间清零重来
                                viewModel.intervalReset()
                                // 间隔时间不显示
                                viewModel.firstInterval.value = false
                                // 同时执行合并
                                startMergeAnimation()
                            }
                            // 启动功能
                            binding.btnRightStopWatch.setOnClickListener {
                                viewModel.start()
                            }
                        }
                    }
                }
            }
        }

        binding.composeView.apply {
            setContent {
                MaterialTheme {
                    stopWatchScreen()
                }
            }
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
        binding.btnLeftStopWatch.apply {
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
        binding.btnRightStopWatch.apply {
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
        listOf(binding.btnLeftStopWatch, binding.btnRightStopWatch).forEach { btn ->
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

    @Composable
    private fun stopWatchScreen() {
        // 其实就是填满整个盒子以及居中排列
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            stopwatchDial()
        }
    }

    @Composable
    private fun stopwatchDial() {
        // 观察数据
        val isRunning by viewModel.isRunning.collectAsState()
        val elapsedNanos by viewModel.elapsedNanos.collectAsState()

        // 计时所用的协程
        LaunchedEffect(isRunning) {
            if (isRunning) {
                var lastFrameTime = System.nanoTime()
                while (true) {
                    withFrameNanos { frameTimeNanos ->
                        val diff = frameTimeNanos - lastFrameTime
                        viewModel.addNanos(diff)
                        lastFrameTime = frameTimeNanos
                    }
                }
            }
        }

        // 当前的指针角度
        val currentAngel = (elapsedNanos / 1_000_000_000f) * 6f

        // 时钟的时间字体表示
        val textPaint = remember {
            Paint().apply {
                color = Color.BLACK
                textSize = 48f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                typeface = Typeface.create("宋体", Typeface.NORMAL)
            }
        }

        val density = LocalDensity.current

        val metrics = remember(density) {
            object {
                val numPadding = 40.dp.toPx(density)
                val bigLine = 15.dp.toPx(density)
                val majorLine = 10.dp.toPx(density)
                val normalLine = 8.dp.toPx(density)
                val strokeW = 1.dp.toPx(density)
                val offsetTop = 10.dp.toPx(density)

                // 指针相关
                val needleGap =3.5.dp.toPx(density)
                val needleTail = 10.dp.toPx(density)
                val needleWidth =1.5.dp.toPx(density)
                val ringRadius =3.dp.toPx(density)
                val ringStroke =1.5.dp.toPx(density)
            }
        }

        Canvas(modifier = Modifier.size(300.dp)) {
            val center = size.center
            val radius = size.width / 2

            for (i in 0 until 300) {
                val angle = i * 1.2f
                val isMajor = i % 5 == 0
                val isBigMajor = i % 25 == 0

                val lineLen = when {
                    isBigMajor -> metrics.bigLine
                    isMajor -> metrics.majorLine
                    else -> metrics.normalLine
                }

                rotate(degrees = angle, pivot = center) {
                    drawLine(
                        color = if (isBigMajor) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.LightGray,
                        start = Offset(center.x, center.y - radius + metrics.offsetTop),
                        end = Offset(center.x, center.y - radius + metrics.offsetTop + lineLen),
                        strokeWidth = metrics.strokeW
                    )
                }

                if (isBigMajor) {
                    val angleRad = Math.toRadians((angle - 90).toDouble())
                    val x = center.x + (radius - metrics.numPadding) * cos(angleRad).toFloat()
                    val y = center.y + (radius - metrics.numPadding) * sin(angleRad).toFloat()
                    val secondText = if (i == 0) "60" else (i / 5).toString()
                    drawContext.canvas.nativeCanvas.drawText(secondText, x, y + 12f, textPaint)
                }
            }

            // 1. 画蓝色秒针
            rotate(degrees = currentAngel, pivot = center) {
                // 针尖
                drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFF3169EC),
                    start = Offset(center.x, center.y - metrics.needleGap),
                    end = Offset(center.x, center.y - radius + 10.dp.toPx()),
                    strokeWidth = metrics.needleWidth,
                    cap = StrokeCap.Round
                )
                // 针尾
                drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFF3169EC),
                    start = Offset(center.x, center.y + metrics.needleGap),
                    end = Offset(center.x, center.y + metrics.needleTail),
                    strokeWidth = metrics.needleWidth,
                    cap = StrokeCap.Round
                )
            }

            // 2. 画轴心圆环
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFF2979FF),
                radius = metrics.ringRadius,
                center = center,
                style = Stroke(width = metrics.ringStroke)
            )
        }
    }

    private fun Dp.toPx(density: Density) = with(density) { this@toPx.toPx() }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}