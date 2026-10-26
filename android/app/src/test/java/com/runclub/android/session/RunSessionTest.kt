package com.runclub.android.session

import android.os.SystemClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RunSession 是进程级单例，测试里要自己接管时间源：默认的 timeSource 读
 * SystemClock.elapsedRealtime()，那是 Android 桩类，在 JVM 上真调用会抛异常。
 *
 * 这组用例锁的是「强杀后恢复」的时间口径：记录按开始墙钟时间归属周期，
 * 开始时间一旦写错，报告里的天数就会算到错误的日期上。
 */
class RunSessionTest {

    private var fakeElapsed = 0L

    @Before
    fun setUp() {
        fakeElapsed = 0L
        RunSession.reset()
        RunSession.timeSource = { fakeElapsed }
    }

    @After
    fun tearDown() {
        RunSession.reset()
        RunSession.timeSource = { SystemClock.elapsedRealtime() }
    }

    @Test
    fun `恢复时带回快照里的开始墙钟时间`() {
        val elapsed = 5 * 60_000L
        val startWall = System.currentTimeMillis() - elapsed

        RunSession.restore(
            elapsedMs = elapsed,
            distanceM = 850.0,
            simulated = true,
            startWallClockMs = startWall
        )

        assertEquals(startWall, RunSession.startWallClockMs)
    }

    @Test
    fun `快照没带开始时间时用当前墙钟倒推而不是留 0`() {
        val before = System.currentTimeMillis()
        val elapsed = 90_000L

        RunSession.restore(elapsedMs = elapsed, distanceM = 260.0, simulated = false)

        val start = RunSession.startWallClockMs
        assertTrue("开始时间不该是 0，实际 $start", start > 0L)
        val lower = before - elapsed - 2_000L
        val upper = System.currentTimeMillis() - elapsed + 2_000L
        assertTrue("开始时间应约等于当前时间减去已跑时长，实际 $start（区间 $lower..$upper）", start in lower..upper)
    }

    @Test
    fun `恢复后再结束，记录的起止跨度等于已跑时长`() {
        val elapsed = 4 * 60_000L
        val startWall = System.currentTimeMillis() - elapsed

        RunSession.restore(
            elapsedMs = elapsed,
            distanceM = 700.0,
            simulated = true,
            startWallClockMs = startWall
        )
        fakeElapsed = elapsed
        RunSession.finish()

        val span = RunSession.endWallClockMs - RunSession.startWallClockMs
        assertTrue("起止跨度应约等于已跑时长 $elapsed，实际 $span", span in elapsed..(elapsed + 5_000L))
    }
}
