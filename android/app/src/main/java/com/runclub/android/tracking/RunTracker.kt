package com.runclub.android.tracking

import kotlin.math.roundToInt

/**
 * 一次跑步的完整状态与指标计算。**纯 Kotlin，不依赖 Android**，可在 JVM 上直接单测。
 *
 * 计时全部基于调用方传入的单调时钟（生产用 `SystemClock.elapsedRealtime()`），
 * 不用 `System.currentTimeMillis()` —— 用户改系统时间或跨时区都不会让时长错乱。
 *
 * 分段（每公里）用的是**轨迹点自带的时间戳**做差分，因为分段边界落在两点之间，
 * 用点时间戳与用单调时钟在时长上的差异不超过 1 秒，但前者实现简单、无需额外传参。
 */
class RunTracker(
    val filter: TrackPointFilter = TrackPointFilter(),
    /** 卡路里估算用的体重（kg），demo 阶段固定 60 */
    private val weightKg: Double = 60.0
) {

    /**
     * 一段距离的成绩。
     * @param distanceM 本段距离（满公里通常是 1000，最后一段是余数）
     * @param durationMs 本段耗时
     * @param partial 是否为最后不足 1 公里的收尾段
     */
    data class Split(
        val index: Int,
        val distanceM: Double,
        val durationMs: Long,
        val partial: Boolean
    ) {
        val paceSecPerKm: Double?
            get() {
                if (distanceM < 50.0 || durationMs <= 0L) return null
                return (durationMs / 1000.0) / (distanceM / 1000.0)
            }
    }

    var state: RunState = RunState.IDLE
        private set

    private var accumulatedMs: Long = 0L
    private var segmentStartMs: Long = 0L

    var distanceM: Double = 0.0
        private set

    var lastPoint: TrackPoint? = null
        private set

    /** 平滑后的瞬时速度（m/s） */
    var smoothedSpeedMps: Double = 0.0
        private set

    // 轨迹与分段
    private val trackPoints = ArrayList<TrackPoint>(1024)
    val track: List<TrackPoint> get() = trackPoints

    private val splitList = ArrayList<Split>()
    val splits: List<Split> get() = splitList

    private var splitAnchorDistanceM = 0.0
    private var splitAnchorTimeMs = 0L
    private var splitAnchorSet = false

    // ---------- 状态迁移 ----------

    fun start(nowMs: Long): Boolean {
        if (!RunStateMachine.canTransition(state, RunState.RUNNING)) return false
        filter.reset()
        distanceM = 0.0
        smoothedSpeedMps = 0.0
        lastPoint = null
        accumulatedMs = 0L
        segmentStartMs = nowMs
        trackPoints.clear()
        splitList.clear()
        splitAnchorDistanceM = 0.0
        splitAnchorTimeMs = 0L
        splitAnchorSet = false
        state = RunState.RUNNING
        return true
    }

    fun pause(nowMs: Long): Boolean {
        if (!RunStateMachine.canTransition(state, RunState.PAUSED)) return false
        accumulatedMs += (nowMs - segmentStartMs).coerceAtLeast(0L)
        state = RunState.PAUSED
        return true
    }

    fun resume(nowMs: Long): Boolean {
        if (!RunStateMachine.canTransition(state, RunState.RUNNING)) return false
        segmentStartMs = nowMs
        state = RunState.RUNNING
        return true
    }

    fun finish(nowMs: Long): Boolean {
        if (!RunStateMachine.canTransition(state, RunState.FINISHED)) return false
        if (state == RunState.RUNNING) {
            accumulatedMs += (nowMs - segmentStartMs).coerceAtLeast(0L)
        }
        state = RunState.FINISHED
        appendFinalSplitIfNeeded()
        return true
    }

    /** 从「异常退出前的快照」恢复为进行中状态。历史轨迹与分段无法还原，从这里重新开始记。 */
    fun restore(elapsedMs: Long, distanceM: Double, nowMs: Long) {
        accumulatedMs = elapsedMs.coerceAtLeast(0L)
        this.distanceM = distanceM.coerceAtLeast(0.0)
        segmentStartMs = nowMs
        state = RunState.RUNNING
        splitAnchorDistanceM = this.distanceM
        splitAnchorTimeMs = 0L
        splitAnchorSet = false
    }

    // ---------- 指标 ----------

    fun elapsedMs(nowMs: Long): Long = when (state) {
        RunState.RUNNING -> accumulatedMs + (nowMs - segmentStartMs).coerceAtLeast(0L)
        else -> accumulatedMs
    }

    /** 当前配速（秒/公里）；无有效速度时返回 null，UI 显示 --'--" */
    val paceSecPerKm: Double?
        get() = if (smoothedSpeedMps < 0.3) null else (1000.0 / smoothedSpeedMps).coerceIn(150.0, 900.0)

    /**
     * 平均配速（秒/公里）；距离不足 50 m 时视为无意义。
     *
     * 必须传入 nowMs —— 平均配速要用**实时**耗时，
     * 而 [accumulatedMs] 只在暂停/结束时更新，跑步途中恒为 0（真实踩过这个坑）。
     */
    fun averagePaceSecPerKm(nowMs: Long): Double? {
        if (distanceM < 50.0) return null
        val seconds = elapsedMs(nowMs) / 1000.0
        if (seconds <= 0.0) return null
        return seconds / (distanceM / 1000.0)
    }

    /** 卡路里估算：kcal = 距离(km) × 体重(kg) × 1.036 */
    val calories: Int
        get() = (distanceM / 1000.0 * weightKg * 1.036).roundToInt()

    /**
     * 提交一个轨迹点。只有 RUNNING 状态才参与距离与轨迹计算。
     * @return 是否被过滤链采纳
     */
    fun onPoint(point: TrackPoint): Boolean {
        if (state != RunState.RUNNING) return false

        // 防御：同一次跑步里不该混入不同数据源的点（切模拟/切真实时会重建 tracker，
        // 万一没重建，这里就地重来，避免过滤器把新源的点全当瞬移丢掉）
        val previous = lastPoint
        if (previous != null && previous.simulated != point.simulated) {
            resetSeriesForNewSource()
        }

        val accepted = filter.offer(point)
        lastPoint = point
        if (accepted) {
            distanceM = filter.acceptedDistanceM
            trackPoints.add(point)
            smoothSpeed(point)
            if (!splitAnchorSet) {
                splitAnchorTimeMs = point.timeMs
                splitAnchorSet = true
            }
            recordSplits(point.timeMs)
        }
        return accepted
    }

    /** 换数据源时把「序列相关」的状态全部清掉，保留状态机与计时 */
    private fun resetSeriesForNewSource() {
        filter.reset()
        trackPoints.clear()
        splitList.clear()
        distanceM = 0.0
        smoothedSpeedMps = 0.0
        lastPoint = null
        splitAnchorDistanceM = 0.0
        splitAnchorTimeMs = 0L
        splitAnchorSet = false
    }

    /** 每跨过一个整公里就落一段 */
    private fun recordSplits(pointTimeMs: Long) {
        while (distanceM >= splitAnchorDistanceM + 1000.0) {
            splitList.add(
                Split(
                    index = splitList.size + 1,
                    distanceM = 1000.0,
                    durationMs = (pointTimeMs - splitAnchorTimeMs).coerceAtLeast(0L),
                    partial = false
                )
            )
            splitAnchorDistanceM += 1000.0
            splitAnchorTimeMs = pointTimeMs
        }
    }

    /** 结束时补最后不足 1 公里的一段（原型里叫「最后 0.41 公里」） */
    private fun appendFinalSplitIfNeeded() {
        val restDistance = distanceM - splitAnchorDistanceM
        val endTime = lastPoint?.timeMs ?: return
        if (restDistance < 30.0) return          // 不足 30 米不值得单独一段
        splitList.add(
            Split(
                index = splitList.size + 1,
                distanceM = restDistance,
                durationMs = (endTime - splitAnchorTimeMs).coerceAtLeast(0L),
                partial = true
            )
        )
        splitAnchorDistanceM = distanceM
    }

    private fun smoothSpeed(point: TrackPoint) {
        val raw = point.speedMps?.toDouble()
            ?: filter.lastAccepted?.let { prev ->
                val dt = (point.timeMs - prev.timeMs) / 1000.0
                if (dt > 0) GeoMath.distanceMeters(prev.lat, prev.lon, point.lat, point.lon) / dt else null
            }
            ?: return
        if (raw <= 0.2) return
        smoothedSpeedMps =
            if (smoothedSpeedMps <= 0.0) raw
            else SPEED_ALPHA * raw + (1 - SPEED_ALPHA) * smoothedSpeedMps
    }

    private companion object {
        /** 速度 EMA 系数（调研报告 §6.2 建议 0.3–0.5） */
        const val SPEED_ALPHA = 0.4
    }
}
