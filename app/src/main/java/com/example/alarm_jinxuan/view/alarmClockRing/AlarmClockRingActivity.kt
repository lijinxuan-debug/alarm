package com.example.alarm_jinxuan.view.alarmClockRing

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.alarm_jinxuan.R
import com.example.alarm_jinxuan.databinding.ActivityAlarmClockRingBinding
import com.example.alarm_jinxuan.model.AddAlarmClockManager
import com.example.alarm_jinxuan.model.RingtoneOption
import com.example.alarm_jinxuan.utils.MediaUtils
import com.example.alarm_jinxuan.utils.VibrationUtils
import com.example.alarm_jinxuan.view.vibration.VibrationActivity

class AlarmClockRingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmClockRingBinding

    private lateinit var ringtoneOptionAdapter: RingtoneAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAlarmClockRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 说明上次选择的是推荐铃声
        binding.rbSelect.isChecked = AddAlarmClockManager.tempRingtoneId == -1

        val ringtoneList = generateRingtoneList()

        ringtoneOptionAdapter = RingtoneAdapter(ringtoneList) { onItemSelected ->
            // 播放音乐前也需要振动
            VibrationUtils.vibrate(this, AddAlarmClockManager.currentVibrationPattern)
            // 播放音乐
            MediaUtils.startRingtonePreview(onItemSelected.resId,this,null)

            binding.rbSelect.isChecked = false

            AddAlarmClockManager.tempRingtoneId = onItemSelected.id
            AddAlarmClockManager.tempRingtoneName = onItemSelected.name
            AddAlarmClockManager.tempRingtoneFileName = onItemSelected.fileName
        }

        binding.listRingtone.apply {
            adapter = ringtoneOptionAdapter
        }

        // 推荐铃声
        binding.recommendRingContainer.setOnClickListener {
            binding.rbSelect.isChecked = true
            // 取消经典铃声的选择即可
            ringtoneOptionAdapter.deselectAll()

            // 这里同理振动
            VibrationUtils.vibrate(this, AddAlarmClockManager.currentVibrationPattern)
            MediaUtils.startRingtonePreview(R.raw.alarm_morning_light,this,null)

            AddAlarmClockManager.tempRingtoneId = -1 // 给推荐铃声定一个特殊 ID
            AddAlarmClockManager.tempRingtoneName = "Morning Light"
            AddAlarmClockManager.tempRingtoneFileName = "alarm_morning_light"
        }

        binding.itemVibration.setOnClickListener {
            val intent = Intent(this, VibrationActivity::class.java)
            startActivity(intent)
        }

        binding.back.setOnClickListener {
            // 停止振动
            VibrationUtils.stop(this)
            finish()
        }

    }



    private fun generateRingtoneList(): List<RingtoneOption> {
        val list = mutableListOf<RingtoneOption>()

        // 获取 raw 文件夹下所有的资源 ID
        val fields = R.raw::class.java.fields

        var idCounter = 0
        for (field in fields) {
            val fileName = field.name

            if (fileName.equals("alarm_morning_light")) {
                continue
            }

            if (fileName.startsWith("alarm_")) {
                try {
                    val resId = field.getInt(null)

                    // "alarm_morning_light" -> "Morning Light"
                    val displayName = fileName
                        .replace("alarm_", "")
                        .replace("_", " ")
                        .replaceFirstChar { it.uppercase() }

                    list.add(RingtoneOption(idCounter++, displayName, resId, fileName))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 升序排列
        return list.sortedBy { it.name }
    }

    override fun onResume() {
        super.onResume()
        binding.tvVibration.text = AddAlarmClockManager.tempVibrationName
    }

    override fun onStop() {
        super.onStop()
        // 不让震动
        MediaUtils.stop(this)
    }

}