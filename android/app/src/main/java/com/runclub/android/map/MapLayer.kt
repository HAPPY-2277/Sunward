package com.runclub.android.map

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup

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

    fun attach(host: ViewGroup, savedInstanceState: Bundle?)

    /** 传入 **WGS-84** 坐标，实现者负责转 GCJ-02 */
    fun centerOnWgs84(lat: Double, lon: Double)

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
