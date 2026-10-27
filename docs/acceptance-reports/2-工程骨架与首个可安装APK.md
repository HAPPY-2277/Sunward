# M1 · 工程骨架跑通，产出第一个可安装 APK

**执行日期**：2026-10-24　**执行耗时**：约 50 分钟（含 5 次失败迭代）　**状态**：✅ 执行完成，⏳ 待用户验收

### 验收项核对

| # | 验收标准（计划 §7 M1） | 结果 | 证据 |
|---|---|---|---|
| 1 | `gradlew.bat assembleDebug` 输出 BUILD SUCCESSFUL | ✅ | `BUILD SUCCESSFUL in 31s`（34 tasks） |
| 2 | APK 存在且体积 > 1 MB | ✅ | `app-debug.apk` **7.02 MB** |
| 3 | `aapt2 dump badging` 显示包名 / minSdk / label | ✅ | `package: name='com.runclub.android' versionCode='1' versionName='0.1.0' compileSdkVersion='37'`<br>`minSdkVersion:'26'`　`targetSdkVersion:'36'`　`application-label:'RUN CLUB'` |
| 4 | 4 个 Activity 注册，启动页为 HomeActivity | ✅ | manifest 中 4 个 `E: activity`；`launchable-activity: com.runclub.android.ui.HomeActivity` |
| 5 | 中文资源无乱码 | ✅ | APK 字符串池含 `今天，跑出你的节奏。`／`今日建议`／`轻松跑 · 5.0 KM`／`结束跑步`／`本次成绩` 等 13 条，全部正确 |

### APK 产物信息

| 项 | 值 |
|---|---|
| 路径 | `android\app\build\outputs\apk\debug\app-debug.apk` |
| 大小 | 7,368,2xx B（7.02 MB） |
| SHA-256 | `D5CE65AC953A7F3307E029444ED357539948E90ED9322FD69168E14E35BADB47` |
| 签名 | CN=Android Debug（debug keystore：`tools\android-user\debug.keystore`） |
| 签名 SHA-1 | `cfd6bad792fa17aca4d7ea8206c6c48f6bcec59a` ← **申请高德 Key 时填这个** |
| 签名 SHA-256 | `51d332f90b96100d263ca50265beed4e891c260e61e404932acaf54dc7bd3272` |

### 已实现的骨架内容

- 4 个 Activity：`HomeActivity`（启动页）/ `LiveRunActivity` / `ResultActivity` / `RecordsActivity`，可互相跳转
- 全局深色主题 `Theme.RunClub`（Material3 Dark），设计令牌 `colors.xml` 与原型一致（`#080B08` / `#D9FF43` / `#FF5C35` 等）
- 复用样式：`Text.Brand/Label/Title/Body/Display`、`Widget.RunClub.PrimaryButton/SecondaryButton/Card`
- 自适应启动图标（`ic_launcher` + `ic_launcher_round`，矢量：荧光绿定位环）
- 依赖已全部预置（appcompat / material / constraintlayout / activity-ktx / fragment-ktx / recyclerview / lifecycle-service / coroutines），后续里程碑无需再改依赖

### 计划外偏差（6 项）

