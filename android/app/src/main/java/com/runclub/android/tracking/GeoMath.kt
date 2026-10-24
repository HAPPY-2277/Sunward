package com.runclub.android.tracking

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理计算工具。纯 Kotlin，不依赖 Android 框架，可直接在 JVM 上跑单元测试。
 */
object GeoMath {

    /** 地球平均半径（米），与调研报告 §6.3 取值一致 */
    const val EARTH_RADIUS_M: Double = 6_371_008.8

    /**
     * Haversine 公式计算两点间大圆距离（米）。
     * 跑步 App 的常规误差远小于公式本身的近似误差，够用。
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)

        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * 经纬度平移：把米换算成度（用于生成模拟轨迹）。
     * 纬度 1 度 ≈ 111,195 m；经度按当前纬度收缩。
     */
    fun offsetMeters(lat: Double, lon: Double, eastM: Double, northM: Double): Pair<Double, Double> {
        val dLat = northM / 111_195.0
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
        val dLon = eastM / (111_195.0 * cosLat)
        return Pair(lat + dLat, lon + dLon)
    }

    /**
     * 把两点差换算成「东向 / 北向」的米制向量。
     *
     * 之所以要向量而不是直接要距离：距离累加前需要对位移向量做 EMA 平滑，
     * 直接平滑经纬度会把稳定速度一起衰减（每步只移动 α 倍），导致少算 60% 以上。
     */
    fun deltaMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Double, Double> {
        val north = (lat2 - lat1) * 111_195.0
        val cosLat = cos(Math.toRadians((lat1 + lat2) / 2.0)).coerceAtLeast(1e-6)
        val east = (lon2 - lon1) * 111_195.0 * cosLat
        return Pair(east, north)
    }
}
