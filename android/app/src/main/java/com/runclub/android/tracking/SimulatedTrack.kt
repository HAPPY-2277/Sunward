package com.runclub.android.tracking

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 内置模拟轨迹：一条约 2.4 km 的环线，按 1Hz 回放。
 *
 * 用途：
 * - 室内无 GPS 信号时也能完整验收 M3–M7；
 * - 测试时提供可复现的输入，验证距离/配速/去漂移是否正确。
 *
 * 生成的点标记为 `simulated = true`，不得写入真实跑步记录。
 */
object SimulatedTrack {

    /** 起点取上海滨江一带，与原型地图的「滨江大道」呼应 */
    private const val CENTER_LAT = 31.2350
    private const val CENTER_LON = 121.4900

    /** 环线周长（米） */
    const val LOOP_LENGTH_M = 2_400.0

    /** 目标配速 5'45"/km ≈ 2.9 m/s */
    private const val SPEED_MPS = 2.9

    private val radiusM: Double = LOOP_LENGTH_M / (2 * Math.PI)
    private val stepRadians: Double = SPEED_MPS / radiusM

    /** 跑完一圈的秒数 */
    val loopSeconds: Int = Math.round(LOOP_LENGTH_M / SPEED_MPS).toInt()

    /** 按弧度取环线上的地理坐标 */
    private fun pointAt(theta: Double): Pair<Double, Double> {
        val east = radiusM * cos(theta)
        val north = radiusM * sin(theta)
        return GeoMath.offsetMeters(CENTER_LAT, CENTER_LON, east, north)
    }

    /**
     * 生成完整一圈的点序列（不依赖协程，便于单元测试直接断言总距离）。
     * @param withNoise 是否加入 ±1 m 横向抖动与 4~8 m 精度噪声（更接近真机）
     */
    fun buildLoop(withNoise: Boolean = true, seed: Long = 42L): List<TrackPoint> {
        val random = Random(seed)
        val count = loopSeconds
        val result = ArrayList<TrackPoint>(count)
        val start = System.currentTimeMillis()
        var theta = 0.0

        for (i in 0 until count) {
            var (lat, lon) = pointAt(theta)
            if (withNoise) {
                // 横向抖动：让过滤链有东西可滤，也让轨迹看起来不是完美圆。
                // 幅度刻意取小（±1 m）：位移向量平滑后噪声会被抵消，不会显著虚增总距离。
                val jitterEast = (random.nextDouble() - 0.5) * 2.0
                val jitterNorth = (random.nextDouble() - 0.5) * 2.0
                val moved = GeoMath.offsetMeters(lat, lon, jitterEast, jitterNorth)
                lat = moved.first
                lon = moved.second
            }
            result += TrackPoint(
                timeMs = start + i * 1000L,
                lat = lat,
                lon = lon,
                accuracyM = if (withNoise) 4f + random.nextFloat() * 4f else 3f,
                altitudeM = 8.0 + random.nextDouble() * 2.0,
                speedMps = (SPEED_MPS + (random.nextDouble() - 0.5) * 0.4).toFloat(),
                provider = "simulated",
                simulated = true
            )
            theta += stepRadians
        }
        return result
    }

    /** 1Hz 回放流；跑完一圈后循环 */
    fun flow(withNoise: Boolean = true): Flow<TrackPoint> = flow {
        val loop = buildLoop(withNoise)
        var index = 0
        var offsetMs = 0L
        while (currentCoroutineContext().isActive) {
            val base = loop[index]
            emit(base.copy(timeMs = System.currentTimeMillis() + offsetMs))
            offsetMs += 0L
            index++
            if (index >= loop.size) index = 0
            delay(1000)
        }
    }
}
