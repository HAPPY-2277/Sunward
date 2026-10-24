package com.runclub.android.session

import android.os.SystemClock
import com.runclub.android.tracking.RunState
import com.runclub.android.tracking.RunStateMachine
import com.runclub.android.tracking.RunTracker
import com.runclub.android.tracking.RunUiState
import com.runclub.android.tracking.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前这次跑步的单一数据源（进程内单例）。
 *
 * 前台服务负责「采集 + 通知」，Activity 只做「观察 + 渲染 + 下发指令」，
 * 两者通过这个 StateFlow 通信。因此 Activity 被销毁/重建（旋转、锁屏）后
 * 状态不会丢 —— 状态活在 Service 与这里，而不是 Activity 里。
 *
 * demo 阶段用进程内单例是刻意简化；M6 引入持久化后由仓库层接管。
 */
object RunSession {

    private var tracker = RunTracker()

    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    /** 可替换的单调时钟（测试时注入假时间）；生产用 elapsedRealtime，不受用户改系统时间影响 */
    var timeSource: () -> Long = { SystemClock.elapsedRealtime() }

    /** 当前是否在用内置模拟轨迹 */
    var simulated: Boolean = false
        private set

    fun nowMs(): Long = timeSource()

    fun trackerState(): RunState = tracker.state

    fun current(): RunUiState = _state.value

    /** 本次跑步已采纳的轨迹点（按时间顺序），用于绘制轨迹 */
    fun track(): List<TrackPoint> = tracker.track

    /** 每公里分段成绩 */
    fun splits(): List<RunTracker.Split> = tracker.splits

    /** 开始一次新的跑步；数据源变化或上一次已结束时都换一个干净的 tracker */
    fun start(simulated: Boolean): Boolean {
        // 切换数据源必须重建 tracker：否则过滤器里留着上一个数据源的点，
        // 新点会被「速度超限」判定成瞬移而全部丢弃（真实踩过）。
        val sourceChanged = simulated != this.simulated && tracker.state == RunState.RUNNING
        if (sourceChanged || tracker.state == RunState.FINISHED || tracker.state == RunState.IDLE) {
            tracker = RunTracker()
        }
        val ok = tracker.start(nowMs())
        this.simulated = simulated
        publish()
        return ok
    }

    fun pause(): Boolean {
        val ok = tracker.pause(nowMs())
        publish()
        return ok
    }

    fun resume(): Boolean {
        val ok = tracker.resume(nowMs())
        publish()
        return ok
    }

    fun finish(): RunUiState {
        tracker.finish(nowMs())
        publish()
        return _state.value
    }

    /** 从崩溃前的快照恢复 */
    fun restore(elapsedMs: Long, distanceM: Double, simulated: Boolean) {
        tracker = RunTracker()
        tracker.restore(elapsedMs, distanceM, nowMs())
        this.simulated = simulated
        publish()
    }

    fun onPoint(point: TrackPoint) {
        tracker.onPoint(point)
        publish()
    }

    /** 每秒驱动一次，用于刷新耗时显示 */
    fun tick() {
        if (RunStateMachine.isActive(tracker.state)) publish()
    }

    /** 丢弃当前会话，回到 IDLE（新一次跑步前调用） */
    fun reset() {
        tracker = RunTracker()
        simulated = false
        _state.value = RunUiState()
    }

    private fun publish() {
        val t = tracker
        val now = nowMs()
        _state.value = RunUiState(
            state = t.state,
            elapsedMs = t.elapsedMs(now),
            distanceM = t.distanceM,
            paceSecPerKm = t.paceSecPerKm,
            avgPaceSecPerKm = t.averagePaceSecPerKm(now),
            calories = t.calories,
            pointsAccepted = t.filter.stats.accepted,
            pointsRejected = t.filter.stats.rejected,
            rejectedAccuracy = t.filter.stats.rejectedAccuracy,
            rejectedTimestamp = t.filter.stats.rejectedTimestamp,
            rejectedSpeed = t.filter.stats.rejectedSpeed,
            rejectedInconsistent = t.filter.stats.rejectedInconsistent,
            accuracyM = t.lastPoint?.accuracyM,
            provider = t.lastPoint?.provider,
            // 这里必须是「会话模式」，不能用 lastPoint.simulated：
            // 切换数据源后 lastPoint 仍是上一模式的点，会把开关状态骗回去。
            simulated = simulated,
            lastPoint = t.lastPoint
        )
    }
}
