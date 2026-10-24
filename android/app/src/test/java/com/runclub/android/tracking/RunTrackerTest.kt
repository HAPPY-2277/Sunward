package com.runclub.android.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunTrackerTest {

    private val baseLat = 31.2350
    private val baseLon = 121.4900

    private fun point(t: Long, lat: Double = baseLat, lon: Double = baseLon, speed: Float = 2.9f) =
        TrackPoint(timeMs = t, lat = lat, lon = lon, accuracyM = 5f, speedMps = speed, provider = "test")

    @Test
    fun `初始状态为 IDLE`() {
        assertEquals(RunState.IDLE, RunTracker().state)
    }

    @Test
    fun `IDLE 不能暂停也不能结束`() {
        val tracker = RunTracker()
        assertFalse(tracker.pause(1_000))
        assertFalse(tracker.finish(1_000))
        assertEquals(RunState.IDLE, tracker.state)
    }

    @Test
    fun `start 后进入 RUNNING`() {
        val tracker = RunTracker()
        assertTrue(tracker.start(1_000))
        assertEquals(RunState.RUNNING, tracker.state)
    }

    @Test
    fun `计时不含暂停区间`() {
        val tracker = RunTracker()
        tracker.start(1_000)
        assertEquals(5_000L, tracker.elapsedMs(6_000))

        tracker.pause(6_000)
        assertEquals("暂停期间不应继续累计", 5_000L, tracker.elapsedMs(60_000))

        tracker.resume(60_000)
        assertEquals(7_000L, tracker.elapsedMs(62_000))

        tracker.finish(62_000)
        assertEquals("结束后时长冻结", 7_000L, tracker.elapsedMs(999_999))
    }

    @Test
    fun `暂停期间提交的轨迹点被拒绝且不计距离`() {
        val tracker = RunTracker()
        tracker.start(0)
        assertTrue(tracker.onPoint(point(1_000)))
        val afterFirst = tracker.distanceM

        tracker.pause(2_000)
        val far = point(3_000, lat = baseLat + 100.0 / 111_195.0)
        assertFalse("暂停时不应处理轨迹点", tracker.onPoint(far))
        assertEquals(afterFirst, tracker.distanceM, 1e-9)
    }

    @Test
    fun `非法迁移被拒绝且状态不变`() {
        val tracker = RunTracker()
        tracker.start(0)
        assertFalse("RUNNING 不能直接再 start", tracker.start(1_000))
        tracker.pause(1_000)
        assertFalse("PAUSED 不能再 pause", tracker.pause(2_000))
        tracker.finish(3_000)
        assertEquals(RunState.FINISHED, tracker.state)
        assertFalse("FINISHED 是终态", tracker.start(4_000))
        assertFalse("FINISHED 不能再 pause", tracker.pause(5_000))
    }

    @Test
    fun `restore 恢复时长与距离并回到 RUNNING`() {
        val tracker = RunTracker()
        tracker.restore(elapsedMs = 123_000, distanceM = 456.0, nowMs = 10_000)
        assertEquals(RunState.RUNNING, tracker.state)
        assertEquals(123_000L, tracker.elapsedMs(10_000))
        assertEquals(126_000L, tracker.elapsedMs(13_000))
        assertEquals(456.0, tracker.distanceM, 1e-9)
    }

    @Test
    fun `距离不足 50 米时不给平均配速`() {
        val tracker = RunTracker()
        tracker.start(0)
        tracker.onPoint(point(1_000))
        assertNull(tracker.averagePaceSecPerKm(1_000))
    }

    @Test
    fun `平均配速使用实时耗时`() {
        val tracker = RunTracker()
        tracker.start(0)
        // 走 100 米、耗时 60 秒（5 m/s 太快，直接构造距离）
        tracker.restore(elapsedMs = 60_000, distanceM = 100.0, nowMs = 0)
        val pace = tracker.averagePaceSecPerKm(0)
        assertEquals("100 米 60 秒 = 600 秒/公里", 600.0, pace ?: 0.0, 1.0)
        // 再过 60 秒，平均配速应变为 1200 秒/公里
        val later = tracker.averagePaceSecPerKm(60_000)
        assertEquals(1200.0, later ?: 0.0, 1.0)
    }

    @Test
    fun `卡路里按距离与体重估算`() {
        val tracker = RunTracker(weightKg = 60.0)
        tracker.restore(elapsedMs = 600_000, distanceM = 1_000.0, nowMs = 0)
        // 1 km × 60 kg × 1.036 ≈ 62 kcal
        assertEquals(62, tracker.calories)
    }

    @Test
    fun `每满一公里生成一段且结束时补最后不足一公里的段`() {
        val tracker = RunTracker()
        tracker.start(0)

        val startTime = 1_000_000L
        var lat = baseLat
        // 每秒 5 米，跑 500 步 = 2500 米
        for (i in 0..500) {
            tracker.onPoint(point(startTime + i * 1000L, lat = lat, speed = 5f))
            lat += 5.0 / 111_195.0
        }

        val fullSplits = tracker.splits.filter { !it.partial }
        assertEquals("2500 米应产生 2 个整公里分段", 2, fullSplits.size)
        assertEquals(1000.0, fullSplits[0].distanceM, 0.5)
        // 5 m/s → 200 秒/公里 = 3'20"
        assertEquals(200.0, fullSplits[0].paceSecPerKm ?: 0.0, 3.0)

        assertEquals("结束前不应有收尾段", 0, tracker.splits.count { it.partial })

        tracker.finish(0)
        val partial = tracker.splits.lastOrNull { it.partial }
        assertEquals("结束后应补一段约 500 米", 500.0, partial?.distanceM ?: 0.0, 5.0)
    }

    @Test
    fun `轨迹只收录被采纳的点`() {
        val tracker = RunTracker()
        tracker.start(0)
        tracker.onPoint(point(1000L, speed = 3f))
        // 精度 50 米，会被过滤链丢弃
        tracker.onPoint(TrackPoint(
            timeMs = 2000L,
            lat = baseLat + 10.0 / 111_195.0,
            lon = baseLon,
            accuracyM = 50f,
            speedMps = 3f,
            provider = "test"
        ))
        tracker.onPoint(point(3000L, lat = baseLat + 3.0 / 111_195.0, speed = 3f))

        assertEquals("只应收录 2 个采纳点", 2, tracker.track.size)
        assertEquals(1, tracker.filter.stats.rejectedAccuracy)
    }
}
