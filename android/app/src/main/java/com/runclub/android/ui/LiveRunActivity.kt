package com.runclub.android.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.runclub.android.R
import com.runclub.android.map.HeadingProvider
import com.runclub.android.map.MapLayer
import com.runclub.android.map.provideMapLayer
import com.runclub.android.service.RunForegroundService
import com.runclub.android.session.RunSession
import com.runclub.android.tracking.LocationTracker
import com.runclub.android.tracking.RunState
import com.runclub.android.tracking.RunUiState
import com.runclub.android.tracking.TrackPoint
import com.runclub.android.ui.widget.TrackCanvasView
import com.runclub.android.util.Format
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 实时跑步页（对应原型 live-run.html）。
 *
 * 职责边界（M3 起）：
 * - **采集与计时在 [RunForegroundService] + [RunSession] 里**，锁屏/切后台继续；
 * - 本 Activity 只做「观察状态 → 渲染」和「按钮 → 下发指令」，
 *   因此旋转屏幕、Activity 被回收重建都不会丢数据。
 * - 界面上所有可变态（是否模拟、是否暂停）都**只从 RunSession.state 推导**，
 *   本地不保存副本，避免两份状态打架。
 */
class LiveRunActivity : AppCompatActivity() {

    private lateinit var tracker: LocationTracker

    private var navigatedToResult = false
    private var lastState: RunState = RunState.IDLE

    private lateinit var tvStatus: TextView
    private lateinit var liveDot: View
    private lateinit var tvSignal: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvPace: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvTime: TextView
    private lateinit var goalFill: View
    private lateinit var ivPause: ImageView
    private lateinit var tvPauseLabel: TextView

    private lateinit var permOverlay: View
    private lateinit var tvPermHint: TextView

    private lateinit var debugPanel: View
    private lateinit var tvDebugProvider: TextView
    private lateinit var tvDebugLatLon: TextView
    private lateinit var tvDebugCounters: TextView
    private lateinit var tvDebugBreakdown: TextView
    private lateinit var tvSimToggle: TextView

    private var dotAnimator: ObjectAnimator? = null

    private lateinit var endModal: View
    private lateinit var modalDistance: TextView
    private lateinit var modalTime: TextView
    private lateinit var modalPace: TextView

    /** 地图层：amap 变体是真高德底图，local 变体是空实现（保留手绘地图） */
    private lateinit var mapLayer: MapLayer
    private lateinit var amapHost: FrameLayout
    private lateinit var trackOverlay: TrackCanvasView

    /** 罗盘：GPS 点没有 bearing 时给地图上的跑者箭头兜底 */
    private lateinit var headingProvider: HeadingProvider

    private lateinit var countdownOverlay: View
    private lateinit var tvCountdown: TextView
    private lateinit var tvCountdownHint: TextView

