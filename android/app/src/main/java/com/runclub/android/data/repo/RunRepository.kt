package com.runclub.android.data.repo

import android.content.Context
import com.runclub.android.data.local.RunDatabase
import com.runclub.android.data.model.ReportData
import com.runclub.android.data.model.ReportPeriod
import com.runclub.android.data.model.RunRecord
import com.runclub.android.data.model.StoredTrackPoint
import com.runclub.android.data.model.WeeklyStats
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.TimeZone

/**
 * 跑步记录的唯一数据出入口。
 *
 * 所有查询都在**记录当时的时区**下界定「本周/本月」，
 * 跨时区或跨月跑步按**开始时间**归属（计划 §5.5 的约定）。
 */
class RunRepository private constructor(context: Context) {

    private val db = RunDatabase(context.applicationContext)

    fun save(
        record: RunRecord,
        points: List<StoredTrackPoint>
    ): Long = db.insertRun(record, points)

    fun all(): List<RunRecord> = db.allRuns()

    fun byId(id: Long): RunRecord? = db.runById(id)

    fun pointsOf(runId: Long): List<StoredTrackPoint> = db.pointsOf(runId)

    fun delete(id: Long) = db.deleteRun(id)

    fun latest(): RunRecord? = all().firstOrNull()

    /**
     * 本周汇总。
     *
     * 进度指标是「本周动了几天」，不是「离 30 km 还差多少」——
     * 逐日的立场是频率优先，公里只是副产品。
     */
    fun weeklyStats(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): WeeklyStats {
        val today = LocalDate.now(zoneId)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val weekStartMs = weekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val weekEndMs = weekEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val inWeek = all().filter { it.startTimeMs in weekStartMs until weekEndMs }

        val daily = ArrayList<Double>(7)
        val dailyMinutes = ArrayList<Int>(7)
        for (i in 0..6) {
            val day = weekStart.plusDays(i.toLong())
            val thatDay = inWeek
                .filter { LocalDate.ofInstant(Instant.ofEpochMilli(it.startTimeMs), zoneId) == day }
            daily += thatDay.sumOf { it.distanceKm }
            // 分钟口径给「本周节奏」柱状图：动了几分钟，比跑了几个 0.x 公里更贴近「今天动了吗」
            val ms = thatDay.sumOf { it.durationMs }
            dailyMinutes += if (ms > 0) ((ms + 59_999) / 60_000).toInt() else 0
        }

        val activeDays = inWeek
            .map { LocalDate.ofInstant(Instant.ofEpochMilli(it.startTimeMs), zoneId) }
            .toSet().size

        return WeeklyStats(
            totalKm = inWeek.sumOf { it.distanceKm },
            totalDurationMs = inWeek.sumOf { it.durationMs },
            totalCalories = inWeek.sumOf { it.calories },
            runCount = inWeek.size,
            dailyKm = daily,
            dailyMinutes = dailyMinutes,
            activeDays = activeDays,
            targetDays = 7
        )
    }

    /**
     * 某次跑步是不是历史最佳配速（用于「上次跑步」卡上的 PB 徽章）。
     * 只在有可比的历史记录时才成立。
     */
    fun isPersonalBest(record: RunRecord): Boolean {
        val pace = record.avgPaceSecPerKm ?: return false
        if (pace <= 0) return false
        val better = all().filter { it.id != record.id }
            .mapNotNull { it.avgPaceSecPerKm }
            .any { it < pace - 0.5 }   // 0.5 秒内视为同一水平
        return !better && all().size > 1
    }

    /** 报告页数据（周 / 月 / 年） */
    fun report(
        period: ReportPeriod,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone)
    ): ReportData = ReportBuilder.build(all(), period, zone, today)

    /**
     * 累计运动天数（**不归零**）。
     *
     * 逐日刻意不用「连续 N 天」：连续断了会归零，而归零会让「漏了一天」
     * 变成一件有负罪感的事——家里长辈最可能因此放弃。
     * 这里统计的是「一共动了多少天」，漏掉的日子只是不增加，绝不惩罚。
     */
    fun activeDaysTotal(zone: ZoneId = ZoneId.systemDefault()): Int =
        all().map { LocalDate.ofInstant(Instant.ofEpochMilli(it.startTimeMs), zone) }
            .toSet().size

    /**
     * 最近的连续天数（用于首页鼓励语）。
     * 只在「今天或昨天动过」时才有意义，否则返回 0。
     */
    fun currentStreak(zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Int {
        val days = all()
            .map { LocalDate.ofInstant(Instant.ofEpochMilli(it.startTimeMs), zone) }
            .toSet()
        if (days.isEmpty()) return 0
        // 今天没动不算断，从昨天开始数
        var cursor = if (days.contains(today)) today else today.minusDays(1)
        if (!days.contains(cursor)) return 0
        var count = 0
        while (days.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    companion object {
        /** 每日目标 5 KM */
        const val DAILY_TARGET_KM = 5.0

        @Volatile
        private var instance: RunRepository? = null

        fun get(context: Context): RunRepository =
            instance ?: synchronized(this) {
                instance ?: RunRepository(context).also { instance = it }
            }
    }
}

/** 记录当时使用的时区 id */
fun ZoneId.toStorageId(): String = TimeZone.getTimeZone(this).id
