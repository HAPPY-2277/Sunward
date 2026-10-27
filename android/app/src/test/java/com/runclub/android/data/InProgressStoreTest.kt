package com.runclub.android.data

import com.runclub.android.tracking.RunState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 只测快照数据本身，不碰 SharedPreferences（那需要 Android 上下文）。
 *
 * 开始时间的取值优先级决定了「强杀后恢复」的记录会不会算错归属日：
 * 快照 12 小时内都算新鲜，如果用恢复时刻倒推，隔天再打开就会把记录算到新的一天。
 */
class InProgressStoreTest {

    @Test
    fun `快照带开始时间时优先用它`() {
        val snapshot = snapshot(
            startWallClockMs = 1_700_000_000_000L,
            savedAtMs = 1_700_000_300_000L,
            elapsedMs = 300_000L
        )

        assertEquals(1_700_000_000_000L, snapshot.resolvedStartWallClockMs)
    }

    @Test
    fun `旧快照没有开始时间时用保存时刻倒推`() {
        val snapshot = snapshot(
            startWallClockMs = 0L,
            savedAtMs = 1_700_000_300_000L,
            elapsedMs = 300_000L
        )

        assertEquals(1_700_000_000_000L, snapshot.resolvedStartWallClockMs)
    }

    private fun snapshot(startWallClockMs: Long, savedAtMs: Long, elapsedMs: Long) =
        InProgressStore.Snapshot(
            state = RunState.RUNNING,
            elapsedMs = elapsedMs,
            distanceM = 900.0,
            simulated = false,
            savedAtMs = savedAtMs,
            startWallClockMs = startWallClockMs
        )
}
