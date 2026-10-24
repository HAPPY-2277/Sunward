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
