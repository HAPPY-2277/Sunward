# 逐日 · SUNWARD

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF)
![Android](https://img.shields.io/badge/Android-minSdk%2026-3DDC84)
![AGP](https://img.shields.io/badge/AGP-9.4.1-4285F4)
![Gradle](https://img.shields.io/badge/Gradle-9.8.2-02303A)

跑步记录 App。原生 Android / Kotlin，无账号、无云同步，跑步记录只存在本机。

主指标是「本周动了几天」，不是「离 30 km 还差多少」。

![首页](docs/screenshots/60-zhuri-home.png)

## 名称

中文名 **逐日**，英文名 **SUNWARD**。

「逐日」一天一天地读，也是追逐每一个日头。SUNWARD 意为「向着太阳」——
`sun` 对应日，`ward` 对应趋向，与中文的逐日近乎逐字对译。

App 启动器上显示「逐日」，面向家里人的日常使用；SUNWARD 用在代码仓库、文档和对外场合。
品牌色 `#D9FF43` 就是太阳的颜色，和这个名字是一套的。

包名 `com.runclub.android` 是早期名。改动会让已申请的高德地图 Key 失效
（Key 绑定包名 + 签名 SHA-1），因此保持原样。

## 想法

频率比强度重要。公里数是结果，不是成绩单。

「还差 29.9 km 达标」是欠账，跑两天看到自己欠一堆账，最可能的选择是干脆不跑了。
所以这个 App 里没有里程目标、没有配速目标、没有断签提醒，也没有任何红色警示。
一天动了就是完成，漏掉的日子只是不增加。

完整理念见 [docs/产品理念.md](docs/产品理念.md)。

## 界面

| | |
|---|---|
| ![实时](docs/screenshots/63-zhuri-live.png) | ![报告](docs/screenshots/62-zhuri-report.png) |
| 跑步中：轨迹、距离、时长、当前位置 | 报告：周 / 月 / 年，主数字是动过的天数 |
| ![记录](docs/screenshots/61-zhuri-record.png) | ![成绩](docs/screenshots/65-zhuri-result.png) |
| 历史：按天归档，可筛选、可展开 | 成绩：路线图、每公里用时、消耗 |

## 功能

- GPS 轨迹采集，前台服务保活，锁屏继续记录，中途可暂停
- 距离用增量 EMA 平滑后累加，过滤瞬移与漂移点
- 轨迹存 WGS-84，渲染时转 GCJ-02 再交给高德
- SQLite 存记录与轨迹点，删除记录后汇总同步减少
- 周 / 月 / 年报告：里程柱 + 快慢折线双轴图、运动构成、观察文案
- 过程被强杀后进入 App 可恢复未完成的跑步

不做：排行榜、社交动态、配速目标、断签提醒。

> 网络权限只用于高德底图的瓦片下载。跑步记录、轨迹点、报告全部存本地 SQLite，不上传。

## 技术选型

| | |
|---|---|
| 语言 | Kotlin 2.2 |
| 界面 | XML View + Material 3，无 Compose |
| 架构 | Activity 观察 `StateFlow<RunUiState>`，状态在进程级单例 `RunSession` + 前台服务里 |
| 存储 | 手写 `SQLiteOpenHelper`，两张表，插入走单事务 |
| 图表 | 自绘 `Canvas`，无图表库 |
| 地图 | 高德 3D SDK（`amap` 变体）/ 手绘底图（`local` 变体） |
| minSdk / targetSdk | 26 / 36 |
| compileSdk | 37（`androidx.core:core:1.19.1` 要求 ≥ 37） |
| AGP / Gradle | 9.4.1 / 9.8.2 |

AGP 9 起 Kotlin 支持已内置，`build.gradle.kts` 里**不能**再声明 `org.jetbrains.kotlin.android` 插件。

## 构建

```powershell
$env:JAVA_HOME         = 'C:\path\to\jdk-21'
$env:ANDROID_HOME      = 'D:\a\tools\android-sdk'
$env:ANDROID_SDK_ROOT  = 'D:\a\tools\android-sdk'
$env:ANDROID_USER_HOME = 'D:\a\tools\android-user'
$env:GRADLE_USER_HOME  = 'D:\a\tools\gradle-home'

cd android
.\gradlew.bat assembleLocalDebug     # 不含高德 SDK，可在 x86_64 模拟器安装
.\gradlew.bat assembleAmapDebug      # 含高德底图，装真机
.\gradlew.bat testLocalDebugUnitTest # 59 个单元测试
```

`local.properties` 不存在时创建并写入 `sdk.dir=D:/a/tools/android-sdk`。

想先看效果而不真去跑步，可以用内置的模拟轨迹：

```powershell
adb shell am start -n com.runclub.android/.ui.HomeActivity --ez sim true
```

模拟轨迹会走一条 2.4 km 的环线，产生一条带「模拟」标记的记录。

### 两个变体

| 变体 | 产物 | 用途 |
|---|---|---|
| `local` | `app/build/outputs/apk/local/debug/app-local-debug.apk`（8.5 MB） | 模拟器 / 无 ARM 环境，用手绘底图 |
| `amap` | `app/build/outputs/apk/amap/debug/app-amap-debug.apk`（43.9 MB） | 真机，高德底图 |

高德 3D SDK 只带 `arm64-v8a` / `armeabi-v7a` 的 native 库。把它打进主变体后，
x86_64 模拟器连安装都会报 `INSTALL_FAILED_NO_MATCHING_ABIS`，开发期没法自测。
两个变体共用同一个 `applicationId`，高德 Key 对两者都有效。

## 目录

```
android/app/src/
├── main/          主源码与资源
│   ├── java/com/runclub/android/
│   │   ├── tracking/     轨迹采集、过滤、距离累加、状态机
│   │   ├── session/      RunSession：跨 Activity 的状态持有者
│   │   ├── service/      前台服务（location 类型）
│   │   ├── data/         SQLite、仓库、报告聚合
│   │   ├── ui/           四个页面 + 自绘控件
│   │   ├── map/          地图抽象，坐标转换
│   │   └── util/         格式化
│   └── res/       布局、图形、颜色、文案
├── amap/          高德实现（变体）
├── local/         手绘地图实现（变体）
└── test/          59 个单元测试

design/prototype/  HTML 原型，视觉基线，不参与构建
docs/              计划、验收报告、调研、产品理念
tools/             本机工具链（SDK、Gradle、模拟器脚本），不入库
```

## 文档

| | |
|---|---|
| [产品理念](docs/产品理念.md) | 名字来源、四条原则、判定方法 |
| [开发计划](docs/开发计划.md) | M0–M7 的任务拆分与验收标准 |
| [验收报告](docs/验收报告.md) | 逐里程碑的实测记录与修过的问题 |
| [调研报告](docs/调研报告.md) | 竞品与技术调研 |
| [原型](design/prototype/) | 四个 HTML 页面 |

## License

尚未选定。仓库目前是私有状态，公开前请补上 `LICENSE` 文件。
