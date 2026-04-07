package com.example.alarm_jinxuan

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.Permission

/**
 * 启动页：根据权限状态决定进入权限设置界面还是主界面
 */
class LauncherActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "AppPrefs"
        const val KEY_PERMISSION_SHOWN = "permission_shown"

        /**
         * 标记权限设置页面已显示
         */
        fun markPermissionShown(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PERMISSION_SHOWN, true).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 延迟 300ms 后检查权限状态（让用户看到启动效果）
        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionsAndNavigate()
        }, 300)
    }

    private fun checkPermissionsAndNavigate() {
        // 获取 SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean(KEY_PERMISSION_SHOWN, false)

        val intent: Intent
        if (!firstLaunch) {
            // 首次启动，进入权限设置页面（不显示返回按钮）
            intent = Intent(this, com.example.alarm_jinxuan.view.permission.PermissionSettingActivity::class.java)
            intent.putExtra("show_back_button", false)

            // 标记已启动过
            prefs.edit().putBoolean(KEY_PERMISSION_SHOWN, true).apply()
        } else {
            // 后续启动，直接进入主界面
            intent = Intent(this, MainActivity::class.java)
        }

        startActivity(intent)
        finish()
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            XXPermissions.isGranted(this, Permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    private fun checkIgnoreBatteryOptimizations(): Boolean {
        return XXPermissions.isGranted(this, Permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    }
}