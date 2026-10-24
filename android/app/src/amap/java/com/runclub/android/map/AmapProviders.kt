package com.runclub.android.map

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng

// ===== amap 变体：真实高德底图 =====

fun provideMapLayer(activity: Activity): MapLayer = AmapLayer(activity)

fun providePrivacyGate(): PrivacyGate = AmapPrivacyGate

/**
 * 高德 3D 地图层。
 *
 * 注意：SDK 每次进程启动都必须重调 `updatePrivacyShow` / `updatePrivacyAgree`，
 * 否则地图白屏且无错误提示（见 [AmapPrivacyGate]）。
 */
class AmapLayer(private val activity: Activity) : MapLayer {

    private var mapView: MapView? = null
    private var aMap: AMap? = null
    private var centeredOnce = false

    override val isActive: Boolean get() = aMap != null

    override val statusText: String
        get() = when {
            !MapSupport.isArmAbi() -> "ABI ${MapSupport.abiText()} 无高德 native 库，已用手绘地图"
            !AmapPrivacyGate.isAgreed(activity) -> "未同意隐私政策，高德地图未初始化"
            aMap == null -> "高德地图初始化失败，已用手绘地图"
            else -> "高德底图已启用"
        }

    override fun attach(host: ViewGroup, savedInstanceState: Bundle?) {
        if (!MapSupport.isArmAbi()) return
        if (!AmapPrivacyGate.isAgreed(activity)) return

        val view = runCatching { MapView(activity) }.getOrNull() ?: return
        host.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        runCatching { view.onCreate(savedInstanceState) }
        mapView = view

        val map = runCatching { view.map }.getOrNull() ?: return
        aMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isScaleControlsEnabled = false
            isMyLocationButtonEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
        }
        map.mapType = AMap.MAP_TYPE_NORMAL
        // 先用上海滨江兜底，收到第一个定位点后移到真实位置
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), 15f))
    }

    override fun centerOnWgs84(lat: Double, lon: Double) {
        val map = aMap ?: return
        if (centeredOnce) return
        centeredOnce = true
        val (gcjLat, gcjLon) = GeoConvert.wgs84ToGcj02(lat, lon)
        runCatching {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(gcjLat, gcjLon), 17f))
        }
    }

    override fun onResume() { mapView?.onResume() }
    override fun onPause() { mapView?.onPause() }
    override fun onDestroy() { mapView?.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { mapView?.onSaveInstanceState(outState) }

    private companion object {
        const val DEFAULT_LAT = 31.2350
        const val DEFAULT_LON = 121.4900
    }
}

/**
 * 高德 SDK 隐私合规。**未调用这两个接口地图会直接白屏**，且没有明显报错。
 * 合规做法：先征得用户同意，再调用；同意状态存本地。
 */
object AmapPrivacyGate : PrivacyGate {

    private const val PREFS = "runclub_privacy"
    private const val KEY_AGREED = "amap_privacy_agreed"

    override fun isAgreed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AGREED, false)

    override fun agree(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AGREED, true).apply()
        apply(context)
    }

    override fun applyIfConsented(context: Context) {
        if (isAgreed(context)) apply(context)
    }

    private fun apply(context: Context) {
        val app = context.applicationContext
        runCatching { MapsInitializer.updatePrivacyShow(app, true, true) }
        runCatching { MapsInitializer.updatePrivacyAgree(app, true) }
    }
}
