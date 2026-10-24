package com.runclub.android.data.repo

import android.content.Context
import com.runclub.android.data.local.RunDatabase
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
     * @param zoneId 统计所用时区，默认取系统时区
     */
    fun weeklyStats(
        zoneId: ZoneId = ZoneId.systemDefault(),
        goalKm: Double = WEEKLY_GOAL_KM,
        targetKmPerDay: Double = DAILY_TARGET_KM
    ): WeeklyStats {
        val today = LocalDate.now(zoneId)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val weekStartMs = weekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val weekEndMs = weekEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val inWeek = all().filter { it.startTimeMs in weekStartMs until weekEndMs }

        val daily = ArrayList<Double>(7)
        for (i in 0..6) {
            val day = weekStart.plusDays(i.toLong())
            val km = inWeek
                .filter { LocalDate.ofInstant(Instant.ofEpochMilli(it.startTimeMs), zoneId) == day }
                .sumOf { it.distanceKm }
            daily += km
        }

        return WeeklyStats(
            totalKm = inWeek.sumOf { it.distanceKm },
            totalDurationMs = inWeek.sumOf { it.durationMs },
            totalCalories = inWeek.sumOf { it.calories },
            runCount = inWeek.size,
            dailyKm = daily,
            goalKm = goalKm,
            targetKmPerDay = targetKmPerDay
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

    companion object {
        /** 周目标 30 KM（与原型一致） */
        const val WEEKLY_GOAL_KM = 30.0

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
