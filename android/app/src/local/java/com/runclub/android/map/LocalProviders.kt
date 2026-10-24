package com.runclub.android.map

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup

// ===== local 变体：不集成高德，界面保留原型手绘城市地图 =====

fun provideMapLayer(activity: Activity): MapLayer = LocalMapLayer

fun providePrivacyGate(): PrivacyGate = LocalPrivacyGate

object LocalMapLayer : MapLayer {
    override val isActive: Boolean = false
    override val statusText: String = "local 变体未集成高德地图，使用手绘地图"

    override fun attach(host: ViewGroup, savedInstanceState: Bundle?) = Unit
    override fun centerOnWgs84(lat: Double, lon: Double) = Unit
    override fun onResume() = Unit
    override fun onPause() = Unit
    override fun onDestroy() = Unit
    override fun onSaveInstanceState(outState: Bundle) = Unit
}

/** 没有地图就不需要征询隐私，直接视为已同意，避免无意义弹窗 */
object LocalPrivacyGate : PrivacyGate {
    override fun isAgreed(context: Context): Boolean = true
    override fun agree(context: Context) = Unit
    override fun applyIfConsented(context: Context) = Unit
}
