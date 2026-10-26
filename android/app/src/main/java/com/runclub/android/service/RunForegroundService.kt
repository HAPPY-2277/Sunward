package com.runclub.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.runclub.android.R
import com.runclub.android.data.InProgressStore
import com.runclub.android.session.RunSession
import com.runclub.android.tracking.LocationTracker
import com.runclub.android.tracking.RunState
import com.runclub.android.tracking.RunUiState
import com.runclub.android.tracking.SimulatedTrack
import com.runclub.android.tracking.TrackPoint
import com.runclub.android.ui.LiveRunActivity
import com.runclub.android.util.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 跑步记录前台服务。
 *
 * 存在的唯一理由：让定位采集在**锁屏 / 切后台**后继续。
 * - `foregroundServiceType="location"`：Android 14+ 强制声明类型；
 *   刻意不用 `dataSync` —— Android 15 起 dataSync 每 24h 最多跑 6 小时就被系统停掉，
 *   而 location 类型不受此限（调研报告 §6.4）。
 * - `startForeground` 必须在 startForegroundService 后 5 秒内调用，这里在 onCreate 里就调。
 * - 状态活在 [RunSession]，Activity 只是观察者，所以旋转/锁屏/重建都不丢。
 */
class RunForegroundService : LifecycleService() {

    private var collectorJob: Job? = null
    private var tickerJob: Job? = null
    private lateinit var store: InProgressStore

    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(this)
    }

    override fun onCreate() {
        super.onCreate()
        store = InProgressStore(this)
        createChannel()
        // 必须尽快进入前台，超 5 秒会被系统判定 ANR / 崩溃
        promoteToForeground(RunSession.current())
        observeState()
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (val action = intent?.action ?: ACTION_START) {
            ACTION_START -> {
                val simulated = intent?.getBooleanExtra(EXTRA_SIMULATED, false) ?: false
                RunSession.start(simulated)
                startCollector()
            }
            ACTION_PAUSE -> {
                RunSession.pause()
                stopCollector()
            }
            ACTION_RESUME -> {
                RunSession.resume()
                startCollector()
            }
            ACTION_FINISH -> finishRun()
        }
        return START_NOT_STICKY
    }

    // ---------- 前台与通知 ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW      // 低重要性：不发声、不弹横幅
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun promoteToForeground(state: RunUiState) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(state), type)
    }

    private fun observeState() {
        lifecycleScope.launch {
            RunSession.state.collect { state -> updateNotification(state) }
        }
    }

    private fun updateNotification(state: RunUiState) {
        if (!notificationManager.areNotificationsEnabled()) return
        runCatching { notificationManager.notify(NOTIF_ID, buildNotification(state)) }
    }

    private fun buildNotification(state: RunUiState): Notification {
        val title = when (state.state) {
            RunState.PAUSED -> getString(R.string.notif_title_paused)
            RunState.FINISHED -> getString(R.string.notif_title_finished)
            else -> getString(R.string.notif_title_running)
        }
        val text = String.format(
            Locale.US,
            "%s KM · %s · %s",
            Format.km(state.distanceM),
            Format.duration(state.elapsedMs),
            Format.pace(state.paceSecPerKm)
        )

        val pauseOrResumeAction =
            if (state.state == RunState.PAUSED) ACTION_RESUME to R.string.notif_resume
            else ACTION_PAUSE to R.string.notif_pause

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_route)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                getString(pauseOrResumeAction.second),
                servicePendingIntent(pauseOrResumeAction.first, 1)
            )
            .addAction(0, getString(R.string.notif_end), servicePendingIntent(ACTION_FINISH, 2))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, LiveRunActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 10, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RunForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------- 采集 ----------

    private fun startCollector() {
        stopCollector()
        val flow: Flow<TrackPoint> = when {
            RunSession.current().simulated -> SimulatedTrack.flow()
            else -> LocationTracker(this).locationFlow().let { locationFlow ->
                kotlinx.coroutines.flow.flow { locationFlow.collect { emit(it.toTrackPoint()) } }
            }
        }
        collectorJob = lifecycleScope.launch {
            flow.collect { point -> RunSession.onPoint(point) }
        }
    }

    private fun stopCollector() {
        collectorJob?.cancel()
        collectorJob = null
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                RunSession.tick()
                seconds++
                // 每 5 秒落一次「进行中」快照，供强杀后恢复
                if (seconds % 5 == 0) store.save(RunSession.current(), RunSession.startWallClockMs)
            }
        }
    }

    private fun finishRun() {
        stopCollector()
        RunSession.finish()
        // 落库（记录 + 轨迹点，单事务）。这里不用 runCatching 静默吞异常，
        // 否则出问题时完全无从查证（真实踩过：记录没存进库却没有任何线索）。
        try {
            val id = RunSession.persist(com.runclub.android.data.repo.RunRepository.get(this))
            android.util.Log.i(TAG, "跑步记录已保存 id=$id")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "保存跑步记录失败: ${e.message}", e)
        }
        store.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIF_ID)
        stopSelf()
    }

    override fun onDestroy() {
        stopCollector()
        tickerJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "run_tracking"
        const val NOTIF_ID = 1001
        private const val TAG = "RunForegroundService"

        const val ACTION_START = "com.runclub.android.action.START"
        const val ACTION_PAUSE = "com.runclub.android.action.PAUSE"
        const val ACTION_RESUME = "com.runclub.android.action.RESUME"
        const val ACTION_FINISH = "com.runclub.android.action.FINISH"

        const val EXTRA_SIMULATED = "extra_simulated"

        fun intent(context: Context, action: String, simulated: Boolean = false): Intent =
            Intent(context, RunForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_SIMULATED, simulated)
    }
}

/** 系统 Location → 内部 TrackPoint */
private fun android.location.Location.toTrackPoint(): TrackPoint = TrackPoint(
    timeMs = time,
    lat = latitude,
    lon = longitude,
    accuracyM = if (hasAccuracy()) accuracy else null,
    altitudeM = if (hasAltitude()) altitude else null,
    speedMps = if (hasSpeed()) speed else null,
    // 朝向：GPS 有多普勒时才有 bearing，网络定位通常没有 → 界面上用罗盘兜底
    bearingDeg = if (hasBearing()) bearing else null,
    provider = provider ?: "",
    simulated = false
)
