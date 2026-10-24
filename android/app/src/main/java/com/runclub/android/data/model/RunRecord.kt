package com.runclub.android.data.model

/**
 * 一次完整跑步记录（对应 SQLite 的 run 表）。
 *
 * 存储铁律：距离等派生指标随记录一起落库，但**每公里分段、心率区间、周月年统计**
 * 不入库，都由原始轨迹点重算，避免「删掉一条记录后总统计不同步」这类问题。
 */
data class RunRecord(
    val id: Long = 0L,
    /** 开始时间（epoch millis） */
    val startTimeMs: Long,
    /** 结束时间（epoch millis） */
    val endTimeMs: Long,
    /** 跑步当时所属时区 id，例如 Asia/Shanghai */
    val timezone: String,
    /** 净运动时长（毫秒，不含暂停） */
    val durationMs: Long,
    /** 总距离（米） */
    val distanceM: Double,
    /** 平均配速（秒/公里），距离不足 50 米时为 null */
    val avgPaceSecPerKm: Double?,
    /** 消耗（kcal） */
    val calories: Int,
    /** 是否为内置模拟轨迹产生的记录（测试用，列表里会标注） */
    val simulated: Boolean = false
) {
    val distanceKm: Double get() = distanceM / 1000.0
}

/** 一次跑步的轨迹点（对应 track_point 表） */
data class StoredTrackPoint(
    val runId: Long,
    val seq: Int,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float?,
    val altitudeM: Double?
)

/**
 * 某一周的汇总（首页「本周节奏」用）。
 * @param dailyKm 周一→周日的每日里程，0 表示当天没跑
 */
data class WeeklyStats(
    val totalKm: Double,
    val totalDurationMs: Long,
    val totalCalories: Int,
    val runCount: Int,
    val dailyKm: List<Double>,
    val goalKm: Double,
    val targetKmPerDay: Double
) {
    val progressPercent: Int
        get() = if (goalKm <= 0) 0 else ((totalKm / goalKm) * 100).toInt().coerceIn(0, 100)

    /** 距离周目标还差多少（已达���返回 0） */
    val remainingKm: Double
        get() = (goalKm - totalKm).coerceAtLeast(0.0)
}
