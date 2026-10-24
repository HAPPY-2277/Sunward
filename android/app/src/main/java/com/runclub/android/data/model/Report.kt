package com.runclub.android.data.model

import kotlin.math.roundToInt

/** 报告周期 */
enum class ReportPeriod(val storageKey: String) {
    WEEK("week"),
    MONTH("month"),
    YEAR("year")
}

/** 训练构成的一类（轻松跑 / 节奏跑 / 长距离） */
data class CompositionSlice(
    val name: String,
    /** 该类别占本周期总里程的百分比（0-100） */
    val percent: Int,
    /** 该类别累计里程 */
    val km: Double,
    val colorHex: String
)

/**
 * 一次报告查询的结果。
 *
 * 所有字段都来自真实记录聚合；没有数据时 [hasData] 为 false，页面显示空态，
 * 不展示任何假数字。
 */
data class ReportData(
    val period: ReportPeriod,
    /** 顶部标签，例如「本周战报」「10月战报」「2026 战报」 */
    val label: String,
    /** 区间文字，例如「10.19 — 10.25」 */
    val rangeText: String,
    val distanceKm: Double,
    val runCount: Int,
    val durationMs: Long,
    /** 周期平均配速（按总距离加权），无有效数据时为 null */
    val avgPaceSecPerKm: Double?,
    /** 与上一周期的变化百分比；上一周期为 0 或本周期为 0 时 null */
    val deltaPercent: Int?,
    /**
     * 本周期「动过的天数」——这是逐日的核心指标，不是公里数。
     * 同一天跑多次只算一天。
     */
    val activeDays: Int,
    /**
     * 本周期可选的天数（周=7，月=当月天数，年=365）。
     * 目标是「这一段时间里，动了几天」，不制造任何欠账。
     */
    val targetDays: Int,
    val chartTitle: String,
    val chartSubtitle: String,
    /** X 轴标签 */
    val labels: List<String>,
    /** 每档里程（km） */
    val mileageKm: List<Double>,
    /** 每档配速（秒/公里），无跑步的档为 null */
    val paceSecPerKm: List<Double?>,
    /** 教练观察文案 */
    val insight: String,
    val composition: List<CompositionSlice>
) {
    val hasData: Boolean get() = runCount > 0 || distanceKm > 0.0

    /**
     * 进度 = 动过的天数占比。
     *
     * 刻意**不是**公里目标：公里目标会让「今天只跑了 1 公里」变成一件
     * 亏欠的事，而逐日的立场是——动了就是完成，一天就是一天。
     */
    val progressPercent: Int
        get() = if (targetDays <= 0) 0 else ((activeDays * 100.0) / targetDays).roundToInt().coerceIn(0, 100)

    /** 进度条下方文案：只讲「动了几天」，不讲「还差多少」 */
    val progressText: String
        get() = if (targetDays > 0 && activeDays >= targetDays) {
            "这 ${targetDays} 天都动过了"
        } else {
            "已动 ${activeDays} 天，共 ${targetDays} 天"
        }
}
