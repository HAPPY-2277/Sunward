package com.runclub.android.data

import android.content.Context
import com.runclub.android.tracking.RunState
import com.runclub.android.tracking.RunUiState
import org.json.JSONObject

/**
 * 「进行中的跑步」快照。
 *
 * 用途：App 被系统或用户强杀后，重开时能提示「检测到未完成的跑步，是否恢复」。
 * 实现说明：M3 用 SharedPreferences 存这份临时快照；**最终跑步记录**在 M6 才落 SQLite。
 */
class InProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Snapshot(
        val state: RunState,
        val elapsedMs: Long,
        val distanceM: Double,
        val simulated: Boolean,
        val savedAtMs: Long,
        /** 这次跑步真正的开始墙钟时间；本次修复之前写的旧快照里没有这一项，读出来是 0 */
        val startWallClockMs: Long = 0L
    ) {
        /** 超过 12 小时的快照视为过期，不再提示 */
        fun isFresh(nowMs: Long): Boolean = nowMs - savedAtMs <= MAX_AGE_MS

        /**
         * 恢复时应当写进记录的开始时间。
         *
         * 优先用快照里的真实开始时间；旧快照没有这一项时退回「保存时刻 − 已跑时长」，
         * 误差只剩暂停过的那段时间。**不能**用「恢复时刻 − 已跑时长」：快照 12 小时内都算新鲜，
         * 用户隔天再打开就会把这条记录算到错误的一天（报告是按开始时间归属周期的）。
         */
        val resolvedStartWallClockMs: Long
            get() = if (startWallClockMs > 0L) startWallClockMs else savedAtMs - elapsedMs

        companion object {
            const val MAX_AGE_MS = 12L * 60 * 60 * 1000
        }
    }

    fun save(state: RunUiState, startWallClockMs: Long = 0L) {
        if (!state.isActive) return
        val json = JSONObject().apply {
            put("state", state.state.name)
            put("elapsedMs", state.elapsedMs)
            put("distanceM", state.distanceM)
            put("simulated", state.simulated)
            put("savedAtMs", System.currentTimeMillis())
            put("startWallClockMs", startWallClockMs)
        }
        prefs.edit().putString(KEY_SNAPSHOT, json.toString()).apply()
    }

    fun load(): Snapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Snapshot(
                state = RunState.valueOf(json.getString("state")),
                elapsedMs = json.getLong("elapsedMs"),
                distanceM = json.getDouble("distanceM"),
                simulated = json.optBoolean("simulated", false),
                savedAtMs = json.getLong("savedAtMs"),
                startWallClockMs = json.optLong("startWallClockMs", 0L)
            )
        }.getOrNull()
    }

    /** 读取一份仍然有效、且确实处于进行中的快照 */
    fun loadRestorable(nowWallMs: Long = System.currentTimeMillis()): Snapshot? {
        val snapshot = load() ?: return null
        val active = snapshot.state == RunState.RUNNING || snapshot.state == RunState.PAUSED
        return if (active && snapshot.isFresh(nowWallMs)) snapshot else null
    }

    fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object {
        const val PREFS_NAME = "run_in_progress"
        const val KEY_SNAPSHOT = "snapshot"
    }
}
