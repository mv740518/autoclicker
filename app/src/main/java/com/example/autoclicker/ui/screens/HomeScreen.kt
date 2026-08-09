package com.example.autoclicker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.autoclicker.model.ClickStep
import com.example.autoclicker.viewmodel.ClickerViewModel

/**
 * 主界面 — 动作序列编排
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ClickerViewModel,
    hasOverlayPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onStartFloatingWindow: () -> Unit,
    onCaptureTap: (Long?) -> Unit
) {
    val steps by viewModel.steps.collectAsState()
    val loopCount by viewModel.loopCount.collectAsState()
    val isInfinite by viewModel.isInfinite.collectAsState()
    val randomOffset by viewModel.randomOffset.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto Clicker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCaptureTap(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加步骤") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 权限状态
            PermissionCard(
                hasOverlayPermission = hasOverlayPermission,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestAccessibilityPermission = onRequestAccessibilityPermission
            )

            HorizontalDivider()

            // 循环设置
            LoopSettingsCard(
                loopCount = loopCount,
                isInfinite = isInfinite,
                onLoopCountChange = { viewModel.setLoopCount(it) },
                onInfiniteChange = { viewModel.setInfinite(it) }
            )

            // 高级设置
            AdvancedSettingsCard(
                randomOffset = randomOffset,
                onRandomOffsetChange = { viewModel.setRandomOffset(it) }
            )

            HorizontalDivider()

            // 步骤列表
            Text(
                "动作序列 (${steps.size} 步)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (steps.isEmpty()) {
                EmptyStepsHint()
            } else {
                steps.forEachIndexed { index, step ->
                    StepCard(
                        index = index,
                        step = step,
                        canPickLocation = hasOverlayPermission,
                        onPickLocation = { onCaptureTap(step.id) },
                        onMoveUp = { if (index > 0) viewModel.moveStep(index, index - 1) },
                        onMoveDown = { if (index < steps.size - 1) viewModel.moveStep(index, index + 1) },
                        onRemove = { viewModel.removeStep(step.id) },
                        onWaitChange = { viewModel.updateStepWait(step.id, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))

            // 启动按钮
            Button(
                onClick = onStartFloatingWindow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = steps.isNotEmpty() && hasOverlayPermission && isAccessibilityEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("启动悬浮窗", style = MaterialTheme.typography.titleLarge)
            }

            if (!hasOverlayPermission || !isAccessibilityEnabled) {
                Text(
                    "请先授予所需权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            if (steps.isEmpty()) {
                Text(
                    "至少添加一个步骤",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// region — 权限卡片

@Composable
private fun PermissionCard(
    hasOverlayPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasOverlayPermission && isAccessibilityEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("权限状态", style = MaterialTheme.typography.titleMedium)

            PermissionRow("悬浮窗权限", hasOverlayPermission, onRequestOverlayPermission)
            PermissionRow("无障碍服务", isAccessibilityEnabled, onRequestAccessibilityPermission)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (granted) Color(0xFF4CAF50) else Color(0xFFEF5350))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (granted) "已授权" else "未授权",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
        if (!granted) {
            TextButton(onClick = onRequest) { Text("去授权") }
        }
    }
}

// endregion

// region — 循环设置卡片

@Composable
private fun LoopSettingsCard(
    loopCount: Int,
    isInfinite: Boolean,
    onLoopCountChange: (Int) -> Unit,
    onInfiniteChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("循环设置", style = MaterialTheme.typography.titleMedium)

            // 无限循环开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("无限循环")
                }
                Switch(checked = isInfinite, onCheckedChange = onInfiniteChange)
            }

            // 循环次数输入（无限循环时禁用）
            if (!isInfinite) {
                OutlinedTextField(
                    value = loopCount.toString(),
                    onValueChange = { onLoopCountChange(it.toIntOrNull() ?: 1) },
                    label = { Text("循环次数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) }
                )
            }
        }
    }
}

// endregion

// region — 高级设置卡片

@Composable
private fun AdvancedSettingsCard(
    randomOffset: Int,
    onRandomOffsetChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("高级设置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = randomOffset.toString(),
                onValueChange = { onRandomOffsetChange(it.toIntOrNull() ?: 0) },
                label = { Text("随机偏移（像素，防检测）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

// endregion

// region — 步骤卡片

@Composable
private fun StepCard(
    index: Int,
    step: ClickStep,
    canPickLocation: Boolean,
    onPickLocation: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onWaitChange: (Long) -> Unit
) {
    var waitText by remember(step.id) { mutableStateOf(step.waitAfterMs.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 序号
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // 步骤信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "坐标: (${step.x.toInt()}, ${step.y.toInt()})",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("等待", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = waitText,
                        onValueChange = {
                            waitText = it
                            it.toLongOrNull()?.let { ms -> onWaitChange(ms) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Text("ms", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 点哪选哪：直接在屏幕上点选该步坐标
                OutlinedButton(
                    onClick = onPickLocation,
                    enabled = canPickLocation,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("选取位置", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 排序按钮
            Column {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "上移", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "下移", modifier = Modifier.size(16.dp))
                }
            }

            // 删除按钮
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "删除", tint = Color(0xFFEF5350))
            }
        }
    }
}

// endregion

// region — 空状态提示

@Composable
private fun EmptyStepsHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("还没有步骤", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "点右下角\"添加步骤\"，再在屏幕上点一下目标位置即可",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "启动悬浮窗后仍可拖拽准星微调；点\"选取位置\"可重新选点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// endregion
