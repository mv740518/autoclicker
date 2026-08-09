package com.example.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.autoclicker.model.ClickStep
import com.example.autoclicker.model.ClickTask

/**
 * 无障碍服务 —— 通过 dispatchGesture 实现模拟点击（无需 root）
 *
 * 支持多步骤动作序列编排：
 * 按顺序执行 [ClickTask.steps] 中的每一步，每步点击指定坐标后等待指定时间，
 * 全部步骤执行完毕后根据 loopCount / isInfinite 决定是否循环重来。
 *
 * 使用方式：
 * 1. 用户在系统设置中开启此服务
 * 2. 通过 [instance] 静态引用调用 startTask / stopTask
 */
class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"

        /** 静态实例引用，供外部调用 */
        var instance: AutoClickService? = null
            private set

        /** 服务是否已激活 */
        fun isActive(): Boolean = instance != null
    }

    // region — 状态

    private var isRunning = false
    private var currentTask: ClickTask? = null
    private val handler = Handler(Looper.getMainLooper())

    /** 当前执行到第几步（0-based） */
    private var currentStepIndex = 0

    /** 当前是第几轮循环（1-based） */
    private var currentLoop = 0

    /** 总共已执行的点击次数 */
    private var totalClicks = 0

    // endregion

    // region — 回调

    /** 运行状态变化回调：running → true/false */
    var onRunningChanged: ((Boolean) -> Unit)? = null

    /**
     * 进度变化回调
     * @param stepIndex 当前步骤索引（0-based）
     * @param stepTotal 总步骤数
     * @param loop      当前循环轮次（1-based）
     * @param loopTotal 总循环轮次（0=无限）
     * @param totalClicks 总点击次数
     */
    var onProgressChanged: ((stepIndex: Int, stepTotal: Int, loop: Int, loopTotal: Int, totalClicks: Int) -> Unit)? = null

    /**
     * 当前步骤变化回调 —— 用于高亮准星
     * @param step 当前执行步骤（null = 已结束）
     */
    var onCurrentStepChanged: ((ClickStep?) -> Unit)? = null

    // endregion

    // region — 生命周期

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Log.d(TAG, "AccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 本应用不需要监听无障碍事件
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
        stopTask()
    }

    // endregion

    // region — 公共 API

    /**
     * 开始执行动作序列
     *
     * @param task 点击任务（包含步骤序列和循环配置）
     */
    fun startTask(task: ClickTask) {
        if (isRunning) {
            Log.w(TAG, "Already running, ignoring start request")
            return
        }

        if (!task.hasSteps) {
            Log.w(TAG, "No steps in task, ignoring start request")
            return
        }

        currentTask = task
        currentStepIndex = 0
        currentLoop = 1
        totalClicks = 0
        isRunning = true
        onRunningChanged?.invoke(true)

        Log.d(TAG, "Started task — steps=${task.stepCount}, " +
                "loops=${if (task.isInfinite) "infinite" else task.effectiveLoopCount}")

        // 开始执行第一步
        handler.post(stepRunnable)
    }

    /**
     * 停止执行
     */
    fun stopTask() {
        if (!isRunning) return

        isRunning = false
        handler.removeCallbacks(stepRunnable)
        onCurrentStepChanged?.invoke(null)
        onRunningChanged?.invoke(false)

        Log.d(TAG, "Stopped task — total clicks: $totalClicks")
    }

    /** 当前是否正在运行 */
    fun isRunning(): Boolean = isRunning

    /** 获取当前步骤索引 */
    fun getCurrentStepIndex(): Int = currentStepIndex

    /** 获取当前循环轮次 */
    fun getCurrentLoop(): Int = currentLoop

    /** 获取总点击次数 */
    fun getTotalClicks(): Int = totalClicks

    // endregion

    // region — 序列执行核心

    /**
     * 步骤执行 Runnable
     *
     * 执行流程：
     * 1. 取出当前步骤
     * 2. 通过 dispatchGesture 执行点击
     * 3. 通知回调（高亮准星 + 进度）
     * 4. 等待 step.waitAfterMs
     * 5. 推进到下一步
     * 6. 如果所有步骤执行完毕 → 循环+1 → 回到步骤1
     * 7. 如果达到循环次数 → 停止
     */
    private val stepRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val task = currentTask ?: run {
                stopTask()
                return
            }

            // 检查是否所有步骤已完成
            if (currentStepIndex >= task.stepCount) {
                // 本轮循环结束
                currentStepIndex = 0

                if (!task.isInfinite) {
                    currentLoop++
                    if (currentLoop > task.effectiveLoopCount) {
                        // 所有循环完成
                        Log.d(TAG, "All loops completed — total clicks: $totalClicks")
                        stopTask()
                        return
                    }
                    Log.d(TAG, "Starting loop $currentLoop/${task.effectiveLoopCount}")
                } else {
                    currentLoop++
                    Log.d(TAG, "Starting loop $currentLoop (infinite)")
                }
            }

            // 取出当前步骤
            val step = task.steps[currentStepIndex]
            onCurrentStepChanged?.invoke(step)

            // 执行点击
            performClick(step, task)
            totalClicks++

            // 通知进度
            onProgressChanged?.invoke(
                currentStepIndex,
                task.stepCount,
                currentLoop,
                task.effectiveLoopCount,
                totalClicks
            )

            // 推进到下一步
            currentStepIndex++

            // 等待 step.waitAfterMs 后执行下一步
            val delay = step.waitAfterMs.coerceAtLeast(10L)
            handler.postDelayed(this, delay)
        }
    }

    // endregion

    // region — dispatchGesture

    /**
     * 执行单次点击 —— 通过 dispatchGesture 模拟手势
     */
    private fun performClick(step: ClickStep, task: ClickTask) {
        // 添加随机偏移（防检测）
        val finalX = step.x + getRandomOffset(task.randomOffset)
        val finalY = step.y + getRandomOffset(task.randomOffset)

        val path = Path().apply {
            moveTo(finalX, finalY)
        }

        val strokeDuration = step.clickDuration.coerceIn(1L, 5000L)
        val strokeDescription = GestureDescription.StrokeDescription(
            path, 0L, strokeDuration
        )
        val gestureDescription = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()

        dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) {
                Log.v(TAG, "Click completed — step #${currentStepIndex + 1}, " +
                        "loop $currentLoop, " +
                        "pos ($${"%.0f".format(finalX)}, ${"%.0f".format(finalY)})")
            }

            override fun onCancelled(gesture: GestureDescription?) {
                Log.w(TAG, "Click cancelled — step #${currentStepIndex + 1}")
            }
        }, null)
    }

    /**
     * 生成随机偏移量
     */
    private fun getRandomOffset(maxOffset: Int): Float {
        return if (maxOffset > 0) {
            (Math.random() * 2 - 1).toFloat() * maxOffset
        } else {
            0f
        }
    }

    // endregion
}
