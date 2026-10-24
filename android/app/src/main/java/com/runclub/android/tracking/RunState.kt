package com.runclub.android.tracking

/** 跑步状态机状态 */
enum class RunState { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * 状态机迁移表。非法迁移一律拒绝，不做「猜测性纠正」。
 *
 *   IDLE → RUNNING
 *   RUNNING ⇄ PAUSED
 *   RUNNING / PAUSED → FINISHED
 *   FINISHED 为终态
 */
object RunStateMachine {

    private val ALLOWED: Map<RunState, Set<RunState>> = mapOf(
        RunState.IDLE to setOf(RunState.RUNNING),
        RunState.RUNNING to setOf(RunState.PAUSED, RunState.FINISHED),
        RunState.PAUSED to setOf(RunState.RUNNING, RunState.FINISHED),
        RunState.FINISHED to emptySet()
    )

    fun canTransition(from: RunState, to: RunState): Boolean =
        ALLOWED[from]?.contains(to) == true

    /** 是否处于「一次跑步进行中」（含暂停） */
    fun isActive(state: RunState): Boolean =
        state == RunState.RUNNING || state == RunState.PAUSED
}
