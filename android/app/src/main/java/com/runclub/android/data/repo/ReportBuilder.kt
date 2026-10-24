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

    // 逐日刻意不再有「公里目标」：目标是「这一段时间动了几天」，
    // 由 ReportData.targetDays 表达，不设 km 门槛，避免欠账式表达。

    /** 长距离门槛下限：单次 8 公里以上算「远足」 */
    private const val LONG_RUN_MIN_KM = 8.0

    /** 慢走门槛上限：单次不到 2.5 公里算「散散步」 */
    private const val SLOW_RUN_MAX_KM = 2.5

    /** 运动构成的三类名称（口语化，不用训练术语） */
    const val COMPOSITION_SLOW = "慢走"
    const val COMPOSITION_REGULAR = "常跑"
    const val COMPOSITION_LONG = "远足"

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
        val activeDays = days.count { d -> inWeek.any { localDate(it, zone) == d } }

        val prevStart = start.minusDays(7)
        val prevDays = (0..6).map { prevStart.plusDays(it.toLong()) }
        val prevInWeek = records.filter { localDate(it, zone) in prevDays }
        val prevActiveDays = prevInWeek.map { localDate(it, zone) }.toSet().size

        return assemble(
            period = ReportPeriod.WEEK,
            records = inWeek,
            label = "本周战报",
            rangeText = "${start.formatShort()} — ${start.plusDays(6).formatShort()}",
            activeDays = activeDays,
            targetDays = 7,
            chartTitle = "每日里程",
            chartSubtitle = "这周动了 $activeDays 天",
            labels = labels,
            mileage = mileage,
            pace = pace,
            previousActiveDays = prevActiveDays,
            insight = weekInsight(inWeek, mileage, labels, activeDays)
        )
    }

    /**
     * 每周观察：**只肯定已经做到的事，不提欠账**。
     *
     * 旧版会写「还差 X km 达标，安排一次轻松跑就能补上」——那是欠账式的话，
     * 会让「今天只跑了一点」变成一件有负罪感的事。逐日不这么说话。
     */
    private fun weekInsight(
        inWeek: List<RunRecord>,
        mileage: List<Double>,
        labels: List<String>,
        activeDays: Int
    ): String {
        if (inWeek.isEmpty()) {
            return "这周还没有动过。挑一个天气好的日子，出门走 20 分钟就够了。"
        }
        val bestIndex = mileage.indices.maxByOrNull { mileage[it] } ?: 0
        val bestKm = mileage[bestIndex]
        val totalKm = mileage.sum()

        val head = when {
            activeDays >= 6 -> "这周动了 $activeDays 天，几乎每天都出门，很稳。"
            activeDays >= 3 -> "这周动了 $activeDays 天，一周里有好几天在动，这就很好。"
            else -> "这周动了 $activeDays 天，每一次都算数。"
        }
        val detail = "${labels[bestIndex]}那次最远，走了 ${"%.1f".format(bestKm)} km，" +
            "这周一共走了 ${"%.1f".format(totalKm)} km。"
        val tail = if (activeDays < 7) "明天也出门走走？" else "保持就好，不用加量。"
        return "$head$detail$tail"
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
        val prevInMonth = records.filter { YearMonth.from(localDate(it, zone)) == prevMonth }
        val prevActiveDays = prevInMonth.map { localDate(it, zone) }.toSet().size
        val activeDays = inMonth.map { localDate(it, zone) }.toSet().size

        return assemble(
            period = ReportPeriod.MONTH,
            records = inMonth,
            label = "${month.monthValue}月战报",
            rangeText = "${month.atDay(1).formatShort()} — ${month.atEndOfMonth().formatShort()}",
            activeDays = activeDays,
            targetDays = month.lengthOfMonth(),
            chartTitle = "每周里程",
            chartSubtitle = "这个月动了 $activeDays 天",
            labels = labels,
            mileage = mileage,
            pace = pace,
            previousActiveDays = prevActiveDays,
            insight = monthInsight(inMonth, month, zone, activeDays)
        )
    }

    /** 每月观察：肯定频率，不评价强度，也不提欠账 */
    private fun monthInsight(
        inMonth: List<RunRecord>,
        month: YearMonth,
        zone: ZoneId,
        activeDays: Int
    ): String {
        if (inMonth.isEmpty()) {
            return "${month.monthValue} 月还没有动过。这个月还长，先挑一天出门走走。"
        }
        val total = inMonth.sumOf { it.distanceKm }
        val head = when {
            activeDays >= 20 -> "这个月动了 $activeDays 天，几乎一天没落。"
            activeDays >= 12 -> "这个月动了 $activeDays 天，一半以上的日子都在动。"
            else -> "这个月动了 $activeDays 天，每一次都记下来了。"
        }
        val detail = "一共走了 ${"%.1f".format(total)} km。"
        val tail = if (activeDays < month.lengthOfMonth()) {
            "剩下的日子不用赶，出门就行。"
        } else {
            "下个月也这样就好。"
        }
        return "$head$detail$tail"
    }

    // ---------------- 年 ----------------

    private fun buildYear(records: List<RunRecord>, zone: ZoneId, today: LocalDate): ReportData {
        val year = today.year
        val inYear = records.filter { localDate(it, zone).year == year }

        val labels = (1..12).map { "${it}月" }
        val mileage = (1..12).map { m -> inYear.filter { localDate(it, zone).monthValue == m }.sumOf { it.distanceKm } }
        val pace = (1..12).map { m -> paceOf(inYear.filter { localDate(it, zone).monthValue == m }) }

        val prevInYear = records.filter { localDate(it, zone).year == year - 1 }
        val prevActiveDays = prevInYear.map { localDate(it, zone) }.toSet().size
        val activeDays = inYear.map { localDate(it, zone) }.toSet().size

        return assemble(
            period = ReportPeriod.YEAR,
            records = inYear,
            label = "$year 战报",
            rangeText = "01.01 — 至今",
            activeDays = activeDays,
            targetDays = 365,
            chartTitle = "月度里程",
            chartSubtitle = "今年动了 $activeDays 天",
            labels = labels,
            mileage = mileage,
            pace = pace,
            previousActiveDays = prevActiveDays,
            insight = yearInsight(inYear, mileage, labels, activeDays)
        )
    }

    /** 年度观察：把一年拆成「动了多少天」的故事 */
    private fun yearInsight(
        inYear: List<RunRecord>,
        mileage: List<Double>,
        labels: List<String>,
        activeDays: Int
    ): String {
        if (inYear.isEmpty()) {
            return "今年还没有动过。不用等一个完美的开始，明天出门就行。"
        }
        val total = mileage.sum()
        val bestIndex = mileage.indices.maxByOrNull { mileage[it] } ?: 0
        val activeMonths = mileage.count { it > 0.01 }
        val head = "今年动了 $activeDays 天，分布在 $activeMonths 个月里。"
        val detail = "一共走了 ${"%.0f".format(total)} km，${labels[bestIndex]}走得最多" +
            "（${"%.1f".format(mileage[bestIndex])} km）。"
        val tail = "每天一点，一年很远。"
        return "$head$detail$tail"
    }

    // ---------------- 共用装配 ----------------

    private fun assemble(
        period: ReportPeriod,
        records: List<RunRecord>,
        label: String,
        rangeText: String,
        activeDays: Int,
        targetDays: Int,
        chartTitle: String,
        chartSubtitle: String,
        labels: List<String>,
        mileage: List<Double>,
        pace: List<Double?>,
        previousActiveDays: Int,
        insight: String
    ): ReportData {
        val totalKm = mileage.sum()
        val totalMs = records.sumOf { it.durationMs }
        val avgPace = when {
            totalKm * 1000.0 > 50.0 && totalMs > 0 -> totalMs / 1000.0 / totalKm
            else -> null
        }
        // 「较上期」比的是**动过的天数**，不是里程。
        // 逐日不奖励「跑得多」，只奖励「来得勤」。
        val delta = when {
            previousActiveDays <= 0 || activeDays <= 0 -> null
            else -> (((activeDays - previousActiveDays) * 100.0) / previousActiveDays).roundToInt()
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
            activeDays = activeDays,
            targetDays = targetDays,
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
     * 运动构成：按里程占比分三类。
     *
     * 分类名刻意用**家人能懂的说法**，而不是「轻松跑 / 节奏跑 / 长距离」这类
     * 训练术语——后者是给竞技跑者的词汇，日常锻炼者用不上：
     * - 慢走：单次距离短（< 2.5 km），是「散散步」级别的运动
     * - 常跑：中等距离，是最常见的日常跑步
     * - 远足：单次较长，走得比平时久
     *
     * 阈值：远足 = 单次 ≥ max(8 km, 平均单次 × 1.5)；慢走 = 单次 < 2.5 km；其余为常跑。
     */
    private fun composition(records: List<RunRecord>, totalKm: Double): List<CompositionSlice> {
        if (records.isEmpty() || totalKm <= 0.0) {
            return listOf(
                CompositionSlice(COMPOSITION_SLOW, 0, 0.0, "#D9FF43"),
                CompositionSlice(COMPOSITION_REGULAR, 0, 0.0, "#FF5C35"),
                CompositionSlice(COMPOSITION_LONG, 0, 0.0, "#5B675B")
            )
        }
        val avgDistance = totalKm / records.size
        val longThreshold = max(LONG_RUN_MIN_KM, avgDistance * 1.5)

        var slow = 0.0
        var regular = 0.0
        var long = 0.0
        records.forEach { r ->
            when {
                r.distanceKm >= longThreshold -> long += r.distanceKm
                r.distanceKm < SLOW_RUN_MAX_KM -> slow += r.distanceKm
                else -> regular += r.distanceKm
            }
        }

        val percents = roundToHundred(listOf(slow, regular, long), totalKm)
        return listOf(
            CompositionSlice(COMPOSITION_SLOW, percents[0], slow, "#D9FF43"),
            CompositionSlice(COMPOSITION_REGULAR, percents[1], regular, "#FF5C35"),
            CompositionSlice(COMPOSITION_LONG, percents[2], long, "#5B675B")
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
