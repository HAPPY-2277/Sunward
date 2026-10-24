package com.runclub.android.data.repo

import com.runclub.android.data.model.CompositionSlice
import com.runclub.android.data.model.ReportData
import com.runclub.android.data.model.ReportPeriod
import com.runclub.android.data.model.RunRecord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 报告聚合的纯逻辑（不依赖 Android，可在 JVM 单测里完整验证）。
 *
 * 设计约定：
 * 1. 周期按**记录开始时间**归属，时区取记录所属时区对应的本地日期。
 * 2. 平均配速按**总时长 ÷ 总距离**加权，不是「各次配速的算术平均」——
 *    后者会让 1 公里的短跑和 20 公里的长跑权重相同，结果失真。
 * 3. 与上期对比要求上一周期有数据，否则返回 null（页面显示「首次统计」）。
 */
object ReportBuilder {

    const val WEEKLY_GOAL_KM = 30.0
    const val MONTHLY_GOAL_KM = 120.0
    const val YEARLY_GOAL_KM = 1000.0

    /** 长距离门槛下限：单次 8 公里以上算长距离 */
    private const val LONG_RUN_MIN_KM = 8.0

    fun build(
        records: List<RunRecord>,
        period: ReportPeriod,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): ReportData = when (period) {
        ReportPeriod.WEEK -> buildWeek(records, zone, today)
        ReportPeriod.MONTH -> buildMonth(records, zone, today)
        ReportPeriod.YEAR -> buildYear(records, zone, today)
    }

    // ---------------- 周 ----------------

    private fun buildWeek(records: List<RunRecord>, zone: ZoneId, today: LocalDate): ReportData {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = (0..6).map { start.plusDays(it.toLong()) }
        val inWeek = records.filter { localDate(it, zone) in days }

        val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val mileage = days.map { d -> inWeek.filter { localDate(it, zone) == d }.sumOf { it.distanceKm } }
        val pace = days.map { d -> paceOf(inWeek.filter { localDate(it, zone) == d }) }

        val prevStart = start.minusDays(7)
        val prevDays = (0..6).map { prevStart.plusDays(it.toLong()) }
        val prevKm = records.filter { localDate(it, zone) in prevDays }.sumOf { it.distanceKm }

        return assemble(
            period = ReportPeriod.WEEK,
            records = inWeek,
            label = "本周战报",
            rangeText = "${start.formatShort()} — ${start.plusDays(6).formatShort()}",
            goalKm = WEEKLY_GOAL_KM,
            chartTitle = "每日里程",
            chartSubtitle = "本周共 ${inWeek.size} 次训练",
            labels = labels,
            mileage = mileage,
            pace = pace,
            previousKm = prevKm,
            insight = weekInsight(inWeek, mileage, labels)
        )
    }

    private fun weekInsight(inWeek: List<RunRecord>, mileage: List<Double>, labels: List<String>): String {
        if (inWeek.isEmpty()) {
            return "这周还没有跑步记录。从一个 20 分钟的轻松跑开始，先把节奏找回来。"
        }
        val bestIndex = mileage.indices.maxByOrNull { mileage[it] } ?: 0
        val bestKm = mileage[bestIndex]
        val avgPace = paceOf(inWeek)
        val paceText = avgPace?.let { "平均配速 ${it.paceText()}。" } ?: ""
        val totalKm = mileage.sum()
        val remain = (WEEKLY_GOAL_KM - totalKm).coerceAtLeast(0.0)
        val tail = if (remain <= 0.0) {
            "本周目标已达成，保持这个频率就好。"
        } else {
            "还差 ${"%.1f".format(remain)} km 达标，安排一次轻松跑就能补上。"
        }
        return "${labels[bestIndex]}的 ${"%.1f".format(bestKm)} km 是本周最远的一次，$paceText$tail"
    }

    // ---------------- 月 ----------------

    private fun buildMonth(records: List<RunRecord>, zone: ZoneId, today: LocalDate): ReportData {
        val month = YearMonth.from(today)
        val inMonth = records.filter { localDate(it, zone).let { d -> YearMonth.from(d) == month } }

        // 按「第 N 周」分组：(day-1)/7 + 1
        val weekCount = ((month.lengthOfMonth() - 1) / 7) + 1
        val labels = (1..weekCount).map { "第${it}周" }
        fun bucketOf(record: RunRecord) = (localDate(record, zone).dayOfMonth - 1) / 7
        val mileage = (0 until weekCount).map { i -> inMonth.filter { bucketOf(it) == i }.sumOf { it.distanceKm } }
        val pace = (0 until weekCount).map { i -> paceOf(inMonth.filter { bucketOf(it) == i }) }

        val prevMonth = month.minusMonths(1)
        val prevKm = records
            .filter { YearMonth.from(localDate(it, zone)) == prevMonth }
            .sumOf { it.distanceKm }

        return assemble(
            period = ReportPeriod.MONTH,
            records = inMonth,
            label = "${month.monthValue}月战报",
            rangeText = "${month.atDay(1).formatShort()} — ${month.atEndOfMonth().formatShort()}",
            goalKm = MONTHLY_GOAL_KM,
            chartTitle = "每周里程",
            chartSubtitle = "本月共 ${inMonth.size} 次训练",
            labels = labels,
            mileage = mileage,
            pace = pace,
            previousKm = prevKm,
            insight = monthInsight(inMonth, month, zone)
        )
    }

