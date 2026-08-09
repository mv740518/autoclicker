package com.example.autoclicker.model

import java.io.Serializable

/**
 * 单个动作步骤
 *
 * @param id            唯一标识
 * @param x             点击 X 坐标
 * @param y             点击 Y 坐标
 * @param waitAfterMs   点击后等待多久再执行下一步（毫秒）
 * @param clickDuration 点击持续时间（毫秒，控制短按/长按）
 */
data class ClickStep(
    val id: Long = System.nanoTime(),
    val x: Float = 0f,
    val y: Float = 0f,
    val waitAfterMs: Long = 1000L,
    val clickDuration: Long = 50L
) : Serializable

/**
 * 完整点击任务 —— 多步骤动作序列
 *
 * @param steps        动作步骤列表（按顺序执行）
 * @param loopCount    循环次数（>0 = 有限次，0 = 无限循环）
 * @param isInfinite   是否无限循环
 * @param randomOffset 随机偏移量（像素，防检测，0=不偏移）
 */
data class ClickTask(
    val steps: List<ClickStep> = emptyList(),
    val loopCount: Int = 1,
    val isInfinite: Boolean = true,
    val randomOffset: Int = 0
) {
    /** 是否有可执行的步骤 */
    val hasSteps: Boolean
        get() = steps.isNotEmpty()

    /** 步骤数量 */
    val stepCount: Int
        get() = steps.size

    /** 有效循环次数（0 表示无限） */
    val effectiveLoopCount: Int
        get() = if (isInfinite) 0 else loopCount.coerceAtLeast(1)
}
