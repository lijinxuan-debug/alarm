package com.example.alarm_jinxuan.view.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alarm_jinxuan.databinding.ActivityPermissionSettingBinding
import com.example.alarm_jinxuan.model.PermissionItem
import com.example.alarm_jinxuan.utils.PermissionUtils
import com.example.alarm_jinxuan.utils.ToastUtils
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.Permission
import com.hjq.permissions.OnPermissionCallback

class PermissionSettingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionSettingBinding
    private lateinit var adapter: PermissionAdapter
    private val dataList = mutableListOf<PermissionItem>()
    private var showBackButton = true

    companion object {
        const val PERMISSION_NOTIFICATION = 0
        const val PERMISSION_IGNORE_BATTERY = 1
        const val PERMISSION_ALERT_WINDOW = 2
        const val PERMISSION_AUTO_START = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPermissionSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取是否显示返回按钮的参数
        showBackButton = intent.getBooleanExtra("show_back_button", true)

        // 根据参数决定是否显示返回按钮
        if (showBackButton) {
            binding.arrowManager.visibility = View.VISIBLE
            binding.arrowManager.setOnClickListener {
                finish()
            }
        } else {
            binding.arrowManager.visibility = View.GONE
        }

        // 隐藏标题文字
        binding.title.visibility = View.GONE

        // 设置完成按钮
        binding.btnComplete.setOnClickListener {
            if (!showBackButton) {
                // 首次启动，跳转到主界面
                val intent = Intent(this, com.example.alarm_jinxuan.MainActivity::class.java)
                startActivity(intent)
            }
            finish()
        }

        // 初始化权限列表
        initData()
    }

    private fun initData() {
        dataList.clear()
        dataList.add(
            PermissionItem(
                "通知权限",
                "开启后可及时接收应用消息推送",
                checkNotificationPermission(),
                PERMISSION_NOTIFICATION
            )
        )
        dataList.add(
            PermissionItem(
                "忽略电池优化",
                "避免应用被系统后台清理",
                checkIgnoreBatteryOptimizations(),
                PERMISSION_IGNORE_BATTERY
            )
        )
        dataList.add(
            PermissionItem(
                "悬浮窗权限",
                "允许应用在其他应用上方显示提醒",
                XXPermissions.isGranted(this, Permission.SYSTEM_ALERT_WINDOW),
                PERMISSION_ALERT_WINDOW
            )
        )
        dataList.add(
            PermissionItem(
                "自启动权限",
                "允许应用在开机或后台自动运行",
                false, // 自启动权限无法准确检测，默认为未授权
                PERMISSION_AUTO_START
            )
        )

        // 初始化适配器
        adapter = PermissionAdapter(dataList) { item, _ ->
            // 自启动权限始终允许点击跳转到设置页面
            if (item.permissionType == PERMISSION_AUTO_START) {
                requestPermission(item)
            } else if (!item.isGranted) {
                requestPermission(item)
            } else {
                ToastUtils.showShort(this, "该权限已授予")
            }
        }

        // 绑定 RecyclerView
        binding.rvPermissionList.apply {
            layoutManager = LinearLayoutManager(this@PermissionSettingActivity)
            this.adapter = this@PermissionSettingActivity.adapter
        }
    }

    private fun requestPermission(item: PermissionItem) {
        when (item.permissionType) {
            PERMISSION_NOTIFICATION -> requestNotificationPermission()
            PERMISSION_IGNORE_BATTERY -> requestIgnoreBatteryOptimizations()
            PERMISSION_ALERT_WINDOW -> requestAlertWindowPermission()
            PERMISSION_AUTO_START -> requestAutoStartPermission()
        }
    }

    /**
     * 申请通知权限
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            XXPermissions.with(this)
                .permission(Permission.POST_NOTIFICATIONS)
                .request(object : OnPermissionCallback {
                    override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                        updatePermissionStatus(PERMISSION_NOTIFICATION, true)
                        ToastUtils.showShort(this@PermissionSettingActivity, "通知权限已授予")
                    }

                    override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                        if (never) {
                            ToastUtils.showLong(this@PermissionSettingActivity, "请在系统设置中开启通知权限")
                            XXPermissions.startPermissionActivity(this@PermissionSettingActivity, permissions)
                        } else {
                            updatePermissionStatus(PERMISSION_NOTIFICATION, false)
                            ToastUtils.showShort(this@PermissionSettingActivity, "通知权限被拒绝")
                        }
                    }
                })
        } else {
            // Android 13 以下无需申请通知权限
            updatePermissionStatus(PERMISSION_NOTIFICATION, true)
        }
    }

    /**
     * 申请忽略电池优化
     */
    private fun requestIgnoreBatteryOptimizations() {
        XXPermissions.with(this)
            .permission(Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                    updatePermissionStatus(PERMISSION_IGNORE_BATTERY, true)
                    ToastUtils.showShort(this@PermissionSettingActivity, "已忽略电池优化")
                }

                override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                    if (never) {
                        ToastUtils.showLong(this@PermissionSettingActivity, "请在系统设置中忽略电池优化")
                        XXPermissions.startPermissionActivity(this@PermissionSettingActivity, permissions)
                    } else {
                        updatePermissionStatus(PERMISSION_IGNORE_BATTERY, false)
                        ToastUtils.showShort(this@PermissionSettingActivity, "忽略电池优化被拒绝")
                    }
                }
            })
    }

    /**
     * 申请悬浮窗权限
     */
    private fun requestAlertWindowPermission() {
        XXPermissions.with(this)
            .permission(Permission.SYSTEM_ALERT_WINDOW)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                    updatePermissionStatus(PERMISSION_ALERT_WINDOW, true)
                    ToastUtils.showShort(this@PermissionSettingActivity, "悬浮窗权限已授予")
                }

                override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                    if (never) {
                        ToastUtils.showLong(this@PermissionSettingActivity, "请在系统设置中开启悬浮窗权限")
                        XXPermissions.startPermissionActivity(this@PermissionSettingActivity, permissions)
                    } else {
                        updatePermissionStatus(PERMISSION_ALERT_WINDOW, false)
                        ToastUtils.showShort(this@PermissionSettingActivity, "悬浮窗权限被拒绝")
                    }
                }
            })
    }

    /**
     * 申请自启动权限（使用荣耀/华为专用方法）
     */
    private fun requestAutoStartPermission() {
        // 使用荣耀/华为专用的电源管理页面跳转方法
        try {
            PermissionUtils.openHonorPowerDetail(this)
            ToastUtils.showLong(this, "请在跳转页面中开启自启动权限")
        } catch (_: Exception) {
            // 降级方案：跳转到应用详情页
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                ToastUtils.showLong(this, "请在应用设置中开启自启动权限")
            } catch (_: Exception) {
                ToastUtils.showShort(this, "无法打开设置页面")
            }
        }
    }

    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            XXPermissions.isGranted(this, Permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    /**
     * 检查忽略电池优化
     */
    private fun checkIgnoreBatteryOptimizations(): Boolean {
        return XXPermissions.isGranted(this, Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    }

    /**
     * 检查自启动权限（简单检查，实际可能不准确）
     */
    private fun checkAutoStartPermission(): Boolean {
        // 自启动权限难以直接检测，始终返回 false
        // 这样用户可以随时点击跳转到设置页面去开启
        return false
    }

    /**
     * 更新权限状态
     */
    private fun updatePermissionStatus(permissionType: Int, isGranted: Boolean) {
        val index = dataList.indexOfFirst { it.permissionType == permissionType }
        if (index >= 0) {
            dataList[index].isGranted = isGranted
            adapter.notifyItemChanged(index)
        }
    }

    /**
     * 检查是否所有权限都已授予
     */
    private fun areAllPermissionsGranted(): Boolean {
        return dataList.all { it.isGranted }
    }

    /**
     * 从系统设置返回时刷新权限状态
     */
    override fun onResume() {
        super.onResume()
        // 刷新所有权限状态
        dataList.forEachIndexed { index, item ->
            val newStatus = when (item.permissionType) {
                PERMISSION_NOTIFICATION -> checkNotificationPermission()
                PERMISSION_IGNORE_BATTERY -> checkIgnoreBatteryOptimizations()
                PERMISSION_ALERT_WINDOW -> XXPermissions.isGranted(this, Permission.SYSTEM_ALERT_WINDOW)
                PERMISSION_AUTO_START -> checkAutoStartPermission()
                else -> item.isGranted
            }
            if (item.isGranted != newStatus) {
                dataList[index].isGranted = newStatus
                adapter.notifyItemChanged(index)
            }
        }
    }
}