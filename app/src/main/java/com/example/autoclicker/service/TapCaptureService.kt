package com.example.autoclicker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.widget.Toast
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 「点哪选哪」选点服务（v1.7 重构）
 *
 * 交互流程（贴合用户习惯：选 App → 开启选点 → 点一下出黄色光标 → 保存 → 可继续添加）：
 *
 *  1. 由 MainActivity 通过 [EXTRA_STEP_ID] 启动本服务（>=0 表示重选已有步骤坐标；
 *     省略/负表示新增一步）。服务先显示一个常驻的悬浮「选点」按钮（即「开启」）。
 *  2. 用户切到目标 App 后，点悬浮「选点」按钮 → 进入全屏透明选点遮罩。
 *  3. 在屏幕上任意位置点一下 → 该处出现一个**黄色十字光标**作为已选标识，
 *     同时底部出现「保存 / 重选」按钮，顶部提示文案更新。
 *  4. 点「保存」→ 回传坐标（广播 [ACTION_TAP_CAPTURED]）并回到悬浮「选点」按钮，
 *     可继续添加下一个位置；点「重选」→ 清除光标重新选；点「关闭」→ 回到悬浮按钮。
 *
 * 坐标使用屏幕绝对坐标（MotionEvent.rawX/rawY），与悬浮窗执行时的点击坐标一致。
 */
class TapCaptureService : Service() {

    companion object {
        const val ACTION_TAP_CAPTURED = "com.example.autoclicker.action.TAP_CAPTURED"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_STEP_ID = "stepId"
    }

    private var stepId: Long = -1L
    private val isUpdate: Boolean get() = stepId >= 0L

    private var wm: WindowManager? = null
    private var triggerView: View? = null
    private var captureView: FrameLayout? = null
    private var cursorView: CursorView? = null
    private var confirmBar: LinearLayout? = null
    private var banner: TextView? = null

    private var selectedX = 0
    private var selectedY = 0
    private var hasSelection = false