| # | 偏差 | 原因 | 影响 |
|---|---|---|---|
| D5 | 移除 `org.jetbrains.kotlin.android` 插件 | **AGP 9.0 起 Kotlin 支持内置**，再声明该插件会直接构建失败并提示移除 | 无；少一个插件 |
| D6 | compileSdk 从 **36 改为 37** | `androidx.core:core:1.19.1` / `core-ktx:1.19.1` 的 AAR 元数据强制要求 ≥ 37 | 无（已预装 `platforms/android-37.0`，D3 的预判正确） |
| D7 | wrapper 的 `distributionUrl` 指向腾讯云镜像 | `services.gradle.org` 与 `downloads.gradle.org` 均连接超时（被墙），官方源不可用 | 仓库内为镜像地址；换到有正常网络的环境时可改回官方源 |
| D8 | 新增空基类样式 `Text` / `Widget` / `Widget.RunClub` | 点号命名样式会隐式继承前缀，缺基类时 AAPT 报 `style/Text not found` | 无 |
| D9 | 不能同时设置 `ANDROID_PREFS_ROOT` 与 `ANDROID_USER_HOME` | AGP 抛 `AndroidLocationsException`，要求只用其一（保留 `ANDROID_USER_HOME`） | 已写入 README 的构建命令 |
| D10 | debug keystore 生成在 `tools\android-user\debug.keystore` | 因为 `ANDROID_USER_HOME` 被重定向到工作区内 | ⚠️ **不要删除 `tools\android-user\`**，否则签名 SHA1 变化、已申请的高德 Key 失效 |

### 待你确认

调研报告文件已被移动到 `docs\调研报告.md`（原 `跑步App市场调研报告.md`，文件 mtime 未变，说明是移动而非重写）。**不是我执行的移动**。我按现状提交为一次重命名；若是误操作，执行 `git mv docs/调研报告.md 跑步App市场调研报告.md` 即可改回。

### 模拟器环境与真机级验证（补充）

模拟器已装好并可复用，之后每个里程碑我都能自测并给出截图：

| 项 | 值 |
|---|---|
| 组件 | `emulator 37.2.12` + `system-images;android-36;default;x86_64`（AOSP 纯镜像，**无 Google Play 服务**，贴近国内手机） |
| 硬件加速 | **WHPX 10.0.26200 已安装且可用** |
| AVD | `runclub36`，1080×2400 @420dpi（≈411×914dp），2 GB RAM，x86_64 |
| AVD 路径 | `tools\android-user\avd\runclub36.avd` |
| 启动命令 | `emulator -avd runclub36 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -port 5554` |
| 冷启动耗时 | 约 **135 秒** |
| 验证方式 | `adb install` → `am start` → `uiautomator dump` 取控件坐标 → `input tap` → `screencap` 截图 |

**四页实测结果**（截图见 `docs/screenshots/`）：

| 页面 | 结果 |
|---|---|
| `01-home.png` | ✅ 深色首页、`RUN CLUB`、日期「10月24日 · 星期六」、主卡、荧光绿 GO、双 Tab |
| `02-live-run.png` | ✅ 返回 chip、「实时跑步」、`0.00 KM` 大字、轨迹占位卡、结束跑步按钮 |
| `03-result.png` | ✅ `RUN COMPLETE`、「本次成绩」、卡片、完成返回按钮 |
| `04-records.png` | ✅ 返回 +「记录」、卡片、运动/记录双 Tab（记录高亮） |

导航链路 `首页 → GO → 实时跑步 → 结束跑步 → 成绩页 → 完成返回首页 → 记录 → 返回` 全部通过，`logcat` 无 `FATAL EXCEPTION`。

### 模拟器暴露并已修复的 2 个视觉缺陷

| # | 缺陷 | 原因 | 修复 |
|---|---|---|---|
| V1 | 状态栏直接压在 `RUN CLUB` 与日期上 | Android 15+ 对 `targetSdk ≥ 35` **强制 edge-to-edge**，内容会绘制到系统栏下方 | 四个布局根节点加 `android:fitsSystemWindows="true"` |
| V2 | 实时跑步/成绩/记录三页**左右 24dp 内边距全部丢失**，卡片与按钮贴到屏幕边缘 | `fitsSystemWindows` 会用系统栏 inset **覆盖**根节点自身的 `android:padding` | 改为「外层吃 inset 且无 padding + 内层带 24dp padding」双层结构（首页本就是此结构，故未受影响） |

修复后实测：`RUN CLUB` 左边界 `x=63px`（= 24dp × 2.625 density ✅），卡片内文字 `x=121px`（= 63 + 22dp ✅）。

### 用户验收

- 验收人：
- 验收日期：
- 结论：⏳ 待确认（需你把 APK 装到手机，确认能启动并跳转 4 个页面）
- 备注：

### M1 提交记录

```
feat: 搭建 Android 工程骨架并产出首个可安装 debug APK
```
