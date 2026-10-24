package com.runclub.android.tracking

import kotlin.math.roundToInt

/**
 * 一次跑步的完整状态与指标计算。**纯 Kotlin，不依赖 Android**，因此可以在 JVM 上直接单测。
 *
 * 计时全部基于调用方传入的单调时钟（生产代码用 `SystemClock.elapsedRealtime()`），
 * 不用 `System.currentTimeMillis()` —— 用户改系统时间或跨时区都不会让时长错乱。
 */
class RunTracker(
    val filter: TrackPointFilter = TrackPointFilter(),
    /** 卡路里估算用的体重（kg），demo 阶段固定 60，后续做设置页 */
    private val weightKg: Double = 60.0
) {

    var state: RunState = RunState.IDLE
        private set

    /** 已累计的运动时长（不含暂停区间） */
    private var accumulatedMs: Long = 0L

    /** 当前 RUNNING 区间的起点 */
    private var segmentStartMs: Long = 0L

    var distanceM: Double = 0.0
        private set

    var lastPoint: TrackPoint? = null
        private set

    /** 平滑后的瞬时速度（m/s） */
    var smoothedSpeedMps: Double = 0.0
        private set

    // ---------- 状态迁移 ----------

    fun start(nowMs: Long): Boolean {
        if (!RunStateMachine.canTransition(state, RunState.RUNNING)) return false
        filter.reset()
        distanceM = 0.0
        smoothedSpeedMps = 0.0
        lastPoint = null
        accumulatedMs = 0L
        segmentStartMs = nowMs
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
        return true
    }

    /** 从「异常退出前的快照」恢复为进行中状态 */
    fun restore(elapsedMs: Long, distanceM: Double, nowMs: Long) {
        accumulatedMs = elapsedMs.coerceAtLeast(0L)
        this.distanceM = distanceM.coerceAtLeast(0.0)
        segmentStartMs = nowMs
        state = RunState.RUNNING
    }

    // ---------- 指标 ----------

    /** 当前已运动时长（毫秒，不含暂停） */
    fun elapsedMs(nowMs: Long): Long = when (state) {
        RunState.RUNNING -> accumulatedMs + (nowMs - segmentStartMs).coerceAtLeast(0L)
        else -> accumulatedMs
    }

    /** 当前配速（秒/公里）；无有效速度时返回 null，UI 显示 --'--" */
    val paceSecPerKm: Double?
        get() = if (smoothedSpeedMps < 0.3) null else (1000.0 / smoothedSpeedMps).coerceIn(150.0, 900.0)

    /** 平均配速（秒/公里）；距离不足 50 m 时视为无意义返回 null */
    val averagePaceSecPerKm: Double?
        get() {
            if (distanceM < 50.0) return null
            val seconds = accumulatedMs / 1000.0
            if (seconds <= 0.0) return null
            return seconds / (distanceM / 1000.0)
        }

    /** 卡路里估算：kcal = 距离(km) × 体重(kg) × 1.036 */
    val calories: Int
        get() = (distanceM / 1000.0 * weightKg * 1.036).roundToInt()

    /**
     * 提交一个轨迹点。
     * 只有 RUNNING 状态才参与距离计算 —— 暂停期间即使系统仍回调也不计入。
     */
    fun onPoint(point: TrackPoint): Boolean {
        if (state != RunState.RUNNING) return false
        val accepted = filter.offer(point)
        lastPoint = point
        if (accepted) {
            distanceM = filter.acceptedDistanceM
            smoothSpeed(point)
        }
        return accepted
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
            if (smoothedSpeedMps <= 0.0) raw else SPEED_ALPHA * raw + (1 - SPEED_ALPHA) * smoothedSpeedMps
    }

    private companion object {
        /** 速度 EMA 系数（调研报告 §6.2 建议 0.3–0.5） */
        const val SPEED_ALPHA = 0.4
    }
}
