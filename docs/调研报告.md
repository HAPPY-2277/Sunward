# 跑步 App 市场调研与技术选型报告

> 版本：v1.0（四路并行调研全部合并完成；开源阵营、国际竞品、中国市场、技术要点均已在线核实并标注证据强度）
> 日期：2026-10-23（检索与核实当日完成）
> 目标读者：产品负责人 / 架构师 / 独立开发者
> 用途：为「记录跑步数据 + 路线 + 时间 + 速度，并生成周报 / 月报 / 年报可视化分析报告」的手机 App 立项提供市场与技术依据

---

## 0. 调研方法与可信度说明

本报告采用四路并行调研，结论按证据强度分级标注，请务必按标注使用：

| 标注 | 含义 | 使用建议 |
|---|---|---|
| ✅ 已核实 | 本次调研中通过在线抓取（官方文档、GitHub API、仓库构建文件、官网页面）直接确认 | 可直接引用 |
| 🟡 高置信推断 | 基于公开常识与行业惯例推断，未在本次在线核实 | 引用时注明"推断" |
| ⚪ 待核实 | 存在不确定性，或本次网络受限未能取得证据 | 立项前需二次确认 |

**本次调研的环境限制（影响证据覆盖率，须如实告知）：**

- **`web_search` 工具全程不可用**（缺少 API Key），因此无法做开放式检索，只能通过 `web_fetch` 抓取**已知 URL**。这意味着"发现新竞品"的能力下降，长尾产品可能遗漏；Bing 中国/百度/搜狗的结果也严重降级或为空。
- **可用的域名**：`api.github.com` 与 `raw.githubusercontent.com`（开源项目元数据证据质量最高）、`pub.dev`、`codeberg.org` 与 `raw.codeberg.org`、`support.strava.com` 与 `press.strava.com`、`github.com/strava`、**Apple iTunes Search API**（中国区与美区的官方描述/版本/评分/IAP 标价）、`developer.apple.com` 的 **JSON 文档接口**、`developers.google.cn`（可抓全文）、`developer.android.google.cn`（仅能确认 URL 与路径有效性，正文为 JS 渲染）、`lbs.amap.com`、`lbs.qq.com`、`maplibre.org`、`github.com/pmtiles`、`dontkillmyapp.com`、`topografix.com/gpx`、各产品官网（gotokeep.com、thejoyrun.com、codoon.com、zepp.com、polar.com、consumer.huawei.com）。
- **不可达的域名（导致对应结论降级为 🟡/⚪）**：`google.com`、`play.google.com`、`en.wikipedia.org`、`medium.com`、`archive.org`、`developer.android.com`（DNS 失败）、`support.google.com`、`mapsplatform.google.com`、`trainingpeaks.com` 支持站、`apps.apple.com` 网页版（重定向到中国区首页，取不到美区应用页）、`adidas.com`（反爬 403）、`nike.com/help`（JS 空壳）、`ir.keep.com`、`gov.cn`/`cac.gov.cn`/`npc.gov.cn` 的法条页。
- **绕过手段**：Apple 文档改抓 `https://developer.apple.com/tutorials/data/documentation/<路径>.json`；Android 文档改用中国镜像；竞品数据改用 iTunes Search API；技术栈改用各公司在 GitHub 的公开组织仓库反推（Strava 即如此）。
- 闭源商业产品（Strava、Nike、Keep 等）的**内部技术栈**几乎不可能从外部完全证实，本报告对这部分只做"有证据的推断"，并明确标注。
- ⚠️ **样本偏差提醒**：用户吐槽证据**全部来自 App Store（中国区）评论区**，Google Play 与各国产安卓商店的数据缺失；中国市场部分因此可能存在平台与人群偏差。

---

## 1. 结论速览（TL;DR）

1. **品类拥挤，但"报告能力"仍是短板，而你的需求恰好压在这个短板上。** 记录 GPS + 配速 + 路线是 2015 年就解决的基线能力。真正稀缺的是**可解释、可分享、带建议的周期报告**：Strava 的 Month in Sport / Year in Sport 是行业标杆，但偏"数据陈列 + 社交叙事"；**开源侧最强的 Runalyze 已于 2019 年终止开源版**（✅ 仓库归档、无许可证），移动端训练分析层出现空档。
2. **记录引擎的难点是平台性的，跨平台框架救不了你。** ✅ 已核实的硬约束：iOS 未配 `UIBackgroundModes=location` 就置 `allowsBackgroundLocationUpdates` 会**直接崩溃**；Android 15 起 `dataSync` 类前台服务**每 24 小时最多跑 6 小时**，但 **`location`/`health` 类型不受此限**；连 Strava 都要为华为机型专门写"记录被 OS 中断"的帮助文档。**结论：记录引擎（定位采样、滤波、落库、BLE）必须用原生 Kotlin/Swift 实现，无论上层用什么框架。**
3. **技术栈推荐（已用证据校准，与直觉相反）：** 小团队做国内外双市场 → **Flutter（UI/报表/业务）+ 自研原生记录引擎插件 + 双地图栈**；已有双端原生班底或要做手表独立 App → **原生双端，或 KMP 共享逻辑 + 原生 UI**。关键依据：✅ `geolocator` 14.1.2（3 天前发布）、`flutter_foreground_task` 11.0.3、`fl_chart` 1.3.0（1 天前）都很活跃，而 ❌ `flutter_background_service`（22 个月未更新）与 `isar`（2025-06 后无推送）必须避开；`compose-multiplatform` 19.4k★ 日更、Vico 已支持 Compose Multiplatform，KMP 路线的图表短板已补上。详见 §7.1 与 §7.6。
4. **报表引擎 = 服务端计算 + 客户端渲染 + 快照分享图，且周/月/年共用一个 `ReportBuilder(period)`。** 直接抄 Strava 已验证的机制（✅）：**卡片式月报、上月 ≥3 次活动才生成、每月 26 日刷新、仅移动端、成就最多 4 项按优先级排序、只匹配关键指标相差 30% 以内的对手、免费给趋势 / 付费给百分位排名**。
5. **⭐ 中国市场存在一个明确的窗口：外资专业跑步工具集体缺位。** ✅ Nike Run Club 与 adidas Running **中国区 App Store 不在架**；**Apple Fitness+ 未在大陆上线**（官网 404）；**Garmin Connect 中国区评分仅 2.99**（5,562 个评分），而国产头部普遍 4.8+。同时 ✅ Keep 官方口径 MAU 从 2021 年 3,440 万到 2022 年 3,640 万仅 +5.8%、会员渗透率 10.0%——**泛健身已见顶，"中文世界最好的专业跑步工具"这个位置是空的**。
6. **两个能立刻变成口碑的差异化点**：① **数据主权**——头部产品的用户级导出与 API 普遍封闭（✅ 咕咚 OpenAPI 是 to B 商务申请 + 付费套餐），"一键导出全量 GPX/TCX/FIT + 个人开放 API"几乎无对手；② **可量化的省电与断轨率**——全行业都在免责声明里承认后台 GPS 耗电、评论区常见断轨漂移抱怨。
7. **⚠️ 不要再集成 Google Fit。** ✅ 官方横幅明示：**Google Fit 各项 API（含 REST）将于 2026 年停用，且自 2024-05-01 起开发者已无法注册**；端侧改用 **Health Connect**（Android 14+ 平台内置），iOS 用 HealthKit，Wear OS 用 Health Services。
8. **地图按发行区域二选一，且存储铁律是"只存 WGS-84"。** 国内：✅ 高德"运动行业解决方案"（一个 Key 覆盖 Android/iOS/Web/鸿蒙）+ **猎鹰轨迹纠偏绑路（存储 1 年免费）**，⚠️ **8.1.0 起必须先调 `updatePrivacyShow`/`updatePrivacyAgree` 否则地图白屏**；海外：Mapbox 或 MapLibre + PMTiles/自建瓦片，⚠️ **OSM 官方瓦片政策明确禁止批量下载与离线使用，不可作生产底图**。
9. **合规不是可选项。** ✅ PIPL 下行踪轨迹属敏感个人信息（第 28 条）、须单独同意（第 29 条）；工信部 App 备案 + 软著 + 增值电信许可是上架前置；底图须来自持甲级测绘资质的图商（✅ 高德页脚"甲测资字 11112528"）。
10. **许可证决定你能抄什么。** 可安全复用：`gpxpy`(Apache-2.0)、`python-fitparse`(MIT)、`leaflet-elevation` 之外的图表栈、MapLibre 生态(BSD)；**AGPL-3.0 的 FitTrackee/Gadgetbridge/wger 只能参考设计，代码不可进闭源产品**；⚠️ `leaflet-elevation` 是 GPL-3.0（不是 MIT）；❌ Runalyze 已归档且 `license=null`，完全不可用。
11. **定价锚点（✅ 中国区实测）**：基础会员 12–19 元/月、48–58 元/季、98–218 元/年；**AI 高级会员可上探 68 元/月**（Keep 已验证，是普通会员的 3.6 倍）；硬件伴生 App 更便宜（华为 15 元/月、208 元/年），因为它们靠卖硬件赚钱。

---

## 2. 品类地图：跑步 App 的四种形态

理解竞品前先分清形态，因为它们的商业模式与技术重心完全不同：

| 形态 | 代表 | 核心价值 | 技术重心 | 变现 |
|---|---|---|---|---|
| A. 社交平台型 | Strava、Keep、咕咚、悦跑圈 | 社交认同、挑战、内容 | 后端 + 推荐 + 地图 + UGC | 订阅 + 电商 + 赛事 |
| B. 品牌生态型 | Nike Run Club、adidas Running、华为运动健康、Garmin Connect | 免费/绑定硬件，导流 | 与硬件/手表深度联动 | 卖鞋、卖表、卖生态 |
| C. 训练科学型 | TrainingPeaks、Runalyze、Polar Flow、COROS | 让认真训练的人变强 | 指标算法 + 长周期数据 | 订阅 + 教练服务 |
| D. 工具/隐私型 | RunnerUp、OpenTracks、OsmAnd、自托管 FitTrackee | 数据自主、离线、无广告 | 客户端工程 + 开放格式 | 捐赠 / 无 |

> **立项判断：** 你描述的需求（记录 + 报告）本质是 **C 与 D 的结合体**——工具型的数据自主 + 训练科学型的分析深度。这在过去是"真实但小众"的切口，但 ✅ 本轮调研发现**中文市场的这个切口现在是空的**：Nike Run Club 与 adidas Running 中国区不在架、Apple Fitness+ 未入华、Garmin Connect 中国区评分仅 2.99、开源侧最强的 Runalyze 已终止开源版（§5.3、§3.4）。
>
> **好处**：不必与 Strava/Keep 拼社交和 UGC，可直接占据"专业 + 干净 + 数据自主"的心智；**坏处**：单一市场的付费天花板确实低。但 ✅ 价格证据显示天花板没那么低——中国区基础会员锚点是 12–19 元/月，而 Keep 的**超级 AI 会员卖到 68 元/月**（普通会员的 3.6 倍），说明"分析与解读"本身是可以卖出溢价的，前提是别像 Keep 那样让用户觉得"AI 不能关"（§5.5）。
>
> **因此差异化必须同时押在三处**：① 报告的解释力与美观度（§8）；② 数据主权与导出自由（§5.5 机会 2）；③ 可量化的省电与断轨率（§6.4）。

---

## 3. 开源阵营调研（证据质量最高的部分）

### 3.1 已在线核实的项目事实总表

✅ 已核实。数据来源：GitHub REST API、Codeberg API、`raw.githubusercontent.com` 构建文件（`build.gradle` / `pubspec.yaml`）、各项目 README；抓取时间 2026-10-23。Star / 许可证 / 语言 / 推送时间均为 API 实测值。

