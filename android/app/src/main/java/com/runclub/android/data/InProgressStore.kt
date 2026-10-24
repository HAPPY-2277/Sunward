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
        val savedAtMs: Long
    ) {
        /** 超过 12 小时的快照视为过期，不再提示 */
        fun isFresh(nowMs: Long): Boolean = nowMs - savedAtMs <= MAX_AGE_MS

        companion object {
            const val MAX_AGE_MS = 12L * 60 * 60 * 1000
        }
    }

    fun save(state: RunUiState) {
        if (!state.isActive) return
        val json = JSONObject().apply {
            put("state", state.state.name)
            put("elapsedMs", state.elapsedMs)
            put("distanceM", state.distanceM)
            put("simulated", state.simulated)
            put("savedAtMs", System.currentTimeMillis())
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
                savedAtMs = json.getLong("savedAtMs")
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