    private val cursorSizePx by lazy {
        (56 * resources.displayMetrics.density).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stepId = intent?.getLongExtra(EXTRA_STEP_ID, -1L) ?: -1L
        try {
            showTriggerPill()
        } catch (e: Exception) {
            // 多半是尚未授予悬浮窗权限，直接结束，交由 UI 提示用户
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // region — 悬浮「选点」按钮（开启）

    private fun showTriggerPill() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        this.wm = wm
        removeCaptureOverlay()
        removeTriggerPill()

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * density).toInt(), (8 * density).toInt(),
                (12 * density).toInt(), (8 * density).toInt())
            background = roundedBg(Color.parseColor("#E65100"))
            setOnTouchListener(DragClickTouchListener(
                onTap = { startCapture() },
                onMove = { dx, dy -> movePill(this, dx, dy) }
            ))
        }

        val label = TextView(this).apply {
            text = "⊕ 选点"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
        val close = Button(this).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { stopSelf() }
        }
        pill.addView(label)
        pill.addView(close)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }
        wm.addView(pill, params)
        pill.layoutParams = params
        triggerView = pill
        // 反馈：确认选点已开启，并提示用法（若没看到此提示，说明悬浮窗权限未授予）
        Toast.makeText(
            applicationContext,
            "选点已开启：切到目标 App 后，点悬浮的「⊕ 选点」按钮",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun movePill(pill: View, dx: Int, dy: Int) {
        val lp = pill.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x += dx
        lp.y += dy
        wm?.updateViewLayout(pill, lp)
    }

    // endregion

    // region — 全屏选点遮罩

    private fun startCapture() {
        removeTriggerPill()
        val wm = this.wm ?: run {
            this.wm = getSystemService(WINDOW_SERVICE) as WindowManager
            this.wm!!
        }
        hasSelection = false
        selectedX = 0
        selectedY = 0

        val root = CaptureLayout(this) { x, y -> onScreenTap(x, y) }

        // 顶部提示横幅
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(20, 20, 20, 20)
            isClickable = true // 横幅自身消费触摸，避免误触发选点
        }
        banner = TextView(this).apply {
            text = "① 点击屏幕选择位置（会出现黄色十字光标）"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        topBar.addView(banner)

        // 底部操作栏：保存 / 重选 / 关闭（选点后才显示）
        confirmBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true // 自身消费触摸，避免点到底部栏时误移动光标
            visibility = View.GONE
        }
        val saveBtn = Button(this).apply {
            text = "保存"
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
            setOnClickListener { doSave() }
        }
        val rePickBtn = Button(this).apply {
            text = "重选"
            setOnClickListener { resetSelection() }
        }
        val closeBtn = Button(this).apply {
            text = "关闭"
            setOnClickListener { backToPill() }
        }
        confirmBar!!.addView(saveBtn)
        confirmBar!!.addView(rePickBtn)
        confirmBar!!.addView(closeBtn)

        // 黄色十字光标（初始隐藏）
        cursorView = CursorView(this).apply { visibility = View.GONE }

        val topLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        val bottomLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        val cursorLp = FrameLayout.LayoutParams(cursorSizePx, cursorSizePx)

        root.addView(topBar, topLp)
        root.addView(cursorView, cursorLp)
        root.addView(confirmBar, bottomLp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            // 注意：不加 FLAG_NOT_TOUCH_MODAL → 全屏可捕获空白处点击
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        wm.addView(root, params)
        captureView = root
    }

    private fun onScreenTap(x: Float, y: Float) {
        selectedX = x.roundToInt()
        selectedY = y.roundToInt()
        hasSelection = true
        placeCursor(selectedX, selectedY)
        banner?.text = "② 已选好位置，点「保存」确认，或「重选」重选"
        confirmBar?.visibility = View.VISIBLE
    }

    private fun placeCursor(x: Int, y: Int) {
        val cv = cursorView ?: return
        val lp = cv.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.leftMargin = x - cursorSizePx / 2
        lp.topMargin = y - cursorSizePx / 2
        cv.layoutParams = lp
        cv.visibility = View.VISIBLE
        cv.invalidate()
    }

    private fun resetSelection() {
        hasSelection = false
        cursorView?.visibility = View.GONE
        confirmBar?.visibility = View.GONE
        banner?.text = "① 点击屏幕选择位置（会出现黄色十字光标）"
    }

    private fun backToPill() {
        removeCaptureOverlay()
        showTriggerPill()
    }

    private fun doSave() {
        if (!hasSelection) return
        val intent = Intent(ACTION_TAP_CAPTURED).apply {
            putExtra(EXTRA_X, selectedX)
            putExtra(EXTRA_Y, selectedY)
            putExtra(EXTRA_STEP_ID, stepId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        // 新增模式：回到悬浮按钮，可继续添加下一个位置；
        // 重选（更新已有步骤）模式：直接结束。
        if (isUpdate) {
            removeCaptureOverlay()
            stopSelf()
        } else {
            removeCaptureOverlay()
            showTriggerPill()
        }
    }

    // endregion

    // region — 视图清理

    private fun removeTriggerPill() {
        triggerView?.let {
            try { wm?.removeView(it) } catch (_: Exception) { }
            triggerView = null
        }
    }

    private fun removeCaptureOverlay() {
        captureView?.let {
            try { wm?.removeView(it) } catch (_: Exception) { }
            captureView = null
        }
        cursorView = null
        confirmBar = null
        banner = null
    }

    // endregion

    override fun onDestroy() {
        super.onDestroy()
        removeCaptureOverlay()
        removeTriggerPill()
    }

    // region — 工具

    private val density: Float
        get() = resources.displayMetrics.density

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun roundedBg(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = 24f * density
        }
    }

    /**
     * 全屏捕获布局：空白处触摸落入 [onTap] 回调（用于放置光标）。
     */
    private class CaptureLayout(
        context: Context,
        private val onTap: (Float, Float) -> Unit
    ) : FrameLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                onTap(ev.rawX, ev.rawY)
                return true
            }
            return super.onTouchEvent(ev)
        }
    }

    /**
     * 黄色十字光标：圆环 + 十字线 + 中心点，居中绘制，便于看清已选位置。
     */
    private class CursorView(context: Context) : View(context) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 4f
        }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 3f
        }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW; style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            val r = width / 2f - 6f
            canvas.drawCircle(c, c, r, ring)
            canvas.drawLine(0f, c, width.toFloat(), c, line)
            canvas.drawLine(c, 0f, c, height.toFloat(), line)
            canvas.drawCircle(c, c, 5f, dot)
        }
    }

    /**
     * 悬浮按钮的「拖动 + 点击」手势识别：移动超过阈值视为拖动，否则视为点击。
     */
    private class DragClickTouchListener(
        private val onTap: () -> Unit,
        private val onMove: (dx: Int, dy: Int) -> Unit
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    lastX = ev.rawX
                    lastY = ev.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - lastX).toInt()
                    val dy = (ev.rawY - lastY).toInt()
                    if (abs(ev.rawX - downX) > 8 || abs(ev.rawY - downY) > 8) {
                        moved = true
                    }
                    if (moved) onMove(dx, dy)
                    lastX = ev.rawX
                    lastY = ev.rawY
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                }
            }
            return true
        }
    }
}
