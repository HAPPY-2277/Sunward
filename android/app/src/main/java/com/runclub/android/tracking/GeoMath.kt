package com.runclub.android.tracking

import kotlin.math.asin
import kotlin.math.atan2
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

    /** 把任意角度归一化到 [0, 360) */
    fun normalizeDegrees(degrees: Double): Float {
        val normalized = (degrees % 360.0 + 360.0) % 360.0
        return normalized.toFloat()
    }

    /**
     * 两点间的前进方向（方位角，度，正北为 0、顺时针 0~360）。
     *
     * 用途：GPS 点的 bearing 在网络定位、模拟轨迹、静止时常常缺失，
     * 地图上的跑者标记需要一个朝向，这时按前后两点的几何方向推算。
     * 用平面近似（[deltaMeters]）而非大圆公式：跑步尺度下两者的差别
     * 远小于 GPS 本身的误差，代码也更好读。
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val (east, north) = deltaMeters(lat1, lon1, lat2, lon2)
        return normalizeDegrees(Math.toDegrees(atan2(east, north)))
    }

    /**
     * 方位角的指数平滑（EMA）。
     *
     * 罗盘读数抖动明显，直接拿去转地图上的箭头会左右乱摆，所以先平滑一次。
     * 角度是环形量：跨 0°/360° 时必须走**最短弧**，
     * 否则从 359° 转到 1° 会反向绕 358°，而不是前进 2°。
     *
     * @param alpha 新值的权重（0~1），越大越跟手、越小越平稳
     */
    fun smoothBearing(previousDeg: Float, currentDeg: Float, alpha: Float = 0.35f): Float {
        var delta = (currentDeg - previousDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return ((previousDeg + alpha * delta) % 360f + 360f) % 360f
    }
}
