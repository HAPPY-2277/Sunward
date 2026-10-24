# RUN CLUB · 跑步 App

原生 Android（Kotlin）跑步记录 App。当前进度见 [docs/开发计划.md](docs/开发计划.md) 与 [docs/验收报告.md](docs/验收报告.md)。

- 视觉基线：[design/prototype/](design/prototype/)（4 个 HTML 原型页面）
- 市场与技术调研：[docs/调研报告.md](docs/调研报告.md)

## 目录结构

```
D:\a\
├── android\               Gradle 工程（Kotlin + XML View）
│   ├── app\src\main\      源码与资源
│   └── gradlew.bat        构建入口
├── design\prototype\      原型页面（视觉基线，不参与构建）
├── docs\                  计划、验收记录、调研报告
└── tools\                 本机工具链（Android SDK / Gradle / 引导脚本，不入库）
```

## 前置环境

| 组件 | 要求 | 本机实测 |
|---|---|---|
| JDK | 17+ | Temurin 21.0.7 ✅ |
| Android SDK | platform 37 + build-tools 36.1.0 | `D:\a\tools\android-sdk` ✅ |
| Gradle | 由 wrapper 提供 | 9.8.2 |

## 构建

```powershell
$env:JAVA_HOME          = 'C:\Users\22205\AppData\Local\Programs\Java\jdk-21.0.7+6'
$env:ANDROID_HOME       = 'D:\a\tools\android-sdk'
$env:ANDROID_SDK_ROOT   = 'D:\a\tools\android-sdk'
$env:ANDROID_USER_HOME  = 'D:\a\tools\android-user'   # 不能同时设 ANDROID_PREFS_ROOT，否则 AGP 报路径冲突
$env:GRADLE_USER_HOME   = 'D:\a\tools\gradle-home'    # 默认 ~/.gradle 在工作区外，会被文件沙箱拒绝

cd D:\a\android
.\gradlew.bat assembleDebug
```

产物：`android\app\build\outputs\apk\debug\app-debug.apk`

若 `local.properties` 不存在，创建它并写入：`sdk.dir=D:/a/tools/android-sdk`

## 安装到手机

1. 把 `app-debug.apk` 传到手机（数据线 / 微信文件传输 / 网盘）。
2. 点击安装，允许「未知来源应用」。
3. 首次启动按提示授予定位权限（本 demo 只需「仅在使用中允许」）。

升级安装若报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先卸载旧版再装（签名不一致）。

## 在模拟器上运行

AVD 已配好：`runclub36`，1080×2400 @420dpi（≈411×914dp），x86_64，AOSP 镜像（**无 GMS**），2 GB RAM。
硬件加速：WHPX 可用（`emulator -accel-check` 验证）。启动后约 80~140 秒开机完成。

### 一键脚本（推荐）

```bat
D:\a\tools\emu.cmd start     :: 启动并等到开机完成，成功后打印装包命令
D:\a\tools\emu.cmd status    :: 查看是否在运行
D:\a\tools\emu.cmd stop      :: 关闭并清理锁文件
```

`tools\emu.cmd` / `tools\emu.ps1` 是本机辅助脚本（`tools/` 不入库）。
`emu.ps1` 必须以 **UTF-8 带 BOM** 保存，否则 PowerShell 5.1 按 GBK 解码会中文乱码；`emu.cmd` 里已 `chcp 65001`。

### 装 App 并启动

```powershell
$adb = 'D:\a\tools\android-sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 install -r 'D:\a\android\app\build\outputs\apk\debug\app-debug.apk'
& $adb -s emulator-5554 shell am start -n com.runclub.android/.ui.HomeActivity
```

### 手动启动（不想用脚本时）

```powershell
$sdk = 'D:\a\tools\android-sdk'
$env:ANDROID_HOME      = $sdk
$env:ANDROID_SDK_ROOT  = $sdk
$env:ANDROID_USER_HOME = 'D:\a\tools\android-user'
$env:ANDROID_AVD_HOME  = 'D:\a\tools\android-user\avd'
& "$sdk\emulator\emulator.exe" -avd runclub36 -no-window -no-audio -no-boot-anim `
    -no-snapshot -gpu swiftshader_indirect -port 5554 -no-metrics
```

### 截图与自动化点击

```powershell
& $adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml   # 导出控件树拿精确坐标
& $adb -s emulator-5554 pull /sdcard/ui.xml .\ui.xml
& $adb -s emulator-5554 shell input tap 540 702                 # 按坐标点击
& $adb -s emulator-5554 shell screencap -p /sdcard/shot.png
& $adb -s emulator-5554 pull /sdcard/shot.png .\shot.png
```

界面截图：[docs/screenshots/](docs/screenshots/)。

> ⚠️ **两个坑**
> 1. **不要用管道捕获 `emu.cmd start` 的输出**（如 `... | ForEach-Object`）——分离出去的 qemu 会继承 stdout 句柄，
>    管道永远等不到 EOF。要重定向就写到文件：`cmd /c "tools\emu.cmd start > start.log 2>&1"`。
> 2. `adb emu kill` 在控制台无响应时**会无限阻塞**，脚本里已加 6 秒上限并回退到强制终止。

> ⚠️ **全面屏注意**：`targetSdk ≥ 35` 会强制 edge-to-edge。布局根节点用 `fitsSystemWindows="true"` 吃 inset 时，
> 该属性会**覆盖**根节点自身的 `android:padding`——必须用「外层吃 inset 无 padding + 内层带 padding」的双层结构，
> 否则左右内边距会全部丢失（M1 曾踩过）。

## 本机环境约束（做后续里程碑时必须知道）

| 约束 | 说明 |
|---|---|
| `curl` / `Invoke-WebRequest` 不可用 | Schannel 报 `SEC_E_NO_CREDENTIALS`，一律改用 **Java**（`tools\Fetch.java`）或 **Node** 下载 |
| `services.gradle.org` / `downloads.gradle.org` 被墙 | Gradle 分发走腾讯云镜像，见 `android\gradle\wrapper\gradle-wrapper.properties` |
| `developer.android.com` 不可达 | 查文档用 `developer.android.google.cn` 或直接实证 |
| `%USERPROFILE%\.android` 与 `%USERPROFILE%\.gradle` 不可写 | 全部重定向到 `D:\a\tools\` 下 |
| debug keystore 在 `tools\android-user\debug.keystore` | **不要删除 `tools\android-user\`**，否则签名 SHA1 变化，已申请的高德 Key 会失效 |

## 关键版本

| 组件 | 版本 | 备注 |
|---|---|---|
| AGP | 9.4.1 | 9.0+ 起 **Kotlin 支持内置**，不得再声明 `org.jetbrains.kotlin.android` 插件 |
| Gradle | 9.8.2 | |
| compileSdk | 37 | `androidx.core:core:1.19.1` 强制要求 ≥ 37 |
| targetSdk / minSdk | 36 / 26 | |

当前 debug 签名：SHA-1 `cfd6bad792fa17aca4d7ea8206c6c48f6bcec59a`
