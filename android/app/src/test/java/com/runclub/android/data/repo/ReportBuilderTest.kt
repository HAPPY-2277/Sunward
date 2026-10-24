package com.runclub.android.data.repo

import com.runclub.android.data.model.ReportPeriod
import com.runclub.android.data.model.RunRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 报告聚合的单元测试。
 *
 * 重点验证容易出错的边界：
 * - 周窗口（周一起算）与跨周归属
 * - 平均配速按距离加权（不是各次配速的算术平均）
 * - 与上期对比在「上期为 0」时必须为 null
 * - 训练构成三个百分比必须加总 100
 * - 空数据不崩溃
 */
class ReportBuilderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 造一条记录：给定本地日期时间、距离（km）、配速（秒/km） */
    private fun run(
        date: LocalDate,
        hour: Int,
        minute: Int,
        km: Number,
        paceSecPerKm: Number,
        id: Long = 0
    ): RunRecord {
        val kmValue = km.toDouble()
        val paceValue = paceSecPerKm.toDouble()
        val start = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()
        val durationMs = (kmValue * paceValue * 1000).toLong()
        return RunRecord(
            id = id,
            startTimeMs = start,
            endTimeMs = start + durationMs,
            timezone = zone.id,
            durationMs = durationMs,
            distanceM = kmValue * 1000,
            avgPaceSecPerKm = paceValue,
            calories = (kmValue * 60 * 1.036).toInt()
        )
    }

    // 2026-10-19 是周一
    private val monday = LocalDate.of(2026, 10, 19)
    private val today = LocalDate.of(2026, 10, 22)   // 周四

    @Test
    fun `本周只统计本周一到周日的记录`() {
        val records = listOf(
            run(monday, 7, 0, 5.0, 360, id = 1),                       // 本周一
            run(monday.plusDays(2), 7, 0, 3.0, 360, id = 2),           // 本周三
            run(monday.minusDays(3), 7, 0, 8.0, 360, id = 3)           // 上周五，应排除
        )
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertEquals(8.0, report.distanceKm, 0.001)
        assertEquals(2, report.runCount)
        assertEquals(7, report.labels.size)
        assertEquals("周一", report.labels[0])
        assertEquals("周日", report.labels[6])
    }

    @Test
    fun `每日里程按天正确归档`() {
        val records = listOf(
            run(monday, 7, 0, 5.0, 360),
            run(monday, 19, 0, 2.0, 360),          // 同一天第二次
            run(monday.plusDays(2), 7, 0, 3.0, 360)
        )
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertEquals(7.0, report.mileageKm[0], 0.001)   // 周一 = 5 + 2
        assertEquals(3.0, report.mileageKm[2], 0.001)   // 周三
        assertEquals(0.0, report.mileageKm[1], 0.001)   // 周二没跑
    }

    @Test
    fun `平均配速按距离加权而不是算术平均`() {
        // 1 km @ 6'00"  +  9 km @ 5'00"
        // 算术平均 = 330 秒；按距离加权 = (360 + 2700) / 10 = 306 秒
        val records = listOf(
            run(monday, 7, 0, 1.0, 360.0),
            run(monday.plusDays(1), 7, 0, 9.0, 300.0)
        )
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertNotNull(report.avgPaceSecPerKm)
        assertEquals(306.0, report.avgPaceSecPerKm!!, 0.5)
    }

    @Test
    fun `距离不足50米时不给配速`() {
        val records = listOf(run(monday, 7, 0, 0.02, 360.0))
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertNull(report.avgPaceSecPerKm)
    }

    @Test
    fun `上期没有数据时对比为 null`() {
        val records = listOf(run(monday, 7, 0, 5.0, 360))
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertNull(report.deltaPercent)
    }

    @Test
    fun `与上周对比按动过的天数算而不是公里`() {
        // 本周：动了 2 天（合计 3 km）
        // 上周：动了 1 天（合计 20 km）
        // 里程大幅下降，但「来得更勤了」→ 应该显示 +100%，而不是 -85%
        val records = listOf(
            run(monday, 7, 0, 1.5, 600, id = 1),
            run(monday.plusDays(1), 7, 0, 1.5, 600, id = 2),
            run(monday.minusDays(7), 7, 0, 20.0, 360, id = 3)
        )
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertEquals(2, report.activeDays)
        assertEquals(100, report.deltaPercent)   // (2-1)/1 = +100%
    }

    @Test
    fun `动的天数一样时对比为持平`() {
        val records = listOf(
            run(monday, 7, 0, 5.0, 360, id = 1),
            run(monday.minusDays(7), 7, 0, 12.0, 360, id = 2)   // 里程更多但天数相同
        )
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertEquals(0, report.deltaPercent)
    }

    @Test
    fun `周进度按动过的天数计算`() {
        // 只跑了一次 0.6 km，但「动了 1 天」就该是 1/7，而不是 0.6/30 km
        val records = listOf(run(monday, 7, 0, 0.6, 900))
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertEquals(1, report.activeDays)
        assertEquals(7, report.targetDays)
        assertEquals(14, report.progressPercent)          // 1/7 = 14.2% → 14
        assertTrue(report.progressText.contains("1 天"))
        // 关键：文案里不应再出现「还差」「达标」这类欠账式表达
        assertTrue(!report.progressText.contains("还差"))
        assertTrue(!report.progressText.contains("达标"))
    }

    @Test
    fun `同一天跑两次只算一天`() {
        val records = listOf(
            run(monday, 7, 0, 1.0, 400, id = 1),
            run(monday, 19, 0, 1.0, 400, id = 2)
        )
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertEquals(2, report.runCount)      // 次数是 2
        assertEquals(1, report.activeDays)    // 但天数只算 1
    }

    @Test
    fun `七天全都动过时进度为 100`() {
        val records = (0..6).map { run(monday.plusDays(it.toLong()), 7, 0, 0.5, 900, id = it.toLong()) }
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, monday.plusDays(6))
        assertEquals(7, report.activeDays)
        assertEquals(100, report.progressPercent)
        assertTrue(report.progressText.contains("都动过了"))
    }

    @Test
    fun `月度按第N周分档且档数匹配月长`() {
        val records = listOf(run(LocalDate.of(2026, 10, 5), 7, 0, 6.0, 360))
        val report = ReportBuilder.build(
            records, ReportPeriod.MONTH, zone, LocalDate.of(2026, 10, 22)
        )
        // 10 月 31 天 → 5 档
        assertEquals(5, report.labels.size)
        assertEquals("第1周", report.labels[0])
        assertEquals(6.0, report.mileageKm[0], 0.001)   // 10/5 在第 1 周（1-7 号）
        assertEquals(0.0, report.mileageKm[1], 0.001)
    }

    @Test
    fun `年度按月分12档且只统计本年`() {
        val records = listOf(
            run(LocalDate.of(2026, 3, 10), 7, 0, 20.0, 360),
            run(LocalDate.of(2026, 7, 10), 7, 0, 30.0, 360),
            run(LocalDate.of(2025, 7, 10), 7, 0, 40.0, 360)   // 去年，应排除
        )
        val report = ReportBuilder.build(
            records, ReportPeriod.YEAR, zone, LocalDate.of(2026, 10, 22)
        )
        assertEquals(12, report.labels.size)
        assertEquals(50.0, report.distanceKm, 0.001)
        assertEquals(20.0, report.mileageKm[2], 0.001)   // 3 月
        assertEquals(30.0, report.mileageKm[6], 0.001)   // 7 月
    }

    @Test
    fun `运动构成三类名称是口语化的且百分比加总为100`() {
        val records = listOf(
            run(monday, 7, 0, 10.0, 360),            // 远足（>= 8km）
            run(monday.plusDays(1), 7, 0, 3.0, 300), // 常跑
            run(monday.plusDays(2), 7, 0, 1.0, 420)  // 慢走（< 2.5km）
        )
        val report = ReportBuilder.build(records, ReportPeriod.WEEK, zone, today)
        assertEquals(3, report.composition.size)
        assertEquals(100, report.composition.sumOf { it.percent })
        assertTrue(report.composition.all { it.percent >= 0 })
        // 分类名必须是家人能懂的说法，不能出现「节奏跑」「长距离」这类训练术语
        val names = report.composition.map { it.name }
        assertTrue(names.contains("慢走"))
        assertTrue(names.contains("常跑"))
        assertTrue(names.contains("远足"))
        assertTrue(!names.contains("节奏跑"))
        assertTrue(!names.contains("长距离"))
        assertTrue(!names.contains("轻松跑"))
    }

    @Test
    fun `空数据不崩溃且各指标为0`() {
        val report = ReportBuilder.build(emptyList(), ReportPeriod.WEEK, zone, today)
        assertEquals(0.0, report.distanceKm, 0.001)
        assertEquals(0, report.runCount)
        assertEquals(0, report.activeDays)
        assertEquals(0, report.progressPercent)
        assertNull(report.avgPaceSecPerKm)
        assertNull(report.deltaPercent)
        assertEquals(7, report.mileageKm.size)
        assertTrue(report.mileageKm.all { it == 0.0 })
        assertTrue(report.paceSecPerKm.all { it == null })
        assertTrue(report.insight.isNotBlank())
        assertEquals(100, report.composition.sumOf { it.percent }.let { if (it == 0) 100 else it })
    }

    @Test
    fun `无数据时进度文案不出现负数或欠账措辞`() {
        val report = ReportBuilder.build(emptyList(), ReportPeriod.MONTH, zone, today)
        assertTrue(report.progressPercent == 0)
        assertTrue(!report.progressText.contains("-"))
        assertTrue(!report.progressText.contains("还差"))
        assertTrue(!report.progressText.contains("达标"))
    }

    @Test
    fun `没有任何一条观察文案使用训练术语`() {
        val records = listOf(run(monday, 7, 0, 5.0, 360))
        listOf(ReportPeriod.WEEK, ReportPeriod.MONTH, ReportPeriod.YEAR).forEach { p ->
            val report = ReportBuilder.build(records, p, zone, today)
            listOf("配速", "达标", "还差", "训练", "节奏跑", "长距离").forEach { banned ->
                assertTrue(
                    "「${p}」的观察文案不应包含「$banned」，实际：${report.insight}",
                    !report.insight.contains(banned)
                )
            }
        }
    }

    @Test
    fun `周日的记录仍属本周`() {
        val records = listOf(run(monday.plusDays(6), 7, 0, 5.0, 360))   // 周日
        // 「今天」设为该周日
        val report = ReportBuilder.build(
            records, ReportPeriod.WEEK, zone, monday.plusDays(6)
        )
        assertEquals(5.0, report.distanceKm, 0.001)
        assertEquals(5.0, report.mileageKm[6], 0.001)
    }
}