    private var countdownJob: Job? = null
    private var warmUpJob: Job? = null

    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                permOverlay.visibility = View.GONE
                beginRunSequence(RunSession.current().simulated)
            } else {
                tvPermHint.visibility = View.VISIBLE
                permOverlay.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_run)
        tracker = LocationTracker(this)
        mapLayer = provideMapLayer(this)
        headingProvider = HeadingProvider(this)
        bindViews()
        bindActions()
        mapLayer.attach(amapHost, savedInstanceState)
        mapLayer.attachOverlay(trackOverlay)
        observeState()
        restoreIfRequested(intent)
        ensureRunning()
    }

    // ---------- 启动与恢复 ----------

    private fun restoreIfRequested(intent: Intent?) {
        val elapsed = intent?.getLongExtra(EXTRA_RESTORE_ELAPSED, -1L) ?: -1L
        if (elapsed < 0) return
        val simulated = intent?.getBooleanExtra(EXTRA_RESTORE_SIMULATED, false) ?: false
        val distance = intent?.getDoubleExtra(EXTRA_RESTORE_DISTANCE, 0.0) ?: 0.0
        RunSession.restore(elapsed, distance, simulated)
        ContextCompat.startForegroundService(
            this,
            RunForegroundService.intent(this, RunForegroundService.ACTION_RESUME)
        )
        intent?.removeExtra(EXTRA_RESTORE_ELAPSED)
    }

    private fun ensureRunning() {
        val state = RunSession.trackerState()
        if (state == RunState.RUNNING || state == RunState.PAUSED) return  // 已在记录，继续观察

        // 调试用：允许用 Intent 直接指定「模拟轨迹」启动，方便自动化验收
        //   adb shell am start -n com.runclub.android/.ui.LiveRunActivity --ez sim true
        val wantSimulated = intent?.getBooleanExtra(EXTRA_SIMULATED, RunSession.current().simulated)
            ?: RunSession.current().simulated

        if (!tracker.hasPermission() && !wantSimulated) {
            permOverlay.visibility = View.VISIBLE
            requestPermissions()
            return
        }
        permOverlay.visibility = View.GONE
        beginRunSequence(wantSimulated)
    }

    /** 从「点 GO」到「开始计时」的完整序列：预热定位 → 3-2-1-GO 倒计时 → 启动前台服务 */
    private fun beginRunSequence(simulated: Boolean) {
        startWarmUp(simulated)
        startCountdown(simulated)
    }

    /**
     * 倒计时期间的定位预热。
     *
     * 进页面就 attach 了地图（瓦片开始下载），但第一个 GPS 点可能要好几秒，
     * 这段时间先用系统缓存的位置把镜头挪到脚下，再开一条 1Hz 定位流持续纠正；
     * 等 3-2-1 走完、真正开始计时时，地图已经停在正确的位置和缩放级别上了。
     * 预热点不会污染记录：RunTracker.onPoint 只接受 RUNNING 状态的点，而此时前台服务还没启动。
     */
    private fun startWarmUp(simulated: Boolean) {
        // 模拟轨迹的起点就是地图兜底位置（上海滨江），预热没有意义
        if (simulated) return
        if (!tracker.hasPermission()) return  // 权限还没给，交给 permissionLauncher 走完再预热
        val state = RunSession.trackerState()
        if (state == RunState.RUNNING || state == RunState.PAUSED) return  // 恢复的记录已在采集

        tracker.lastKnownLocation()?.let { location -> applyRunnerPosition(location) }
        warmUpJob?.cancel()
        warmUpJob = lifecycleScope.launch {
            tracker.locationFlow()
                // 定位服务没开时这条流会直接 close，倒计时照常走完，开跑后由前台服务再暴露问题
                .catch { Log.i(TAG, "倒计时预热定位不可用：${it.message}") }
                .collect { location -> applyRunnerPosition(location) }
        }
    }

    /** 系统定位 → 地图层入参（朝向缺失时留空，跑步中由罗盘兜底） */
    private fun applyRunnerPosition(location: Location) {
        mapLayer.updateRunnerPosition(
            location.latitude,
            location.longitude,
            if (location.hasBearing()) location.bearing else null,
            if (location.hasAccuracy()) location.accuracy else null
        )
    }

    /**
     * 3-2-1-GO 倒计时，全部在 Activity 内完成，不占用前台服务。
     * 每个数字淡入放大 → 停留 → 淡出；GO 出现满 1 秒才开始计时（淡出与启动并行，不额外拖时间）。
     */
    private fun startCountdown(simulated: Boolean) {
        countdownJob?.cancel()
        countdownOverlay.visibility = View.VISIBLE
        countdownJob = lifecycleScope.launch {
            for (label in COUNTDOWN_LABELS) pulseDigit(label)
            showCountdownText(getString(R.string.home_go), hint = false)
            delay(COUNTDOWN_GO_TOTAL_MS)
            startNewRun(simulated)
            countdownOverlay.animate()
                .alpha(0f)
                .setDuration(COUNTDOWN_OVERLAY_FADE_MS)
                .withEndAction {
                    countdownOverlay.visibility = View.GONE
                    countdownOverlay.alpha = 1f
                }
                .start()
        }
    }

    /** 一个数字的完整生命周期：淡入放大 → 停留 → 淡出 */
    private suspend fun pulseDigit(label: String) {
        showCountdownText(label, hint = true)
        delay(COUNTDOWN_DIGIT_TOTAL_MS - COUNTDOWN_FADE_OUT_MS)
        tvCountdown.animate().alpha(0f).setDuration(COUNTDOWN_FADE_OUT_MS).start()
        delay(COUNTDOWN_FADE_OUT_MS)
    }

    private fun showCountdownText(label: String, hint: Boolean) {
        tvCountdown.text = label
        tvCountdownHint.visibility = if (hint) View.VISIBLE else View.GONE
        tvCountdown.alpha = 0f
        tvCountdown.scaleX = COUNTDOWN_SCALE_FROM
        tvCountdown.scaleY = COUNTDOWN_SCALE_FROM
        tvCountdown.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(COUNTDOWN_FADE_IN_MS)
            .start()
    }

    private fun startNewRun(simulated: Boolean) {
        // 计时开始后位置由前台服务的采集流驱动，预热流必须停掉，避免两路重复请求定位
        warmUpJob?.cancel()
        warmUpJob = null
        ContextCompat.startForegroundService(
            this,
            RunForegroundService.intent(this, RunForegroundService.ACTION_START, simulated)
        )
    }

    private fun sendAction(action: String) {
        ContextCompat.startForegroundService(this, RunForegroundService.intent(this, action))
    }

    // ---------- 视图 ----------

    private fun bindViews() {
        tvStatus = findViewById(R.id.tvStatus)
        liveDot = findViewById(R.id.liveDot)
        tvSignal = findViewById(R.id.tvSignal)
        tvDistance = findViewById(R.id.tvDistance)
        tvPace = findViewById(R.id.tvPace)
        tvCalories = findViewById(R.id.tvCalories)
        tvTime = findViewById(R.id.tvTime)
        goalFill = findViewById(R.id.goalFill)
        ivPause = findViewById(R.id.ivPause)
        tvPauseLabel = findViewById(R.id.tvPauseLabel)
        permOverlay = findViewById(R.id.permOverlay)
        tvPermHint = findViewById(R.id.tvPermHint)
        debugPanel = findViewById(R.id.debugPanel)
        tvDebugProvider = findViewById(R.id.tvDebugProvider)
        tvDebugLatLon = findViewById(R.id.tvDebugLatLon)
        tvDebugCounters = findViewById(R.id.tvDebugCounters)
        tvDebugBreakdown = findViewById(R.id.tvDebugBreakdown)
        tvSimToggle = findViewById(R.id.tvSimToggle)
        amapHost = findViewById(R.id.amapHost)
        trackOverlay = findViewById(R.id.trackOverlay)

        countdownOverlay = findViewById(R.id.countdownOverlay)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvCountdownHint = findViewById(R.id.tvCountdownHint)

        endModal = findViewById(R.id.endModal)
        modalDistance = findViewById(R.id.modalDistance)
        modalTime = findViewById(R.id.modalTime)
        modalPace = findViewById(R.id.modalPace)
        if (!isDebuggable) debugPanel.visibility = View.GONE
    }

    private fun bindActions() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnGrant).setOnClickListener { requestPermissions() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { openAppSettings() }
        findViewById<View>(R.id.btnPause).setOnClickListener { togglePause() }
        findViewById<View>(R.id.btnEnd).setOnClickListener { showEndModal() }
        findViewById<View>(R.id.btnModalCancel).setOnClickListener {
            endModal.visibility = View.GONE
        }
        findViewById<View>(R.id.btnModalConfirm).setOnClickListener {
            endModal.visibility = View.GONE
            sendAction(RunForegroundService.ACTION_FINISH)
        }
        findViewById<View>(R.id.gpsChip).setOnClickListener {
            if (!isDebuggable) return@setOnClickListener
            debugPanel.visibility =
                if (debugPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.tvDebugClose).setOnClickListener {
            debugPanel.visibility = View.GONE
        }
        // 切换数据源：重新开始一次记录（调试用，距离清零）。
        // 目标值从会话状态取反，界面文案由 render() 统一驱动，本地不留副本。
        tvSimToggle.setOnClickListener {
            navigatedToResult = false
            startNewRun(!RunSession.current().simulated)
        }
        renderSimMode(RunSession.current().simulated)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RunSession.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: RunUiState) {
        tvDistance.text = Format.km(state.distanceM)
        tvTime.text = Format.duration(state.elapsedMs)
        tvPace.text = Format.pace(state.paceSecPerKm)
        tvCalories.text = state.calories.toString()

        when (state.state) {
            RunState.PAUSED -> {
                tvStatus.text = getString(R.string.live_status_paused)
                liveDot.setBackgroundResource(R.drawable.bg_dot_orange)
                stopDotPulse()
                ivPause.setImageResource(R.drawable.ic_play)
                tvPauseLabel.text = getString(R.string.live_resume)
            }
            RunState.RUNNING -> {
                tvStatus.text = getString(R.string.live_status_running)
                liveDot.setBackgroundResource(R.drawable.bg_dot_lime)
                startDotPulse()
                ivPause.setImageResource(R.drawable.ic_pause)
                tvPauseLabel.text = getString(R.string.live_pause)
            }
            else -> {
                tvStatus.text = getString(R.string.live_status_idle)
                stopDotPulse()
            }
        }

        val percent = (state.distanceKm / 5.0 * 100).coerceIn(0.0, 100.0)
        goalFill.post {
            val trackWidth = (goalFill.parent as View).width
            goalFill.layoutParams = goalFill.layoutParams.apply {
                width = (trackWidth * percent / 100.0).roundToInt()
            }
            goalFill.requestLayout()
        }

        tvSignal.text = when {
            state.state != RunState.RUNNING && state.state != RunState.PAUSED -> getString(R.string.live_signal_none)
            state.accuracyM == null -> getString(R.string.live_signal_none)
            state.accuracyM <= 10f -> getString(R.string.live_signal_strong)
            state.accuracyM <= 30f -> getString(R.string.live_signal_medium)
            else -> getString(R.string.live_signal_weak)
        }

        renderSimMode(state.simulated)
        renderDebug(state)

        // 轨迹：把已采纳的点交给地图层（amap 画在地图上，local 画在叠加层上）
        // 跑步中不传 endpoints=true：终点就是跑者当前位置，交给下面的定位标表示
        mapLayer.setTrack(RunSession.track())
        // 记录中让当前位置脉冲（对应原型 animate-pulse），暂停/结束时停掉
        if (state.state == RunState.RUNNING) trackOverlay.startPulse() else trackOverlay.stopPulse()
        // 跑者位置：优先用 GPS 的多普勒朝向，缺失（网络定位、静止）时用罗盘兜底
        state.lastPoint?.let { point ->
            mapLayer.updateRunnerPosition(
                point.lat,
                point.lon,
                point.bearingDeg ?: headingProvider.lastHeadingDeg,
                state.accuracyM
            )
        }

        if (state.state == RunState.FINISHED && !navigatedToResult &&
            (lastState == RunState.RUNNING || lastState == RunState.PAUSED)
        ) {
            navigatedToResult = true
            startActivity(Intent(this, ResultActivity::class.java))
            finish()
        }
        lastState = state.state
    }

    private fun renderSimMode(simulated: Boolean) {
        if (tvSimToggle == null) return
        tvSimToggle.text = getString(
            if (simulated) R.string.debug_sim_on else R.string.debug_sim_off
        )
        tvSimToggle.setTextColor(
            ContextCompat.getColor(this, if (simulated) R.color.accent_lime else R.color.text_muted)
        )
    }

    private fun renderDebug(state: RunUiState) {
        if (!isDebuggable) return
        tvDebugProvider.text = String.format(
            Locale.US,
            "state %s   provider %s   acc %s\nmap: %s",
            state.state.name,
            state.provider?.ifEmpty { "-" } ?: "-",
            state.accuracyM?.let { String.format(Locale.US, "%.1fm", it) } ?: "-",
            mapLayer.statusText
        )
        tvDebugLatLon.text = state.lastPoint?.latLonText() ?: "---.------, ---.------"
        tvDebugCounters.text = String.format(
            Locale.US,
            "pts %d · ok %d · drop %d · %s km",
            state.pointsAccepted + state.pointsRejected,
            state.pointsAccepted,
            state.pointsRejected,
            Format.km(state.distanceM)
        )
        tvDebugBreakdown.text = getString(
            R.string.debug_breakdown,
            state.rejectedAccuracy, state.rejectedTimestamp,
            state.rejectedSpeed, state.rejectedInconsistent
        )
    }

    /** 结束确认弹窗：与原型 endModal 一致，三宫格显示当前实时数据 */
    private fun showEndModal() {
        val state = RunSession.current()
        modalDistance.text = Format.km(state.distanceM)
        modalTime.text = Format.duration(state.elapsedMs)
        modalPace.text = Format.pace(state.avgPaceSecPerKm)
        endModal.visibility = View.VISIBLE
    }

    private fun togglePause() {
        when (RunSession.trackerState()) {
            RunState.RUNNING -> sendAction(RunForegroundService.ACTION_PAUSE)
            RunState.PAUSED -> sendAction(RunForegroundService.ACTION_RESUME)
            else -> {
                if (!tracker.hasPermission() && !RunSession.current().simulated) requestPermissions()
                else startNewRun(RunSession.current().simulated)
            }
        }
    }

    // ---------- 权限 ----------

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    // ---------- 圆点动画 ----------

    private fun startDotPulse() {
        if (dotAnimator?.isStarted == true) return
        dotAnimator = ObjectAnimator.ofFloat(liveDot, "alpha", 1f, 0.25f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopDotPulse() {
        dotAnimator?.cancel()
        dotAnimator = null
        liveDot.alpha = 1f
    }

    override fun onResume() {
        super.onResume()
        mapLayer.onResume()
        // 罗盘只在实时页可见期间开着：传感器常开会明显耗电
        headingProvider.start()
    }

    override fun onPause() {
        headingProvider.stop()
        mapLayer.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapLayer.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        warmUpJob?.cancel()
        stopDotPulse()
        trackOverlay.stopPulse()
        mapLayer.onDestroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiveRunActivity"

        const val EXTRA_RESTORE_ELAPSED = "restore_elapsed_ms"
        const val EXTRA_RESTORE_DISTANCE = "restore_distance_m"
        const val EXTRA_RESTORE_SIMULATED = "restore_simulated"

        /** 调试用：true 表示直接用内置模拟轨迹开跑（自动化验收用） */
        const val EXTRA_SIMULATED = "sim"

        // ---------- 起跑倒计时 ----------

        private val COUNTDOWN_LABELS = listOf("3", "2", "1")

        /** 一个数字的总时长：淡入 160ms → 停留 540ms → 淡出 300ms */
        private const val COUNTDOWN_DIGIT_TOTAL_MS = 1000L
        private const val COUNTDOWN_FADE_IN_MS = 160L
        private const val COUNTDOWN_FADE_OUT_MS = 300L

        /** GO 出现满 1 秒才开始计时（用户明确要求「再次出现 GO，1 秒后开始计时」） */
        private const val COUNTDOWN_GO_TOTAL_MS = 1000L
        private const val COUNTDOWN_OVERLAY_FADE_MS = 250L

        /** 数字进场时略微放大，避免生硬地「啪」一下出现 */
        private const val COUNTDOWN_SCALE_FROM = 0.82f
    }
}
