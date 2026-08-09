package com.example.autoclicker.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.autoclicker.service.AutoClickService

/**
 * 权限检查工具类
 */
object PermissionUtils {

    /**
     * 检查悬浮窗权限是否已授予
     *
     * 优先使用系统 API [Settings.canDrawOverlays]；
     * 但 MIUI / EMUI / ColorOS 等大量国产 ROM 存在已知框架 bug：
     * 用户明明已在系统设置里授予「显示在其他应用上层」，该 API 仍返回 false。
     * 此时再用「真实添加 1px 悬浮窗」做最终判定——这是业界最可靠的二次确认方式。
     */
    fun hasOverlayPermission(context: Context): Boolean {
        // 系统 API 通过则直接放行
        if (Settings.canDrawOverlays(context)) return true
        // ROM 误报时，用真实添加悬浮窗来验证
        return tryDrawTestOverlay(context)
    }

    /**
     * 悬浮窗权限详细诊断（供调试面板展示）
     */
    data class OverlayDebug(val apiCanDraw: Boolean, val drawTestOk: Boolean)
    fun overlayDebug(context: Context): OverlayDebug {
        val api = Settings.canDrawOverlays(context)
        val draw = tryDrawTestOverlay(context)
        return OverlayDebug(api, draw)
    }

    /**
     * 尝试真实添加并立即移除一个 1px 悬浮窗，用于确认是否真的拥有悬浮窗权限。
     */
    private fun tryDrawTestOverlay(context: Context): Boolean {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = View(context)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                1, 1, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 0
            wm.addView(view, params)
            wm.removeView(view)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查无障碍服务是否已启用
     *
     * 判定策略（任一命中即视为已启用）：
     * 1. 服务实例当前存活（已 onServiceConnected）——运行时最真实的状态；
     * 2. 系统设置里的启用服务列表中包含本服务，兼容多种组件名格式：
     *    - 完整格式:  pkg/完整类名
     *    - 短格式:    pkg/相对类名(带前导点)
     *    - 大小写变体 / ROM 自定义分隔符
     *    - 仅凭简单类名后缀匹配（类名唯一，足够精确）
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
        val simpleName = AutoClickService::class.java.simpleName    // AutoClickService

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        for (raw in colonSplitter) {
            val component = raw.trim()
            if (component.isEmpty()) continue

            val expectedFull = "$pkg/$fullClassName"
            val expectedShort = "$pkg/$shortClassName"

            if (component.equals(expectedFull, ignoreCase = true) ||
                component.equals(expectedShort, ignoreCase = true) ||
                component.equals(fullClassName, ignoreCase = true) ||
                component.endsWith("/$simpleName", ignoreCase = true) ||
                component.endsWith(".$simpleName", ignoreCase = true) ||
                component.endsWith(simpleName, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /** 无障碍服务实例当前是否存活（运行时最真实状态） */
    fun isAccessibilityServiceActive(): Boolean = AutoClickService.isActive()

    /** 系统设置里「已启用的无障碍服务」原始字符串（调试用） */
    fun getEnabledAccessibilityServicesRaw(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: "(null)"
    }

    /**
     * 获取本应用的安装来源包名（调试展示用）
     */
    fun getInstallSource(context: Context): String {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).initiatingPackageName ?: "(null)"
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg) ?: "(null)"
            }
        } catch (e: Exception) {
            "(unknown:${e.message})"
        }
    }

    /**
     * 是否「非可信安装源」（即 Android 12+ 受限设置可能拦截无障碍开关）
     *
     * Android 12 起，系统对「未知来源」安装的 APK 默认禁止开启无障碍等敏感权限，
     * 并在设置里报「未知来源应用，系统已拒绝此应用获取敏感权限」。
     *
     * 只有经可信安装源安装才不拦截：
     *  - adb（com.android.shell）
     *  - Google Play（com.android.vending）等预授权商店
     * 其余（系统安装器 / 浏览器下载 / 文件管理器侧载等）均视为未知来源 → 可能受限。
     */
    fun fromUnknownSource(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(pkg).initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }
            val trusted = setOf("com.android.shell", "com.android.vending")
            installer.isNullOrEmpty() || installer !in trusted
        } catch (e: Exception) {
            true // 无法判定时保守提示
        }
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
