package com.example.autoclicker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autoclicker.service.FloatingWindowService
import com.example.autoclicker.ui.screens.HomeScreen
import com.example.autoclicker.ui.theme.AutoClickerTheme
import com.example.autoclicker.viewmodel.ClickerViewModel

class MainActivity : ComponentActivity() {

    private lateinit var appViewModel: ClickerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoClickerTheme {
                appViewModel = viewModel()
                val hasOverlayPermission by appViewModel.hasOverlayPermission.collectAsState()
                val isAccessibilityEnabled by appViewModel.isAccessibilityEnabled.collectAsState()

                // 监听生命周期，从设置返回时刷新权限状态
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            appViewModel.updatePermissionState(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                HomeScreen(
                    viewModel = appViewModel,
                    hasOverlayPermission = hasOverlayPermission,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestAccessibilityPermission = { openAccessibilitySettings() },
                    onStartFloatingWindow = { startFloatingWindow(appViewModel) }
                )
            }
        }
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
