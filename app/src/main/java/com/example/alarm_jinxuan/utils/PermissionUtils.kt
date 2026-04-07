package com.example.alarm_jinxuan.utils

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import java.util.Locale

object PermissionUtils {

    /**
     * 检查是否需要请求权限，如果需要则显示对话框引导用户
     * @return true 表示权限都正常，false 表示缺少权限
     */
    fun checkAndRequestPermissions(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val missingPermissions = mutableListOf<String>()

        // 1. 检查通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (!notificationManager.areNotificationsEnabled()) {
                missingPermissions.add("通知权限")
            }
        }

        // 2. 检查闹钟和提醒权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                missingPermissions.add("闹钟和提醒权限")
            }
        }

        // 3. 检查电池优化
        if (!isIgnoringBatteryOptimizations(context)) {
            missingPermissions.add("关闭电池优化")
        }

        // 如果所有权限都正常
        if (missingPermissions.isEmpty()) {
            onComplete?.invoke(true)
            return
        }

        // 组装未打开的权限
        val basePermissions = missingPermissions.joinToString("\n") { "• $it" }

        val message = "为了确保闹钟能准时响起，请开启以下权限：\n\n" +
                "$basePermissions\n\n" +
                "--- --- --- --- --- ---\n" +
                "另外，为了提升您的体验，您还需在点击“去设置”后，在跳转到的页面中手动打开“自启动”与“后台活动”开关。"

        // 显示引导对话框
        AlertDialog.Builder(context)
            .setTitle("需要开启权限")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                openBatterySettings(context)
                onComplete?.invoke(false)
            }
            .setNegativeButton("稍后") { _, _ ->
                onComplete?.invoke(false)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 检查是否忽略了电池优化
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * 跳转到系统“忽略电池优化”设置列表页面
     */
    fun openBatterySettings(context: Context) {
        val intent = Intent().apply {
            // 修改 Action 为跳转到电池优化列表
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 兜底方案：如果极个别魔改系统找不到该页面，跳转到应用详情页
            val detailIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(detailIntent)
        }
    }

    /**
     * 精准跳转到各厂商自启动/应用耗电管理详情页
     */
    fun openAutoStartSetting(context: Context) {
        val brand = Build.BRAND.lowercase(Locale.getDefault())
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        val packageName = context.packageName
        val intent = Intent()

        try {
            when {
                // 小米 / 红米
                brand.contains("xiaomi") || manufacturer.contains("xiaomi") -> {
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    intent.putExtra("extra_pkgname", packageName)
                }

                // 荣耀 (独立后的新系统路径)
                brand.contains("honor") || manufacturer.contains("honor") -> {
                    intent.component = ComponentName(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.power.ui.DetailOfSoftConsumptionActivity"
                    )
                    intent.putExtra("pkg_name", packageName)
                }

                // 华为
                brand.contains("huawei") || manufacturer.contains("huawei") -> {
                    // 华为路径较多，ProtectActivity 是最常用的自启动/受保护应用路径
                    intent.component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }

                // OPPO
                brand.contains("oppo") || brand.contains("realme") -> {
                    intent.component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                }

                // vivo
                brand.contains("vivo") || brand.contains("iqoo") -> {
                    intent.component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }

                // 默认兜底：跳转到系统应用详情页
                else -> {
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.fromParts("package", packageName, null)
                }
            }

            // 统一添加参数和 Flag
            intent.putExtra("package_name", packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

        } catch (e: Exception) {
            // 第一层降级：尝试跳转到通用的电池使用情况汇总页
            try {
                val batteryIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                batteryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(batteryIntent)
            } catch (e2: Exception) {
                // 第二层降级：跳转到最稳妥的应用详情页（XXPermissions 也是这么做的）
                val detailIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(detailIntent)
            }
        }
    }

    /**
     * 检查闹钟权限是否正常（不弹窗，仅检查）
     */
    fun hasAllPermissions(context: Context): Boolean {
        // 检查通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (!notificationManager.areNotificationsEnabled()) {
                return false
            }
        }

        // 检查闹钟和提醒权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                return false
            }
        }

        // 检查电池优化
        if (!isIgnoringBatteryOptimizations(context)) {
            return false
        }

        return true
    }
}