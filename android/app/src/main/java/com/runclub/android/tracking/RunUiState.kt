package com.runclub.android.tracking

/**
 * 一次跑步的 UI 状态快照。用于 RunSession → 界面 / 通知的单向数据流。
 */
data class RunUiState(
    val state: RunState = RunState.IDLE,
    val elapsedMs: Long = 0L,
    val distanceM: Double = 0.0,
    val paceSecPerKm: Double? = null,
    val avgPaceSecPerKm: Double? = null,
    val calories: Int = 0,
    val pointsAccepted: Int = 0,
    val pointsRejected: Int = 0,
    val rejectedAccuracy: Int = 0,
    val rejectedTimestamp: Int = 0,
    val rejectedSpeed: Int = 0,
    val rejectedInconsistent: Int = 0,
    val accuracyM: Float? = null,
    val provider: String? = null,
    val simulated: Boolean = false,
    val lastPoint: TrackPoint? = null
) {
    val distanceKm: Double get() = distanceM / 1000.0
    val isRunning: Boolean get() = state == RunState.RUNNING
    val isActive: Boolean get() = RunStateMachine.isActive(state)
}
