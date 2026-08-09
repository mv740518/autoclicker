# Auto Clicker — Android 自动点击器

## 功能
- 多步骤动作序列编排：按顺序点击多个屏幕位置，每步可设置不同的等待时间
- 循环执行：支持指定循环次数或无限循环
- 悬浮窗控制：准星标记每个步骤位置，可拖拽定位，当前执行步骤高亮显示
- 无需 root：通过 AccessibilityService 的 dispatchGesture 实现模拟点击

## 使用方法
1. 用 Android Studio 打开项目 `AutoClicker/`
2. 等待 Gradle Sync 完成
3. 连接 Android 手机（开启 USB 调试）
4. 点击 Run 编译安装
5. 打开 App，依次授权：
   - 悬浮窗权限（点击"去授权"）
   - 无障碍服务（点击"去授权" → 找到 Auto Clicker → 开启）
6. 添加步骤：点右下角"添加步骤"按钮
7. 设置循环次数或勾选"无限循环"
8. 点击"启动悬浮窗"
9. 拖拽准星到目标位置
10. 点击控制面板的"开始"按钮

## 技术栈
- Kotlin + Jetpack Compose + Material 3
- AccessibilityService（dispatchGesture）
- WindowManager（TYPE_APPLICATION_OVERLAY）
- 前台服务（Foreground Service）

## 最低系统要求
- Android 7.0 (API 24) 及以上
- compileSdk 34, targetSdk 34

## 项目结构
```
app/src/main/java/com/example/autoclicker/
├── AutoClickerApp.kt           # Application 类
├── MainActivity.kt              # 主 Activity
├── model/ClickConfig.kt          # ClickStep + ClickTask 数据模型
├── service/
│   ├── AutoClickService.kt       # AccessibilityService（dispatchGesture 执行点击）
│   ├── FloatingWindowManager.kt  # 悬浮窗管理（多准星 + 控制面板）
│   └── FloatingWindowService.kt  # 前台服务
├── viewmodel/ClickerViewModel.kt # ViewModel
├── ui/
│   ├── screens/HomeScreen.kt     # 主界面 UI
│   └── theme/                    # Material 3 主题
└── util/PermissionUtils.kt       # 权限检查工具
```

## 构建命令
```bash
# Debug build
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

## 注意事项
1. 首次使用需要在系统设置中手动开启无障碍服务
2. 悬浮窗权限也需要手动授权
3. Android 14+ 需声明 foregroundServiceType="specialUse"
4. 拖拽准星时点击坐标会实时更新
5. 可选设置随机偏移来防检测
6. **Android 12+ 侧载安装会触发「受限设置」**：通过浏览器下载 APK 再安装的应用，系统默认禁止开启无障碍服务，会弹「未知来源应用，系统已拒绝此应用获取敏感权限」。解决：进入「应用信息」页 ⋮ →「允许受限设置」后再开开关；或改用电脑端 `adb install` 安装（可信来源，永久不受限）。详见各版本更新说明。

---

## 不用 Android Studio？用 GitHub Actions 云端编译

如果你的电脑没装 Android Studio，又想直接拿到 APK，可以用 GitHub 的免费云端编译（GitHub 服务器自带完整 Android SDK，不需要你本地装任何东西）：

### 步骤

1. **注册 GitHub 账号**（github.com）
2. **新建仓库**：点右上角 `+` → New repository → 填个名字（如 `autoclicker`）→ 创建
3. **上传代码**：把本项目 zip 解压后的全部文件上传到仓库（可直接拖拽，或本地 `git push`）
4. **等自动编译**：
   - 代码推上去后，GitHub 会自动检测 `.github/workflows/build.yml` 并开始编译
   - 进仓库 → Actions 标签页 → 点 `Build Debug APK` → 看进度（一般 3-5 分钟）
5. **下载 APK**：
   - 编译完成后，在 Actions 页面下方 `Artifacts` 区域点 `app-debug-apk` 下载
   - 把 APK 传到手机安装即可

> 手机浏览器直接打开 GitHub 的 APK 下载链接也能装（需开启"允许未知来源应用"）。

### 本地用 git 推送的方式（可选）

```bash
cd AutoClicker
git init
git add .
git commit -m "init"
git branch -M main
git remote add origin https://github.com/你的用户名/autoclicker.git
git push -u origin main
```

推送后 Actions 会自动触发编译。
