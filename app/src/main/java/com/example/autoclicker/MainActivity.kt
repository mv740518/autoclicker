package com.example.autoclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autoclicker.model.ClickStep
import com.example.autoclicker.service.FloatingWindowService
import com.example.autoclicker.service.TapCaptureService
import com.example.autoclicker.ui.screens.HomeScreen
import com.example.autoclicker.ui.theme.AutoClickerTheme
import com.example.autoclicker.viewmodel.ClickerViewModel

class MainActivity : ComponentActivity() {

    private lateinit var appViewModel: ClickerViewModel

    /** 选点广播接收器：接收 [TapCaptureService] 回传的坐标 */
    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TapCaptureService.ACTION_TAP_CAPTURED) return
            val x = intent.getIntExtra(TapCaptureService.EXTRA_X, 0)
            val y = intent.getIntExtra(TapCaptureService.EXTRA_Y, 0)
            val stepId = intent.getLongExtra(TapCaptureService.EXTRA_STEP_ID, -1L)
            if (!::appViewModel.isInitialized) return
            if (stepId >= 0L) {
                appViewModel.updateStepPosition(stepId, x.toFloat(), y.toFloat())
            } else {
                appViewModel.addStep(
                    ClickStep(x = x.toFloat(), y = y.toFloat(), waitAfterMs = 1000L)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册选点广播（仅本应用接收）
        val filter = IntentFilter(TapCaptureService.ACTION_TAP_CAPTURED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tapReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tapReceiver, filter)
        }
        setContent {
            AutoClickerTheme {
                appViewModel = viewModel()
                val hasOverlayPermission by appViewModel.hasOverlayPermission.collectAsState()
                val isAccessibilityEnabled by appViewModel.isAccessibilityEnabled.collectAsState()
                val debug by appViewModel.debug.collectAsState()

                HomeScreen(
                    viewModel = appViewModel,
                    hasOverlayPermission = hasOverlayPermission,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    debug = debug,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestAccessibilityPermission = { openAccessibilitySettings() },
                    onRefreshPermissions = { appViewModel.updatePermissionState(this) },
                    onStartFloatingWindow = { startFloatingWindow(appViewModel) },
                    onCaptureTap = { startTapCapture(it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appViewModel.isInitialized) {
            appViewModel.updatePermissionState(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(tapReceiver) } catch (_: Exception) { }
    }

    /**
     * 启动「点哪选哪」透明选点覆盖层
     * @param stepId 非空表示更新已有步骤坐标；null 表示选取后新增一步
     */
    private fun startTapCapture(stepId: Long?) {
        val intent = Intent(this, TapCaptureService::class.java).apply {
            putExtra(TapCaptureService.EXTRA_STEP_ID, stepId ?: -1L)
        }
        startService(intent)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /**
     * 启动悬浮窗服务
     */
    private fun startFloatingWindow(viewModel: ClickerViewModel) {
        val task = viewModel.getClickTask()

        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra(FloatingWindowService.EXTRA_STEPS, ArrayList(task.steps))
            putExtra(FloatingWindowService.EXTRA_LOOP_COUNT, task.loopCount)
            putExtra(FloatingWindowService.EXTRA_IS_INFINITE, task.isInfinite)
            putExtra(FloatingWindowService.EXTRA_RANDOM_OFFSET, task.randomOffset)
        }
        startForegroundService(intent)
    }
}
