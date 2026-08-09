package com.example.autoclicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.autoclicker.model.ClickStep
import com.example.autoclicker.model.ClickTask

/**
 * 悬浮窗前台服务
 *
 * 职责：
 * - 以前台服务方式运行，确保悬浮窗不被系统回收
 * - 创建和管理 [FloatingWindowManager] 实例
 * - 在服务销毁时清理所有悬浮窗资源
 *
 * 接收参数（通过 Intent extras）：
 * - "steps": ArrayList<ClickStep> — 动作步骤序列
 * - "loopCount": Int — 循环次数
 * - "isInfinite": Boolean — 是否无限循环
 * - "randomOffset": Int — 随机偏移
 */
class FloatingWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "auto_clicker_channel"
        private const val NOTIFICATION_ID = 1

        const val EXTRA_STEPS = "steps"
        const val EXTRA_LOOP_COUNT = "loopCount"
        const val EXTRA_IS_INFINITE = "isInfinite"
        const val EXTRA_RANDOM_OFFSET = "randomOffset"
    }

    private var floatingWindowManager: FloatingWindowManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val steps = intent?.getSerializableExtra(EXTRA_STEPS) as? ArrayList<ClickStep>
            ?: arrayListOf()
        val loopCount = intent?.getIntExtra(EXTRA_LOOP_COUNT, 1) ?: 1
        val isInfinite = intent?.getBooleanExtra(EXTRA_IS_INFINITE, true) ?: true
        val randomOffset = intent?.getIntExtra(EXTRA_RANDOM_OFFSET, 0) ?: 0

        val task = ClickTask(
            steps = steps.toList(),
            loopCount = loopCount,
            isInfinite = isInfinite,
            randomOffset = randomOffset
        )

        // 清理旧悬浮窗
        floatingWindowManager?.hideAll()

        floatingWindowManager = FloatingWindowManager(
            context = this,
            task = task
        ).also { manager ->
            manager.showTargets()
            manager.showControlPanel()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingWindowManager?.hideAll()
        floatingWindowManager = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // region — 通知

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Clicker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗自动点击服务"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Clicker 运行中")
            .setContentText("拖拽准星定位，点击开始/停止")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // endregion
}
