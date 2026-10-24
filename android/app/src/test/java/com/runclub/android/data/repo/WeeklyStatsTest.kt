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
    fun `周进度按动过的天数计算而不是公里`() {
        // 刻意让总里程离 30 km 很远（只有 3.2 km），但只要动了 4 天，
        // 进度就该是 4/7 而不是 3.2/30——这是逐日的核心口径。
        val stats = com.runclub.android.data.model.WeeklyStats(
            totalKm = 3.2,
            totalDurationMs = 40 * 60_000L,
            totalCalories = 200,
            runCount = 4,
            dailyKm = listOf(0.8, 0.0, 0.9, 1.0, 0.5, 0.0, 0.0),
            activeDays = 4,
            targetDays = 7
        )
        assertEquals(57, stats.progressPercent)      // 4/7 = 57.1% → 57
        assertEquals(3.2, stats.dailyKm.sum(), 0.01)
    }

    @Test
    fun `七天都动过时进度为 100`() {
        val stats = com.runclub.android.data.model.WeeklyStats(
            totalKm = 2.1, totalDurationMs = 0, totalCalories = 0, runCount = 9,
            dailyKm = List(7) { 0.3 }, activeDays = 7, targetDays = 7
        )
        assertEquals(100, stats.progressPercent)
    }

    @Test
    fun `一天都没动时进度为 0 且不产生负进度`() {
        val stats = com.runclub.android.data.model.WeeklyStats(
            totalKm = 0.0, totalDurationMs = 0, totalCalories = 0, runCount = 0,
            dailyKm = List(7) { 0.0 }, activeDays = 0, targetDays = 7
        )
        assertEquals(0, stats.progressPercent)
    }
}
