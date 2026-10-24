package com.runclub.android.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Haversine 与米/度换算的基准校验 */
class GeoMathTest {

    @Test
    fun `赤道上 0_01 度经度差约等于 1111_95 米`() {
        val distance = GeoMath.distanceMeters(0.0, 0.0, 0.0, 0.01)
        val expected = GeoMath.EARTH_RADIUS_M * Math.toRadians(0.01)
        assertEquals(expected, distance, 0.5)
    }

    @Test
    fun `同一点距离为零`() {
        assertEquals(0.0, GeoMath.distanceMeters(31.235, 121.49, 31.235, 121.49), 1e-9)
    }

    @Test
    fun `距离具有对称性`() {
        val ab = GeoMath.distanceMeters(31.2350, 121.4900, 31.2450, 121.5000)
        val ba = GeoMath.distanceMeters(31.2450, 121.5000, 31.2350, 121.4900)
        assertEquals(ab, ba, 1e-6)
    }

    @Test
    fun `北向 0_001 度约等于 111_195 米`() {
        val (east, north) = GeoMath.deltaMeters(31.0, 121.0, 31.001, 121.0)
        assertEquals(0.0, east, 0.01)
        assertEquals(111.195, north, 0.01)
    }

    @Test
    fun `offsetMeters 与 deltaMeters 互为逆运算`() {
        val (lat, lon) = GeoMath.offsetMeters(31.2350, 121.4900, 30.0, -40.0)
        val (east, north) = GeoMath.deltaMeters(31.2350, 121.4900, lat, lon)
        assertEquals(30.0, east, 0.2)
        assertEquals(-40.0, north, 0.2)
    }
}
