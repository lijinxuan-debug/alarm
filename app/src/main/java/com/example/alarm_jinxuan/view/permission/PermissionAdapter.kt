package com.example.alarm_jinxuan.view.permission

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.alarm_jinxuan.databinding.ItemPermissionBinding
import com.example.alarm_jinxuan.model.PermissionItem

class PermissionAdapter(
    private val items: List<PermissionItem>,
    private val onActionClick: (PermissionItem, Int) -> Unit // 点击回调，传出对象和索引
) : RecyclerView.Adapter<PermissionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPermissionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPermissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvTitle.text = item.title
        holder.binding.tvDesc.text = item.desc

        // 检查是否是自启动权限（PERMISSION_AUTO_START = 3）
        val isAutoStartPermission = item.permissionType == com.example.alarm_jinxuan.view.permission.PermissionSettingActivity.PERMISSION_AUTO_START

        // 如果是自启动权限，显示提示文字
        if (isAutoStartPermission) {
            holder.binding.tvHint.visibility = android.view.View.VISIBLE
        } else {
            holder.binding.tvHint.visibility = android.view.View.GONE
        }

        if (item.isGranted) {
            // 已授权状态：绿色文字，无背景
            holder.binding.statusBadge.text = "已授权"
            holder.binding.statusBadge.setTextColor(Color.parseColor("#4CAF50"))
            holder.binding.statusBadge.setBackgroundResource(0)
            holder.binding.statusBadge.setPadding(0, 0, 0, 0)
        } else {
            // 未授权状态：蓝色文字，带胶囊背景
            holder.binding.statusBadge.text = "去设置"
            holder.binding.statusBadge.setTextColor(Color.parseColor("#007AFF"))
            holder.binding.statusBadge.setBackgroundResource(com.example.alarm_jinxuan.R.drawable.bg_status_capsule)
            // 保持与 XML 中一致的 padding：14dp 水平，6dp 垂直
            val density = holder.binding.root.context.resources.displayMetrics.density
            val paddingH = (14 * density + 0.5f).toInt()
            val paddingV = (6 * density + 0.5f).toInt()
            holder.binding.statusBadge.setPadding(paddingH, paddingV, paddingH, paddingV)
        }

        // 点击"去设置"或整行触发逻辑
        holder.binding.root.setOnClickListener {
            onActionClick(item, position)
        }
    }

    override fun getItemCount() = items.size
}