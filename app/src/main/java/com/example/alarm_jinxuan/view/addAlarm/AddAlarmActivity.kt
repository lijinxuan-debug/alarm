package com.example.alarm_jinxuan.view.addAlarm

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.alarm_jinxuan.databinding.ActivityAddAlarmBinding
import com.example.alarm_jinxuan.databinding.LayoutAlarmDurationBinding
import com.example.alarm_jinxuan.databinding.LayoutAlarmNameDialogBinding
import com.example.alarm_jinxuan.databinding.LayoutConfirmDialogBinding
import com.example.alarm_jinxuan.databinding.LayoutDeleteDialogBinding
import com.example.alarm_jinxuan.databinding.LayoutIntervalDialogBinding
import com.example.alarm_jinxuan.databinding.LayoutRepeatDialogBinding
import com.example.alarm_jinxuan.model.AddAlarmClockManager
import com.example.alarm_jinxuan.model.AlarmEntity
import com.example.alarm_jinxuan.model.DurationOption
import com.example.alarm_jinxuan.model.RepeatDay
import com.example.alarm_jinxuan.utils.AlarmManagerUtils
import com.example.alarm_jinxuan.utils.PermissionUtils
import com.example.alarm_jinxuan.utils.StringUtils
import com.example.alarm_jinxuan.view.alarmClockRing.AlarmClockRingActivity
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.getValue

class AddAlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddAlarmBinding
    private lateinit var repeatAdapter: RepeatAdapter
    private val viewModel: AddAlarmViewModel by viewModels()

    private val dataList = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        .mapIndexed { index, name -> RepeatDay(index, name) }

    private val minutesList = listOf(1, 5, 10, 15, 20, 30)

    private var alarmEntity: AlarmEntity ?= null

    // 当前的响铃时长
    private var currentRingMinute = 5

    // 待保存的闹钟数据
    private var pendingAlarm: AlarmEntity? = null

    // 通知权限请求（Android 13+）
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // 用户拒绝了权限
            Toast.makeText(this, "没有通知权限，闹钟可能无法正常提醒", Toast.LENGTH_LONG).show()
        }
        pendingAlarm?.let {
            saveAlarm(it)
        }
    }

    // 获取响铃时长数据列表（每次调用都基于当前的 currentRingMinute 重新生成）
    private fun getDurationData(): List<DurationOption> {
        return minutesList.mapIndexed { index, min ->
            DurationOption(
                id = index,
                minute = min,
                label = "$min 分钟",
                isSelected = (min == currentRingMinute)  // 动态计算，确保状态准确
            )
        }
    }

    // 1. 上午/下午 数据
    val periodData = listOf("上午", "下午")

    // 2. 小时数据 (1-12)
    val hourData = (1..12).map { String.format("%02d", it) }

    // 3. 分钟数据 (00-59)
    val minuteData = (0..59).map { String.format("%02d", it) }

    // 1. 小时数据 (1..12) 的 Int 集合
    val hourDataInt = (1..12).toList()

    // 2. 分钟数据 (0..59) 的 Int 集合
    val minuteDataInt = (0..59).toList()

    // 当前的响铃间隔时间（分钟）
    private var intervalRingValue = 10

    // 当前的重复响铃次数
    private var repeatRingValue = 3

    // 是否发生了修改
    private var isUpdateBool = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAddAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 判断是编辑模式还是新建模式
        val alarmId = intent.getIntExtra("ALARM_ID",-1)

        binding.wheelPeriod.apply {
            data = periodData
        }
        binding.wheelHour.apply {
            data = hourData
        }
        binding.wheelMinute.apply {
            data = minuteData
        }

        if (alarmId != -1) {
            binding.btnDeleteAlarm.visibility = View.VISIBLE

            lifecycleScope.launch {
                val alarmData = viewModel.selectAlarmData(alarmId)
                if (alarmData != null) {
                    updateUI(alarmData)
                    alarmEntity = alarmData
                } else {
                    // 没查到，弹个提示并关掉页面
                    Toast.makeText(this@AddAlarmActivity, "该闹钟不存在", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            binding.btnDeleteAlarm.visibility = View.GONE

            // 更新当前默认时间
            selectWheel()
        }

        // 修改星期几
        binding.itemRepeat.setOnClickListener { repeatDialog() }

        // 修改闹钟名弹窗
        binding.itemLabel.setOnClickListener { updateAlarmName() }

        // 响铃时长弹窗
        binding.itemRingDuration.setOnClickListener {
            selectedDuration()
        }

        // 打开再响间隔
        binding.itemBeepInterval.setOnClickListener {
            slideBeepInterval()
        }

        // 跳转到闹钟铃声
        binding.itemRingtone.setOnClickListener {
            isUpdateBool = true
            val intent = Intent(this, AlarmClockRingActivity::class.java)
            startActivity(intent)
        }

        // 返回弹窗
        binding.back.setOnClickListener {
            if (isUpdateBool) {
                popConfirmDialog()
            } else {
                finish()
            }
        }

        // 保存数据
        binding.success.setOnClickListener {
            save()
        }

        // 删除制定闹钟数据
        binding.btnDeleteAlarm.setOnClickListener {
            popDeleteDialog(alarmId)
        }
    }

    private fun updateUI(alarm: AlarmEntity) {
        // 先设置时间
        if (alarm.hour24 < 12) {
            binding.wheelPeriod.selectedItemPosition = 0
        } else {
            binding.wheelPeriod.selectedItemPosition = 1
        }
        binding.wheelHour.selectedItemPosition = alarm.hour - 1

        binding.wheelMinute.selectedItemPosition = alarm.minute

        // 初始化内存数据
        AddAlarmClockManager.init(alarm)

        // 闹钟重复日期
        binding.tvRepeatValue.text = alarm.repeatText
        val str = alarm.repeatData.split(",")
        dataList.forEachIndexed { index, day ->
            day.isChecked = str[index] == "1"
        }
        // 闹钟名
        binding.alarmName.text = alarm.label
        // 响铃时长
        currentRingMinute = alarm.ringDuration
        binding.textDuration.text = "$currentRingMinute 分钟"
        // 响铃音乐
        binding.ringToneName.text = alarm.ringtoneName

        // 再响间隔
        intervalRingValue = alarm.snoozeInterval
        repeatRingValue = alarm.snoozeCount
        binding.beepIntervalValue.text = "$intervalRingValue 分钟，$repeatRingValue 次"
    }

    private fun selectWheel() {
        val calendar = Calendar.getInstance()
        // 获取上午、下午
        val amPm = calendar.get(Calendar.AM_PM)
        binding.wheelPeriod.selectedItemPosition = amPm
        // 获取12小时制的时间
        var hour12 = calendar.get(Calendar.HOUR)
        // 0点换成12点
        if (hour12 == 0) hour12 = 12

        binding.wheelHour.selectedItemPosition = hour12 - 1

        val minute = calendar.get(Calendar.MINUTE)
        binding.wheelMinute.selectedItemPosition = minute
    }

    /**
     * 星期几重复弹窗
     */
    private fun repeatDialog() {
        isUpdateBool = true
        val dialog = Dialog(this)

        // 2. 拿到你的 ViewBinding
        val dialogBinding = LayoutRepeatDialogBinding.inflate(layoutInflater)

        // 3. 把布局塞进去
        dialog.setContentView(dialogBinding.root)

        dialog.window?.apply {
            // 让背景透明（这样你的 CardView 圆角才能露出来）
            setBackgroundDrawableResource(android.R.color.transparent)

            // 设置弹窗的位置和宽度
            attributes?.apply {
                gravity = Gravity.BOTTOM // 贴在底部（如果你想居中就用 Gravity.CENTER）
                width = WindowManager.LayoutParams.MATCH_PARENT // 宽度撑满
                height = WindowManager.LayoutParams.WRAP_CONTENT // 高度自适应
            }
        }
        dialog.setCanceledOnTouchOutside(false)

        repeatAdapter = RepeatAdapter(dataList) { position ->
            dataList[position].isChecked = !dataList[position].isChecked

            repeatAdapter.notifyItemChanged(position)

            // 全部选择需要更改为每天
            if (dataList.all { it.isChecked }) {
                binding.tvRepeatValue.text = "每天"
            }
        }

        dialogBinding.rvRepeat.apply {
            adapter = repeatAdapter
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            val selectedResult = dataList.filter { it.isChecked }.joinToString(" ") { it.name }
            binding.tvRepeatValue.text = selectedResult.ifEmpty { "不重复" }

            dialog.dismiss()
        }
        dialog.show()

    }

    /**
     * 闹钟名弹窗
     */
    private fun updateAlarmName() {
        isUpdateBool = true
        val dialog = Dialog(this)

        val dialogBinding = LayoutAlarmNameDialogBinding.inflate(layoutInflater)

        dialog.setContentView(dialogBinding.root)

        dialog.window?.apply {
            // 让背景透明（这样你的 CardView 圆角才能露出来）
            setBackgroundDrawableResource(android.R.color.transparent)

            // 设置弹窗的位置和宽度
            attributes?.apply {
                gravity = Gravity.BOTTOM // 贴在底部（如果你想居中就用 Gravity.CENTER）
                width = WindowManager.LayoutParams.MATCH_PARENT // 宽度撑满
                height = WindowManager.LayoutParams.WRAP_CONTENT // 高度自适应
            }
        }
        dialog.setCanceledOnTouchOutside(false)

        // 假设你的 EditText id 是 input
        dialogBinding.input.apply {
            // 1. 获取焦点（锁定光标）
            requestFocus()

            setText(binding.alarmName.text)
            // 2. 全选已有内容
            // 注意：必须先 setText 再 setSelection，或者直接调用 selectAll()
            selectAll()

            // 3. 弹出软键盘
            // 这是一个经典坑：有时候 View 还没贴到窗口上，键盘弹不出来，所以稍微推迟一点点
            postDelayed({
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }, 200) // 延迟 200 毫秒最稳
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            binding.alarmName.text = dialogBinding.input.text
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 响铃时长弹窗
     */
    private fun selectedDuration() {
        isUpdateBool = true
        val dialog = Dialog(this)

        val dialogBinding = LayoutAlarmDurationBinding.inflate(layoutInflater)

        dialog.setContentView(dialogBinding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)

            attributes?.apply {
                gravity = Gravity.BOTTOM // 贴在底部（如果你想居中就用 Gravity.CENTER）
                width = WindowManager.LayoutParams.MATCH_PARENT // 宽度撑满
                height = WindowManager.LayoutParams.WRAP_CONTENT // 高度自适应
            }
        }
        dialog.setCanceledOnTouchOutside(false)

        val durationAdapter = DurationAdapter(getDurationData()) { selectedOption ->
            binding.textDuration.text = selectedOption.label

            // 更改当前响铃时长
            currentRingMinute = selectedOption.minute

            dialog.dismiss()
        }

        dialogBinding.rvRepeat.apply {
            adapter = durationAdapter
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 再响间隔弹窗
     */
    private fun slideBeepInterval() {
        isUpdateBool = true
        val dialog = Dialog(this)

        val dialogBinding = LayoutIntervalDialogBinding.inflate(layoutInflater)

        dialog.setContentView(dialogBinding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)

            attributes?.apply {
                gravity = Gravity.BOTTOM // 贴在底部（如果你想居中就用 Gravity.CENTER）
                width = WindowManager.LayoutParams.MATCH_PARENT // 宽度撑满
                height = WindowManager.LayoutParams.WRAP_CONTENT // 高度自适应
            }
        }
        dialog.setCanceledOnTouchOutside(false)
        // 沿用上次选择数据
        dialogBinding.sliderInterval.value = intervalRingValue.toFloat()
        dialogBinding.sliderRepeat.value = repeatRingValue.toFloat()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            intervalRingValue = dialogBinding.sliderInterval.value.toInt()
            repeatRingValue = dialogBinding.sliderRepeat.value.toInt()

            // 进行格式化展示
            val result = String.format("%d 分钟，%d 次", intervalRingValue, repeatRingValue)
            binding.beepIntervalValue.text = result

            dialog.dismiss()
        }

        dialog.show()
    }

    // 确认是否保存的弹窗
    private fun popConfirmDialog() {
        val dialog = Dialog(this)

        val dialogBinding = LayoutConfirmDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)

            attributes?.apply {
                gravity = Gravity.BOTTOM // 贴在底部（如果你想居中就用 Gravity.CENTER）
                width = WindowManager.LayoutParams.MATCH_PARENT // 宽度撑满
                height = WindowManager.LayoutParams.WRAP_CONTENT // 高度自适应
            }
        }
        dialog.setCanceledOnTouchOutside(false)

        // 放弃保存
        dialogBinding.btnCancel.setOnClickListener {
            // 必须恢复初始状态
            AddAlarmClockManager.clear()

            dialog.dismiss()
            finish()
        }

        // 确认保存
        dialogBinding.btnConfirm.setOnClickListener {
            AddAlarmClockManager.clear()

            dialog.dismiss()
            // 同时需要保存到数据库
            save()
        }

        dialog.show()
    }

    // 确认是否删除按钮
    private fun popDeleteDialog(alarmId: Int) {
        val dialog = Dialog(this)

        val dialogBinding = LayoutDeleteDialogBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)

            attributes?.apply {
                gravity = Gravity.BOTTOM // 贴在底部（如果你想居中就用 Gravity.CENTER）
                width = WindowManager.LayoutParams.MATCH_PARENT // 宽度撑满
                height = WindowManager.LayoutParams.WRAP_CONTENT // 高度自适应
            }
        }
        dialog.setCanceledOnTouchOutside(false)

        // 取消删除
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // 确认删除
        dialogBinding.btnConfirm.setOnClickListener {
            AddAlarmClockManager.clear()

            dialog.dismiss()
            // 同时需要同步到数据库
            delete(alarmId)

        }

        dialog.show()
    }

    /**
     * 保存到数据库
     */
    private fun save() {
        // 先整理时间
        val periodPosition = binding.wheelPeriod.currentItemPosition
        val period = periodData[periodPosition]
        val hourPosition = binding.wheelHour.currentItemPosition
        val hour = hourDataInt[hourPosition]
        val minutePosition = binding.wheelMinute.currentItemPosition
        val minute = minuteDataInt[minutePosition]

        // 整理时间称呼的同时完成24小时转换
        val (displayPeriod, h24) = if (period == "上午") {
            when (hour) {
                12 -> "半夜" to 0
                in 1..4 -> "凌晨" to hour
                in 5..6 -> "清晨" to hour
                in 7..8 -> "早上" to hour
                else -> "上午" to hour
            }
        } else {
            when (hour) {
                12 -> "中午" to 12
                in 1..4 -> "下午" to hour + 12
                in 5..6 -> "傍晚" to hour + 12
                in 7..10 -> "晚上" to hour + 12
                else -> "半夜" to 23
            }
        }
        // 整理星期
        val repeatDataString = dataList.joinToString(",") { if (it.isChecked) "1" else "0" }
        // 整理显示文字
        val checkedNames = dataList.filter { it.isChecked }.map { it.name }
        val repeatText = when {
            checkedNames.size == 7 -> "每天"
            checkedNames.isEmpty() -> "不重复"
            else -> checkedNames.joinToString(", ")
        }
        // 计算下一次响铃的时间戳
        val nextTriggerTime = AlarmManagerUtils.calculateNextTriggerTimeByTime(h24,minute, repeatDataString)
        // 设置闹钟名称
        val alarmName = binding.alarmName.text.toString()
        // 整理对象
        val newAlarm = AlarmEntity(
            id = alarmEntity?.id ?: 0,
            period = displayPeriod,
            hour = hour,
            minute = minute,
            nextTriggerTime = nextTriggerTime, // 先存一个然后进行计算

            hour24 = h24,
            isEnabled = true, // 默认为打开状态

            repeatText = repeatText,
            repeatData = repeatDataString,

            ringtoneName = AddAlarmClockManager.tempRingtoneName,
            ringtoneFileName = AddAlarmClockManager.tempRingtoneFileName,
            ringtoneId = AddAlarmClockManager.tempRingtoneId,

            vibrationName = AddAlarmClockManager.tempVibrationName,
            vibrationId = AddAlarmClockManager.tempVibrationId,

            ringDuration = currentRingMinute,
            snoozeInterval = intervalRingValue,
            snoozeCount = repeatRingValue,
            computeSnoozeCount = repeatRingValue,

            label = alarmName
        )
        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                // 没有权限，请求权限
                pendingAlarm = newAlarm
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        saveAlarm(newAlarm)
    }

    private fun saveAlarm(alarm: AlarmEntity) {
        // 先检查是否有必要的权限
        if (!PermissionUtils.hasAllPermissions(this)) {
            // 没有权限，引导用户去设置
            PermissionUtils.checkAndRequestPermissions(this) { allGranted ->
                if (allGranted) {
                    // 用户授权后，重新保存
                    saveAlarm(alarm)
                } else {
                    // 用户未授权，但仍尝试保存（数据会保存，但闹钟可能无法准时触发）
                    doSaveAlarm(alarm)
                }
            }
            return
        }

        // 权限正常，直接保存
        doSaveAlarm(alarm)
    }

    private fun doSaveAlarm(alarm: AlarmEntity) {
        // 存储到数据库
        lifecycleScope.launch {
            val rowId = viewModel.insertAlarm(alarm)

            if (rowId != -1L) {
                val updatedAlarm = alarm.copy(id = rowId.toInt())

                val triggerTime = AlarmManagerUtils.calculateNextTriggerTime(updatedAlarm)
                val (d, h, m) = AlarmManagerUtils.getRemainingTime(triggerTime)

                // 同时把闹钟交给系统管理器
                AlarmManagerUtils.setAlarm(this@AddAlarmActivity, updatedAlarm, triggerTime)

                val time = StringUtils.formatRemainingTime(d, h, m)
                Toast.makeText(applicationContext, time, Toast.LENGTH_SHORT).show()

                // 返回页面
                finish()
            } else {
                Toast.makeText(applicationContext, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 删除闹钟
     */
    private fun delete(id: Int) {
        lifecycleScope.launch {
            val rowId = viewModel.delete(id)
            if (rowId == 1) {
                Toast.makeText(applicationContext, "删除成功", Toast.LENGTH_SHORT).show()
                // 同时也要取消闹钟
                AlarmManagerUtils.cancelAlarm(this@AddAlarmActivity,id)

                finish()
            } else {
                Toast.makeText(applicationContext, "删除失败", Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        binding.ringToneName.text = AddAlarmClockManager.tempRingtoneName
    }

}