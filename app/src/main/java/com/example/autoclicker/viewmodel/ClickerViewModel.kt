package com.example.autoclicker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.autoclicker.model.ClickStep
import com.example.autoclicker.model.ClickTask
import com.example.autoclicker.service.AutoClickService
import com.example.autoclicker.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主界面 ViewModel（多步骤序列版）
 *
 * 管理 UI 状态：
 * - 动作步骤列表（增删改排序）
 * - 循环次数 / 无限循环
 * - 权限状态（悬浮窗 + 无障碍）
 */
class ClickerViewModel : ViewModel() {

    // region — 动作步骤

    private val _steps = MutableStateFlow<List<ClickStep>>(emptyList())
    val steps: StateFlow<List<ClickStep>> = _steps.asStateFlow()

    // endregion

    // region — 循环配置

    private val _loopCount = MutableStateFlow(3)
    val loopCount: StateFlow<Int> = _loopCount.asStateFlow()

    private val _isInfinite = MutableStateFlow(true)
    val isInfinite: StateFlow<Boolean> = _isInfinite.asStateFlow()

    // endregion

    // region — 高级设置

    private val _randomOffset = MutableStateFlow(0)
    val randomOffset: StateFlow<Int> = _randomOffset.asStateFlow()

    // endregion

    // region — 权限状态

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    // endregion

    // region — 调试信息（权限检测原始值，便于排查「已授权却显示未授权」）

    data class PermissionDebug(
        val overlayApi: Boolean,
        val overlayDraw: Boolean,
        val accessibilityActive: Boolean,
        val accessibilityRaw: String
    )

    private val _debug = MutableStateFlow<PermissionDebug?>(null)
    val debug: StateFlow<PermissionDebug?> = _debug.asStateFlow()

    // endregion

    // region — 步骤操作

    /**
     * 添加一个步骤
     */
    fun addStep(step: ClickStep) {
        _steps.value = _steps.value + step
    }

    /**
     * 更新指定步骤
     */
    fun updateStep(step: ClickStep) {
        _steps.value = _steps.value.map { if (it.id == step.id) step else it }
    }

    /**
     * 仅更新指定步骤的点击坐标（供「点哪选哪」选点后调用）
     */
    fun updateStepPosition(stepId: Long, x: Float, y: Float) {
        _steps.value = _steps.value.map {
            if (it.id == stepId) it.copy(x = x, y = y) else it
        }
    }

    /**
     * 删除指定步骤
     */
    fun removeStep(stepId: Long) {
        _steps.value = _steps.value.filter { it.id != stepId }
    }

    /**
     * 移动步骤（拖拽排序）
     */
    fun moveStep(from: Int, to: Int) {
        val list = _steps.value.toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            _steps.value = list
        }
    }

    /**
     * 更新步骤等待时间
     */
    fun updateStepWait(stepId: Long, waitMs: Long) {
        _steps.value = _steps.value.map {
            if (it.id == stepId) it.copy(waitAfterMs = waitMs.coerceIn(10L, 60000L)) else it
        }
    }

    // endregion

    // region — 循环设置

    fun setLoopCount(value: Int) {
        _loopCount.value = value.coerceAtLeast(1)
    }

    fun setInfinite(value: Boolean) {
        _isInfinite.value = value
    }

    // endregion

    // region — 高级设置

    fun setRandomOffset(value: Int) {
        _randomOffset.value = value.coerceAtLeast(0)
    }

    // endregion

    // region — 权限检查

    fun updatePermissionState(context: Context) {
        val overlay = PermissionUtils.overlayDebug(context)
        _hasOverlayPermission.value = overlay.apiCanDraw || overlay.drawTestOk
        _isAccessibilityEnabled.value = PermissionUtils.isAccessibilityServiceEnabled(context)
        _debug.value = PermissionDebug(
            overlayApi = overlay.apiCanDraw,
            overlayDraw = overlay.drawTestOk,
            accessibilityActive = PermissionUtils.isAccessibilityServiceActive(),
            accessibilityRaw = PermissionUtils.getEnabledAccessibilityServicesRaw(context)
        )
    }

    // endregion

    // region — 任务生成

    /**
     * 获取当前点击任务
     */
    fun getClickTask(): ClickTask {
        return ClickTask(
            steps = _steps.value,
            loopCount = _loopCount.value,
            isInfinite = _isInfinite.value,
            randomOffset = _randomOffset.value
        )
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        com.example.autoclicker.service.AutoClickService.instance?.stopTask()
    }
}
