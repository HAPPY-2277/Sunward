package com.runclub.android.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.runclub.android.map.MapLayer
import com.runclub.android.map.provideMapLayer
import com.runclub.android.service.RunForegroundService
import com.runclub.android.session.RunSession
import com.runclub.android.tracking.LocationTracker
import com.runclub.android.tracking.RunState
import com.runclub.android.tracking.RunUiState
import com.runclub.android.tracking.TrackPoint
import com.runclub.android.util.Format
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

    /** 地图层：amap 变体是真高德底图，local 变体是空实现（保留手绘地图） */
    private lateinit var mapLayer: MapLayer
    private lateinit var amapHost: FrameLayout

    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                permOverlay.visibility = View.GONE
                startNewRun(RunSession.current().simulated)
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
        bindViews()
        bindActions()
        mapLayer.attach(amapHost, savedInstanceState)
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
        if (!tracker.hasPermission() && !RunSession.current().simulated) {
            permOverlay.visibility = View.VISIBLE
            requestPermissions()
            return
        }
        permOverlay.visibility = View.GONE
        startNewRun(RunSession.current().simulated)
    }

    private fun startNewRun(simulated: Boolean) {
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
        if (!isDebuggable) debugPanel.visibility = View.GONE
    }

    private fun bindActions() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnGrant).setOnClickListener { requestPermissions() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { openAppSettings() }
        findViewById<View>(R.id.btnPause).setOnClickListener { togglePause() }
        findViewById<View>(R.id.btnEnd).setOnClickListener {
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
        state.lastPoint?.let { mapLayer.centerOnWgs84(it.lat, it.lon) }

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
    }

    override fun onPause() {
        mapLayer.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapLayer.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stopDotPulse()
        mapLayer.onDestroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESTORE_ELAPSED = "restore_elapsed_ms"
        const val EXTRA_RESTORE_DISTANCE = "restore_distance_m"
        const val EXTRA_RESTORE_SIMULATED = "restore_simulated"
    }
}
