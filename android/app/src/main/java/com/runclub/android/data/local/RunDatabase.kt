package com.runclub.android.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.runclub.android.data.model.RunRecord
import com.runclub.android.data.model.StoredTrackPoint

/**
 * 跑步记录的本地存储。
 *
 * 刻意用手写 SQLite 而不是 Room：
 * Room 需要 KSP 插件，而 KSP 与 Kotlin 2.4.21 的版本对应关系无法确证；
 * 这里只有两张表，手写更稳、零额外插件。批量插入走单一事务。
 */
class RunDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_RUN (
              id          INTEGER PRIMARY KEY AUTOINCREMENT,
              start_time  INTEGER NOT NULL,
              end_time    INTEGER NOT NULL,
              timezone    TEXT    NOT NULL,
              duration_ms INTEGER NOT NULL,
              distance_m  REAL    NOT NULL,
              avg_pace_s  REAL,
              calories    REAL    NOT NULL,
              simulated   INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_POINT (
              id          INTEGER PRIMARY KEY AUTOINCREMENT,
              run_id      INTEGER NOT NULL,
              seq         INTEGER NOT NULL,
              time_ms     INTEGER NOT NULL,
              lat         REAL    NOT NULL,
              lon         REAL    NOT NULL,
              accuracy_m  REAL,
              altitude_m  REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_point_run ON $TABLE_POINT(run_id, seq)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // demo 阶段还没有需要迁移的历史数据，直接重建
        db.execSQL("DROP TABLE IF EXISTS $TABLE_POINT")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RUN")
        onCreate(db)
    }

    /** 插入一次跑步及其全部轨迹点（单事务，保证要么都成功要么都失败） */
    fun insertRun(record: RunRecord, points: List<StoredTrackPoint>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("start_time", record.startTimeMs)
                put("end_time", record.endTimeMs)
                put("timezone", record.timezone)
                put("duration_ms", record.durationMs)
                put("distance_m", record.distanceM)
                put("avg_pace_s", record.avgPaceSecPerKm)
                put("calories", record.calories)
                put("simulated", if (record.simulated) 1 else 0)
            }
            val runId = db.insert(TABLE_RUN, null, values)
            if (runId <= 0) error("插入跑步记录失败")

            if (points.isNotEmpty()) {
                val statement = db.compileStatement(
                    "INSERT INTO $TABLE_POINT(run_id, seq, time_ms, lat, lon, accuracy_m, altitude_m) VALUES(?,?,?,?,?,?,?)"
                )
                statement.use {
                    points.forEachIndexed { index, p ->
                        it.clearBindings()
                        it.bindLong(1, runId)
                        it.bindLong(2, index.toLong())
                        it.bindLong(3, p.timeMs)
                        it.bindDouble(4, p.lat)
                        it.bindDouble(5, p.lon)
                        if (p.accuracyM != null) it.bindDouble(6, p.accuracyM.toDouble())
                        else it.bindNull(6)
                        if (p.altitudeM != null) it.bindDouble(7, p.altitudeM)
                        else it.bindNull(7)
                        it.executeInsert()
                    }
                }
            }
            db.setTransactionSuccessful()
            return runId
        } finally {
            db.endTransaction()
        }
    }

    fun deleteRun(runId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_POINT, "run_id = ?", arrayOf(runId.toString()))
            db.delete(TABLE_RUN, "id = ?", arrayOf(runId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun allRuns(): List<RunRecord> {
        val result = ArrayList<RunRecord>()
        readableDatabase.query(
            TABLE_RUN, null, null, null, null, null, "start_time DESC"
        ).use { c ->
            while (c.moveToNext()) result += c.toRunRecord()
        }
        return result
    }

    fun runById(runId: Long): RunRecord? {
        readableDatabase.query(
            TABLE_RUN, null, "id = ?", arrayOf(runId.toString()), null, null, null
        ).use { c ->
            return if (c.moveToFirst()) c.toRunRecord() else null
        }
    }

    fun pointsOf(runId: Long): List<StoredTrackPoint> {
        val result = ArrayList<StoredTrackPoint>()
        readableDatabase.query(
            TABLE_POINT, null, "run_id = ?", arrayOf(runId.toString()), null, null, "seq ASC"
        ).use { c ->
            while (c.moveToNext()) {
                result += StoredTrackPoint(
                    runId = c.getLong(c.getColumnIndexOrThrow("run_id")),
                    seq = c.getInt(c.getColumnIndexOrThrow("seq")),
                    timeMs = c.getLong(c.getColumnIndexOrThrow("time_ms")),
                    lat = c.getDouble(c.getColumnIndexOrThrow("lat")),
                    lon = c.getDouble(c.getColumnIndexOrThrow("lon")),
                    accuracyM = c.getColumnIndexOrThrow("accuracy_m").let { i ->
                        if (c.isNull(i)) null else c.getFloat(i)
                    },
                    altitudeM = c.getColumnIndexOrThrow("altitude_m").let { i ->
                        if (c.isNull(i)) null else c.getDouble(i)
                    }
                )
            }
        }
        return result
    }

    private fun android.database.Cursor.toRunRecord() = RunRecord(
        id = getLong(getColumnIndexOrThrow("id")),
        startTimeMs = getLong(getColumnIndexOrThrow("start_time")),
        endTimeMs = getLong(getColumnIndexOrThrow("end_time")),
        timezone = getString(getColumnIndexOrThrow("timezone")),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
        avgPaceSecPerKm = getColumnIndexOrThrow("avg_pace_s").let { i ->
            if (isNull(i)) null else getDouble(i)
        },
        calories = getDouble(getColumnIndexOrThrow("calories")).toInt(),
        simulated = getInt(getColumnIndexOrThrow("simulated")) == 1
    )

    companion object {
        const val DB_NAME = "runclub.db"
        const val DB_VERSION = 1
        const val TABLE_RUN = "run"
        const val TABLE_POINT = "track_point"
    }
}
