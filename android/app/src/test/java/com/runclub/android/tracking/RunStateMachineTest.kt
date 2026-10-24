package com.runclub.android.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStateMachineTest {

    @Test
    fun `合法迁移`() {
        assertTrue(RunStateMachine.canTransition(RunState.IDLE, RunState.RUNNING))
        assertTrue(RunStateMachine.canTransition(RunState.RUNNING, RunState.PAUSED))
        assertTrue(RunStateMachine.canTransition(RunState.RUNNING, RunState.FINISHED))
        assertTrue(RunStateMachine.canTransition(RunState.PAUSED, RunState.RUNNING))
        assertTrue(RunStateMachine.canTransition(RunState.PAUSED, RunState.FINISHED))
    }

    @Test
    fun `非法迁移`() {
        assertFalse(RunStateMachine.canTransition(RunState.IDLE, RunState.PAUSED))
        assertFalse(RunStateMachine.canTransition(RunState.IDLE, RunState.FINISHED))
        assertFalse(RunStateMachine.canTransition(RunState.RUNNING, RunState.RUNNING))
        assertFalse(RunStateMachine.canTransition(RunState.PAUSED, RunState.PAUSED))
        assertFalse("FINISHED 是终态", RunStateMachine.canTransition(RunState.FINISHED, RunState.RUNNING))
        assertFalse(RunStateMachine.canTransition(RunState.FINISHED, RunState.PAUSED))
    }

    @Test
    fun `活跃状态判定`() {
        assertTrue(RunStateMachine.isActive(RunState.RUNNING))
        assertTrue(RunStateMachine.isActive(RunState.PAUSED))
        assertFalse(RunStateMachine.isActive(RunState.IDLE))
        assertFalse(RunStateMachine.isActive(RunState.FINISHED))
    }
}
