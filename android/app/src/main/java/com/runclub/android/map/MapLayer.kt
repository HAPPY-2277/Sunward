package com.runclub.android.map

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import com.runclub.android.tracking.TrackPoint
import com.runclub.android.ui.widget.TrackCanvasView

/**
 * 地图层抽象。
 *
 * 为什么要有这层：高德 3D 地图 SDK 只有 ARM 的 native 库，打进 APK 后
 * **x86_64 模拟器连安装都会被拒**（`INSTALL_FAILED_NO_MATCHING_ABIS`）。
 * 所以拆成两个构建变体：
 * - `amap` 变体：真实高德底图（真机用）
 * - `local` 变体：不含高德 SDK（模拟器用，保留原型手绘地图）
 *
 * 两边各自提供 `provideMapLayer()` / `providePrivacyGate()`，
 * 主源码集只依赖这两个接口，因此两种变体都能编译。
 */
interface MapLayer {
    /** 地图是否真的挂上了（false 时界面留在手绘地图上） */
    val isActive: Boolean

    /** 给调试面板显示的一句话状态 */
    val statusText: String

    /**
     * 预热：点 GO 时立刻调用。
     * 只做合规状态与 SDK 的类加载这类「不依赖 View」的准备，
     * 让底图瓦片的下载尽量提前到 [attach] 那一刻，而不是等计时开始。
     */
    fun prewarm()

    fun attach(host: ViewGroup, savedInstanceState: Bundle?)

    /**
     * 挂上「归一化轨迹叠加层」。
     * 只有 local 变体（无真实底图）需要它；amap 变体用地理坐标直接画在地图上。
     */
    fun attachOverlay(overlay: TrackCanvasView)

    /**
     * 更新轨迹；坐标为 WGS-84，实现者负责按需转换。
     * @param endpoints true = 同时画起点与终点标记（成绩页的完整路线）；
     *   跑步中传 false —— 终点就是跑者当前的位置，由 [updateRunnerPosition] 的标记表示。
     */
    fun setTrack(points: List<TrackPoint>, endpoints: Boolean = false)

    /**
     * 更新跑者位置与朝向，并把镜头推到道路级跟随。
     * 入参是 **WGS-84**，实现者负责转 GCJ-02。
     * @param bearingDeg 正北 0°、顺时针；为 null 时实现者应保持上一次朝向。
     */
    fun updateRunnerPosition(lat: Double, lon: Double, bearingDeg: Float?, accuracyM: Float?)

    /** 把整条轨迹装进视野（成绩页用）。跑步中不要调，会打断跟随。 */
    fun fitTrack(points: List<TrackPoint>)

    /** 是否允许手势平移/缩放。成绩页要关掉，免得地图和页面滚动抢手势。 */
    fun setGesturesEnabled(enabled: Boolean)

    fun onResume()
    fun onPause()
    fun onDestroy()
    fun onSaveInstanceState(outState: Bundle)
}

/** 隐私合规门面：高德要求先征得同意再初始化地图 */
interface PrivacyGate {
    fun isAgreed(context: Context): Boolean
    fun agree(context: Context)
    fun applyIfConsented(context: Context)
}
