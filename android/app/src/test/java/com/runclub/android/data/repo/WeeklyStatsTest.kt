package com.runclub.android.data.repo

import com.runclub.android.data.model.RunRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 周汇总的边界规则（纯逻辑，不依赖 Android）。
 * 注意：RunRepository 依赖 SQLite，无法在 JVM 单测；
 * 这里把「周窗口如何界定」与「每日归属」抽成可测的纯函数来验证。
 */
class WeeklyStatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 本周（周一起）的起止，与 RunRepository.weeklyStats 使用同一套规则 */
    private fun weekWindow(today: LocalDate): Pair<LocalDate, LocalDate> {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return start to start.plusDays(6)
    }

    @Test
    fun `周窗口总是从周一开始`() {
        val (start, end) = weekWindow(LocalDate.of(2026, 10, 24))  // 周六
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 19), start)
        assertEquals(LocalDate.of(2026, 10, 25), end)
    }

    @Test
    fun `周日的窗口是上一个周一到本周日`() {
        val (start, end) = weekWindow(LocalDate.of(2026, 10, 25))  // 周日
        assertEquals(LocalDate.of(2026, 10, 19), start)
        assertEquals(LocalDate.of(2026, 10, 25), end)
    }

    @Test
    fun `周一时窗口就是本周一到周日`() {
        val (start, end) = weekWindow(LocalDate.of(2026, 10, 19))  // 周一
        assertEquals(LocalDate.of(2026, 10, 19), start)
        assertEquals(LocalDate.of(2026, 10, 25), end)
    }

    @Test
    fun `跨月的那一周仍然正确归入同一周窗口`() {
        // 2026-10-26 是周一，属于 10-26 ~ 11-01 这一周
        val (start, end) = weekWindow(LocalDate.of(2026, 11, 1))  // 周日
        assertEquals(LocalDate.of(2026, 10, 26), start)
        assertEquals(LocalDate.of(2026, 11, 1), end)
    }

    @Test
    fun `跨年的那一周以周一为准`() {
        val (start, end) = weekWindow(LocalDate.of(2027, 1, 3))  // 周日
        assertEquals(LocalDate.of(2026, 12, 28), start)
        assertEquals(LocalDate.of(2027, 1, 3), end)
    }

    @Test
    fun `周进度百分比与剩余距离计算正确`() {
        val stats = com.runclub.android.data.model.WeeklyStats(
            totalKm = 23.6,
            totalDurationMs = 8 * 3600_000L + 18 * 60_000L,
            totalCalories = 1840,
            runCount = 5,
            dailyKm = listOf(5.0, 4.0, 0.0, 6.0, 8.6, 0.0, 0.0),
            goalKm = 30.0,
            targetKmPerDay = 5.0
        )
        assertEquals(78, stats.progressPercent)      // 23.6/30 = 78.6% → 78
        assertEquals(6.4, stats.remainingKm, 0.01)  // 30 - 23.6
        assertEquals(23.6, stats.dailyKm.sum(), 0.01)
    }

    @Test
    fun `超出周目标时进度封顶 100 且剩余为 0`() {
        val stats = com.runclub.android.data.model.WeeklyStats(
            totalKm = 35.0, totalDurationMs = 0, totalCalories = 0, runCount = 8,
            dailyKm = List(7) { 5.0 }, goalKm = 30.0, targetKmPerDay = 5.0
        )
        assertEquals(100, stats.progressPercent)
        assertEquals(0.0, stats.remainingKm, 0.001)
    }
}
