package com.example.autoclicker.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.example.autoclicker.service.AutoClickService

/**
 * 权限检查工具类
 */
object PermissionUtils {

    /**
     * 检查悬浮窗权限是否已授予
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * 检查无障碍服务是否已启用
     *
     * 同时支持两种组件名格式：
     * - 完整格式: pkg/完整类名            (com.example.autoclicker/com.example.autoclicker.service.AutoClickService)
     * - 短格式:   pkg/相对类名(带前导点) (com.example.autoclicker/.service.AutoClickService)
     * 另外若服务实例当前已存活（已 onServiceConnected），也视为已启用。
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // 服务实例存活即视为已启用（运行时真实状态）
        if (AutoClickService.isActive()) return true

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val pkg = context.packageName
        val fullClassName = AutoClickService::class.java.name       // com.example.autoclicker.service.AutoClickService
        val shortClassName = fullClassName.removePrefix(pkg)        // .service.AutoClickService

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        for (raw in colonSplitter) {
            val component = raw.trim()
            if (component.isEmpty()) continue
            // 兼容完整格式与短格式（带前导点）
            val expectedFull = "$pkg/$fullClassName"
            val expectedShort = "$pkg/$shortClassName"
            if (component.equals(expectedFull, ignoreCase = true) ||
                component.equals(expectedShort, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 检查通知权限（Android 13+）
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
