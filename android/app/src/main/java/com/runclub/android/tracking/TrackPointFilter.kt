package com.runclub.android.tracking

import kotlin.math.hypot

/**
 * 轨迹点过滤链 —— 去漂移，对齐调研报告 §6.2。
 *
 * 四条剔除规则：
 * 1. 精度：水平精度差于 [maxAccuracyM] 的点丢弃（GPS 漂移的主要来源）
 * 2. 时间戳：非递增（回退或重复）丢弃
 * 3. 速度：位置差分速度超过 [maxSpeedMps]（默认 12 m/s ≈ 43 km/h）丢弃
 * 4. 一致性：位置差分速度远大于传感器上报速度时丢弃（城市峡谷里的瞬移）
 *
 * 距离累加的关键设计：
 * 对**位移向量**做 EMA 平滑后再累加，而不是直接累加逐点距离。
 * - 直接累加会把静止时的抖动全算进去（实测 1 分钟能凭空长出几十米）；
 * - 直接平滑经纬度则会衰减稳定速度（每步只前进 α 倍），距离少算 60% 以上；
 * - 平滑「每步位移向量」两者兼顾：匀速运动时向量长度不变，随机抖动互相抵消。
 */
class TrackPointFilter(
    private val maxAccuracyM: Float = 30f,
    private val maxSpeedMps: Double = 12.0,
    /** 位移向量 EMA 系数 */
    private val deltaAlpha: Double = 0.3,
    /** 小于该长度的位移不计入累计距离，用于吃掉残余噪声 */
    private val minSegmentM: Double = 0.6
) {

    data class Stats(
        var total: Int = 0,
        var accepted: Int = 0,
        var rejectedAccuracy: Int = 0,
        var rejectedTimestamp: Int = 0,
        var rejectedSpeed: Int = 0,
        var rejectedInconsistent: Int = 0
    ) {
        val rejected: Int get() = rejectedAccuracy + rejectedTimestamp + rejectedSpeed + rejectedInconsistent
    }

    var stats: Stats = Stats()
        private set

    /** 最后一个被采纳的原始点 */
    var lastAccepted: TrackPoint? = null
        private set

    /** 已采纳点的累计距离（米，基于平滑后的位移向量） */
    var acceptedDistanceM: Double = 0.0
        private set

    private var smoothedEastM = 0.0
    private var smoothedNorthM = 0.0
    private var hasSmoothed = false

    fun reset() {
        stats = Stats()
        lastAccepted = null
        acceptedDistanceM = 0.0
        smoothedEastM = 0.0
        smoothedNorthM = 0.0
        hasSmoothed = false
    }

    /**
     * 提交一个候选点。
     * @return true 表示该点被采纳
     */
    fun offer(point: TrackPoint): Boolean {
        stats.total++
        val prev = lastAccepted

        // 1. 精度
        val accuracy = point.accuracyM
        if (accuracy != null && accuracy > maxAccuracyM) {
            stats.rejectedAccuracy++
            return false
        }

        // 2. 时间戳
        if (prev != null && point.timeMs <= prev.timeMs) {
            stats.rejectedTimestamp++
            return false
        }

        if (prev != null) {
            val dtSeconds = (point.timeMs - prev.timeMs) / 1000.0
            val (eastM, northM) = GeoMath.deltaMeters(prev.lat, prev.lon, point.lat, point.lon)
            val segmentM = hypot(eastM, northM)
            val derivedSpeed = if (dtSeconds > 0) segmentM / dtSeconds else Double.MAX_VALUE

            // 3. 速度
            if (derivedSpeed > maxSpeedMps) {
                stats.rejectedSpeed++
                return false
            }

            // 4. 一致性
            val reported = point.speedMps?.toDouble() ?: prev.speedMps?.toDouble()
            if (reported != null && reported > 1.0 && derivedSpeed > reported * 4 + 3.0) {
                stats.rejectedInconsistent++
                return false
            }

            // 位移向量平滑后累加
            if (!hasSmoothed) {
                smoothedEastM = eastM
                smoothedNorthM = northM
                hasSmoothed = true
            } else {
                smoothedEastM = deltaAlpha * eastM + (1 - deltaAlpha) * smoothedEastM
                smoothedNorthM = deltaAlpha * northM + (1 - deltaAlpha) * smoothedNorthM
            }
            val smoothedSegmentM = hypot(smoothedEastM, smoothedNorthM)
            if (smoothedSegmentM >= minSegmentM) {
                acceptedDistanceM += smoothedSegmentM
            }
        }

        stats.accepted++
        lastAccepted = point
        return true
    }
}
