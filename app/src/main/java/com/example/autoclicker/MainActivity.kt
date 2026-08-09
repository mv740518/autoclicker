package com.example.autoclicker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
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

    override fun onResume() {
        super.onResume()
        if (::appViewModel.isInitialized) {
            appViewModel.updatePermissionState(this)
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