| 项目 | 平台 | 语言与技术栈（证据来源） | 许可证 | Star | 最近推送 | 状态 |
|---|---|---|---|---|---|---|
| [RunnerUp](https://github.com/jonasoreland/runnerup) | Android（含 Wear OS 变体） | Java 17；地图 **osmdroid 6.1.20**（自由版）或可选 **Mapbox 11.32.x**（Play 版）；图表 **MPAndroidChart v4.0.1**；OkHttp 5.5.0；`androidx.core:core-location-altitude`；独立 **`:hrdevice`** 模块做 BLE/ANT+；内建 Strava / Runalyze / Dropbox 同步开关；AIDL 对外 API（`app/build.gradle` 确认） | GPL-3.0 | 961 | 2026-10-18 | 活跃（创建于 2012-11，222 open issues） |
| [OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks) | Android | Java；本地 **SQLite**（照片单独存文件）；BLE 心率 / 骑行速度距离 / 踏频 / 功率 / 跑步速度踏频，**支持无 GPS 的室内 BLE 训练**；气压计海拔（显示 EGM2008、导出 WGS84）；**自身不内建地图**，通过 **Dashboard API** 交给配套 OSMDashboard；导出 **KMZ 2.3（含照片）/ KML 2.3 / GPX 1.1**，每文件带唯一 `opentracks:trackid` 防重复；Public API 供 Tasker/Automate 启停；与 Gadgetbridge 集成（README 确认） | Apache-2.0 | 144（Codeberg 主）/ 1,402（GH 镜像，已归档） | 2026-10-21 | 活跃（187 releases，主仓在 Codeberg） |
| [FitoTrack](https://codeberg.org/jannis/FitoTrack) | Android | **Kotlin + Jetpack Compose**（compose-bom 2026.05.01、Material3）——已从早期 Java 迁移；离线矢量地图 **mapsforge 0.27.0** + androidsvg；**Room 2.8.4**；MPAndroidChart v3.1.0；BLE 用 **Nordic `no.nordicsemi.android:ble` 2.2.4**；Jackson dataformat-xml + StAX；`osmapi-traces` 上传 OSM；OAuth2（scribejava + androidbrowserhelper）；WorkManager；minSdk 26 / target 37，versionName 16.3（`app/build.gradle` 确认） | GPL-3.0 | 434（Codeberg） | 2026-10-15 | 活跃（45 releases） |
| [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) | Android（可穿戴桥接） | Java；BLE 为核心，逆向各家设备协议（Pebble / Mi Band / Amazfit / Garmin / Huawei / Casio / Fossil）；与 OpenTracks 通过 Dashboard API 集成，把实时统计推到手表 | AGPL-3.0 | 1,984（Codeberg 主）/ 4,601（GH 镜像，已归档） | 2026-10-22 | 极活跃（154 未合并 PR） |
| [OwnTracks Android](https://github.com/owntracks/android) | Android（+iOS） | Kotlin；**MQTT + HTTP + JSON**；自建 Recorder 后端；地理围栏、好友位置。**不含**运动记录/心率/分段（topics 确认） | EPL-1.0 | 1,769 | 2026-10-22 | 活跃（定位上报，非运动记录器） |
| [iOS Open GPX Tracker](https://github.com/merlos/iOS-Open-GPX-Tracker) | iOS / watchOS | Swift；基于 **OSM 瓦片**的离线地图（具体地图 SDK ⚪ 待核实）；GPX 记录与导出 | GPL-3.0 | 723 | 2026-08-25 | 活跃 |
| [OsmAnd](https://github.com/osmandapp/OsmAnd) | Android / iOS | Java；离线矢量 OSM（按国家/地区下载，可选完整数据或仅路网）；在线/缓存瓦片、Bing 卫星图；GPX 叠加；**GPX 行程记录可后台/息屏**；语音导航；等高线与坡面阴影为付费插件 | 核心 GPL-3.0，GitHub 标 "Other"（含 OsmAnd+ 买断 / Pro 订阅与资源混合许可） | 6,103 | 2026-10-22 | 非常活跃（3,487 open issues） |
| [gpx.studio](https://github.com/gpxstudio/gpx.studio) | Web | **SvelteKit + TypeScript**；地图 **MapLibre GL JS** + MapTiler 瓦片；路由引擎 **GraphHopper**；3D 地形 Mapterhorn；图表 **Chart.js**；本地存储 **Dexie.js(IndexedDB)**；GPX 解析 **fast-xml-parser**；UI shadcn-svelte + Tailwind + lucide；代码拆成独立 **`gpx` TS 库** + website（README 确认） | MIT | 1,245 | 2026-10-18 | 活跃 |
| [FitTrackee](https://codeberg.org/FitTrackee/FitTrackee) | Web（自托管） | **Python Flask 3.1 + Vue 3.5 + TypeScript**；**PostgreSQL 14–18 + PostGIS 3.4–3.6**；图表 Chart.js；地图 Leaflet + OSM；GPX 解析 gpxpy；工具链 ruff + mypy + prettier；**PyPI 包 + Docker 镜像**分发；Weblate 翻译（README 徽章 + topics 确认） | AGPL-3.0 | 1,172（GH 镜像） | 2026-10-21 | 活跃（主仓在 Codeberg） |
| [Runalyze](https://github.com/Runalyze/Runalyze) | Web（曾可自托管） | PHP（历史开源版，默认分支 `support/4.3.x`） | **无许可证**（license=null） | 226 | **2019-11-08** | **已停止开源**（仓库归档，README 原文："Discontinued as OpenSource"） |
| [Golden Cheetah](https://github.com/GoldenCheetah/GoldenCheetah) | 桌面 Win/macOS/Linux | **C++ / Qt**（GitHub language 字段误报为 Standard ML，由 topics `c-plus-plus`/`qt` 更正）；内嵌 **Python + R 运行时**做自定义指标；云同步 Strava/Withings/Today's Plan；ANT + BTLE；指标含 BikeStress、**TRIMP、RPE**、Critical Power、W'bal、Virtual Elevation、Banister/PMC 体能模型 | GPL-2.0 | 2,243 | 2026-10-20 | 活跃（**骑行/铁三为核心，跑步为次要支持**） |
| [wger](https://github.com/wger-project/wger) | Web（自托管） | Python **Django + DRF**；Docker Compose 自托管；营养数据接 Open Food Facts；多用户/健身房管理。**不含 GPS 记录** | AGPL-3.0 | 7,170 | 2026-10-21 | 活跃 |
| [wger Flutter](https://github.com/wger-project/flutter) | Android/iOS/桌面 | Dart（sdk ≥3.10）；状态 **Riverpod 3.x**；本地库 **drift(SQLite)** + drift_sqlite_async；同步 **PowerSync**；图表 **fl_chart 1.2.0**；健康数据 **health_bridge**（对接 Health Connect + HealthKit）；freezed/json_serializable/build_runner；versionName 2.1.1+280（`pubspec.yaml` 确认） | AGPL-3.0 | 988 | 2026-10-21 | 活跃 |
| [python-fitparse](https://github.com/dtcooper/python-fitparse) | 库(Python) | 解析 ANT/Garmin **.FIT** | MIT | 826 | 2025-01-28 | 维护偏弱但可用 |
| [gpxpy](https://github.com/tkrajina/gpxpy) | 库(Python) | GPX 解析/操作，**FitTrackee 在用** | Apache-2.0 | 1,108 | 2026-09-26 | 活跃 |
| [python-tcxparser](https://github.com/vkurup/python-tcxparser) | 库(Python) | 解析 Garmin **TCX**（现行维护版为 vkurup；旧的 `vbabiy/tcxparser` 已 404） | BSD-2-Clause | 92 | 2025-05-20 | 维护偏弱 |
| [leaflet-elevation](https://github.com/Raruto/leaflet-elevation) | 库(JS) | Leaflet 插件，用 **d3.js** 画海拔剖面，支持 GPX/TCX/KML/GeoJSON（Raruto 版为当前活跃主线） | GPL-3.0 | 281 | 2026-10-20 | 活跃 |
| gpx-player / georeel / Wanderer | Web / 桌面 / Web | 开源版 "Strava Flyby" 多轨迹回放（Python + Leaflet, MIT）／GPX→3D 地形飞越视频（PySide6 + Blender + ffmpeg，**自托管版 Relive**）／自托管步道与活动数据库（OpenTracks 兼容表列出） | MIT 等 | — | — | 生态参考 |

**未能定位的项目（如实标注，未编造）：** `GpsTrackServer` 在 GitHub 搜索 0 命中；`yohcop/trackprofiler` 返回 404（据既有认知 ⚪ 它曾是 Python + Google App Engine 的 GPX/TCX/FIT 剖面对比工具）；未发现知名的 `OpenStrava` 项目。三者当前地址与维护状态 ⚪ 待核实——调研期间 GitHub 搜索 API 触发限流。"开源 Strava 替代"这一需求，实际上是由 **FitTrackee（Web 后台）+ 各移动采集端** 组合承担的。

**从这张表能读出的四个结论：**

1. **开源跑步 App 的社区天花板不高。** 移动端项目 Star 多在 400–1,500 区间，说明可复用的是"设计与代码"，不是"社区活跃度"。
2. **采集端与分析端在开源生态里是解耦的。** 手机负责记录（OpenTracks / FitoTrack / RunnerUp），Web 负责分析（FitTrackee / Golden Cheetah），中间靠 **GPX/TCX/FIT 文件 + "导出即同步"** 打通。**含义：只要你的 App 把标准格式导出做成一等公民，就能直接接入整个现成的开源分析生态，而不必自己从零建后台。**
3. **FitTrackee 是报告系统最好的样板。** Flask 3.1 + Vue 3.5 + PostGIS + Chart.js + Leaflet + gpxpy，Docker 一键自托管，已内建周/月/年统计与仪表盘，且支持从 FitoTrack / OpenTracks / OsmAnd / RunnerUp 导入。⚠️ 但它是 **AGPL-3.0**：在联网服务中使用其代码须开源服务端；**仅参考设计与算法**则不受此约束（🟡 法律判断，建议咨询法务）。
4. **Runalyze 退出开源留下了明确空档。** 目前缺少"开源 + 可自托管 + 高级训练负荷/体能分析（PMC/CTL/ATL、跑力）"的跑步后台：FitTrackee 偏记录与基础统计，Golden Cheetah 偏桌面且以骑行为核心。**这正是你的差异化空间**（另注：RunnerUp 里仍保留的"上传 Runalyze"选项，指的是其托管 SaaS API，而非自托管）。

> 补充观察：OpenTracks、Gadgetbridge、FitTrackee、FitoTrack 均已把**主仓迁到 Codeberg**（去中心化/隐私导向），GitHub 仅作镜像或 CI。**核对这些项目的活跃度必须看 Codeberg，只看 GitHub 会误判为"已停更"。**

### 3.2 许可证对商用的影响（必读）

| 许可证 | 出现在 | 能否用于闭源商业 App | 注意事项 |
|---|---|---|---|
| MIT | gpx.studio 及其 `gpx` TS 库、python-fitparse、gpx-player | ✅ 最宽松 | 保留版权声明即可 |
| Apache-2.0 | **OpenTracks**、gpxpy | ✅ 可以 | 保留版权与 NOTICE，标注修改；含专利授权条款 |
| BSD-2-Clause | python-tcxparser | ✅ 可以 | 保留版权声明 |
| EPL-1.0 | OwnTracks | ⚠️ 弱传染 | 修改过的文件需开源，仅链接使用相对安全（🟡 建议咨询法务） |
| GPL-2.0 | Golden Cheetah | ⚠️ 有风险 | 衍生作品须以 GPL-2.0 开源 |
| GPL-3.0 | **RunnerUp、FitoTrack、iOS Open GPX Tracker、OsmAnd（核心）、leaflet-elevation** | ⚠️ 有风险 | 若构成"衍生作品"，你的 App 需以 GPL-3.0 开源；仅参考思路、自行实现则安全 |
| AGPL-3.0 | **FitTrackee、Gadgetbridge、wger（含 Flutter 客户端）** | ❌ 服务端衍生须开源 | 网络服务也触发开源义务，闭源商业后端不要直接复用其代码 |
| 无许可证 | Runalyze（归档仓库 `license=null`） | ❌ 不可用 | 无许可证＝保留所有权利，不得复制 |

> **实操建议（🟡 推断）：** 把开源项目当作"可执行的规格说明书"——读它们的 `ForegroundService`、BLE 处理、auto-pause 阈值、GPX 序列化代码，理解后**独立实现**；只有 MIT / Apache-2.0 / BSD 的代码可以直接搬。
> **易踩的坑：** `leaflet-elevation` 是 **GPL-3.0** 而非 MIT，闭源前端不要直接引入，改为自绘海拔剖面图。OsmAnd 的许可构成混合（核心 GPLv3 + 付费解锁 + 资源各异，GitHub 标 "Other"），商用集成前需逐项甄别。

### 3.3 可直接复用的资产清单（均已在线核实存在性与许可证）

**GPX / TCX / FIT 解析**

| 资产 | 语言 | 许可证 | 备注 |
|---|---|---|---|
| `gpxpy` | Python | Apache-2.0 | ✅ FitTrackee 正在用；1,108★，2026-09 仍活跃 |
| gpx.studio 的 **`gpx` TS 库** | TypeScript | MIT | ✅ 从 gpx.studio 拆出的独立库，前端可直接复用 |
| `fast-xml-parser` | JS | ⚪ | ✅ gpx.studio 在用 |
| Jackson dataformat-xml | Kotlin/Java | ⚪ | ✅ FitoTrack 的方案（Android 端解析 GPX/TCX） |
| `python-fitparse` | Python | MIT | ✅ 826★，2025-01 后维护偏弱但可用；FIT 是手表生态事实标准 |
| Garmin 官方 **FIT SDK** | C++/Java/C#/Python/TS | ⚪ 需注册 | 🟡 权威实现，商用前确认许可条款 |
| `python-tcxparser` | Python | BSD-2 | ✅ 现行维护版是 `vkurup/python-tcxparser`（旧的 `vbabiy` 已 404） |

**地图与路线**

| 资产 | 用途 | 备注 |
|---|---|---|
| **mapsforge 0.27.0** | Android 纯离线矢量 OSM | ✅ FitoTrack 在用，**无需 token**，最适合"离线 + 隐私"定位 |
| **osmdroid 6.1.20** | Android 在线/离线瓦片 | ✅ RunnerUp 自由版在用 |
| **Leaflet** | Web 地图 | ✅ FitTrackee 在用 |
| **MapLibre GL JS** | Web 矢量地图 / 3D | ✅ gpx.studio 在用（配 MapTiler 瓦片） |
| Mapbox | 商业矢量地图 | ⚠️ 需 token，属非自由依赖；RunnerUp 用 **build flavor** 把它隔离在 Play 版里 |
| **GraphHopper / OSRM** | 路线规划引擎 | ✅ gpx.studio 用 GraphHopper |
| Mapterhorn / Mapbox Terrain | 地形与 3D | ✅ gpx.studio 用 Mapterhorn |
| OSMDashboard | 独立的轨迹显示端 | ✅ OpenTracks 配套，见下方"工程实践" |

**图表**

| 资产 | 端 | 备注 |
|---|---|---|
| **Chart.js** | Web | ✅ FitTrackee 与 gpx.studio 都在用 |
| **MPAndroidChart** | Android | ✅ RunnerUp 用 v4.0.1、FitoTrack 用 v3.1.0 |
| **fl_chart 1.2.0** | Flutter | ✅ wger Flutter 在用 |
| **leaflet-elevation**（Raruto 主线） | Web | ✅ 281★，用 d3 画海拔剖面，支持 GPX/TCX/KML/GeoJSON；⚠️ **GPL-3.0** |
| Charts / Swift Charts | iOS | 🟡 业界常规选择，本次未核实具体仓库状态 |

**存储与同步**

| 资产 | 备注 |
|---|---|
| SQLite | ✅ OpenTracks 直接用 `database.db`，照片单独存文件 |
| **Room 2.8.4** | ✅ FitoTrack 在用 |
| **drift + drift_sqlite_async** | ✅ wger Flutter 在用（Flutter 侧的 SQL 化本地库） |
| **PostgreSQL 14–18 + PostGIS 3.4–3.6** | ✅ FitTrackee 在用——地理查询用 PostGIS 是成熟做法 |
| **PowerSync** | ✅ wger Flutter 在用，本地优先 + 云同步的现成方案 |

**BLE 与可穿戴**

| 资产 | 备注 |
|---|---|
| Nordic `no.nordicsemi.android:ble` 2.2.4 | ✅ FitoTrack 在用，Android BLE 的稳妥选择 |
| RunnerUp 的 `:hrdevice` 模块 | ✅ BLE + ANT+，另支持 Polar WearLink、Zephyr——多传感器适配层的参考实现 |
| **Gadgetbridge** | ✅ AGPL-3.0，开源可穿戴桥接事实标准，覆盖 Pebble/Mi Band/Amazfit/Garmin/Huawei/Casio/Fossil；已与 OpenTracks 打通（手表显示实时统计） |
| `health_bridge` / `health` 包 | ✅ wger Flutter 在用，一个 API 同时对接 **Health Connect + HealthKit** |

**最值得抄的三条工程实践**

1. **RunnerUp 的 free / nonfree 双 build flavor**：用构建变体隔离 Mapbox、Google Play Services 等非自由依赖，从而**同时发布 F-Droid 纯净版与 Play 版**。如果你打算做"无广告、无追踪"的定位，这是现成的工程范式。
2. **OpenTracks 的"记录 App 与地图 App 解耦"**：主 App 不内建地图，通过 **Dashboard API** 把轨迹交给独立的 OSMDashboard 渲染。收益是**主 App 体积与权限面显著缩小**，地图实现可替换。
3. **OpenTracks 的"导出即同步"**：每次记录后自动导出 GPX/KMZ 到文件系统，由 Nextcloud 等第三方负责同步，并用唯一 `opentracks:trackid` 去重。**这意味着你可以在不自建云端的前提下提供备份与多端能力**——对独立开发者极有价值。

**可视化增值（差异化灵感）**

- `kirienko/gpx-player`（Python + Leaflet，MIT）：✅ 开源版 "Strava Flyby"，多轨迹同时回放。
- `elegos/georeel`（PySide6 + Blender + ffmpeg）：✅ 自托管版 Relive，把 GPX 渲染成 3D 地形飞越视频。
- `Wanderer`：✅ 自托管步道/活动数据库，出现在 OpenTracks 的兼容列表中。

### 3.4 开源阵营的系统性短板 = 你的机会

基于上述项目的实际功能画像（✅ 证据来自 README / 构建文件）：

- **报告能力薄弱**：Web 侧 FitTrackee 有周/月/年统计与仪表盘，但**移动端**（OpenTracks / FitoTrack / RunnerUp）基本只有列表 + 简单图表，没有周期性叙事、对比解读与训练建议。
- **训练科学层几乎缺失**：移动端没有实现训练负荷、恢复建议、成绩预测；桌面侧 Golden Cheetah 有 Banister/PMC、TRIMP、RPE、Critical Power，但它是 **C++/Qt 桌面软件且以骑行/铁三为核心**，跑步只是次要支持，无法嵌入手机。
- **社交与动态流完全空白**：所有开源项目都刻意不做 Strava 式社交（关注、动态、赛段排行榜）；唯一相关的是 gpx-player 的 Flyby 式多轨迹回放。
- **iOS 侧极弱**：iOS 只有 Open GPX Tracker（723★，基础 GPX 记录，无传感器/分析/训练）。跨端一致性目前只能靠"手机记录 + 自托管 Web 分析"拼装。
- **UI/UX 新旧分化**：RunnerUp 仍是 Java + 传统 View（2012 年项目，222 个未决 issue）；**FitoTrack 已迁到 Kotlin + Compose + Material3**，是当前最现代、最"纯自由"的 Android 开源底座。

**机会矩阵（按投入产出排序）：**

| 缺口 | 谁在补 | 机会评级 | 说明 |
|---|---|---|---|
| 会"讲人话"的周期报告（结论 + 建议，而非数字罗列） | 开源：无；闭源：Strava/Garmin 偏数据陈列 | ★★★ | **你的核心差异化**，见 §8 |
| 高级训练分析（训练负荷/PMC/跑力）在移动端可得 | Runalyze 已退出开源，Golden Cheetah 是桌面骑行向 | ★★★ | 若走开源自托管路线，这是明确蓝海 |
| 美观可分享的年报长图 | Strava Year in Sport（闭源）、Relive（视频，重） | ★★ | 传播引擎，成本低于视频 |
| iOS + Android 一致的记录体验 | 开源只有 Android | ★★ | 自然形成的差异，但成本翻倍 |
| 社交 / 赛段排行榜 | 开源完全空白 | ★ | 最难做、烧钱、且与你的定位不符，**早期不建议** |

> **一句话战略：** 开源生态已经证明了"记录"可以做得很干净（OpenTracks 模式），但**没有人把"分析与人话报告"做好**。你应当把工程重心从"再做一个记录器"转向"做一个能解读数据的教练式报告系统"，记录引擎则尽量沿用已收敛的开源栈（Kotlin + Compose + Room + mapsforge/osmdroid + Nordic BLE + MPAndroidChart）。

---

## 4. 国际闭源竞品调研

> **证据来源（✅ 均为本次在线抓取）**：Strava 官方帮助中心 `support.strava.com`（2026-09 更新的订阅功能说明）、Strava 新闻室 `press.strava.com`、Strava 在 GitHub 的公开组织仓库 `github.com/strava`（用于反推技术栈）、Apple **iTunes Search API**（各 App 的官方描述、版本号、评分与评分数）、`polar.com` 官方产品页。
> **本章的网络限制（影响证据覆盖）**：`google.com`、`play.google.com`、`en.wikipedia.org`、`medium.com`、`archive.org` 不可达；`apps.apple.com` 网页版会重定向到中国区首页（取不到美区应用页）；`adidas.com` 官网反爬 403；`nike.com/help` 为 JS 空壳。因此 **Google Play 侧的评分与评论数据缺失**，Android 侧证据以 iTunes API 与厂商官方文档为主。

### 4.1 主要玩家与定位

| 产品 | 定位 | 商业模式 | 报告/分析能力 | 证据强度 |
|---|---|---|---|---|
| **Strava** | 运动员社交平台（社交 + 赛段 + 路线） | 免费 + 订阅（⚪ 具体价格未取到） | **最强且最成体系**：Month in Sport 月报、Year in Sport 年报、Fitness & Freshness、Relative Effort、Performance Predictions | ✅ 帮助中心全文 |
| **Nike Run Club** | 品牌导流 + 语音指导跑 | **完全免费，无订阅** | 中：挑战/徽章/好友排行榜，弱于 Strava | ✅ iTunes API 官方描述 |
| **adidas Running**（Runtastic 血统） | 训练计划 + 多运动记录 | 免费 + 订阅 | 中 | ✅ iTunes API |
| **Garmin Connect** | 硬件生态的分析后台 | 随硬件免费 | **强**：健康趋势、自定义训练/课程、徽章 | ✅ iTunes API（更深的 Training Readiness 等指标页 ⚪ 未取到） |
| **Polar Flow** | 硬件生态 + 训练科学 | 平台免费随设备赠送 | **强**：Smart Coaching 全家桶 + 新增 Diary Trends 双周总览 | ✅ polar.com + iTunes API |
| COROS | 硬件生态 + 训练系统（EvoLab） | 随硬件免费 | 强（跑力、训练负荷、赛事预测） | ⚪ 本轮未抓到 |
| Suunto | 硬件生态 | 随硬件免费 + 订阅 | 中 | ⚪ 本轮未抓到 |
| TrainingPeaks | 教练/严肃训练者分析平台 | 订阅 | **理论最强**：PMC/ATL/CTL/TSB | ⚪ 支持站不可达 |
| Komoot / AllTrails / Relive | 路线规划 / 徒步数据库 / 3D 视频回顾 | 免费 + 订阅 | 弱或特殊形态 | ⚪ 本轮未抓到 |
| Runkeeper / MapMyRun / Fitbit / Samsung Health / Apple Fitness | 记录或健康聚合 | 混合 | 中 | ⚪ 本轮未抓到 |

### 4.2 Strava 深潜（本报告最重要的单体样本）

#### 4.2.1 订阅解锁的完整能力清单（✅ support.strava.com，2026-09）

**分析与训练类：**
- **Relative Effort（相对努力度）**：用心率**或自觉受累 RPE** 计算，给出周累积图 + **3 周滑动均值的建议区间带**——即"告诉你本周该练多少"，而不只是"你练了多少"。
- **Fitness & Freshness（体能与状态）**：明确基于 **Banister 1975 的脉冲响应模型**，输出 **CTL（体能）/ ATL（疲劳）/ TSB（状态）**；有功率时用功率算 Training Load，无功率时用 Relative Effort 拟合。
- **Performance Predictions**：用**机器学习**预测 5K / 10K / 半马 / 全马成绩。
- **GAP（坡度调整配速）**：官方注明基于 **Davies / Minetti 的研究**。
- **Pace / Power 分析**：6 档配速区间、Estimated FTP、Power Curve。
- **Best Efforts**：400m 到 50K 的个人纪录矩阵。
- **Matched Activities / Activity Replay / Training Log / Streaks**。
- **Instant Workouts**：把个性化周训练**下发到 Garmin / COROS / Wahoo / Pioneer** 设备。

**地图与路线类：**
- Suggested Routes、个人热力图（**周 / 年 / 全部**三档）、离线路线地图（订阅）、3D 地图、坡度/雪崩/坡向/POI 图层。
- **Flyover**：把活动渲染成 3D 视频用于 Instagram 分享。

**安全与实时类：**
- **Beacon**（实时位置分享给紧急联系人）——**App 内免费，设备端需订阅**；Live Performance Data、Live Segments。
- 赛段：完整排行榜 + 过滤榜（时间 / 关注者 / 俱乐部 / 年龄 / 体重）。

**其他：** Group Challenges、Apple WeatherKit 天气、力量训练的肌肉图、训练计划。

> ⚠️ 重要动向：**Strava 的跑步训练计划正在迁移到 Runna（Strava 已收购 Runna）**——说明连行业龙头也把"结构化训练计划"当作独立产品来经营，而不是塞在主 App 里。

#### 4.2.2 月报「Month in Sport」的具体设计（✅ 已核实，对你最有直接参考价值）

这是你最该抄的部分，因为它是**已被市场验证的月报形态**：

| 要素 | Strava 的做法 | 对你的启示 |
|---|---|---|
| 形态 | **卡片式**（不是一长串图表） | 月报应是"可滑动的卡片序列"，每卡一个结论 |
| 卡片 1 | **Active Days**（活跃天数） | 用"天数"而非"里程"作为第一个指标，对普通跑者更友好、更不易挫败 |
| 卡片 2 | **Trophy Case**：最多 **4 项**成就，**按优先级排序** | 成就要限量并按优先级取舍，否则会稀释重点 |
| 卡片 3 | **Social Competition**：匹配"关键指标相差 **30% 以内**的互关好友"做对比 | **相似水平对比**是社交化报告的精髓——差距过大就没有可比性 |
| 卡片 4 | **Totals**：订阅版含**百分位排名** + 可自定义分享；免费版含**近 12 个月柱状图** | 免费给趋势、付费给排名——这是很干净的分层设计 |
| 触发门槛 | **上月需 ≥3 次活动**才生成 | 数据太少时不出报告，避免"尴尬的空报告" |
| 刷新时间 | **每月 26 日** | 错峰生成，避开月初服务器高峰，也覆盖月末 |
| 平台 | **仅移动端** | 报告是移动端体验，不是 Web 报表 |
| 年报 | **Year in Sport 为季节性开放**，2026-01-23 下线（⚪ 具体卡片内容未取到） | 年报做成"限时活动"能制造传播峰值 |

#### 4.2.3 技术栈反推（✅ 证据来自 github.com/strava 的公开仓库）

| 层 | 证据 | 结论 |
|---|---|---|
| 主后端 | Ruby 仓库：`adyen`（支付）、`thrift_client`、`passenger_exporter` | **Ruby on Rails + nginx/Passenger**，服务间用 Thrift |
| 大数据 | Scala 仓库：`finagle-prometheus`、`sparksql-scalapb` | **Spark + ScalaPB**（Finagle 生态） |
| 基础设施 | Go 仓库：`ec2metaproxy`、`bamboo`、`go.serversets` | **Mesos/Marathon + HAProxy + ZooKeeper**，Go 写基础设施工具 |
| 地图渲染 | `go-mapnik` + **OSM iD 编辑器的 fork** | **自建地图渲染与描图管线**（不完全依赖第三方） |
| 地图数据 | ✅ 官方帮助中心确认 | **Mapbox + OpenStreetMap** |
| 监控 | `grafanalib` + Prometheus | **Grafana + Prometheus** |
| iOS | 公开的 Swift 风格指南 | **原生 Swift** |
| CMS / 客服 / 支付 | Contentful（stories 站）、Intercom（从 Zendesk 迁移）、Adyen | — |
| 开放 API | ✅ API v3 端点已确认 | Streams、Segments、Routes（**GPX/TCX 导出**）、Uploads、Laps、HR/Power Zones、`estimatedFTP` 字段 |

> **对你的意义：** Strava 的栈是"Rails 单体 + Spark 分析 + Go 基础设施 + 自建地图管线"，这是**十亿美元级公司的规模**，你不需要照搬。但有两点值得学：①**地图数据用 Mapbox + OSM，渲染管线自建**（说明地图是可替换的供应商，不是绑定）；②**API v3 的数据模型（Activity / Laps / Streams / Zones / estimatedFTP）就是行业公认的活动数据模型**，你的 §6.1 数据模型应与它兼容，这样导入导出与第三方互通才顺畅。

#### 4.2.4 2026 年动向（✅ press.strava.com）

- **收购 Runna**，跑步训练计划向其迁移；赛事/俱乐部活动发现接入 Runna 赛事数据库。
- **推出 MCP Connector 接入 Claude**——把运动数据开放给 AI 助手查询。这是"AI 教练"的另一种落地路径：**不做自己的 AI，而是把数据变成 AI 可调用的工具**。
- **与 Samsung 合作**：Galaxy Watch 预装 + 路线接入 Samsung Health。
- **与 adidas adiClub 积分打通**（跨品牌忠诚度互操作）。
- **turn-by-turn 语音导航即将上线**。
- ⚠️ 用户量口径不一致：一处自称 **1.95 亿运动员**，另一页写 **1.2 亿**——引用时须注明出处与时间。

### 4.3 报告能力横向对比（你的核心需求）

| 产品 | 周报 | 月报 | 年报 | 报告特质 |
|---|---|---|---|---|
| **Strava** | ✅ Weekly Summary + Relative Effort 周累积与 3 周滑动均值建议带 | ✅ **Month in Sport**（4 类卡片、百分位排名、26 日刷新、≥3 次门槛） | ✅ Year in Sport（季节性限时） | **社交对比 + 成就叙事 + 分层付费** |
| **Polar Flow** | ✅ App 7.37.0 新增 **Diary Trends**（近两周数据总览，实质是双周报） | 🟡 Web 端 flow.polar.com 提供报告 | 🟡 | **训练科学导向**：Running Index（官方称"你的跑步 VO2max"）、Running Program、FitSpark、Work-Rest Guide、Walking Test、自适应 Fitness Program |
| **Garmin Connect** | 🟡 | 🟡 | 🟡 | 个性化主屏 + 健康趋势 + 徽章；深度指标（Training Readiness 等）⚪ 未取到细节 |
| **Nike Run Club** | 🟡 | 🟡 | 🟡 | 挑战 + 徽章 + 好友排行榜 + 实时 Cheers；**完全免费**，报告不是卖点 |
| **adidas Running** | 🟡 | 🟡 | 🟡 | 全球挑战 / 虚拟赛 / adiClub 奖励；个性化训练计划（目标问卷 + 桌面小组件） |

**从对比中提炼的三条设计准则：**

1. **报告要有"门槛"和"节奏"。** Strava 的"上月 ≥3 次活动才出月报""每月 26 日刷新"不是随意设定：前者避免空报告带来的挫败感，后者错峰生成并制造固定期待。
2. **免费给趋势，付费给排名。** Strava 的 Totals 卡片在免费版给近 12 个月柱状图、订阅版给百分位排名——**排名是天然的付费点**，因为它需要全量用户数据，个人无法自算。
3. **对比对象要"势均力敌"。** Strava 只匹配关键指标相差 30% 以内的互关好友。若无社交关系链，可退化为"**与过去的自己对比**"或"**与同水平匿名分位数对比**"。

### 4.4 国际产品的功能基线（"没有就会被差评"的能力）

- GPS 记录 + 实时配速/距离/时长显示，锁屏与后台可持续记录
- 自动暂停（Auto Pause）与每公里自动分段（Lap / Split）
- 语音播报（每公里配速、心率、目标进度）
- 心率：BLE 心率带 / 手表同步（✅ Strava、Polar、Garmin 均以心率为核心指标来源）
- 结构化训练下发到设备（✅ Strava Instant Workouts 支持 Garmin/COROS/Wahoo/Pioneer）
- 导入导出 GPX / TCX / FIT，第三方互通（✅ Strava API v3 提供 Routes 的 GPX/TCX 导出）
- 地图路线回放 + 海拔剖面 + 配速着色轨迹
- 社交：动态流、Kudos、俱乐部、挑战、赛段排行榜与过滤榜
- 安全：实时位置分享（✅ Strava Beacon，App 内免费、设备端订阅）
- 装备里程统计与换鞋提醒（✅ Nike Run Club 有鞋款打标 + 里程提醒）
- 隐私区域（Privacy Zone）与地图可见性控制
- 周报 / 月报 / 年度总结

### 4.5 值得抄的具体设计（✅ 已核实版）

1. **Relative Effort 的"建议区间带"**：不只画你的周努力分曲线，还叠加 **3 周滑动均值**形成的建议区间——一张图同时回答"我练了多少"和"我该练多少"。实现成本低（§6.3 的 TRIMP + 滑动均值），感知价值极高。
2. **Fitness & Freshness 用公开学术模型**：Strava 明确标注基于 **Banister 1975 脉冲响应模型**。这告诉你：**不需要自研算法，把经典模型做对、讲清楚就够专业**。CTL/ATL/TSB 三线图是严肃跑者的刚需。
3. **GAP 坡度调整配速引用具体研究**（Davies / Minetti）：在 UI 里注明算法依据，是建立专业信任的廉价手段。
4. **Month in Sport 的卡片化 + 限量成就**（详见 §4.2.2）。
5. **Beacon 的免费/付费切分**：安全功能在 App 内免费（建立信任与口碑），在设备端收费。
6. **MCP Connector 思路**：与其自研 AI 教练，不如**把运动数据做成 AI 可调用的接口**，让 Claude 类助手替用户解读——对独立开发者是极高杠杆的差异化。
7. **Relive / Strava Flyover 的"轨迹变视频"**：分享物料的形态创新；对独立开发者而言静态长图（§8.5）是性价比更高的等价物，开源侧还有 `georeel` 可参考（§3.3）。

### 4.6 本章待核实清单

- ⚪ 各产品**订阅价格与权益明细**（Strava pricing 页未取到）
- ⚪ **Year in Sport 的具体卡片内容**（季节性下线，仅确认 2026-01-23 下线时间）
- ⚪ COROS / Suunto / TrainingPeaks / Komoot / AllTrails / Relive / Runkeeper / MapMyRun / Fitbit / Samsung Health / Apple Fitness 的详情
- ⚪ Garmin 的 Training Readiness、Body Battery 等指标页细节
- ⚪ **Nike Run Club 于 2022 年退出 Wear OS** 一事（新闻源不可达，未二次确认）
- ⚪ adidas Running 的 Live Tracking（官方 App 描述中未出现）
- ⚪ Google Play 侧的评分、评论与用户痛点样本（域名不可达）
- ⚪ Strava 用户量的准确口径（1.95 亿 vs 1.2 亿，官方页面自相矛盾）

---

## 5. 中国市场竞品调研

> **证据来源（✅ 均为本次在线抓取，2026-10-23）**：Apple **iTunes Search / Lookup API（country=cn）**——可拿到中国区官方描述、版本号、评分与评分数、IAP 标价；App Store 中国区产品页与**评论区原文**；各产品官网（gotokeep.com 含官方运营数据、thejoyrun.com、codoon.com 含 OpenAPI 条款页、zepp.com、consumer.huawei.com）；**高德开放平台与腾讯位置服务官方文档全文**。
> **本章的网络限制**：Bing 中国/百度/搜狗结果严重降级或为空，Wikipedia 与 web.archive.org 不可达；`ir.keep.com`（Keep 投资者关系）、`gov.cn` 的 PIPL 文本、`cac.gov.cn` 的认定方法、`developers.weixin.qq.com` 的 `wx.getWeRunData` 文档、`consumer.huawei.com/cn/support/huawei-health/` 当日均 404/不可达。**因此 Android 侧（各国产商店）的评分与评论数据缺失，Google Play 数据同样缺失。** 未编造任何价格、下载量或营收数字；厂商自报用户量一律标注为营销口径。

### 5.1 主要玩家与已核实的价格锚点

中国区 App Store IAP 标价（✅ 2026-10-23 实测）与评分：

| 产品 | 评分（中国区，评分数） | 会员定价（元） | 定位 | 关键差异点 |
|---|---|---|---|---|
| **Keep** | 4.9（约 295 万） | 会员 19/月、58/季、218/年；**超级 AI 会员 68/月、188/季、648/年**；直播畅练卡 28/月、70/季、273/年 | AI 运动教练 + 社区 + 消费品电商 | 内容与电商生态最强；跑步只是模块之一 |
| **悦跑圈 Joyrun** | 4.9（33 万） | 19/月、58/季、218/年 | 跑步垂直 + 马拉松赛事服务 | **数据防作弊系统**、线上马拉松开创者、实体奖牌文化 |
| **咕咚 Codoon** | 4.9（34 万） | 18/月、48/季、168/年（三家最低） | 一站式运动 + 赛事 + 硬件 + 企业 | **接入佳明/松拓/华为/高驰**；已上 **HarmonyOS 原生版**；有海外版 Runtopia |
| **华为运动健康** | 4.86（71 万） | 「活力人生」**首月 1 元、续 15/月、年卡 208** | 鸿蒙生态穿戴伴生 | TruSport 科学体系；硬件驱动 |
| **小米运动健康** | —（188 万评分） | 免费（无订阅条款） | 硬件伴生 | 向 Apple 健康同步步数/距离/能量/睡眠/心率/**有氧适能** |
| **Zepp**（原 Amazfit App） | 4.78（7.35 万） | 免费 | 硬件伴生 + AI 健康预警 | 2020-08-25 由 Amazfit 更名 |
| **Zepp Life**（原小米运动） | 4.73（34.8 万） | 免费 | 硬件伴生 | 2022-03-16 更名并转由华米运营 |
| **悦动圈** | 4.85（81 万） | 12/月、28/3 月、98/年；自动续订 10/月 | 计步/健走 + 游戏化激励 | **红包挑战赛**（每日 2 公里赢现金）；**每周健康周报自动生成**；Apple 分级 17+（含"赌博/竞赛"提示） |
| **Garmin Connect** | **2.99（5,562）** | 免费 | 硬件伴生 | ⚠️ **中国区评分仅 2.99**，与国产 4.8+ 形成断崖式反差 |
| **COROS 高驰** | 4.61（3,084） | 免费 | 硬件伴生 | 描述明示**与微信运动、悦跑圈、Keep 数据互联** |
| 行者（骑行） | 4.68（5.9 万） | PRO 18/月、50/季、188/年 | 骑行垂直 | 路书离线导航、3D 路线/海拔图、实物金牌挑战 |
| 两步路户外助手 | 4.62（2.9 万） | VIP 18/月、50/季、198/年 | 户外轨迹 | **看广告抽会员**（广告变现明显） |

**价格结论（可直接用于定价）：** 中国区基础会员的市场锚点是 **12–19 元/月、48–58 元/季、98–218 元/年**；**AI 高级会员可上探到 68 元/月**（Keep 已验证，是普通会员的 3.6 倍定价）；硬件伴生 App 的会员更便宜（华为 15 元/月、208 元/年），因为它们靠卖硬件赚钱。

### 5.2 逐产品要点（✅ 已核实）

**Keep** —— 官方"发展历程"页披露的运营数据是本章唯一可溯源的规模数据：2016-06 MAU 1,000 万；2017-08 累计用户 1 亿；2019-07 订阅会员 100 万；2020-12 软银愿景领投 F 轮 3.6 亿美元；**2021 全年平均 MAU 3,440 万 → 2022 全年平均 MAU 3,640 万（约 +5.8%）、平均月度订阅会员 362 万、会员渗透率 10.0%**。官网自称"2 亿用户"、App Store 文案"4 亿运动爱好者"（营销口径，⚪ 数字待核实）；2023 年后数据需查港股年报（`ir.keep.com` 当日不可达）。
功能侧（App Store 描述 + 9.1 更新日志确认）：户外跑/跑步机/越野跑、**AI 语音互动跑**（跑动中 AI 实时反馈与鼓励）、剧情跑、组队跑、一键搜索附近热门路线、语音播报（官方注明后台播报保持 GPS 连接、耗电）；万节课程 + 直播课 + AI 教练"卡卡"排期；记录最大摄氧量/HRV/心率/体重三围；HealthKit 双向同步；拍照识别食物热量；"身体年龄"；自研 **Keepace.ai 运动健康大模型**做吃练睡解读；线上赛 + 特色奖牌电商。
**技术栈线索已断**：GitHub 官方组织 `gotokeep` 现仅剩 1 个 2015 年的 Ruby 小仓库，历史开源项目 Nuwa（Android 热修复）已 404 ✅——**Keep 是否用 Flutter/RN 无法从公开渠道证实（⚪ 待核实），业内流传的分享文章原始链接不可达，不建议引用**。可确认的间接线索：iOS 包体积 515MB，单 App 覆盖 iPhone/iPad/Watch/iMessage。地图供应商与后端语言均未披露。

**悦跑圈** —— Android 包名 `com.linkedtech.joyRunner` ✅；官网为传统 PHP 结构（`about.php`/`join.php`）✅，🟡 推断技术栈历史较久；深度绑定微信生态（公众号 + 小程序线上赛）✅。资质齐全：粤ICP备13015116号、**增值电信业务经营许可证 粤B2-20220493**、食品经营许可证（奖牌等实物电商配套）✅。

**咕咚** —— 生态最宽：咕咚 APP / **企业咕咚（B2B 员工健身）** / 咕咚赛事 / 咕咚装备（X3-RE 手表、21K 逐日 PRO 跑鞋、跑步精灵、智能跳绳、体脂秤）/ 咕咚路线 / 海外版 **Runtopia** / 衍生工具 App ✅；**已有 HarmonyOS 原生版**（`com.codoon.gps.hos`）✅ → iOS/Android/鸿蒙三端。
**数据开放是 to B 收费模式**：咕咚 OpenAPI 走 OAuth2.0，**非公开自助接入，需商务申请**；付费数据套餐示例为"1 万授权账户 + 10 万次/年数据访问"，智能硬件接入最低 1 万台、按月结算 ✅。**用户级的 GPX/TCX 自助导出能力 ⚪ 待核实**——这本身就是机会（见 §5.5）。

**华为运动健康 / TruSport** —— TruSport 是华为的运动科学品牌（2021 发布），指标体系含**跑力指数（官方英文名 Running Ability Index，缩写 RAI）**、训练负荷（有氧/无氧）、训练压力、恢复时间建议、VO2Max、心率 5 区间、跑姿指标（步频/步幅/触地时间/冲击），部分表款支持**腕上跑步功率**（🟡 官方专页当日不可达，属常识性描述，⚪ 待核实）。v17 更新带来首页改版、**健康洞察（AI 健康解读）**、生理周期桌面卡片 ✅。GT 7 Pro 产品页确认：越野跑/滑雪/骑行/高尔夫进阶模式、光学心率+气压+温度+ECG+深度计、21 天续航（越野跑模式 51 小时）、适配 HarmonyOS 2.0+/Android 9+/iOS 13+、¥2,688 起 ✅。注意**荣耀已独立"荣耀运动健康"**（100+ 运动类型）✅ → 华为系数据入口分裂。

**小米 / Zepp 系** —— 现行小米运动健康描述用**"活力指标"**（中高强度活动时长 + 站立目标，"活力彩虹"）而非 PAI ✅；小米手环时代的 **PAI 活力指数**（Personal Activity Intelligence，源自挪威 HUNT 研究、华米获授权，把心率折算为个性化周活动分值，目标周 PAI ≥ 100）是否仍为主打 ⚪ 待核实。三者均免费、无订阅条款 ✅ → 变现靠硬件。

**垂直轨迹类** —— **六只脚**集成多地图源（谷歌街道图/卫星图/地形图、OCM、OSM、高德）+ 离线地图下载与离线导航 ✅，说明**户外场景对多图源与离线瓦片有刚需**；**两步路**靠"看广告抽会员"变现 ✅。

### 5.3 市场格局与趋势（仅列可溯源结论）

1. **Keep 领跑但增长见顶**：官方口径 MAU 2021 → 2022 仅 +5.8%，会员渗透率 10.0% ✅。
2. **第二梯队垂直分化**：悦跑圈=马拉松赛事服务 + 防作弊；咕咚=平台 + 硬件 + 企业 + 海外；悦动圈=红包游戏化吃下沉市场 ✅。
3. **硬件厂商伴生 App 占据免费入口**：小米运动健康 188 万评分、华为 71 万评分 ✅。手表用户天然沉淀在厂商 App，独立跑步 App 只能靠"**跨设备聚合 + 社区 + 赛事**"差异化——Keep 与咕咚都把"接入佳明/松拓/华为/高驰"当作核心卖点 ✅。
4. **⭐ 外资全面退场（本章最重要的战略发现）**：
   - **Nike Run Club 中国区不在架**：检索只返回第三方仿名 App（"跑步—跑步俱乐部"，Miro Consulting SRL）✅（Nike 2022 年停止 NRC 大陆服务为 🟡 常识，原始公告 ⚪ 待核实）；
   - **adidas Running 中国区不在架**：检索只返回悦跑圈 ✅；
   - **Apple Fitness+ 未在中国大陆上线**：`apple.com.cn/apple-fitness-plus/` 返回 404，且苹果中国官网页脚"娱乐"栏只有 Music/播客/App Store ✅；
   - **Garmin Connect 虽在架，但中国区评分仅 2.99（5,562 个评分）** ✅。
   → **结论：中文世界的"专业跑步工具"位置是空的。** 进阶跑者既拿不到 NRC 的语音指导跑，也受不了 Garmin Connect 的同步体验，只能在 Keep/咕咚的"泛健身"产品里凑合。
5. **奖牌经济是共同第二曲线**：Keep（特色奖牌）、悦跑圈（线上马开创者 + 勋章殿）、行者（实物金牌挑战）、两步路（赛事系统）全部布局实体奖牌 ✅；但 Keep 评论区的退款纠纷提示售后合规风险 ✅。
6. **AI 是 2025–2026 主线**：Keep 9.1 主推 Keepace.ai + AI 语音互动跑 + AI 教练卡卡 + 68 元/月的超级 AI 会员；咕咚 AI"配速兔子"（配速稳定性评分 + 提升课程）；华为"健康洞察"；Zepp AI 健康预警 ✅。
7. ⚪ 第三方行业报告（QuestMobile/艾媒）的月活与增速数据未取到：检索通道不可用且多为付费墙。

### 5.4 国产产品的共性功能基线（你的 MVP 必须对齐）

1. **记录内核**：GPS 轨迹 + 省电优化（后台常驻 + 官方耗电免责声明）、自动暂停、每公里分段配速、语音播报（后台可用）、多运动模式（跑/走/骑/徒步是底线，头部产品 5–17 种）。
2. **数据修正与合规**：WGS-84 → GCJ-02 纠偏、轨迹绑路纠偏（**可直接采购高德猎鹰或腾讯轨迹云，不必自研**）、里程与卡路里算法。
3. **训练体系**：心率区间（蓝牙心率带/手表）、间歇跑与课程跟练、**5K–全马备赛计划**（华为/Keep/咕咚的共同卖点）、VO2Max/训练负荷/恢复时间等进阶指标（华为 TruSport、Garmin、COROS 已完成市场教育）。
4. **社交与赛事**：跑团、动态分享、排行榜/挑战赛、**线上赛 + 实体奖牌**、组队跑。
5. **报告体系**：周报/月报/**年度运动报告 H5 分享海报**；"更高维度数据分析"作为会员权益（咕咚模式）；免费档自动周报（悦动圈模式）。
6. **生态互联**：HealthKit / Apple Watch、主流国产手表数据聚合、**微信登录 + 小程序 + 公众号矩阵**（悦跑圈/咕咚/Keep 全部如此）、对第三方输出数据（COROS 已示范）。
7. **鸿蒙原生版应列入路线图**：高德已有星河版（HarmonyOS NEXT）地图/定位/导航 SDK ✅，咕咚已上鸿蒙原生版 ✅。

### 5.5 用户痛点（App Store 评论区实证）→ 差异化机会

| 头部产品被吐槽的点（✅ 评论原文取证） | 你的机会 |
|---|---|
| 免费课程逐步转会员、会员与商品涨价（Keep，10 年老用户 2025-06 长评） | **承诺核心记录功能永久免费**，会员只卖"分析与内容" |
| 界面功能堆砌、核心入口深、社交动态冗余（Keep 2026-02 长评建议"只留首页/饮食/我的数据三入口"） | **"工具优先"极简模式**，可一键关闭社交与 AI |
| AI 教练"不能关"、蹭热度（Keep） | 做**可关闭的、标注算法出处的** AI |
| **语音播报与界面数据不一致**（播报步频 130 vs 界面 170）、课程最后一段无结束提示（Keep/咕咚） | **播报引擎与渲染层同源**，课程状态机严格收尾 |
| 自有手环心率不准（Keep） | **中立聚合第三方设备**，不自产低质硬件 |
| 线上赛奖牌"不完赛不退款"引发消保投诉（Keep 2026-03，官方回复坚持规则） | 透明规则：**定金制 / 完赛后付费** |
| 统计口径 bug：删除单条记录后总统计不同步、里程偶发出错（咕咚） | **数据层单一事实源 + 可审计对账**（呼应 §6.1 的"派生指标可重算"） |
| 新版砍掉速度趋势/海拔趋势等细节图表（咕咚） | 细节图表是进阶跑者的刚需，**不要为了简化而删** |
| 普通用户社交分发被大 V 挤占、涨粉停滞（咕咚） | **跑团私域优先于公域广场** |
| 后台被杀、断轨（🟡 行业通病，评论区未直接取证，但 Strava 官方文档已证实华为机型问题，见 §6.4） | 国产 ROM 白名单引导 + 本地缓存补传 |
| 两步路广告过多；Garmin Connect 中国区 2.99 分 | 干净付费体验；面向进阶跑者提供**国内低延迟同步 + 数据导出自由** |

**战略级空白点（按可行性排序）：**

1. **"外资退场"承接**：做"中文世界最好的专业跑步工具"——把跑力/训练负荷/恢复等科学指标平权，且不锁硬件（§5.3 第 4 条已证实空位存在）。
2. **数据主权与开放**：头部产品的用户级导出与 API 普遍封闭（咕咚 API 是 to B 收费；Keep 未见导出功能 ⚪）——**"一键导出全量轨迹（GPX/TCX/FIT）+ 个人开放 API"** 可以成为口碑卖点，且与 §3 的开源生态天然衔接。
3. **省电与 GPS 稳定性作为可量化营销点**：所有产品都在免责声明里承认后台 GPS 耗电，评论区常见断轨漂移抱怨——把"**全马不断轨、耗电低于竞品**"做成可量化承诺。
4. **AI 教练分层定价**：Keep 已验证 68 元/月可行，但用户反感"不能关"——做可关闭、有训练科学出处的 AI（另可参考 Strava 的 MCP Connector 思路，见 §4.5）。
5. **奖牌经济合规化重做**：需求已验证、纠纷多发，透明规则 + IP 联名是低风险切入点。
6. **户外/越野细分**：六只脚/两步路口碑与体验偏低且广告多，越野跑（爬升图、离线等高线、多图源、SOS）存在"专业但干净"的空位。

---

## 6. 记录引擎与数据模型（技术核心）

### 6.1 一次跑步的数据模型

```
Run (Activity)
├─ id, userId, sportType=run
├─ startTime, endTime, timeZone        // 必须存时区，跨时区跑与报告聚合都依赖它
├─ distanceMeters, durationMs, movingDurationMs   // 总时长 vs 移动时长（自动暂停会拉开差距）
├─ avgPace, bestPace, avgHr, maxHr, avgCadence, calories
├─ elevationGainM, elevationLossM
├─ deviceInfo, appVersion, gpsSource            // 排障与反作弊需要
├─ qualityFlags                                 // 漂移/中断/异常标记
├─ gearId (鞋), notes, photos[], privacyZoneApplied
└─ Lap[] (分段)
   ├─ index, type=auto|manual|interval
   ├─ startOffsetMs, durationMs, distanceM
   ├─ avgPace, avgHr, cadence
   └─ (间歇训练时) targetPace, phase=warmup|work|recovery
TrackPoint[] (轨迹点，量最大)
├─ t (相对或绝对时间戳), lat, lon, alt
├─ horizontalAccuracy, speed, bearing, hr, cadence
└─ flags (paused, interpolated, filtered)
DerivedSeries[] (可选，按需物化)
└─ 每公里分段、心率区间时长、滚动配速窗口
```

**设计要点（🟡 工程经验）：**

- **TrackPoint 与 Run 分离存储**，写入走追加式（WAL / 文件缓冲），跑步结束后再落库并抽稀。一小时跑步按 1Hz 约 3,600 点，按 2Hz 约 7,200 点；长期用户可能有上千次跑步，务必做索引与冷数据压缩。
- **原始点与展示点分层**：保留原始 GPS 流（可导出、可重算），展示用抽稀后的轨迹。
- **所有派生指标可重算**：算法迭代后，历史数据应能用新算法重新计算（这要求保留原始点，而不是只存结果）。这是"报告能力"长期演进的基础。
- **时区与本地日期**：周报/月报的边界必须按用户本地时区计算，否则跨时区用户的报告会错一天。

### 6.2 距离、速度与海拔的正确算法

| 项目 | 做法 | 坑 |
|---|---|---|
| 距离 | 逐点 **Haversine** 累加：`a=sin²(Δφ/2)+cosφ₁cosφ₂sin²(Δλ/2)`，`d=2R·atan2(√a,√(1−a))`，R≈6,371,008.8m。跑步单点间距 <50m，**Haversine 足够**，无需 Vincenty/Karney 椭球解（长距离或高纬度才需要） | 直接把所有点连起来会导致"静止时距离还在涨" |
| 去漂移过滤链 | ① 丢弃 `accuracy > 20~30m` 的点 → ② 丢弃速度异常点（跑步 >10~12 m/s，即 >36~43 km/h）→ ③ 丢弃时间戳乱序/间隔异常点 → ④ **速度-位置一致性校验**（Δ位置/Δ时间 与 GPS 上报 speed 偏差过大即剔除，用于隧道/高楼漂移） | 只做①不够，城市峡谷需要④ |
| 速度平滑 | 滑动窗口（3~5 点）或 **EMA**：`s_t = α·x_t + (1−α)·s_{t−1}`，`α = 2/(N+1)`，α≈0.3~0.5；位置可用一维恒速卡尔曼滤波（状态 `[x,v]`，观测 GPS 位置 + 多普勒速度）。**Android 的 `Location.speed` 多为多普勒导出，比位置差分更稳，应优先使用** | 未平滑的配速曲线全是毛刺 |
| 配速显示 | 用**每公里分段配速**而非瞬时值；瞬时配速对平滑后的速度取倒数并**限幅**（如 2'30"~15'00"/km） | 不限幅会出现"0'00"/km"的荒谬值 |
| 抽稀 | 上传/渲染前用 **Douglas–Peucker**（垂距阈值递归删点）：ε=5~15m 时，1 小时轨迹可从约 3,600 点降到数百点而视觉无损 | 抽稀会略微缩短距离，需保留原始点重算 |
| 海拔 | **气压计**（Android `TYPE_PRESSURE` / iOS `CMAltimeter`）短期相对高差准（±1m 级）但绝对值随天气漂移；GPS 海拔噪声是水平误差的 **2~3 倍**。方案：爬升累计用气压计相对值 + GPS 绝对值锚定，或设**爬升阈值**（只统计超过 ~3m 的净变化） | 不做阈值处理，爬升会虚高数倍 |
| 自动暂停 | 速度 <0.5~1.0 m/s 持续 3~5s → 暂停；**恢复阈值设 1.0~1.5 m/s 形成迟滞**，避免红绿灯抖动；有步频/心率时联合判定更稳 | 阈值太敏感 → 慢跑被误暂停；太钝 → 等红灯时距离乱涨 |
| 采样频率 | **1Hz + `distanceFilter/minUpdateDistance` 0~5m 是主流**（Strava/Garmin 均为此量级）；2~5Hz 用于冲刺分析但电量翻倍。**用"最小距离"让芯片降采样，比单纯拉大时间间隔更省电** | 高频采样是耗电与漂移的双重来源 |
| 分段（Lap）触发 | 按**累计距离**触发（每公里），不是按时间 | 按时间触发会导致分段距离不齐 |

**GNSS 原始观测值（进阶）**：Android 7.0(API 24) 起可拿 `GnssMeasurement`/`GnssClock`/`GnssNavigationMessage`（伪距/载波相位/多普勒），Android 9+ 更多机型支持载波相位。跑步 App 可用**原始多普勒测速**提升配速平滑度（🟡 属进阶优化，MVP 不必做；官方文档现行路径为 `/develop/sensors-and-location/sensors/gnss`，✅ 已核实旧路径 `/location/gnss-raw-measurements` 已 404）。

### 6.3 报告需要的训练指标（公式 + Strava 对标）

> ⚪ **重要声明**：以下公式为教科书/论文共识版本，但本次调研环境中 Wikipedia 与论文站均不可达，**公式本身未能在线核实**，落地前请对照原始文献。产品对标关系（右列）则为 ✅ 已核实（来自 Strava 官方帮助中心）。

| 指标 | 公式 | Strava 对应功能（✅） | 用途 |
|---|---|---|---|
| 最大心率 | `220 − age`（误差 ±10~12bpm，仅作缺省）；**Tanaka(2001)：`HRmax = 208 − 0.68 × age`**；实测法优先 | — | 一切心率区间的基础 |
| 储备心率 Karvonen | `THR = HRrest + 强度% × (HRmax − HRrest)`；5 区间常用 Z1 50–60% / Z2 60–70% / Z3 70–80% / Z4 80–90% / Z5 90–100% | HR Zones（API 有独立端点） | 周报堆叠图 |
| 乳酸阈心率 LTHR | 30 分钟全力跑，取**后 20 分钟平均心率**近似（Joe Friel 法） | — | 更准的区间划分 |
| **TRIMP（Banister 1991）** | `ΔHR = (HRex − HRrest)/(HRmax − HRrest)`；男：`TRIMP = t(min) × ΔHR × 0.64 × e^(1.92×ΔHR)`；女：`× 0.86 × e^(1.67×ΔHR)`。实现：逐分钟心率分桶累加 | **Relative Effort** 的同类思路 | 无功率计时的训练量量化 |
| TSS（功率，Coggan） | `IF = NP/FTP`；`TSS = (t_sec × NP × IF)/(FTP × 3600) × 100`；NP = 30s 滑动平均功率的四次方均值再开四次方。跑步无功率时用 **hrTSS / rTSS**（按心率区间时长或相对阈值配速折算） | Training Load / Estimated FTP / Power Curve | 有功率计时的统一口径 |
| **CTL / ATL / TSB** | Banister 脉冲响应模型：`CTL` = 42 天指数加权均值（体能）、`ATL` = 7 天（疲劳）、`TSB = CTL − ATL`（状态） | **Fitness & Freshness**（官方明示基于 **Banister 1975** 脉冲响应模型；有功率用 Training Load，无功率用 Relative Effort 拟合） | 严肃跑者刚需的三线图 |
| **ACWR（急慢性负荷比）** | 滚动比法：`acute = Σ近7天负荷`；`chronic = Σ近28天负荷 / 4`；`ACWR = acute / chronic`。EWMA 版：`λ_acute = 2/(7+1) = 0.25`，`λ_chronic = 2/(28+1) ≈ 0.069` 递推。**甜区 0.8~1.3，>1.5 受伤风险陡增**（Gabbett, BJSM 2016） | — | 受伤风险提示 |
| VO2max 估算 | ① Firstbeat/Garmin 法：取次最大强度稳态段的 (速度, 心率) 对做线性回归 `HR = a×v + b`，外推至 HRmax 得 `v_VO2max`，再用跑步代谢方程 `VO2 = 0.2×v + 3.5`（v 为 m/min，平地）反解；需多次跑积累。② 简易法（Uth–Sørensen）：`VO2max ≈ 15.3 × HRmax/HRrest` | — | 有氧能力趋势（Polar 称 Running Index 为"你的跑步 VO2max" ✅） |
| **临界速度 CV / D'** | 两参数模型（Monod & Scherrer 1965 起源）：两次以上不同距离全力跑 `(d1,t1),(d2,t2)` → `CV = (d2−d1)/(t2−t1)`（m/s）、`D' = d1 − CV×t1`（m）；等价 `t = D'/(v−CV)`，可用多场成绩做最小二乘。CV≈可持续阈速度，D'≈无氧容量 | — | **比 VO2max 更适合跑者**，且只需历史成绩即可算 |
| 成绩预测 Riegel(1981) | `T2 = T1 × (D2/D1)^1.06`（1.06 适用于训练有素者，业余跑者常用 1.06~1.10）；可与 CV 模型互相校验 | **Performance Predictions**（Strava 用 ML 预测 5K/10K/半马/全马） | 年报里极受欢迎 |
| 坡度调整配速 GAP | 按坡度修正配速（Strava 官方注明基于 **Davies / Minetti** 研究） | **GAP** ✅ | 山地跑的公平比较 |
| 恢复时间 | Firstbeat 类方法基于 EPOC 累积与训练状态映射到小时数；开源近似：按 TRIMP/TSS 分档 + HRV 修正 | — | 给结论式建议 |
| 鞋里程与寿命 | 累计 `Σ距离` 到 **600~800km** 提醒退役；中底（EVA/PEBA）衰减与**里程 + 时间双相关**（>12 个月也提示） | Best Efforts / gear 里程（Nike Run Club 有鞋款打标 + 里程提醒 ✅） | 装备卡片 |

**落地优先级建议（成本 vs 报告观感）：**

1. **第一批（必做，公式简单、可解释）**：TRIMP（→ 努力分）、心率区间时长、每公里分段、Riegel 成绩预测、CV/D'（只需历史成绩，无需传感器）。
2. **第二批（形成专业感）**：CTL/ATL/TSB 三线图、ACWR 风险提示、GAP 坡度调整配速。
3. **第三批（涉及私有算法，标注"估算"）**：VO2max、跑力、恢复时间。

> **一个重要的产品判断：** Strava 明确把 Fitness & Freshness 标注为"基于 Banister 1975 模型"、把 GAP 标注为"基于 Davies/Minetti 研究"（✅ 已核实）。**这说明你不需要自研算法——把经典模型实现正确、并在 UI 里注明出处，就足以建立专业信任。** 这恰好也是开源生态（Runalyze 退出后）留下的空档（§3.4）。

### 6.4 平台侧硬约束（决定"记录能否可靠"的成败项）

> 这是整个项目**风险最高**的部分。以下 Android 条目中，官方文档站 `developer.android.com` 在本环境不可达，改用中国镜像 `developer.android.google.cn`（可确认 URL 与路径有效性，正文为 JS 渲染未抓取）与 `developers.google.cn`（可抓全文）核实；iOS 条目通过 **Apple 文档 JSON 接口**抓取到原文，证据强度最高。

#### Android

| 约束 | 内容 | 证据 |
|---|---|---|
| 定位 API 选择 | `FusedLocationProviderClient` 属 Google Play services（`com.google.android.gms.location`），融合 GPS/Wi-Fi/基站/传感器；`LocationManager` 是 AOSP 系统服务、**不依赖 GMS**。**国内无 GMS 的设备必须回退到 `LocationManager`**——`geolocator` 插件正是"FLPC 优先、无 GMS 回退 LocationManager"的实现 | ✅ 插件 README |
| 记录参数 | `LocationRequest.Builder`：`setMinUpdateIntervalMillis(1000)`、`setMinUpdateDistanceMeters(0~5)`、`setPriority(PRIORITY_HIGH_ACCURACY)`；Android 12+ 推荐用 **PendingIntent 直接投递到前台服务内的 BroadcastReceiver**，省一次进程唤醒 | 🟡 工程共识 |
| 前台服务类型 | 跑步记录用 `android:foregroundServiceType="location"`；**Android 14 起必须声明类型**，并同时申请 `FOREGROUND_SERVICE_LOCATION` 权限；Android 14 还新增了 **`health`** 类型（适合健身记录）；Android 14 起需在 **5 秒内**调用 `startForeground` | ✅ 路径与类型清单核实（`health` 类型的运行时前提 ⚪ 待核实） |
| **⭐ Android 15 的 6 小时限制** | `dataSync` / `mediaProcessing` 类前台服务**每 24 小时最多运行 6 小时即被系统停止**；`BOOT_COMPLETED` 接收器禁止启动 `dataSync`/`camera`/`mediaPlayback`/`phoneCall`/`microphone` 五类前台服务。**但 `location` 与 `health` 类型不受 6 小时限制** → **跑步记录必须用 `location|health`，绝对不能用 `dataSync`** | ✅（二手转引官方 `behavior-changes-15#fgs-hardening`，原站被墙 ⚪） |
| 后台定位权限流程 | Android 10+ 需 `ACCESS_BACKGROUND_LOCATION`；**Android 11+ 不能在运行时对话框中与前台权限同时请求**——必须先取得"仅使用期间"，再引导用户到系统设置页选"始终允许" | 🟡 + ✅ 插件 README 佐证 |
| **⭐ 可以不要"始终允许"** | 跑步记录**只用"使用期间"权限 + `location` 类型前台服务即可完成全程记录**（前台服务运行期间视为"使用中"）。只有"自动检测到我开始跑步了"这类功能才真正需要"始终允许"。**这能大幅降低 Google Play 审核难度与用户授权流失** | 🟡 推断，但强烈建议按此设计 |
| WorkManager 的边界 | 单次运行上限约 10 分钟且受 Doze 批处理，**不能用于连续 GPS 记录**；正确用途是跑后轨迹上传、周期性同步、数据清理 | 🟡 |
| Doze 与上传 | 运行中的前台服务不会被 Doze 直接杀死，但息屏长时间无网络会导致上传延迟 → **跑后在 Wi-Fi 环境重试** | 🟡 |
| 电池优化白名单 | 可用 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 弹窗申请，但 **Google Play 限制该权限的使用范围**（健身类通常在允许清单内） | 🟡（政策原文 ⚪ 不可达） |
| 自动开始/自动暂停 | **Activity Recognition API**：`ActivityRecognitionClient.requestActivityUpdates(intervalMillis, PendingIntent)` 返回带 `confidence` 的 `DetectedActivity`；更好用的是 **Activity Transition API**（`ActivityTransition`/`ActivityTransitionRequest`）订阅"开始/结束某活动"的转换事件，**官方文档明确举例"里程记录 App 在用户开始驾驶时开始记录"**。需 `ACTIVITY_RECOGNITION` 运行时权限（Android 10+）；**无 GMS 设备需自写加速度计启发式替代** | ✅ `developers.google.cn` 全文 |
| **厂商 ROM 杀后台** | `dontkillmyapp.com` 按厂商给出白名单设置指引（**华为、小米、一加、三星评级最差**），并提供 Google IssueTracker 上报模板。**连 Strava 官方帮助文档都专门列出"记录中会被 OS 杀掉"的华为机型清单（Mate/P/Honor 多代）与《How to Fix GPS Recording Issues on Huawei Phones》专文，小米部分机型需 HyperOS 3 才修复** | ✅ 两站全文 |

> **战略含义：** 厂商 ROM 杀后台是**行业级未解难题**，连 Strava 都只能写帮助文档教用户改设置。你无法根治，但可以：① 首次记录前做**分机型引导流**（跳设置页：华为"手动管理应用启动"、小米"自启动 + 无限制省电策略"、OPPO/vivo"允许后台运行 + 锁定任务卡片"）；② 记录中周期性把状态写入本地，被杀后能恢复"未完成的跑步"；③ **上报被杀机型统计，按机型下发差异化引导**；④ 把"断轨率"做成可量化指标对外承诺（§5.5 机会 3）。

#### iOS

| 约束 | 内容 | 证据 |
|---|---|---|
| **⭐ 崩溃陷阱** | `allowsBackgroundLocationUpdates = true` 之前，**必须先在 Info.plist 配置 `UIBackgroundModes` 含 `location`，否则直接致命崩溃**；置 true 后系统自动显示后台定位指示（蓝条/胶囊）；官方建议**只在用户真正开启需要后台定位的功能时才置 true** | ✅ Apple 文档 JSON 原文 |
| 精度档位 | `kCLLocationAccuracyBestForNavigation`（额外传感器辅助、最耗电）→ `Best` → `NearestTenMeters` → `HundredMeters` → `Kilometer` → `ThreeKilometers`。**跑步记录用 `Best`**，导航类才用 BestForNavigation | 🟡（映射表 ✅ 自插件 README 转引） |
| **`activityType = .fitness`** | `CLActivityType` 共六个 case：`airborne`、`automotiveNavigation`、`fitness`、`maritime`、`other`、`otherNavigation`；官方对 `.fitness` 的释义是"**专门健身时段（步行/跑步/骑行训练）中的定位**"，会影响系统的暂停策略与功耗调优 | ✅ Apple JSON 原文 |
| **自动暂停必须关掉** | `pausesLocationUpdatesAutomatically` 允许系统在用户停止移动时自动暂停定位（省电），但**跑步记录应设为 `false`**——暂停判定必须自己做，否则系统暂停后无法自动恢复记录 | 🟡 工程共识（符号存在 ✅） |
| Background Modes | `location`（定位）、`audio`（跑步语音播报保活）、`processing`/BGTaskScheduler（后台处理，**iOS 约每 15 分钟给一次 ~30 秒窗口**） | ✅（窗口时长自插件文档转引） |
| Visit API 类名 | 是 **`CLVisit`**，不是 "CMVisit"（后者文档 404，不存在）；`startMonitoringVisits()` 适合低功耗的"到访地点"检测（到家/到公司识别），**不适合跑步记录本身** | ✅ |
| 计步与运动识别 | `CMPedometer`（步数/距离/楼层/配速）、`CMMotionActivityManager`（walking/running/automotive/cycling/stationary + 置信度）→ 可实现"检测到跑步就提示开始"；需 `NSMotionUsageDescription` | ✅ 符号页 200 |
| 显著位置变化 | `startMonitoringSignificantLocationChanges()` 基于基站、**App 被杀后可重启进程**、功耗极低；只适合"唤醒后判断是否需要开始精细定位"，精度不足以记录轨迹 | 🟡 |
| 状态栏指示 | `showsBackgroundLocationIndicator` 可控制状态栏变色（WhenInUse 后台定位时） | ✅ |
| iOS 17+ 新 API | `CLBackgroundActivitySession`：向系统声明长时后台会话、改善被挂起表现 | ✅ 符号存在（细节 ⚪ 待核实） |
| 电量实践 | `distanceFilter` 5~10m + `Best` 精度 + `.fitness` activityType + **关闭自动暂停**；后台持续定位时系统会保持 App 活跃，**无需用音频保活**；避免同时开 CMMotion 高频采样 | 🟡 |

#### 电量与稳定性的实测方法

- **Android**：`dumpsys batterystats` + **Perfetto** + Android Studio Energy Profiler。⚠️ 注意官方工具 **Battery Historian 的 GitHub 仓库已归档（`archived=true`，最后推送 2019-07）**（✅ 已核实），仍可用（Go 实现、Docker 一键起）但已停止维护。
- **iOS**：Xcode Instruments 的 Energy Log / Energy Diagnostics 模板 + **MetricKit**（`MXCPUMetric`、`MXAppExitMetric` 可做后台退出归因）。
- **方法论**：固定机型 + 固定路线（或用 mock location 注入 GPX 模拟）+ 满电量跑 1 小时，对比方案前后 `batterystats` 中 GPS/CPU/Wakelock 的占比。
- **跑步 App 特有的监控指标**：记录中断率、GPS 首次定位耗时、轨迹缺口率、被杀机型分布（🟡 建议自建埋点，这四项比崩溃率更能反映产品质量）。

---

## 7. 技术选型建议

### 7.1 客户端框架决策表

| 方案 | 后台定位稳定性 | BLE/传感器 | 地图生态 | 开发成本 | 关键证据（✅ 已核实） |
|---|---|---|---|---|---|
| **原生双端（Kotlin + Swift）** | ★★★★★ | ★★★★★ | ★★★★★ | 高（约 1.6~2 倍人力 🟡） | HealthKit、Wear OS、watchOS 独立 App、Live Activities（灵动岛实时配速）全是一等公民（🟡）；开源参考实现最多（§3） |
| **KMP（共享逻辑 + 原生 UI）** | ★★★★★ | ★★★★ | ★★★★★ | 中高 | `JetBrains/compose-multiplatform` **19.4k★、Apache-2.0、2026-10-22 仍在推送**，1.13.x 在开发中；**Vico 图表库已定位为 "Compose Multiplatform 图表库（Android+iOS）"、2026-10-20 推送**——全共享 UI 路线的图表问题已有解 |
| **Flutter + 自研原生记录引擎** | ★★★★ | ★★★☆ | ★★★★ | 中 | `geolocator` **14.1.2（3 天前发布）**、Flutter Favorite、6.1k likes / 2.76M 下载；`flutter_foreground_task` **11.0.3（45 天前）**，Android FGS 封装最完善（支持 Android 14 全部类型含 `location`/`health`/`connectedDevice`，文档已跟进 Android 15 的 6h 限制）；`fl_chart` **1.3.0（1 天前发布）**。⚠️ **`flutter_background_service` 5.1.0 已约 22 个月未更新，不建议新项目使用** |
| React Native | ★★★ | ★★★ | ★★★ | 中 | `react-native-background-geolocation` 活跃（2026-10 有推送、2.9k★）、**仓库 LICENSE 文件确为 MIT**（已 base64 解码核实），但 homepage 指向商业商店 `shop.transistorsoft.com`——**Android 生产环境是否仍需购买 license ⚪ 待向官方确认**，这是该方案最大的不确定性 |

**按团队情况选择（决策表）：**

| 你的情况 | 推荐方案 |
|---|---|
| 小团队（≤5 人）、要快速验证、国内外双市场 | **Flutter（UI/报表/业务）+ 自研原生记录引擎插件 + 双地图栈** |
| 已有 Kotlin/Swift 班底、追求顶级体验与穿戴生态 | **原生双端**；或 **KMP 共享逻辑 + 原生 UI** |
| 想全共享 UI，且能接受 iOS 侧 UI 妥协 | KMP + Compose Multiplatform（生态上行） |
| React 栈深厚、预算可买商业插件 | RN + background-geolocation（**先确认授权现状**） |
| 只做国内、极致低成本 | Flutter/原生 + **高德全家桶**（地图 + 定位 + 猎鹰轨迹纠偏） |
| 手表独立记录是核心卖点 | watchOS 原生（`HKWorkoutSession`）+ Wear OS（Health Services）**不可省** |

**推荐结论（🟡 我的判断，已用上述 ✅ 证据校准）：**

- 这个 App 的技术风险 90% 集中在**"长时间后台定位不被杀 + 省电 + BLE 稳定"**。**关键认知：无论选哪个框架，这部分难点都逃不掉原生代码**——差别只在于"你写多少原生"。
- **iOS 侧尤其要注意**：iOS 完全不允许"后台运行任意 Dart/JS 代码"，只有 location/audio 等系统回调路径。因此 **Flutter 的 `flutter_foreground_task` 在 iOS 上只是 BGTaskScheduler 周期任务（约 15 分钟一次、每次约 30 秒），iOS 的持续记录仍必须走 `CLLocationManager` 后台定位**（✅ 已核实）。这意味着"跨平台"在记录引擎这一层是伪命题。
- 因此我的默认推荐是：**记录引擎（定位采样、滤波、落库、BLE）用原生 Kotlin/Swift 写成自研插件，上层 UI/业务/报表用 Flutter（或全原生）**。直接用 `geolocator + flutter_foreground_task` 组合也能跑通到 Strava 级功能，但**极限省电与厂商 ROM 兼容性的调优上限低于纯原生**。
- 若团队本就是双端原生班底、或要做手表独立 App，则**直接原生双端或 KMP 共享逻辑 + 原生 UI**，不要为了"跨平台"而跨平台。
- ✅ **已核实的旁证（双向的，需诚实看待）**：
  - 不利面：Flutter 阵营的**跑步记录类**开源参考实现极弱——GitHub 搜索 `running tracker language:dart` 共 96 个仓库，Star 最高的 `RunFlutterRun` 仅 116★ 且最后推送为 2024-08；而原生阵营成熟得多（RunnerUp 961★、2026-10 仍在推送；OpenTracks 1,402★/Codeberg 主仓活跃；FitoTrack 已用 Kotlin+Compose）。**"GPS 后台记录"这一层的作业，能抄的都在原生侧。**
  - 有利面：Flutter 的**应用层**栈其实很成熟——wger Flutter（988★，2026-10 活跃）实证了 `Riverpod 3.x + drift(SQLite) + PowerSync（本地优先云同步）+ fl_chart + health_bridge（一个 API 同时接 Health Connect 与 HealthKit）` 这套组合可用。**问题不在 Flutter 本身，而在"没有可靠的开源后台定位/BLE 记录引擎参考"。**
- 若团队规模小（1–2 人）且必须单栈，Flutter 是可行的折中，但请把"后台定位"当作**第一优先级技术验证项**，先做两周 spike：连续 1 小时户外跑 + 锁屏 + 息屏 + 省电模式，验证轨迹完整率与耗电。

**✅ 证据支持的"收敛栈"（直接沿用风险最低）：** 调研发现开源生态的技术选型已经明显收敛，跟随它可以最大程度复用现成代码与踩坑经验：

| 层 | Android | iOS | Web 后台 | 跨端（若选 Flutter） |
|---|---|---|---|---|
| 语言/UI | Kotlin + Jetpack Compose（FitoTrack 实证） | Swift + SwiftUI | — | Dart |
| 地图 | mapsforge（纯离线，无 token）/ osmdroid；商业版隔离进 build flavor | ⚪ MapKit 或 MapLibre | Leaflet 或 MapLibre GL JS | flutter_map / maplibre |
| 本地存储 | Room | ⚪ SwiftData / Core Data | PostgreSQL + PostGIS | drift |
| 图表 | MPAndroidChart | ⚪ Charts / Swift Charts | Chart.js | fl_chart |
| BLE | Nordic android-ble | ⚪ CoreBluetooth | — | ⚪ flutter_blue_plus |
| 同步 | 导出即同步（GPX 落盘 + 唯一 trackid 去重）→ 或 PowerSync | 同左 | — | PowerSync |
| 健康平台 | Health Connect | HealthKit | — | health_bridge（两端统一） |

### 7.2 后端与存储

| 层 | 建议 | 理由 / 证据 |
|---|---|---|
| API | 单体起步（Go 或 Node/TS 或 Python FastAPI），REST + 后续按需 gRPC | 早期不要上微服务（对照：Strava 是 Rails 单体 + Spark 分析 + Go 基础设施，§4.2.3） |
| 主库 | **PostgreSQL（+ PostGIS）** | ✅ FitTrackee 用 PostgreSQL 14–18 + PostGIS 3.4–3.6；轨迹点按 run 分区 + BRIN/复合索引，JSONB 存弹性字段 |
| 轨迹存储 | 原始文件（GPX/FIT）存 **S3 兼容对象存储** + 抽稀点入库 | 成本与查询性能平衡；入库策略是"统一解析为内部模型，原始文件另存" |
| 时序库 | **早期不需要** InfluxDB/TimescaleDB | 跑步指标是"每次活动一批"而非连续时间流，PG 分区表足够；仅当要做秒级设备流分析时才引入（届时 TimescaleDB 作为 PG 扩展运维成本更低） |
| 本地存储（Android） | **Room**（✅ FitoTrack 用 Room 2.8.4） | 官方首选 |
| 本地存储（iOS） | Core Data / SwiftData | ⚪ 本次未核实细节 |
| 本地存储（Flutter） | **drift**（✅ wger Flutter 在用） | 响应式 SQLite |
| ⚠️ 避开的库 | **Isar 不推荐**：✅ Apache-2.0 但 **2025-06-14 后无推送、185 个 open issues**，官网仍宣传"开源免费"，Isar 4 的许可与维护主体变动传闻 ⚪ 待核实。`realm-swift` ✅ 仍在更新（2026-09 推送、默认分支名为 `community`），但 **Atlas Device Sync 停服/EOL 细节 ⚪ 待核实，新项目慎用其同步功能** | 依赖选型必须看维护状态，不能只看 Star |
| 同步 | 离线优先：本地为真相源 → 变更日志/`updated_at` 游标增量上传 → 服务端**幂等 upsert（客户端生成 UUID）**。**跑步数据天然 append-only，冲突面小，字段级 LWW + 服务端校验即可，无需 CRDT** | 跑步常在无信号环境（山地、地铁沿线） |
| 报表 | 服务端预聚合（按周/月/年物化）+ 客户端渲染 | 年报涉及全年数据，客户端算太慢且费电 |
| 异步解析 | 上传后走队列（Redis/消息队列）异步解析 GPX/FIT | ✅ FitTrackee 即"从 GPX 文件导入活动"的模型 |

> FitTrackee 的 **Flask 3.1 + Vue 3.5 + PostgreSQL/PostGIS + Chart.js + Leaflet + gpxpy** 组合（✅ 已核实）证明：**一个自托管的完整跑步分析后台，用很小的技术栈就能实现**，且它提供 PyPI 包与 Docker 镜像（`fittrackee/fittrackee`）两种分发方式。若你想先做 Web 版验证报告形态，这是最快路径；甚至可以直接对接 FitTrackee 作为你的自托管后台（它已支持从 FitoTrack/OpenTracks/OsmAnd/RunnerUp 导入），⚠️ 但注意 AGPL-3.0。

**文件格式与互通（✅ GPX 1.1 schema 已核实全文）**

| 格式 | 要点 |
|---|---|
| **GPX 1.1** | WGS-84、公制；结构为 `trk/trkseg/trkpt`（**GPS 断开处分段**）、`wpt`/`rte`；`extensions` 可挂心率/步频（TrackPointExtension 命名空间 🟡）。规范：`https://www.topografix.com/gpx/1/1/` ✅ |
| **TCX** | Garmin Training Center XML（⚪ 官方页本次未取到） |
| **FIT** | 二进制、信息最全（`file_id`/`activity`/`session`/`lap`/`record` 消息族）；**Garmin FIT SDK 免费注册下载，但许可是 Garmin 自有条款，商用前必须确认**（⚪ 许可原文待核实） |
| 转换工具 | GPSBabel（命令行万能转换）、`gpxpy`、FIT SDK 自带编解码器（Java/C++/C#/Python 等）；前端可用 gpx.studio 的 `gpx` TS 库（MIT） |

**隐私设计（必须内置，不是可选项）**

- **Privacy Zone**：Strava 式做法——在家/公司地址周边半径多边形内遮蔽轨迹（🟡 常见默认 200m~1km；Strava 帮助文档对应文章 ID 已变动 ⚪）。
- 分享/热力图默认**截断起终点**，公开活动可配置隐藏首尾 N 米。
- GDPR：数据可携权（导出 GPX/FIT/JSON）、被遗忘权（删除账号 + 全部轨迹）、处理合法性基础与 DPIA。
- 中国 PIPL：**行踪轨迹属敏感个人信息（第 28 条），须单独同意（第 29 条）**——详见 §10.1。

### 7.3 地图方案（按发行区域二选一）

| 场景 | 推荐 | 说明 |
|---|---|---|
| **中国大陆发行** | **高德"运动行业解决方案"**（首选）或腾讯位置服务 | ✅ 高德方案是为跑步/骑行/滑雪/轨迹记录定制的：场景化地图 SDK（地图展示/路线绘制/实时定位/手势交互）、**一个 Key 覆盖 Android/iOS/Web/鸿蒙 SDK**、**轨迹上传→纠偏绑路→展示全链路**、官方明文提供**坐标系转换服务（"解决 GCJ02 与 WGS84 转换问题"）**、跑步路线推荐、运动热力图；生态含**官方 Flutter 地图插件、微信小程序插件、HarmonyOS NEXT 星河版地图/定位/导航 SDK、智能手表解决方案**；页脚展示测绘资质"甲测资字 11112528 / 11111093" ✅ |
| 中国大陆（轨迹服务） | **高德猎鹰轨迹服务** | ✅ 轨迹上传/存储（**1 年免费**）/查询、里程计算、**轨迹纠偏绑路**、实时监控、海量终端检索、**轨迹分析（停留点、超速/急加速急减速）**、道路属性、地理围栏，提供 API + 双端 SDK。**建议直接采购，不要自研纠偏** |
| 中国大陆（备选） | 腾讯位置服务 | ✅ 行业方案明列"运动健康"；含地图/定位/导航 SDK、**Android/iOS 轨迹 SDK 与轨迹云**、微信小程序 JS SDK/插件、RTK 鉴权 SDK、数据可视化；8000 万+ POI、1300 万公里道路。百度"鹰眼轨迹"为同类能力（⚪ 当日未抓到页面） |
| **海外发行** | **Mapbox**（矢量、样式灵活、Static Images API 适合分享卡片）或 **MapLibre**（免授权费） | ✅ MapLibre 是组织级项目：MapLibre GL JS（Web）、**MapLibre Native**（Android/iOS/C++，GPU 渲染支持 OpenGL/Metal/Vulkan）、Style Spec、新一代 Tile Spec、**Martin 瓦片服务器（Rust，可从 PostGIS 动态生成 MVT，或直接服务 PMTiles/MBTiles）**；BSD 许可、无厂商锁定，但**需自备瓦片源**（自建或 Protomaps/MapTiler/Stadia 等商业源）。Mapbox 计费以 **MAU** 为主（⚪ 具体额度未取到） |
| **⚠️ OSM 官方瓦片不可用于生产** | 只能作调试/兜底 | ✅ 官方瓦片政策全文核实：只允许 `https://tile.openstreetmap.org/{z}/{x}/{y}.png`；必须署名；**必须发送可识别的自定义 User-Agent（SDK 默认 UA 会被封）**；尊重缓存头或至少缓存 7 天；**禁止批量下载/预取/离线使用（"下载城市离线地图"功能明确违规）**；无 SLA、随时可封禁 |
| 离线地图 | **PMTiles v3**（远端）/ **MBTiles**（本地） | ✅ PMTiles v3：单文件瓦片归档，**HTTP Range 请求按需读取（S3 即可托管，零运维）**，只读格式，全球矢量底图相比逐瓦片方案可省 70%+ 体积与请求费；MBTiles（Mapbox 制定，SQLite 容器）适合本地磁盘/服务端。移动端离线方案 = mapsforge（✅ FitoTrack 在用，纯离线矢量 OSM、无需 token）或预下载区域 → 本地 MBTiles/PMTiles → MapLibre Native / flutter_map 读取 |
| 双区域 | 抽象 `MapProvider` 接口，按区域注入实现 | 避免把某家 SDK 的 API 渗透到业务层 |

**⚠️ 坐标系是国产化的第一大坑**：GPS 原始输出是 **WGS-84**；国测局加密偏移是 **GCJ-02**（高德/腾讯/Google 中国用）；百度再加密是 **BD-09**。**在中国大陆公开地图上直接绘制 WGS-84 轨迹会产生约 50~500m 偏移**（🟡 量级为行业共识）。WGS84→GCJ02 是官方保密算法（民间有逆向实现如 eviltransform，但**商用合规性存疑**），✅ **建议直接用高德 SDK 官方的 `CoordinateConverter`**（已核实存在）。

> **存储铁律：轨迹永远存 WGS-84 原始值，仅在渲染/调用国内地图 SDK 时转 GCJ-02。** 否则导出 GPX 给 Strava/Garmin 时会整体偏移，且无法逆向还原。

**跨平台地图封装现状（✅ 版本与发布时间已核实）**

| 封装 | 版本 | 状态 | 适用 |
|---|---|---|---|
| `flutter_map` | **8.3.2**（55 天前发布） | 2.18k likes / 1.02M 下载 / **BSD-3**，"100% 纯 Flutter、无厂商锁定"，栅格/矢量瓦片客户端（Leaflet 风格） | OSM 系/自建瓦片；**无内建 3D/地形，纯 Dart 渲染性能弱于原生** |
| `maplibre`（Flutter） | **0.3.6**（58 天前发布） | BSD-3，是旧 `maplibre_gl` 的现代重写，经 FFI/JNI 绑定 **MapLibre Native**（Android/iOS 原生渲染）；⚠️ 0.x 版本、社区较小（73 likes / 15.5k 下载），**API 可能变动** | 需要矢量/3D/地形与原生渲染性能时 |
| 高德官方 Flutter 插件 | 兼容地图 SDK 8.1.0+ | ✅ 官方维护（`amap_flutter_map` / `amap_flutter_location`），国内 Flutter 方案事实标准之一 | 只做国内时 |
| `react-native-maps` | ⚪ 未核实 | 🟡 Google/Apple 地图原生桥接，历史上对 Google Maps 新版 SDK 适配滞后 | RN 栈 |

### 7.4 健康平台与可穿戴集成（⚠️ 有一个必须知道的时间线变更）

**Google Fit API 已在停用通道上**（✅ 已核实，来自 `developers.google.cn/fit` 官方横幅原文）：

> "Google Fit 的各项 API（包括 REST API）**将于 2026 年停用**；**自 2024 年 5 月 1 日起开发者已无法注册使用这些 API**；云端集成迁移到 Google Health API，步数/移动端优先场景迁移到 Health Connect。"

**含义：新 App 不要再集成 Google Fit SDK。** Android 侧直接写 **Health Connect**（Android 14+/API 34 平台内置，更低版本走 Play 商店独立 APK 🟡），同时读取手表厂商写入的数据。数据模型为 `ExerciseSessionRecord`（运动时段）+ 关联的 `DistanceRecord`/`HeartRateRecord`/`StepsRecord`，权限是按记录类型的 READ/WRITE 运行时权限（`android.permission.health.*`）（🟡 类名细节 ⚪ 待核实）。正确的迁移文档路径是 `/health-and-fitness/health-connect/migration/fit` ✅（旧 `migrate-google-fit` 已 404）。Wear OS 侧用 **Health Services**（`ExerciseClient` 提供锻炼中的心率/卡路里流，✅ 路径核实；是否已有官方弃用公告 ⚪）。

**iOS：HealthKit**（✅ `HKLiveWorkoutBuilder`、`HKWorkoutSession` 符号存在性已核实）。标准流程 🟡：`requestAuthorization(toShare:read:)` 按类型申请（heartRate、stepCount、activeEnergyBurned、distanceWalkingRunning、workout route）→ 跑步中启动 `HKWorkoutSession`（watchOS 上保证后台常驻 + 传感器优先）→ `HKLiveWorkoutBuilder` 实时收集 → 结束时落 `HKWorkout`（GPX 路线需 `HKWorkoutRouteBuilder`）。注意：**授权状态不可读回具体值（隐私设计），写入失败要容忍**。

**手表与第三方平台开放程度：**

| 平台 | 开放程度 |
|---|---|
| Garmin | **FIT SDK** 免费注册下载（✅ 概览页可达；许可为 Garmin 自有条款，商用前须确认 ⚪）；**Garmin Connect API**（Health/Activity/Training）需申请加入开发者计划并经审批，走用户 OAuth 拉取活动摘要与 FIT 文件（🟡，官方页本次抓取失败 ⚪） |
| COROS | 🟡 无公开第三方 API，仅支持导出 FIT（⚪ 待核实）；但 ✅ 中国区 App 描述明示与**微信运动、悦跑圈、Keep** 数据互联 |
| Polar | 🟡 有 Polar Open AccessLink API 与 BLE SDK（⚪ 待核实）；✅ 官方列出与 Strava/TrainingPeaks/adidas/komoot 同步 |
| Suunto / Wahoo | ⚪ 走合作伙伴计划 |
| 华为 / 小米 / Zepp | ⚪ 各 Health Kit 文档当日不可达；但 ✅ Keep 与咕咚都已实现"接入佳明/松拓/华为/高驰"，说明技术上可行 |
| watchOS 独立记录 | 🟡 手表 App 可完全独立记录（手表 GPS + `HKWorkoutSession`），iPhone 端通过 HealthKit 读取——"不带手机跑步"场景的标准做法 |
| 开源桥接 | ✅ **Gadgetbridge**（AGPL-3.0，覆盖 Pebble/Mi Band/Amazfit/Garmin/Huawei/Casio/Fossil），OpenTracks 已与其打通。**优先对接而非自研每家协议** |
| Strava 互通 | ✅ Strava API v3 提供 Uploads、Streams、Laps、Routes（GPX/TCX 导出）、HR/Power Zones、`estimatedFTP` 等端点——**你的数据模型应与它兼容**（§4.2.3） |

### 7.5 BLE 传感器（GATT Profile）

UUID 基础格式 `0000xxxx-0000-1000-8000-00805F9B34FB`（🟡）。以下特征值细节为行业共识（🟡，具体字段 ⚪ 未在线核实；规范下载索引 ✅ 可达 `bluetooth.com/specifications/specs/`）：

| Profile | UUID | 关键特征值 | 说明 |
|---|---|---|---|
| 心率 HRS | 0x180D | Heart Rate Measurement **0x2A37**（Notify，flags bit0 决定 8/16bit HR，**可带 RR-interval 用于 HRV**）；Body Sensor Location 0x2A38 | 写 CCCD(0x2902) 开启通知 |
| **跑步动态 RSC** | 0x1814 | RSC Measurement **0x2A53**：瞬时速度（uint16，1/256 m/s）、瞬时步频（1/2 spm）、步幅、累计距离 | **跑表用 RSC 而非 CSC** |
| 速度/踏频 CSC | 0x1816 | CSC Measurement 0x2A5B：累计车轮转数 + 上次车轮事件时间（1/1024s）、累计曲柄转数 + 事件时间 | 速度 = Δ转数 × 轮周长 / Δ事件时间（**事件时间回绕需处理**） |
| 功率计 CPS | 0x1818 | Cycling Power Measurement 0x2A63：瞬时功率 int16(W)，可带踏频/左右平衡 | 跑步功率较少见 |

实现要点 🟡：Android 用 `BluetoothLeScanner` + `ScanFilter(serviceUuid)`，iOS 用 `CBCentralManager.scanForServices`；**心率带掉线重连与多传感器（心率 + 步频器）并发管理是主要工程量**；Android 12+ 需 `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`；国内还要处理厂商省电杀连接。

**ANT+ 现状** 🟡：现代手机基本不再内置 ANT+ 芯片；主流配件（Garmin/Polar/Wahoo 心率带）均为 **BLE + ANT+ 双模**，**手机 App 一律走 BLE 即可**，ANT+ 只在手表—配件链路仍有意义。（对照：✅ RunnerUp 的 `:hrdevice` 模块同时实现了 BLE 与 ANT+，还额外支持 Polar WearLink、Zephyr；✅ OpenTracks 支持 BLE 心率/踏频/功率/跑速，且**支持无 GPS 的室内 BLE 训练**——这是跑步机场景的好设计。）

### 7.6 依赖健康度速查表（✅ 全部于 2026-10-23 核实）

| 依赖 | 状态 | 结论 |
|---|---|---|
| `geolocator` 14.1.2 | **3 天前发布**，Flutter Favorite，2.76M 下载 | ✅ 可用 |
| `flutter_foreground_task` 11.0.3 | 45 天前发布，文档已跟进 Android 15 限制 | ✅ 可用（iOS 侧仅周期任务） |
| `fl_chart` 1.3.0 | **1 天前发布**，7.2k likes / 2.03M 下载 | ✅ 可用 |
| `flutter_map` 8.3.2 | 55 天前发布，BSD-3 | ✅ 可用 |
| `maplibre`（Flutter）0.3.6 | 58 天前发布，0.x | ⚠️ 可用但 API 可能变动 |
| **`flutter_background_service` 5.1.0** | **约 22 个月未更新** | ❌ 不建议 |
| **`isar`** | **2025-06-14 后无推送、185 个 open issues** | ❌ 不建议 |
| `realm-swift` | 2026-09 有推送，默认分支名 `community` | ⚠️ 可用，慎用其同步功能 |
| **MPAndroidChart** | 38.2k★、Apache-2.0、**2026-10 仍有推送、已迁移 Kotlin、issue 清零重新维护**（此前多年停滞） | ✅ 已复活，可用 |
| **Charts（iOS）** | 28k★、Apache-2.0、**已从 danielgindi 转至 ChartsOrg 组织**、2026-03 有推送、**978 个 open issues** | ⚠️ 维护偏慢 |
| **Vico** | 3.2k★、**已定位 Compose Multiplatform（Android+iOS）**、2026-10-20 推送 | ✅ 极活跃，KMP 路线首选 |
| `react-native-skia` | **已从 Shopify 转移至 wcandillon 个人组织**、MIT、2026-10-22 仍日更 | ⚠️ 注意归属变化 |
| `compose-multiplatform` | 19.4k★、Apache-2.0、2026-10-22 推送、1.13.x 开发中 | ✅ 极活跃 |
| **`google/battery-historian`** | **已归档（archived=true，最后推送 2019-07）** | ⚠️ 仍可用但停维护，改用 Perfetto |

### 7.7 监控、反作弊与商店审核

- **崩溃与性能监控** 🟡：海外 Sentry + Firebase Crashlytics（国内可用镜像 `firebase.google.cn`）；国内 Bugly / 友盟 U-APM；Flutter 侧注意上传符号表（`--split-debug-info`）。
- **反作弊（服务端校验）** 🟡：最大速度阈（人类极限约 12.4 m/s，跑步记录持续 >10 m/s 即可疑）、时间戳单调性与"总时长 vs 轨迹"一致性、分段距离和 vs 起终点位移比、**`accuracy` 分布异常（模拟器 GPS 注入常为恒定 accuracy=0）**、心率—速度耦合校验、设备指纹与多账号行为、GPX schema 合法性校验；排行榜活动加人工复核。（对照：✅ 悦跑圈官网明示有"数据防作弊系统"并关联手表数据校验。）
- **Apple 审核** ✅：App Review Guidelines 页面在线（2026 版可达）；历史上 **2.5.4**（多任务 App 的后台服务仅限预期用途，如导航/健身）与 **5.1.1/5.1.2**（数据收集需同意、位置数据使用声明）是定位类 App 的审核焦点（⚪ 条款编号以最新版为准）。✅ `geolocator` README 明确警告：**开启 Location Updates 后台能力需在审核时向 Apple 详细说明用途，否则会被拒**。Info.plist 的 `NSLocationWhenInUseUsageDescription` / `NSLocationAlwaysAndWhenInUseUsageDescription` 文案必须讲清用途（审核会看文案）🟡。
- **Google Play** 🟡：`ACCESS_BACKGROUND_LOCATION` 属敏感权限，需提交**位置权限申报表**（核心功能演示视频、隐私政策链接、显著披露声明）；未获批强行上架会被移除（⚪ 政策页本环境不可达）。
- **国内安卓商店**：隐私合规（工信部 SDK 检查要求**调用 `updatePrivacyAgree` 前不得采集**）+ 图商 SDK 合规中心文档（✅ 高德合规中心目录存在）。

---

## 8. 周报 / 月报 / 年报：产品设计规格

这是你产品的核心差异点，值得单独设计。

### 8.1 设计原则

1. **先给结论，再给数据。** 每份报告第一屏是一句人话（"本周跑量比上周 +18%，配速稳中有升，但高强度占比偏高，注意恢复"），下面才是图表。
2. **对比优先于绝对值。** 跑者关心"比上周/上月/去年如何"，所有主图都应有对比基线。
3. **可分享。** 每份报告都能一键导出为长图（竖版、适合手机截图分享），这是最低成本的获客渠道。
4. **可解释。** 每个指标旁有"这是什么/怎么算的"入口，建立专业信任。
5. **同一套聚合器，不同时间窗。** 周/月/年报共用一个 `ReportBuilder(period)`，避免三套代码。
6. **报告要有门槛与节奏（✅ 抄 Strava）。** Strava 的 Month in Sport 要求**上月 ≥3 次活动**才生成、**每月 26 日**刷新、**仅移动端**。门槛避免"空报告"带来的挫败感，固定日期制造期待并错峰生成。
7. **免费给趋势，付费给排名（✅ 抄 Strava）。** 同一个 Totals 卡片：免费版给近 12 个月柱状图，订阅版给**百分位排名** + 可自定义分享。排名需要全量用户数据、个人无法自算，是天然的付费点。
8. **对比对象要势均力敌（✅ 抄 Strava）。** Strava 只匹配"关键指标相差 **30% 以内**的互关好友"。若你没有社交关系链，退化为"**与过去的自己对比**"或"**与同水平匿名分位数对比**"。
9. **成就要限量并按优先级取舍（✅ 抄 Strava）。** Trophy Case 最多 4 项、按优先级排序——不限量会稀释重点。

### 8.2 周报内容清单

| 模块 | 内容 | 图表类型 |
|---|---|---|
| 摘要 | 跑量、次数、总时长、平均配速、努力分（TRIMP），以及与上周的百分比变化 | 大数字卡片 + 环比箭头 |
| 趋势 | 最近 8–12 周跑量柱状图，叠加目标线 | 柱状图 + 参考线 |
| 强度分布 | 本周各心率区间时长占比，与上周对比 | 堆叠条形图 |
| 配速 | 每次跑的配速分布 / 每公里分段配速折线 | 折线图 + 箱线图（进阶） |
| 单次详情 | 每次跑步的地图缩略图 + 关键数据 | 卡片列表 |
| 风险提示 | ACWR 是否进入风险区、连续高强度天数、睡眠/HRV（若有手表数据） | 徽标 + 一句话建议 |
| 装备 | 各双鞋累计里程，接近寿命提醒 | 进度条 |

### 8.3 月报 = 卡片流（直接对标 Strava「Month in Sport」）

月报是三者中**最适合抄 Strava 的一档**，因为它有完整、已验证的机制可参照（✅ 见 §4.2.2）。建议做成**可滑动的卡片序列**，每卡一个结论，而不是一长串图表：

| 卡片 | 内容 | 图表/形态 | Strava 对应（✅） |
|---|---|---|---|
| 1. 活跃天数 | 本月跑步天数 + 与上月对比 | 大数字 + 环比箭头 | **Active Days**（Strava 月报第一张卡就是"天数"而非"里程"——对普通跑者更友好、更不易挫败） |
| 2. 本月成就 | **最多 4 项**，按优先级排序（PB、连续打卡、首次 10K、目标达成…） | 徽章网格 | **Trophy Case**（限量 4 项、按优先级取舍） |
| 3. 月度日历 | GitHub 风格日历热力图，颜色深浅=当日跑量 | 日历格 | — （Strava 无，你的加分项） |
| 4. 同水平对比 | 与"关键指标相差 30% 以内"的跑者对比；无社交关系链时退化为**与过去的自己**或**匿名分位数** | 条形对比 / 百分位刻度 | **Social Competition**（匹配互关好友、指标差 30% 以内） |
| 5. 汇总与趋势 | 里程/次数/时长/平均配速/累计爬升；**免费档给近 12 个月柱状图，付费档给百分位排名 + 自定义分享** | 柱状图 + 排名条 | **Totals**（免费给趋势、付费给排名） |
| 6. 结构变化 | 强度分布（心率区间占比）月度对比、配速进步曲线（同距离配速随月变化）、CTL/ATL 月度轨迹 | 堆叠条 + 折线 | Fitness & Freshness（✅ 基于 Banister 1975） |
| 7. 月度最佳 | 最快 1K/5K/10K、最长距离、最努力一次 | 卡片列表 | Best Efforts |
| 8. 轨迹叠加 | 把当月所有轨迹画在一张地图上（视觉冲击力最强、最适合分享） | 地图 | 个人热力图（周/年/全部三档） |
| 9. 目标与下月建议 | 目标完成度 + 一句话建议（基于 ACWR 与 CTL 趋势） | 文本 + 进度环 | Relative Effort 的 3 周滑动均值建议带 |

**生成规则（务必照抄 Strava 的三条机制，它们都是被验证过的）：**

1. **门槛**：上月 **≥3 次跑步**才生成完整月报；不足时降级为"简版卡片 + 鼓励文案"，**绝不出空报告**（避免挫败感与差评）。
2. **节奏**：**固定每月某日刷新**（Strava 是 26 日，错峰且覆盖月末；你可选 1 日或 26 日，但要固定并写进产品文案，制造期待）。
3. **平台与分享**：移动端优先，生成后即可导出分享长图；**免费版给趋势、付费版给排名**是干净的分层设计。

> **时区陷阱**：月报的"本月"必须按**用户当时所在时区**界定（§6.1 的 `timezone` 字段），且跨月的那次跑步归属规则要明确（建议按**开始时间**归属），否则用户会在月末发现"我明明跑了却不算"。

### 8.4 年报 = 叙事化总结（对标 Strava Year in Sport）

| 模块 | 内容 |
|---|---|
| 开场 | 全年总里程、总时长、总爬升，换算成有趣类比（"相当于上海到杭州"、"爬了 3 次泰山"） |
| 时间线 | 12 个月跑量曲线 + 每月最长一次跑 |
| 高光 | 最快配速、最长距离、最努力一次、个人纪录（PB）清单 |
| 地理 | 全年轨迹叠加地图、跑过的城市/区域数、最常跑的路线 Top 3、最东/最西/最南/最北点 |
| 习惯 | 最常跑步的星期与时段（极坐标热力图，观感极佳）、连续打卡最长纪录、早起跑 vs 夜跑占比 |
| 进步 | 同距离配速年度对比、心率下降趋势、跑力/VO2max 估算曲线 |
| 装备 | 全年鞋里程与更换记录 |
| 结尾 | 一句个性化总结 + 下一年目标建议 + 分享按钮 |

> ✅ **对标说明与一个运营技巧**：Strava 的 **Year in Sport 是季节性开放的限时功能**（本轮调研确认其于 **2026-01-23 下线**，具体卡片内容 ⚪ 未取到）。**把年报做成"限时活动"而非永久功能**，能制造集中传播峰值、降低服务器常态成本，也让用户形成"每年 12 月—1 月等它上线"的仪式感。你的年报窗口建议设为 **12 月中旬至次年 1 月底**。
>
> 另可参考 ✅ Strava 的 **Flyover**（把活动渲染成 3D 视频用于 Instagram 分享）——但视频成本高，对独立开发者而言**静态长图 + 地图快照**是性价比更高的等价物（§8.5）。

### 8.5 图表与分享图技术方案

| 需求 | 方案 | 证据 |
|---|---|---|
| App 内交互图表 | Android: **MPAndroidChart**（✅ 已复活：38.2k★、2026-10 有推送、已迁 Kotlin）或 **Vico**（✅ Compose Multiplatform，2026-10-20 推送）；iOS: **Charts**（⚠️ 已迁至 ChartsOrg，2026-03 推送、978 open issues，维护偏慢）或 Swift Charts；Flutter: **fl_chart**（✅ 1.3.0，1 天前发布）；Web: Chart.js（✅ FitTrackee/gpx.studio 在用）/ ECharts | ✅ §7.6 |
| 海拔剖面图 | Web 可用 `leaflet-elevation`（⚠️ **GPL-3.0**，闭源前端需自绘）；移动端自绘更可控 | ✅ §3.3 |
| 地图轨迹渲染 | MapLibre（✅ Native 支持 OpenGL/Metal/Vulkan）/ 高德 SDK；**轨迹按配速着色（分段渐变）是行业标配** | ✅ |
| **分享长图生成** | 三种方案对比 🟡：① **原生绘制**（Android `View`→Bitmap、iOS `UIGraphicsImageRenderer`、Flutter `RepaintBoundary.toImage`）——像素级可控、无 WebView 开销，**首选**；② **Skia 直绘**（RN Skia `makeImageSnapshot` / Flutter Canvas）——跨端一致、可做复杂渐变卡片；③ **WebView 渲染 HTML 模板再截图**——设计迭代最快（模板可下发），但首帧慢、字体与缩放一致性差，适合运营活动页式年报 | 🟡 |
| 地图快照底图 | **高德静态地图 API**（✅ 官方目录已核实存在）、Mapbox Static Images API（⚪ 未核实）、**MapLibre Native 离线渲染快照**（🟡 无网可用、免 API 费，适合隐私定位） | 混合 |
| 年报动效 | 分页卡片式滑动（类 Wrapped / Strava Month in Sport 卡片流），避免一次性长图渲染卡顿 | ✅ 形态抄 Strava |

### 8.6 报告系统的架构建议

```
原始点 → 派生序列（每公里分段、心率区间、滚动配速）
        → 周期聚合器 ReportBuilder(period: week|month|year, tz)
        → 报告数据模型 ReportSnapshot(JSON, 带 schemaVersion)
        → 客户端渲染 + 分享图生成
```

- **ReportSnapshot 落库并带版本号**：算法升级后可选择"重算历史"或"保留当时口径"，避免用户发现"上周数据变了"。
- **报告生成放服务端**（年报需全年数据），客户端只做渲染与缓存；离线场景可降级为本地计算周报。
- **指标层与展示层解耦**：新增一个图表不应改动聚合逻辑。

---

## 9. MVP 路线图

| 阶段 | 周期 | 目标 | 交付与验收标准 |
|---|---|---|---|
| **P0 技术验证** | 2 周 | 消灭最大风险 | 后台定位 spike：固定机型 + 固定路线（或 mock location 注入 GPX）+ 满电量跑 1 小时，**锁屏 + 息屏 + 省电模式**下验收：轨迹完整率 >99%、缺口可定位归因、耗电落在可接受区间、**被杀后能恢复"未完成的跑步"**。同时验证 iOS 的 `UIBackgroundModes`+`allowsBackgroundLocationUpdates` 不崩溃、Android 的 `location|health` 前台服务在 Android 14/15 上不被 6 小时规则打断 |
| P1 记录闭环 | 4–6 周 | 能可靠记录一次跑步 | 前台服务 + 实时数据 UI + 自动暂停（含迟滞阈值）+ 每公里分段 + 去漂移过滤链 + 本地存储（Room/SQLite）+ **GPX 1.1 导出** + 地图回放与配速着色 + 语音播报（**播报与渲染同源**，避免 §5.5 的口径不一致问题） |
| P2 报告 v1 | 4 周 | 差异化能力上线 | 周报（结论摘要 + 8~12 周趋势 + 心率区间堆叠 + ACWR 风险徽标）+ **分享长图**；ReportSnapshot 带 `schemaVersion` 落库 |
| P3 生态互通 | 3–4 周 | 不被孤立 | **Health Connect（不是 Google Fit，§7.4）** + HealthKit 读写、FIT/TCX 导入、BLE 心率带（HRS 0x180D）与跑速步频（RSC 0x1814）、Strava API v3 兼容的数据模型 |
| P4 月报年报 | 4–6 周 | 传播引擎 | 月报（卡片式、≥3 次门槛、固定刷新日）+ 年度总结（Wrapped 式分页）+ 日历热力图 + 全年轨迹叠加地图 + 极坐标时段热力图 |
| P5 训练科学 | 持续 | 留存与付费 | TRIMP/努力分 → CTL/ATL/TSB → CV/D' 与 Riegel 成绩预测 → GAP 坡度调整配速；训练计划、鞋里程（600–800km 提醒） |
| P6 中国区专项 | 与 P1 并行 | 合规可上架 | 高德 SDK（**8.1.0+ 必须先调 `updatePrivacyShow`/`updatePrivacyAgree`，否则地图白屏**）+ **猎鹰轨迹纠偏**（1 年免费存储）+ WGS-84↔GCJ-02 + App 备案/软著 + 各厂商 ROM 白名单引导流 + 单独同意弹窗 |
| P7 鸿蒙原生（可选） | 后置 | 增量入口 | ✅ 高德已有 HarmonyOS NEXT 星河版 SDK、✅ 咕咚已上鸿蒙原生版——华为设备存量是不可忽略的增量入口 |

**P0 必须先做**：这个品类的失败案例几乎都死在"记录不可靠"，而不是"功能不够多"。✅ 佐证：连 Strava 都要在官方帮助文档里专门列出"记录中会被 OS 杀掉"的华为机型清单并写《How to Fix GPS Recording Issues on Huawei Phones》专文（§6.4）。

**反作弊与数据一致性从 P1 就要内建**（不要等到有排行榜才补）：✅ 咕咚的评论区实证了"删除单条记录后总统计不同步""里程偶发出错"这类口径 bug 会直接变成差评（§5.5）。做法见 §6.1"所有派生指标可重算"与 §7.7 的服务端校验清单。

---

## 10. 合规与上架清单

### 10.1 中国大陆

**资质与备案**

- [ ] **App 备案（工信部）**：依据工信部 2023 年《关于开展移动互联网应用程序备案工作的通知》，App（含小程序、快应用）须完成备案，**未备案不得上架分发/接入网络**；各应用商店自 2024 年起强制核验备案号。入口 `https://beian.miit.gov.cn/`。✅ 佐证：本次抓取的 Keep / 咕咚 / 高德官网页脚均挂有 ICP 备案号 + 公安备案号。（⚪ 通知原文本次未取到，属 🟡 高置信常识）
- [ ] 如自建后端，需 ICP 备案；**会员订阅 + 电商需增值电信业务经营许可证**（✅ 实例：悦跑圈官网挂"增值电信业务经营许可证 粤B2-20220493"）
- [ ] 软件著作权证书（各安卓商店通用要求）
- [ ] 若做线上赛 + 实体奖牌，涉及实物电商资质与售后合规（✅ 悦跑圈另挂食品经营许可证；✅ Keep 的"不完赛不退款"投诉是前车之鉴，规则要写清）

**地图与坐标系（最容易被忽视的硬约束）**

- [ ] 底图数据须来自持**导航电子地图制作甲级测绘资质**的图商（高德/百度/腾讯）或自持资质（✅ 高德开放平台页脚即展示"甲测资字 11112528 / 11111093"）
- [ ] 公开展示的坐标须为 **GCJ-02**；GPS 原始 WGS-84 直接叠国内底图会偏移数百米 → **必须纠偏**；✅ 建议直接用高德官方 `CoordinateConverter` 与**猎鹰轨迹纠偏绑路**，不要自研（民间逆向算法商用合规性存疑）
- [ ] **内部一律存 WGS-84，仅在展示层转 GCJ-02**（保证导出 GPX 给国际平台不偏移）
- [ ] ⚠️ **高德 SDK 8.1.0 起的强制隐私接口**：必须先调用 `updatePrivacyShow` 与 `updatePrivacyAgree`，**否则地图白屏、搜索定位抛异常**（✅ 官方概述页原文）；工信部 SDK 检查亦要求**调用 `updatePrivacyAgree` 前不得采集**
- [ ] ⚠️ 境内不要使用 OSM 官方瓦片做生产底图（✅ 官方政策禁止批量下载/离线，且国界画法有审图合规问题）；Mapbox 在境内无节点与审图号（🟡）

**个人信息保护（PIPL）**

- [ ] **行踪轨迹属"敏感个人信息"（PIPL 第 28 条），处理须取得单独同意（第 29 条）**，并具备特定目的与充分必要性
- [ ] 最小必要原则；隐私政策明示收集范围；**第三方 SDK（地图/推送/统计）共享数据必须在隐私政策中列明目录**
- [ ] 提供关闭、导出、删除入口（数据可携与删除权）
- [ ] 高频踩线点（🟡 基于监管通报常见案由）：未单独同意即收集精确定位、后台持续定位无提示、**轨迹默认公开到社交**、SDK 未披露
- [ ] **数据出境**：接入 Strava / Google Fit 或使用海外服务器会触发安全评估或标准合同义务
- [ ] 执法依据：《App 违法违规收集使用个人信息行为认定方法》（四部门，2019）
- ⚠️ **引用注意**：`gov.cn` 的 PIPL 文本、`cac.gov.cn` 的认定方法、`npc.gov.cn` 的 PIPL 链接本次均 404，**引用请改用国家法律法规数据库 `flk.npc.gov.cn`**

**权限话术与商店**

- [ ] **后台定位话术基线**：✅ 头部产品（Keep / 悦动圈 / 咕咚 / 华为 / 荣耀）的 App Store 描述**统一附有"持续使用后台 GPS 会显著降低电池续航"及定位用途说明**——这既是苹果审核基线，也是你该照抄的话术
- [ ] 各安卓商店（华为/小米/OPPO/vivo/应用宝）分别提交；敏感权限（定位、健康、身体传感器）**逐项声明用途**；实名开发者账号；未成年人保护与适老化说明；华为市场另有 HarmonyOS 兼容性要求
- [ ] 若有社交/UGC，需内容安全审核机制
- [ ] Android 侧必须做**各厂商 ROM 白名单引导流**（华为/小米/OPPO/vivo 路径不同，✅ 可参照 `dontkillmyapp.com` 的分厂商指引），否则杀后台导致断轨

### 10.2 海外（App Store / Google Play）

**iOS**

- [ ] `NSLocationWhenInUseUsageDescription` / `NSLocationAlwaysAndWhenInUseUsageDescription` **文案必须具体讲清用途**（审核会看文案）🟡
- [ ] `NSMotionUsageDescription`（若用 CMPedometer/CMMotionActivityManager）、`NSHealthShareUsageDescription` / `NSHealthUpdateUsageDescription`
- [ ] Info.plist 的 `UIBackgroundModes` 含 `location`（及语音播报所需 `audio`）——⚠️ **✅ 已核实：未配 `location` 就把 `allowsBackgroundLocationUpdates` 置 true 会直接致命崩溃**
- [ ] `activityType = .fitness`、`pausesLocationUpdatesAutomatically = false`（✅/🟡，见 §6.4）
- [ ] ✅ `geolocator` README 明确警告：**开启 Location Updates 后台能力需在审核时向 Apple 详细说明用途，否则会被拒**
- [ ] App Review Guidelines：历史上 **2.5.4**（后台服务仅限预期用途，如导航/健身）与 **5.1.1/5.1.2**（数据收集需同意、位置数据使用声明）是定位类 App 的审核焦点（⚪ 条款编号以最新版为准；✅ 指南页面本身可达）
- [ ] 可评估 iOS 17+ 的 `CLBackgroundActivitySession`（✅ 符号存在，细节 ⚪）

**Android**

- [ ] 权限：`ACCESS_FINE_LOCATION`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_LOCATION`、`POST_NOTIFICATIONS`、`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`（12+）、`ACTIVITY_RECOGNITION`（若做自动开始）、`android.permission.health.*`（Health Connect）
- [ ] **`ACCESS_BACKGROUND_LOCATION`：建议设计上避免必需**——跑步记录用"仅使用期间"权限 + `location` 类型前台服务即可完成（🟡，见 §6.4），只有"自动检测开始跑步"才真正需要"始终允许"；Android 11+ 不能与前台权限同时请求，须分两步引导
- [ ] `android:foregroundServiceType="location"`（Android 14+ 强制声明类型，✅ 文档路径已核实；⚪ 正文因原站不可达未抓取，上架前请对照官方文档确认最新要求）
- [ ] ⚠️ **不要用 `dataSync` 类型前台服务**：✅ Android 15 起 `dataSync`/`mediaProcessing` 每 24 小时最多运行 6 小时即被停止，而 **`location`/`health` 类型不受此限**
- [ ] `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 受 Google Play 政策限制（健身类通常在允许清单内 🟡）
- [ ] **Google Play 位置权限申报表**：核心功能演示视频 + 隐私政策链接 + 显著披露声明；未获批强行上架会被移除（🟡；⚪ 政策页本环境不可达）
- [ ] **不要再集成 Google Fit SDK**：✅ 官方明示其 API（含 REST）**2026 年停用**、**2024-05-01 起已无法注册**，端侧迁移到 Health Connect

**通用**

- [ ] 隐私区域（Privacy Zone）与地图可见性设置，避免暴露用户住址；公开活动默认截断起终点
- [ ] GDPR：数据可携（导出 GPX/FIT/JSON）、被遗忘权（删号 + 全部轨迹）、处理合法性基础与 DPIA（🟡；⚪ 原文站点本次不可达）

---

## 11. 风险清单

| 风险 | 影响 | 缓解 | 证据 |
|---|---|---|---|
| 后台被杀导致轨迹中断 | 致命（差评主因） | `location\|health` 类型前台服务 + 断点续记 + 分机型白名单引导流 + 本地每 5s 持久化 + 上报被杀机型统计 | ✅ 连 Strava 都要为华为机型写专门帮助文档 |
| 误用 `dataSync` 前台服务 | 高（Android 15 起 6 小时被强制停止） | 记录服务只用 `location`（或 `health`）类型 | ✅ |
| 耗电过高 | 高 | 1Hz + `minUpdateDistance` 0~5m、优先用多普勒速度、关闭无用传感器、避免高频地图重绘；用 Perfetto/`batterystats` 实测 | 🟡 |
| GPS 漂移导致距离虚高 | 高 | 四步去漂移过滤链 + EMA 平滑 + Douglas–Peucker 抽稀 + UI 质量标记；国内可买猎鹰纠偏 | ✅ 纠偏服务存在 |
| **坐标系错用**（WGS-84 直接叠国内底图） | 高（轨迹偏移数百米，且难以事后修复） | 内部只存 WGS-84，展示层转 GCJ-02；用官方 `CoordinateConverter` | 🟡 量级为行业共识 |
| **高德 SDK 未调隐私接口** | 高（地图白屏、功能直接不可用） | 启动即调 `updatePrivacyShow`/`updatePrivacyAgree`，且**在同意前不采集** | ✅ 官方原文 |
| 与巨头同质化 | 高 | 押注报告解释力与美学，避开社交战场；抓"外资退场"留下的专业工具空位 | ✅ NRC/adidas 中国区不在架、Garmin Connect 中国区 2.99 分 |
| 依赖停滞 | 中高 | 选型前查维护状态：**避开 `isar`（2025-06 后无推送）与 `flutter_background_service`（22 个月未更新）** | ✅ |
| **误集成 Google Fit** | 中（返工） | 直接用 Health Connect | ✅ 官方明示 2026 停用 |
| 地图授权成本 | 中 | 早期用 MapLibre + 自建/商业瓦片源或高德免费额度（猎鹰存储 1 年免费）；**不要用 OSM 官方瓦片做生产** | ✅ OSM 政策禁止离线/批量 |
| 开源许可证污染 | 中 | 只用 MIT/Apache-2.0/BSD 代码；GPL/AGPL 仅参考设计；注意 `leaflet-elevation` 是 GPL-3.0 | ✅ |
| 指标算法误导用户 | 中 | 标注"估算"、注明模型出处（学 Strava 标注 Banister 1975 / Davies-Minetti）、不做医疗宣称 | ✅ |
| 统计口径 bug（删记录后汇总不同步） | 中（直接变差评） | 数据层单一事实源 + 派生指标可重算 + 可审计对账 | ✅ 咕咚评论区实证 |
| 奖牌/线上赛售后纠纷 | 中 | 透明规则（定金制或完赛后付费），避免"不完赛不退款" | ✅ Keep 投诉实证 |
| AI 功能引起反感 | 低中 | 可关闭、有依据、不默认开启 | ✅ Keep"AI 不能关"吐槽实证 |

---

## 12. 证据缺口：本轮已解决 vs 仍需补充

### 12.1 本轮已解决（原计划的待核实项，现已取得证据）

| 原待核实项 | 现状 |
|---|---|
| Runalyze 是否开源/可自托管 | ✅ 已确认**开源版终止**（仓库 2019-11-08 归档、无许可证） |
| Health Connect 取代 Google Fit 的时间线 | ✅ 官方横幅原文：**API（含 REST）2026 年停用，2024-05-01 起无法注册** |
| Android 前台服务与后台定位的当前约束 | ✅ 部分解决：Android 14 类型强制、Android 15 的 6 小时限制与 `location`/`health` 豁免、Activity Transition API、权限分步申请流程（⚪ 官方正文仍因原站不可达未逐页抓取） |
| 各产品技术栈的直接证据 | ✅ 部分解决：Strava（GitHub 组织仓库反推）、FitTrackee/FitoTrack/RunnerUp/wger/gpx.studio（构建文件与 topics 直读）；⚪ Keep 的跨平台方案证据链已消失 |
| 国产 ROM 后台管控 | ✅ 取得权威二手证据：`dontkillmyapp.com` 全文 + **Strava 官方帮助文档列出会被中断记录的华为机型清单**（⚪ 仍缺自研实测数据） |
| 中国市场价格锚点 | ✅ iTunes Search API 实测 IAP 标价（Keep/悦跑圈/咕咚/华为/悦动圈/行者/两步路） |
| 地图与轨迹合规方案 | ✅ 高德"运动行业解决方案"与猎鹰轨迹服务、腾讯位置服务官方文档全文 |

### 12.2 仍需补充（按对决策的影响排序）

1. **各产品订阅价格与权益明细（海外）**：Strava pricing 页未取到；COROS / Suunto / TrainingPeaks / Komoot / AllTrails / Relive / Runkeeper / MapMyRun / Fitbit / Samsung Health / Apple Fitness 的详情本轮未抓到。
2. **Strava Year in Sport 的具体卡片内容**（仅确认季节性开放与 2026-01-23 下线）；Garmin 的 Training Readiness / Body Battery 指标页细节。
3. **训练指标公式的原始文献**：Banister TRIMP、Riegel、ACWR（Gabbett BJSM 2016）、Tanaka、Monod-Scherrer CV/D'——本次 Wikipedia 与论文站均不可达，公式为教科书共识但**未在线核实**，实现前务必对照原文。
4. **国产 ROM 后台管控的自研实测数据**：需在目标机型矩阵上跑 1 小时实测，量化断轨率与耗电。
5. **Google Play 侧的评分与用户痛点样本**：`play.google.com` 不可达，本报告的用户吐槽证据**全部来自 App Store 中国区评论区**，存在平台偏差。
6. **`react-native-background-geolocation` 的 Android 商业授权现状**：仓库 LICENSE 为 MIT，但 homepage 指向商业商店，选型前须向官方确认。
7. **Garmin FIT SDK 与 Garmin Connect API 的许可条款原文**（商用前必须确认）。
8. **Keep 的跨平台方案**：官方 GitHub 组织证据已消失（仅剩 2015 年 Ruby 仓库，Nuwa 已 404），业内流传的分享文章原始链接不可达，**不建议引用传闻**。
9. ⚪ 未能定位的开源项目：`GpsTrackServer`（搜索 0 命中）、`Trackprofiler`（原地址 404）、`OpenStrava`（未发现知名同名项目）；GPXSee 的 Star 与最近发布；iOS Open GPX Tracker 的具体地图 SDK。
10. ⚪ 失效链接警示（引用时勿用）：`gov.cn` 的 PIPL 文本、`cac.gov.cn` 的认定方法、`npc.gov.cn` 的 PIPL 页、`ir.keep.com`、`developers.weixin.qq.com` 的 `wx.getWeRunData` 文档、`consumer.huawei.com/cn/support/huawei-health/`、高德 FAQ 36055、`yohcop/trackprofiler`、`vbabiy/tcxparser`、Apple 的 `CMVisit`（正确类名是 `CLVisit`）。

---

## 13. 附录：本轮在线核实的关键事实清单（可直接引用）

**平台与 API**

1. ✅ **Google Fit 的各项 API（含 REST）将于 2026 年停用；自 2024-05-01 起开发者已无法注册**；云端集成迁移到 Google Health API，端侧迁移到 Health Connect（来源：`developers.google.cn/fit` 官方横幅）。
2. ✅ Health Connect 迁移文档正确路径为 `/health-and-fitness/health-connect/migration/fit`（旧 `migrate-google-fit` 已 404）。
3. ✅ GNSS Raw Measurements 文档现行路径为 `/develop/sensors-and-location/sensors/gnss`（旧路径已 404）。
4. ✅ iOS：**未配置 `UIBackgroundModes=location` 时将 `allowsBackgroundLocationUpdates` 置 true 会直接崩溃**；系统会自动显示蓝条/胶囊后台定位指示（Apple 文档 JSON 原文）。
5. ✅ `CLActivityType` 六个 case：`airborne`/`automotiveNavigation`/`fitness`/`maritime`/`other`/`otherNavigation`；`.fitness` 官方释义为"专门健身时段（步行/跑步/骑行训练）中的定位"。Visit API 的正确类名是 **`CLVisit`**（"CMVisit"不存在）。
6. ✅ **Android 15：`dataSync` 类前台服务每 24 小时最多 6 小时即被停止；`BOOT_COMPLETED` 禁止启动 5 类前台服务；`location` 与 `health` 类型不受 6 小时限制**（二手转引官方，原站被墙）。
7. ✅ Activity Recognition 的 **Activity Transition API** 可订阅"开始/结束某活动"的转换事件，官方文档明确举例"里程记录 App 在用户开始驾驶时开始记录"（`developers.google.cn` 全文）。
8. ✅ `geolocator` 的实现是"**FLPC 优先、无 GMS 回退 `LocationManager`**"——这对国内无 GMS 设备是必需能力。

**地图与瓦片**

9. ✅ **OSM 官方瓦片政策**：只允许 `tile.openstreetmap.org/{z}/{x}/{y}.png`；必须署名；**必须发送可识别的自定义 User-Agent（SDK 默认 UA 会被封）**；缓存至少 7 天；**禁止批量下载/预取/离线使用**；无 SLA、随时可封禁。
10. ✅ **PMTiles v3**：单文件瓦片归档 + HTTP Range 按需读取（S3 即可托管、零运维）、只读；全球矢量底图相比逐瓦片方案可省 70%+ 体积与请求费。
11. ✅ **MapLibre** 是组织级项目：GL JS（Web）、Native（Android/iOS/C++，支持 OpenGL/Metal/Vulkan）、Style Spec、新 Tile Spec、**Martin 瓦片服务器（Rust，可从 PostGIS 动态生成 MVT）**。
12. ✅ **高德 SDK 8.1.0 起强制 `updatePrivacyShow`/`updatePrivacyAgree`**（源于 PIPL），否则地图白屏；官方提供 Flutter 插件、猎鹰轨迹纠偏（**存储 1 年免费**）、离线地图、坐标转换工具、HarmonyOS NEXT 星河版 SDK；页脚测绘资质"甲测资字 11112528/11111093"。高德 FAQ 36055 已失效。
13. ✅ **GPX 1.1 schema**：WGS-84、公制，结构为 `trk/trkseg/trkpt` + `wpt`/`rte` + `extensions`（`topografix.com/gpx/1/1/` 全文）。

**依赖健康度（2026-10-23 实测）**

14. ✅ 活跃：`geolocator` 14.1.2（3 天前）、`flutter_foreground_task` 11.0.3（45 天前）、`fl_chart` 1.3.0（1 天前）、`flutter_map` 8.3.2（55 天前）、`maplibre` 0.3.6（58 天前）、`compose-multiplatform`（19.4k★，日更，1.13 开发中）、Vico（日更）、`react-native-skia`（日更，已迁 wcandillon 组织）、**MPAndroidChart 已复活**（38.2k★，2026-10 推送，Kotlin 化）、Charts iOS（已迁 ChartsOrg，2026-03 推送，978 open issues）、`realm-swift`（2026-09 推送，默认分支 `community`）。
15. ✅ **停滞/归档，不建议**：`isar`（2025-06-14 后无推送、185 open issues）、`flutter_background_service`（约 22 个月未更新）、`google/battery-historian`（已归档，最后推送 2019-07）。
16. ✅ `react-native-background-geolocation`：仓库 LICENSE 文件为 MIT、homepage 指向 `shop.transistorsoft.com`、2026-10 仍有推送；**Android 商业授权现状待向官方确认**。

**开源生态**

17. ✅ **Runalyze 开源版已终止**（`Runalyze/Runalyze` 归档、最后推送 2019-11-08、`license=null`，README 原文 "Discontinued as OpenSource"）。
18. ✅ 多个项目主仓已迁 **Codeberg**（OpenTracks、Gadgetbridge、FitTrackee、FitoTrack），GitHub 仅镜像/CI——**只看 GitHub 会误判为停更**。
19. ✅ 技术栈直读：FitTrackee = Flask 3.1 + Vue 3.5 + TS + PostgreSQL 14–18/PostGIS + Chart.js + Leaflet + gpxpy；FitoTrack = Kotlin + Compose（BOM 2026.05.01）+ mapsforge 0.27.0 + Room 2.8.4 + Nordic BLE 2.2.4 + MPAndroidChart 3.1.0；RunnerUp = Java 17 + osmdroid 6.1.20/Mapbox 11.32.x + MPAndroidChart 4.0.1 + `:hrdevice`(BLE/ANT+)；gpx.studio = SvelteKit + MapLibre GL JS + GraphHopper + Chart.js + Dexie.js；wger Flutter = Riverpod 3.x + drift + PowerSync + fl_chart + health_bridge。

**竞品**

20. ✅ **Strava 地图数据 = Mapbox + OpenStreetMap**（官方帮助中心）；技术栈线索指向 **Rails + nginx/Passenger（Ruby）、Spark + ScalaPB（Scala）、Mesos/Marathon + HAProxy + ZooKeeper（Go 工具）、go-mapnik + OSM iD 编辑器 fork（自建地图管线）、Grafana + Prometheus、原生 Swift iOS、Contentful CMS、Intercom 客服、Adyen 支付**。
21. ✅ **Strava 的 Fitness & Freshness 明确基于 Banister 1975 脉冲响应模型（CTL/ATL/TSB）**；GAP 基于 Davies/Minetti 研究；Performance Predictions 用 ML 预测 5K/10K/半马/全马；Instant Workouts 可下发 Garmin/COROS/Wahoo/Pioneer；Beacon 在 App 内免费、设备端订阅。
22. ✅ **Strava Month in Sport 的机制**：卡片式（Active Days / Trophy Case 最多 4 项按优先级 / Social Competition 匹配关键指标相差 30% 以内的互关好友 / Totals 订阅版含百分位排名、免费版含近 12 个月柱状图）；**上月需 ≥3 次活动**；**每月 26 日刷新**；**仅移动端**。
23. ✅ Strava 2026 动向：收购 Runna（跑步训练计划向其迁移）、推出接 Claude 的 MCP Connector、与 Samsung 合作（Galaxy Watch 预装 + 路线入 Samsung Health）、与 adidas adiClub 积分打通、turn-by-turn 语音导航即将上线；用户量口径自相矛盾（1.95 亿 vs 1.2 亿）。
24. ✅ **Nike Run Club 完全免费无订阅**，Audio Guided Runs（含 Kipchoge 等教练语音），训练计划限 US/UK/JP/CN/BR/FR/DE/ES/IT，鞋款打标 + 里程提醒，美区 4.77★/约 42 万评分（2026-09 版本）。
25. ✅ **adidas Running 的 bundle id 仍是 `at.runtastic.gpssportapp`**（Runtastic 血统），宣称 70M 成员，90+ 运动类型，4.80★/约 10.9 万评分。
26. ✅ **Polar Flow**：平台免费随设备赠送；Smart Coaching 含 **Running Index（官方称"你的跑步 VO2max"）**、Running Program、FitSpark、Work-Rest Guide、Walking Test；App 7.37.0 新增 **Diary Trends**（近两周数据总览）；官方列出与 Strava/TrainingPeaks/adidas/komoot 同步。
27. ✅ **中国区价格锚点（iTunes API 实测）**：Keep 19/58/218 元，**超级 AI 会员 68/188/648 元**；悦跑圈 19/58/218；咕咚 18/48/168；华为「活力人生」首月 1 元、续 15 元/月、年卡 208 元；悦动圈 12/28/98；行者 18/50/188；两步路 18/50/198。
28. ✅ **Keep 官方运营数据**：2021 全年平均 MAU 3,440 万 → 2022 全年平均 MAU 3,640 万（约 +5.8%）、平均月度订阅会员 362 万、**会员渗透率 10.0%**；2020-12 软银愿景领投 F 轮 3.6 亿美元。
29. ✅ **外资退场**：Nike Run Club 与 adidas Running **中国区 App Store 不在架**；`apple.com.cn/apple-fitness-plus/` 返回 404（Fitness+ 未在大陆上线）；**Garmin Connect 中国区评分仅 2.99（5,562 个评分）**，而国产头部普遍 4.8+。
30. ✅ **COROS 中国区 App 描述明示与微信运动、悦跑圈、Keep 数据互联**；咕咚已有 HarmonyOS 原生版（`com.codoon.gps.hos`）；咕咚 OpenAPI 为 **to B 商务申请制 + 付费数据套餐**（非用户自助导出）。

---

*本报告由四路并行调研 + 在线证据核实生成，检索与核实日期为 2026-10-23。标注 ✅ 的条目可直接引用；🟡 为高置信推断；⚪ 为待核实。*
*由于本会话 `web_search` 不可用（缺 API Key）且部分域名被网络环境阻断，报告不含开放式检索发现的长尾竞品；用户吐槽证据全部来自 App Store 中国区评论区，存在平台偏差。*
