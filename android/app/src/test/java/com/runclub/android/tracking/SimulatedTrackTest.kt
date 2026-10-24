package com.runclub.android.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedTrackTest {

    @Test
    fun `环线总长接近 2400 米`() {
        val points = SimulatedTrack.buildLoop(withNoise = false)
        assertTrue("点数应大于 100", points.size > 100)

        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            total += GeoMath.distanceMeters(a.lat, a.lon, b.lat, b.lon)
        }
        assertEquals(SimulatedTrack.LOOP_LENGTH_M, total, SimulatedTrack.LOOP_LENGTH_M * 0.03)
    }

    @Test
    fun `带噪声的环线总长偏差不超过 5 百分比`() {
        val filter = TrackPointFilter()
        SimulatedTrack.buildLoop(withNoise = true).forEach { filter.offer(it) }
        assertEquals(
            "时间戳应严格递增，不应被规则剔除",
            0,
            filter.stats.rejectedTimestamp
        )
        assertEquals(
            SimulatedTrack.LOOP_LENGTH_M,
            filter.acceptedDistanceM,
            SimulatedTrack.LOOP_LENGTH_M * 0.05
        )
    }

    @Test
    fun `模拟点都带 simulated 标记且时间戳单调递增`() {
        val points = SimulatedTrack.buildLoop(withNoise = true)
        assertTrue(points.all { it.simulated })
        for (i in 1 until points.size) {
            assertTrue(points[i].timeMs > points[i - 1].timeMs)
        }
    }
}
