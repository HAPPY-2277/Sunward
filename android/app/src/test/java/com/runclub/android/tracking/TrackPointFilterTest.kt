package com.runclub.android.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.random.Random

class TrackPointFilterTest {

    private val baseLat = 31.2350
    private val baseLon = 121.4900

    private fun point(
        t: Long,
        lat: Double,
        lon: Double,
        accuracy: Float? = 5f,
        speed: Float? = 2.9f
    ) = TrackPoint(
        timeMs = t,
        lat = lat,
        lon = lon,
        accuracyM = accuracy,
        altitudeM = 8.0,
        speedMps = speed,
        provider = "test"
    )

    @Test
    fun `匀速行进的点全部采纳且累计距离接近真实值`() {
        val filter = TrackPointFilter()
        var lat = baseLat
        // 每步向北 2.9 米，1 秒 1 步，共 10 步 = 29 米
        for (i in 0..10) {
            assertTrue("第 $i 点应被采纳", filter.offer(point(i * 1000L, lat, baseLon)))
            lat += 2.9 / 111_195.0
        }
        assertEquals(11, filter.stats.accepted)
        assertEquals(0, filter.stats.rejected)
        assertEquals(29.0, filter.acceptedDistanceM, 1.0)
    }

    @Test
    fun `精度差于 30 米的点被丢弃`() {
        val filter = TrackPointFilter()
        assertTrue(filter.offer(point(0L, baseLat, baseLon, accuracy = 5f)))
        assertFalse(filter.offer(point(1000L, baseLat + 0.00002, baseLon, accuracy = 50f)))
        assertEquals(1, filter.stats.rejectedAccuracy)
        assertEquals(1, filter.stats.accepted)
    }

    @Test
    fun `时间戳非递增的点被丢弃`() {
        val filter = TrackPointFilter()
        assertTrue(filter.offer(point(1000L, baseLat, baseLon)))
        assertFalse("同一时间戳应被丢弃", filter.offer(point(1000L, baseLat + 0.00002, baseLon)))
        assertFalse("时间回退应被丢弃", filter.offer(point(500L, baseLat + 0.00004, baseLon)))
        assertEquals(2, filter.stats.rejectedTimestamp)
    }

    @Test
    fun `一秒移动 111 米即 400 km 每小时的瞬移被丢弃`() {
        val filter = TrackPointFilter()
        assertTrue(filter.offer(point(0L, baseLat, baseLon, speed = null)))
        // 纬度 +0.001 度 ≈ 111 米，用时 1 秒
        assertFalse(filter.offer(point(1000L, baseLat + 0.001, baseLon, speed = null)))
        assertEquals(1, filter.stats.rejectedSpeed)
    }

    @Test
    fun `位置差分速度与传感器速度严重不符时被丢弃`() {
        val filter = TrackPointFilter()
        // 上报 1.2 m/s，实际 1 秒走了 8 米：
        // 8 < 12（不触发速度规则），但 8 > 1.2 * 4 + 3 = 7.8，应被一致性规则拦下
        assertTrue(filter.offer(point(0L, baseLat, baseLon, speed = 1.2f)))
        assertFalse(filter.offer(point(1000L, baseLat + 8.0 / 111_195.0, baseLon, speed = 1.2f)))
        assertEquals(0, filter.stats.rejectedSpeed)
        assertEquals(1, filter.stats.rejectedInconsistent)
    }

    @Test
    fun `静止抖动 60 秒累计距离小于 20 米`() {
        val filter = TrackPointFilter()
        val random = Random(7)
        val cosLat = cos(Math.toRadians(baseLat))
        for (i in 0..60) {
            // ±0.8 米随机漂移，速度上报接近 0
            val east = (random.nextDouble() - 0.5) * 1.6
            val north = (random.nextDouble() - 0.5) * 1.6
            val lat = baseLat + north / 111_195.0
            val lon = baseLon + east / (111_195.0 * cosLat)
            filter.offer(point(i * 1000L, lat, lon, accuracy = 5f, speed = 0.05f))
        }
        assertTrue(
            "静止 60 秒累计距离应小于 20 米，实测 ${filter.acceptedDistanceM}",
            filter.acceptedDistanceM < 20.0
        )
    }

    @Test
    fun `reset 后统计与距离归零`() {
        val filter = TrackPointFilter()
        filter.offer(point(0L, baseLat, baseLon))
        filter.offer(point(1000L, baseLat + 2.9 / 111_195.0, baseLon))
        filter.reset()
        assertEquals(0, filter.stats.total)
        assertEquals(0.0, filter.acceptedDistanceM, 1e-9)
        assertEquals(null, filter.lastAccepted)
    }
}