    private fun monthInsight(inMonth: List<RunRecord>, month: YearMonth, zone: ZoneId): String {
        if (inMonth.isEmpty()) {
            return "${month.monthValue} 月还没有跑步记录。先跑一次，这份报告就会自动生成。"
        }
        val mid = month.lengthOfMonth() / 2
        val firstHalf = inMonth.filter { localDate(it, zone).dayOfMonth <= mid }
        val secondHalf = inMonth.filter { localDate(it, zone).dayOfMonth > mid }
        val firstPace = paceOf(firstHalf)
        val secondPace = paceOf(secondHalf)

        val trend = if (firstPace != null && secondPace != null) {
            val diff = firstPace - secondPace   // 正数 = 后半程更快
            when {
                abs(diff) < 3 -> "上下半程配速很稳定，节奏控制得不错。"
                diff > 0 -> "后半程比前半程快了 ${diff.roundToInt()} 秒，状态在回升。"
                else -> "后半程比前半程慢了 ${abs(diff).roundToInt()} 秒，注意别练过头。"
            }
        } else {
            ""
        }
        val total = inMonth.sumOf { it.distanceKm }
        // 与「训练构成」用同一套长距离规则，避免两处口径不一致（真实踩过：
        // 构成卡显示长距离 33%，教练观察却说「单次最长只占 17%」，看起来自相矛盾）
        val avgDistance = total / inMonth.size
        val longThreshold = max(LONG_RUN_MIN_KM, avgDistance * 1.5)
        val longKm = inMonth.filter { it.distanceKm >= longThreshold }.sumOf { it.distanceKm }
        val longShare = if (total > 0) longKm / total * 100 else 0.0
        val longest = inMonth.maxByOrNull { it.distanceM }?.distanceKm ?: 0.0
        val longTip = if (longShare < 25.0) {
            String.format(
                Locale.US,
                "长距离只占 %.0f%%，建议每周安排一次 %.0f km 的慢跑。",
                longShare, max(8.0, longest * 1.2)
            )
        } else {
            String.format(
                Locale.US,
                "长距离占 %.0f%%（最长 %.1f km），训练结构健康。",
                longShare, longest
            )
        }
        return "$trend$longTip"
    }

    // ---------------- 年 ----------------

    private fun buildYear(records: List<RunRecord>, zone: ZoneId, today: LocalDate): ReportData {
        val year = today.year
        val inYear = records.filter { localDate(it, zone).year == year }

        val labels = (1..12).map { "${it}月" }
        val mileage = (1..12).map { m -> inYear.filter { localDate(it, zone).monthValue == m }.sumOf { it.distanceKm } }
        val pace = (1..12).map { m -> paceOf(inYear.filter { localDate(it, zone).monthValue == m }) }

        val prevKm = records.filter { localDate(it, zone).year == year - 1 }.sumOf { it.distanceKm }

        return assemble(
            period = ReportPeriod.YEAR,
            records = inYear,
            label = "$year 战报",
            rangeText = "01.01 — 至今",
            goalKm = YEARLY_GOAL_KM,
            chartTitle = "月度里程",
            chartSubtitle = "今年已完成 ${inYear.size} 次跑步",
            labels = labels,
            mileage = mileage,
            pace = pace,
            previousKm = prevKm,
            insight = yearInsight(inYear, mileage, labels)
        )
    }

    private fun yearInsight(inYear: List<RunRecord>, mileage: List<Double>, labels: List<String>): String {
        if (inYear.isEmpty()) {
            return "今年还没有跑步记录。跑起来，明年的今天会感谢现在的自己。"
        }
        val total = mileage.sum()
        val bestIndex = mileage.indices.maxByOrNull { mileage[it] } ?: 0
        val avgPace = paceOf(inYear)
        val paceText = avgPace?.let { "全年平均配速 ${it.paceText()}。" } ?: ""
        val activeMonths = mileage.count { it > 0.01 }
        return "今年已累计 ${"%.0f".format(total)} km，活跃 $activeMonths 个月，" +
            "${labels[bestIndex]}状态最好（${"%.1f".format(mileage[bestIndex])} km）。$paceText"
    }

