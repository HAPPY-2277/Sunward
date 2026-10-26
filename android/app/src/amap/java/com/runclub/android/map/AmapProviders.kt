package com.runclub.android.map

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.runclub.android.R
import com.runclub.android.tracking.TrackPoint
import com.runclub.android.ui.widget.TrackCanvasView
import kotlin.math.roundToInt

// ===== amap 变体：真实高德底图 =====

fun provideMapLayer(activity: Activity): MapLayer = AmapLayer(activity)

fun providePrivacyGate(): PrivacyGate = AmapPrivacyGate

/**
 * 高德 3D 地图层。
 *
 * 注意：SDK 每次进程启动都必须重调 `updatePrivacyShow` / `updatePrivacyAgree`，
 * 否则地图白屏且无错误提示（见 [AmapPrivacyGate]）。
 *
 * 定位口径：不使用高德自带的定位（那会多一套定位 SDK 与合规声明），
 * 而是把 App 自己采集链里**已采纳**的点经 [GeoConvert] 转成 GCJ-02 后，
 * 通过自定义 [LocationSource] 喂给「我的位置」图层。
 * 这样地图上跑者的位置与轨迹线永远同源，不会出现两套定位各自漂移的情况。
 */
class AmapLayer(private val activity: Activity) : MapLayer {

    private var mapView: MapView? = null
    private var aMap: AMap? = null

    private var polyline: Polyline? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null

    /** 上一次 setTrack 的点数：变小即说明重新开始了一次记录，覆盖物要重建 */
    private var lastTrackSize = 0

    /** 第一次拿到真实位置后才推到道路级，之后由跟随模式保持这个缩放 */
    private var streetZoomApplied = false

    /** 「我的位置」图层的回调口，由 [updateRunnerPosition] 往里灌点 */
    private var locationListener: LocationSource.OnLocationChangedListener? = null

    private val locationSource = object : LocationSource {
        // 接口方法名是 activate/deactivate（不是 onActivate）：SDK 开启「我的位置」图层时回调，
        // 把灌点入口交给我们；关闭图层或销毁地图时回调 deactivate
        override fun activate(listener: LocationSource.OnLocationChangedListener) {
            locationListener = listener
        }

        override fun deactivate() {
            locationListener = null
        }
    }

    override val isActive: Boolean get() = aMap != null

    override val statusText: String
        get() = when {
            !MapSupport.isArmAbi() -> "ABI ${MapSupport.abiText()} 无高德 native 库，已用手绘地图"
            !AmapPrivacyGate.isAgreed(activity) -> "未同意隐私政策，高德地图未初始化"
            aMap == null -> "高德地图初始化失败，已用手绘地图"
            else -> "高德底图已启用"
        }

