package com.example.autoclicker.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.autoclicker.model.ClickStep
import com.example.autoclicker.model.ClickTask

/**
 * 悬浮窗管理器（多步骤序列版）
 *
 * 管理两部分悬浮 UI：
 * 1. 准星标记 — 每个步骤一个带序号的准星，可拖拽定位
 * 2. 控制面板 — 开始/停止/关闭按钮 + 进度显示
 *
 * @param context 上下文
 * @param task    点击任务
 */
class FloatingWindowManager(
    private val context: Context,
    private var task: ClickTask
) {

    companion object {
        private const val TAG = "FloatingWindowManager"
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // region — 视图引用

    /** 步骤准星列表：index → (view, params) */
    private val targetViews = mutableMapOf<Int, Pair<View, WindowManager.LayoutParams>>()

    /** 控制面板 */
    private var controlPanel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null

    // 控制面板 UI 引用
    private var statusText: TextView? = null
    private var progressText: TextView? = null
    private var toggleButton: Button? = null

    // endregion

    // region — 公共 API

    /**
     * 显示所有步骤的准星标记
     */
    fun showTargets() {
        task.steps.forEachIndexed { index, step ->
            addTargetView(index, step)
        }
        Log.d(TAG, "Shown ${task.stepCount} target views")
    }

    /**
     * 显示控制面板
     */
    fun showControlPanel() {
        if (controlPanel != null) return

        val density = context.resources.displayMetrics.density
        val params = createOverlayParams().apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 200
        }
        panelParams = params

        val panel = createControlPanelView(density)
        controlPanel = panel

        try {
            windowManager.addView(panel, params)
            panel.setOnTouchListener(PanelTouchListener())
            Log.d(TAG, "Control panel shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show control panel", e)
        }
    }

    /**
     * 隐藏所有悬浮窗
     */
    fun hideAll() {
        AutoClickService.instance?.stopTask()

        targetViews.values.forEach { (view, _) ->
            try { windowManager.removeView(view) } catch (e: Exception) { }
        }
        targetViews.clear()

        controlPanel?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { }
            controlPanel = null
        }
        panelParams = null
        statusText = null
        progressText = null
        toggleButton = null

        Log.d(TAG, "All floating views hidden")
    }

    /**
     * 获取当前任务（包含准星位置更新）
     */
    fun getCurrentTask(): ClickTask {
        val updatedSteps = task.steps.mapIndexed { index, step ->
            val pair = targetViews[index]
            if (pair != null) {
                val (view, params) = pair
                step.copy(
                    x = params.x.toFloat() + view.width / 2f,
                    y = params.y.toFloat() + view.height / 2f
                )
            } else {
                step
            }
        }
        return task.copy(steps = updatedSteps)
    }

    // endregion

    // region — 准星视图

    /**
     * 创建并添加单个准星视图
     */
    private fun addTargetView(index: Int, step: ClickStep) {
        val density = context.resources.displayMetrics.density
        val size = (48 * density).toInt()

        val params = createOverlayParams().apply {
            gravity = Gravity.TOP or Gravity.START
            x = (step.x - size / 2f).toInt().coerceAtLeast(0)
            y = (step.y - size / 2f).toInt().coerceAtLeast(0)
        }

        // 准星容器：序号标签 + 准星图标
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // 序号标签
        val label = TextView(context).apply {
            text = "${index + 1}"
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(0, 0, 0, 0)
        }

        // 准星图标（圆形）
        val icon = View(context).apply {
            background = createTargetDrawable(density, isActive = false)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

        container.addView(label)
        container.addView(icon)
        container.setOnTouchListener(TargetTouchListener(index))

        try {
            windowManager.addView(container, params)
            targetViews[index] = Pair(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add target view #$index", e)
        }
    }

    /**
     * 创建准星 drawable
     */
    private fun createTargetDrawable(density: Float, isActive: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (isActive) {
                setStroke((5 * density).toInt(), Color.GREEN)
                setColor(0x4400FF00)
            } else {
                setStroke((3 * density).toInt(), Color.RED)
                setColor(0x22FF0000)
            }
        }
    }

    /**
     * 更新准星高亮状态
     *
     * @param activeStep 当前执行的步骤（null = 已结束）
     */
    fun highlightStep(activeStep: ClickStep?) {
        val activeIndex = task.steps.indexOfFirst { it.id == activeStep?.id }

        targetViews.forEach { (index, pair) ->
            val (view, _) = pair
            val isActive = index == activeIndex

            val container = view as? LinearLayout ?: return@forEach
            val icon = container.getChildAt(1) ?: return@forEach
            (icon.background as? GradientDrawable)?.let { drawable ->
                if (isActive) {
                    drawable.setStroke(5, Color.GREEN)
                    drawable.setColor(0x4400FF00)
                } else {
                    drawable.setStroke(3, Color.RED)
                    drawable.setColor(0x22FF0000)
                }
            }

            val label = container.getChildAt(0) as? TextView
            label?.setTextColor(if (isActive) Color.GREEN else Color.WHITE)
            label?.textSize = if (isActive) 12f else 10f
        }
    }

    // endregion

    // region — 控制面板

    private fun createControlPanelView(density: Float): View {
        val statusLabel = TextView(context).apply {
            text = "就绪"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        statusText = statusLabel

        val progressLabel = TextView(context).apply {
            text = "步骤: 0 / ${task.stepCount} | 循环: 0"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 11f
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        progressText = progressLabel

        val clickLabel = TextView(context).apply {
            text = "总点击: 0"
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 11f
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val startBtn = Button(context).apply {
            text = "开始"
            setOnClickListener { toggleRunning(progressLabel, clickLabel, statusLabel) }
        }
        toggleButton = startBtn

        val closeBtn = Button(context).apply {
            text = "关闭"
            setOnClickListener {
                AutoClickService.instance?.stopTask()
                hideAll()
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(), (16 * density).toInt(),
                (16 * density).toInt(), (16 * density).toInt()
            )
            background = GradientDrawable().apply {
                setCornerRadius(16 * density)
                setColor(0xDD1E1E1E.toInt())
                setStroke(1, 0x44FFFFFF.toInt())
            }
            addView(statusLabel)
            addView(progressLabel)
            addView(clickLabel)
            addView(startBtn)
            addView(closeBtn)
        }
    }

    /**
     * 切换运行/停止状态
     */
    private fun toggleRunning(
        progressLabel: TextView,
        clickLabel: TextView,
        statusLabel: TextView
    ) {
        val service = AutoClickService.instance
        if (service == null) {
            statusLabel.text = "无障碍服务未启用"
            statusLabel.setTextColor(Color.parseColor("#FF5252"))
            return
        }

        if (service.isRunning()) {
            service.stopTask()
            toggleButton?.text = "开始"
            statusLabel.text = "已停止"
            statusLabel.setTextColor(Color.parseColor("#FF5252"))
        } else {
            // 从准星位置获取最新坐标
            val currentTask = getCurrentTask()

            // 设置回调
            service.onCurrentStepChanged = { step ->
                highlightStep(step)
            }
            service.onProgressChanged = { stepIndex, stepTotal, loop, loopTotal, totalClicks ->
                val loopStr = if (loopTotal == 0) "循环: $loop (无限)" else "循环: $loop/$loopTotal"
                progressLabel.text = "步骤: ${stepIndex + 1} / $stepTotal | $loopStr"
                clickLabel.text = "总点击: $totalClicks"
            }
            service.onRunningChanged = { running ->
                if (!running) {
                    toggleButton?.text = "开始"
                    statusLabel.text = "已完成"
                    statusLabel.setTextColor(Color.parseColor("#4CAF50"))
                    highlightStep(null)
                }
            }

            service.startTask(currentTask)
            toggleButton?.text = "停止"
            statusLabel.text = "运行中..."
            statusLabel.setTextColor(Color.parseColor("#4CAF50"))
        }
    }

    // endregion

    // region — 拖拽监听

    /**
     * 准星拖拽监听器 — 更新对应步骤的坐标
     */
    private inner class TargetTouchListener(
        private val stepIndex: Int
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = targetViews[stepIndex]?.second ?: return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(v, params)

                    // 更新步骤坐标
                    val step = task.steps[stepIndex]
                    task = task.copy(
                        steps = task.steps.toMutableList().also { list ->
                            list[stepIndex] = step.copy(
                                x = params.x.toFloat() + v.width / 2f,
                                y = params.y.toFloat() + v.height / 2f
                            )
                        }
                    )
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        Log.d(TAG, "Target #$stepIndex moved to " +
                                "(${params.x + v.width / 2}, ${params.y + v.height / 2})")
                    }
                    return true
                }
            }
            return false
        }
    }

    /**
     * 控制面板拖拽监听器
     */
    private inner class PanelTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = panelParams?.x ?: 0
                    initialY = panelParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    panelParams?.let { params ->
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(controlPanel, params)
                    }
                    return true
                }
            }
            return false
        }
    }

    // endregion

    // region — 工具方法

    private fun createOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    // endregion
}
