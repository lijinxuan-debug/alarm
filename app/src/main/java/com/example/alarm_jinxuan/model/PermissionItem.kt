package com.example.alarm_jinxuan.model

data class PermissionItem(
    val title: String,      // 标题
    val desc: String,       // 描述
    var isGranted: Boolean, // 授权状态：true 已授权, false 未授权
    val permissionType: Int // 权限类型：0=通知, 1=忽略电池优化, 2=悬浮窗, 3=自启动
)