    override fun prewarm() {
        if (!MapSupport.isArmAbi()) return
        // 合规状态每个进程都要重报一次；顺带把 SDK 的类加载提前到点 GO 的时刻，
        // 这样进入实时页 attach() 创建 MapView（真正开始下载瓦片）时不必再等这一段。
        AmapPrivacyGate.applyIfConsented(activity)
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

        // 跑者标记：位置与朝向都由我们自己喂，图标按 bearing 旋转
        map.setLocationSource(locationSource)
        map.setMyLocationStyle(runnerStyle())
        map.isMyLocationEnabled = true

        // 先用上海滨江兜底，收到第一个定位点后移到真实位置并推到道路级
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_LAT, DEFAULT_LON), DEFAULT_ZOOM))
    }

    private fun runnerStyle(): MyLocationStyle = MyLocationStyle().apply {
        // 移动到地图中心 + 按 bearing 旋转图标（地图本身不转，转地图容易看晕）
        myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
        myLocationIcon(BitmapDescriptorFactory.fromBitmap(AmapMarkers.runner(activity)))
        // 关掉 SDK 默认的蓝色精度圈：配色与本项目不符，而且会盖住轨迹线
        strokeColor(Color.TRANSPARENT)
        radiusFillColor(Color.TRANSPARENT)
        strokeWidth(0f)
        interval(LOCATION_INTERVAL_MS)
    }

    /**
     * 组一个高德能吃的 Location。
     * 传入的经纬度必须已经是 **GCJ-02**（调用处转换），bearing 是真北顺时针角度。
     * `elapsedRealtimeNanos` 必须填：地图链路用它判断位置是否新鲜，
     * 只填墙钟 `time` 可能被当成过期点直接丢掉。
     */
    private fun buildLocation(
        gcjLat: Double,
        gcjLon: Double,
        bearingDeg: Float?,
        accuracyM: Float?
    ): Location = Location(MY_LOCATION_PROVIDER).apply {
        latitude = gcjLat
        longitude = gcjLon
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        bearingDeg?.let { bearing = it }
        accuracyM?.let { accuracy = it }
    }

    override fun updateRunnerPosition(
        lat: Double,
        lon: Double,
        bearingDeg: Float?,
        accuracyM: Float?
    ) {
        val map = aMap ?: return
        val (gcjLat, gcjLon) = GeoConvert.wgs84ToGcj02(lat, lon)
        runCatching {
            locationListener?.onLocationChanged(buildLocation(gcjLat, gcjLon, bearingDeg, accuracyM))
            if (!streetZoomApplied) {
                streetZoomApplied = true
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(gcjLat, gcjLon), STREET_ZOOM)
                )
            }
        }
    }

    /** 高德底图按地理坐标画线，不需要归一化叠加层（local 变体才用） */
    override fun attachOverlay(overlay: TrackCanvasView) = Unit

    override fun setTrack(points: List<TrackPoint>, endpoints: Boolean) {
        val map = aMap ?: return
        if (points.isEmpty()) {
            if (lastTrackSize > 0) runCatching { resetOverlays() }
            return
        }
        val density = activity.resources.displayMetrics.density

        runCatching {
            // 点数变小说明重新开始了一次记录（切换数据源 / 重新起跑），旧覆盖物要清掉，
            // 否则上一圈的线会一直留在地图上
            if (points.size < lastTrackSize) resetOverlays()
            lastTrackSize = points.size

            val latLngs = points.map {
                val (lat, lon) = GeoConvert.wgs84ToGcj02(it.lat, it.lon)
                LatLng(lat, lon)
            }

            // 1Hz 更新时只改点集，不要 remove + add：一次跑步上千个点，
            // 每帧重建 polyline 会明显掉帧（M4 验收时在真机上看到过卡顿）
            val existing = polyline
            if (existing == null) {
                polyline = map.addPolyline(
                    PolylineOptions()
                        .addAll(latLngs)
                        .width(TRACK_WIDTH_DP * density)
                        .color(ContextCompat.getColor(activity, R.color.accent_orange))
                        .setDottedLine(false)
                )
            } else {
                existing.setPoints(latLngs)
            }

            if (startMarker == null) {
                startMarker = map.addMarker(
                    MarkerOptions()
                        .position(latLngs.first())
                        .icon(BitmapDescriptorFactory.fromBitmap(AmapMarkers.start(activity)))
                        .anchor(ANCHOR_CENTER, ANCHOR_CENTER)
                        .zIndex(START_Z_INDEX)
                )
            }

            // 终点标记只有成绩页需要：跑步中「终点」就是跑者当前位置，由定位标记表示
            if (endpoints && latLngs.size >= 2) {
                val marker = endMarker
                if (marker == null) {
                    endMarker = map.addMarker(
                        MarkerOptions()
                            .position(latLngs.last())
                            .icon(BitmapDescriptorFactory.fromBitmap(AmapMarkers.end(activity)))
                            .anchor(ANCHOR_CENTER, ANCHOR_CENTER)
                            .zIndex(END_Z_INDEX)
                    )
                } else {
                    marker.position = latLngs.last()
                }
            } else if (endMarker != null) {
                endMarker?.remove()
                endMarker = null
            }
        }
    }

    override fun fitTrack(points: List<TrackPoint>) {
        val map = aMap ?: return
        val view = mapView ?: return
        if (points.size < 2) return
        val padding = (FIT_PADDING_DP * activity.resources.displayMetrics.density).roundToInt()

        // MapView 还没测量完时 newLatLngBounds 会因为「地图尺寸为 0」抛异常，
        // 成绩页是在 onCreate 里就调的，所以 post 到布局完成之后再取景
        view.post {
            runCatching {
                val builder = LatLngBounds.Builder()
                points.forEach {
                    val (lat, lon) = GeoConvert.wgs84ToGcj02(it.lat, it.lon)
                    builder.include(LatLng(lat, lon))
                }
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding))
            }
        }
    }

    override fun setGesturesEnabled(enabled: Boolean) {
        val map = aMap ?: return
        // 旋转与俯仰始终关闭（attach 里已经关掉），这里只放开平移与缩放
        map.uiSettings.isScrollGesturesEnabled = enabled
        map.uiSettings.isZoomGesturesEnabled = enabled
    }

    private fun resetOverlays() {
        polyline?.remove()
        polyline = null
        startMarker?.remove()
        startMarker = null
        endMarker?.remove()
        endMarker = null
        lastTrackSize = 0
        streetZoomApplied = false
    }

    override fun onResume() { mapView?.onResume() }
    override fun onPause() { mapView?.onPause() }
    override fun onDestroy() {
        locationListener = null
        mapView?.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { mapView?.onSaveInstanceState(outState) }

    private companion object {
        const val DEFAULT_LAT = 31.2350
        const val DEFAULT_LON = 121.4900

        /** 还没有任何定位时的兜底缩放：能看到城区即可 */
        const val DEFAULT_ZOOM = 15f

        /**
         * 道路级缩放：能看清路口与路名，又能容下两三百米的轨迹。
         * 高德 3D 地图的 zoom 取值 3~20，17~18 即街道级。
         */
        const val STREET_ZOOM = 17.5f

        /** 轨迹线宽（dp），与手绘叠加层的 5.5dp 线 + 9dp 白边视觉量级相当 */
        const val TRACK_WIDTH_DP = 9f

        /** 成绩页取景时四周留白，避免轨迹贴着卡片边缘 */
        const val FIT_PADDING_DP = 40

        const val ANCHOR_CENTER = 0.5f
        const val START_Z_INDEX = 2f
        const val END_Z_INDEX = 3f

        const val LOCATION_INTERVAL_MS = 1000L
        const val MY_LOCATION_PROVIDER = "runclub-runner"
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
