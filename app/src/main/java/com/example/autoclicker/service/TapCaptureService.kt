package com.example.autoclicker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 透明全屏选点服务 —— 实现「在屏幕上点哪选哪」
 *
 * 启动方式：
 * ```kotlin
 * startService(Intent(context, TapCaptureService::class.java).apply {
 *     putExtra(EXTRA_STEP_ID, stepId)   // >=0 更新已有步骤；省略/负表示新增
 * })
 * ```
 *
 * 采集完成后通过广播 [ACTION_TAP_CAPTURED] 回传坐标 (x, y) 与 stepId，
 * 由 MainActivity 的广播接收器写入 ViewModel。
 *
 * 交互说明：
 * - 半透明遮罩上点任意空白位置 → 记录该屏幕坐标为目标点，自动关闭；
 * - 「前往其他应用」→ 回到桌面，遮罩仍置顶，便于在目标 App 上直接点选；
 * - 「取消」→ 不记录，直接关闭。
 */
class TapCaptureService : Service() {

    companion object {
        const val ACTION_TAP_CAPTURED = "com.example.autoclicker.action.TAP_CAPTURED"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_STEP_ID = "stepId"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stepId = intent?.getLongExtra(EXTRA_STEP_ID, -1L) ?: -1L
        try {
            showOverlay(stepId)
        } catch (e: Exception) {
            // 多半是尚未授予悬浮窗权限，直接结束，交由 UI 提示用户
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showOverlay(stepId: Long) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val captureLayout = CaptureLayout(this) { x, y -> capture(x, y, stepId) }

        // 顶部横幅 + 操作按钮
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(20, 20, 20, 20)
            // 让横幅区域自身消费触摸，避免点到横幅时误触发选点（按钮仍可正常点击）
            isClickable = true
        }

        val banner = TextView(this).apply {
            text = "点哪选哪：点击屏幕任意位置记录为坐标\n（点上方按钮可取消，或先去目标 App 再点）"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        topBar.addView(banner)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancelBtn = Button(this).apply {
            text = "取消"
            setOnClickListener {
                removeOverlay()
                stopSelf()
            }
        }
        val goHomeBtn = Button(this).apply {
            text = "前往其他应用"
            setOnClickListener {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        }
        btnRow.addView(cancelBtn)
        btnRow.addView(goHomeBtn)
        topBar.addView(btnRow)

        val topBarLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        captureLayout.addView(topBar, topBarLp)

        wm.addView(captureLayout, params)
        overlayView = captureLayout
    }

    /**
     * 采集到一次点击：移除遮罩、回传坐标、停止服务
     */
    private fun capture(x: Float, y: Float, stepId: Long) {
        removeOverlay()
        val intent = Intent(ACTION_TAP_CAPTURED).apply {
            putExtra(EXTRA_X, x.roundToInt())
            putExtra(EXTRA_Y, y.roundToInt())
            putExtra(EXTRA_STEP_ID, stepId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        stopSelf()
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) { }
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    /**
     * 全屏捕获布局：
     * - 不拦截触摸，让子 View（按钮）正常响应点击；
     * - 空白处的触摸落入 [onTouchEvent] → 采集坐标。
     */
    private class CaptureLayout(
        context: Context,
        private val onCapture: (Float, Float) -> Unit
    ) : FrameLayout(context) {

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                onCapture(ev.rawX, ev.rawY)
                return true
            }
            return super.onTouchEvent(ev)
        }
    }
}