    // ---------------- 共用装配 ----------------

    private fun assemble(
        period: ReportPeriod,
        records: List<RunRecord>,
        label: String,
        rangeText: String,
        goalKm: Double,
        chartTitle: String,
        chartSubtitle: String,
        labels: List<String>,
        mileage: List<Double>,
        pace: List<Double?>,
        previousKm: Double,
        insight: String
    ): ReportData {
        val totalKm = mileage.sum()
        val totalMs = records.sumOf { it.durationMs }
        val avgPace = when {
            totalKm * 1000.0 > 50.0 && totalMs > 0 -> totalMs / 1000.0 / totalKm
            else -> null
        }
        val delta = when {
            previousKm <= 0.01 || totalKm <= 0.01 -> null
            else -> (((totalKm - previousKm) / previousKm) * 100).roundToInt()
        }

        return ReportData(
            period = period,
            label = label,
            rangeText = rangeText,
            distanceKm = totalKm,
            runCount = records.size,
            durationMs = totalMs,
            avgPaceSecPerKm = avgPace,
            deltaPercent = delta,
            goalKm = goalKm,
            chartTitle = chartTitle,
            chartSubtitle = chartSubtitle,
            labels = labels,
            mileageKm = mileage,
            paceSecPerKm = pace,
            insight = insight,
            composition = composition(records, totalKm)
        )
    }

    /**
     * 训练构成：按里程占比分三类。
     *
     * 分类规则（写进验收报告，便于你核对）：
     * - 长距离：单次 ≥ max(8 km, 本周期平均单次距离 × 1.5)
     * - 节奏跑：配速比本周期平均配速快 3% 以上
     * - 轻松跑：其余
     */
    private fun composition(records: List<RunRecord>, totalKm: Double): List<CompositionSlice> {
        if (records.isEmpty() || totalKm <= 0.0) {
            return listOf(
                CompositionSlice("轻松跑", 0, 0.0, "#D9FF43"),
                CompositionSlice("节奏跑", 0, 0.0, "#FF5C35"),
                CompositionSlice("长距离", 0, 0.0, "#5B675B")
            )
        }
        val avgDistance = totalKm / records.size
        val longThreshold = max(LONG_RUN_MIN_KM, avgDistance * 1.5)
        val avgPace = records.mapNotNull { it.avgPaceSecPerKm }.average().takeIf { !it.isNaN() }

        var easy = 0.0
        var tempo = 0.0
        var long = 0.0
        records.forEach { r ->
            when {
                r.distanceKm >= longThreshold -> long += r.distanceKm
                avgPace != null && r.avgPaceSecPerKm != null &&
                    r.avgPaceSecPerKm < avgPace * 0.97 -> tempo += r.distanceKm
                else -> easy += r.distanceKm
            }
        }

        val percents = roundToHundred(listOf(easy, tempo, long), totalKm)
        return listOf(
            CompositionSlice("轻松跑", percents[0], easy, "#D9FF43"),
            CompositionSlice("节奏跑", percents[1], tempo, "#FF5C35"),
            CompositionSlice("长距离", percents[2], long, "#5B675B")
        )
    }

    /** 三个百分比四舍五入后强制加总为 100（差额补到最大的一项） */
    private fun roundToHundred(values: List<Double>, total: Double): List<Int> {
        val raw = values.map { it / total * 100 }
        val rounded = raw.map { it.roundToInt() }.toMutableList()
        val diff = 100 - rounded.sum()
        if (diff != 0) {
            val idx = raw.indices.maxByOrNull { raw[it] } ?: 0
            rounded[idx] += diff
        }
        return rounded.map { it.coerceIn(0, 100) }
    }

    /** 一组记录的加权配速（秒/公里），数据不足返回 null */
    private fun paceOf(records: List<RunRecord>): Double? {
        if (records.isEmpty()) return null
        val km = records.sumOf { it.distanceKm }
        val ms = records.sumOf { it.durationMs }
        if (km * 1000.0 <= 50.0 || ms <= 0) return null
        return ms / 1000.0 / km
    }

    private fun localDate(record: RunRecord, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(record.startTimeMs).atZone(zone).toLocalDate()

    private fun LocalDate.formatShort(): String =
        String.format(Locale.US, "%02d.%02d", monthValue, dayOfMonth)

    private fun Double.paceText(): String {
        val m = (this / 60).toInt()
        val s = (this - m * 60).roundToInt()
        return String.format(Locale.US, "%d'%02d\"", m, if (s == 60) 0 else s)
    }
}
