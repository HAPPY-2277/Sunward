package com.runclub.android.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 定位采集封装。
 *
 * 刻意使用系统 [LocationManager] 而不是 FusedLocationProviderClient：
 * 后者依赖 Google Play 服务，国内多数手机没有 GMS 会直接失败；
 * LocationManager 是 AOSP 系统服务，无此依赖（调研报告 §6.4）。
 */
class LocationTracker(private val context: Context) {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(): Boolean = runCatching {
        locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    }.getOrDefault(false)

    fun isNetworkEnabled(): Boolean = runCatching {
        locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }.getOrDefault(false)

    /** 当前可用 provider，用于调试面板展示 */
    fun availableProviders(): List<String> = runCatching {
        locationManager?.getProviders(true)?.toList().orEmpty()
    }.getOrDefault(emptyList())

    /**
     * 1Hz 定位流：GPS 优先，不可用时回退 NETWORK。
     * 触发条件为「每 1 秒」而非「每 N 米」，距离过滤交给 [TrackPointFilter]，
     * 这样静止时也能持续拿到重复点，便于观察漂移是否被正确剔除。
     */
    fun locationFlow(): Flow<Location> = callbackFlow {
        val manager = locationManager
        if (manager == null) {
            close(IllegalStateException("设备不支持定位服务"))
            return@callbackFlow
        }

        val provider = when {
            isGpsEnabled() -> LocationManager.GPS_PROVIDER
            isNetworkEnabled() -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            close(IllegalStateException("GPS 与网络定位均未开启"))
            return@callbackFlow
        }

        val listener = LocationListener { location -> trySend(location) }

        try {
            manager.requestLocationUpdates(
                provider,
                SAMPLE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (security: SecurityException) {
            close(security)
            return@callbackFlow
        } catch (illegal: IllegalArgumentException) {
            close(illegal)
            return@callbackFlow
        }

        awaitClose { runCatching { manager.removeUpdates(listener) } }
    }

    /**
     * 预热用的「最后一已知位置」：GPS / 网络 / 被动三个 provider 里取时间最新的一个。
     *
     * 为什么需要它：点 GO 到真正开始计时之间有 3-2-1 倒计时，冷启动的第一个 GPS 点
     * 往往要好几秒，这段时间先用系统缓存的位置把地图挪到跑者脚下，
     * 就不会出现「已经在计时了，地图还停在兜底城市」的情况。
     * 无权限 / 无缓存 / 无 provider 时返回 null，调用方按「还没有位置」处理。
     */
    @SuppressLint("MissingPermission")  // 入口已判 hasPermission()
    fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        val manager = locationManager ?: return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    companion object {
        /** 1Hz，行业主流采样频率 */
        const val SAMPLE_INTERVAL_MS = 1000L
    }
}
