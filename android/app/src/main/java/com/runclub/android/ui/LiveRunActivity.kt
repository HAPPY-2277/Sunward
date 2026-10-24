package com.runclub.android.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.runclub.android.R
import com.runclub.android.tracking.LocationTracker
import com.runclub.android.tracking.SimulatedTrack
import com.runclub.android.tracking.TrackPoint
import com.runclub.android.tracking.TrackPointFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 实时跑步页（对应原型 live-run.html）。
 *
 * M2 范围：运行时权限、真实 GPS 采集、四步去漂移过滤、调试面板、模拟轨迹开关。
 * 计时状态机在 M3、轨迹绘制与配速/卡路里在 M4、结束弹窗在 M5。
 */
class LiveRunActivity : AppCompatActivity() {

    private lateinit var tracker: LocationTracker
    private val filter = TrackPointFilter()

    private var collectJob: Job? = null
    private var dotAnimator: ObjectAnimator? = null
    private var paused = false
    private var useSimulated = false

    /** 平滑后的瞬时速度（m/s），用于「当前配速」 */
    private var smoothedSpeed = 0.0

    private lateinit var tvStatus: TextView
    private lateinit var liveDot: View
    private lateinit var tvSignal: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvPace: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvTime: TextView
    private lateinit var goalFill: View
    private lateinit var ivPause: View
    private lateinit var tvPauseLabel: TextView

    private lateinit var permOverlay: View
    private lateinit var tvPermHint: TextView
    private lateinit var debugPanel: View
    private lateinit var tvDebugProvider: TextView
    private lateinit var tvDebugLatLon: TextView
    private lateinit var tvDebugCounters: TextView
    private lateinit var tvDebugBreakdown: TextView
    private lateinit var tvSimToggle: TextView

    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                permOverlay.visibility = View.GONE
                startTracking()
            } else {
                tvPermHint.visibility = View.VISIBLE
                permOverlay.visibility = View.VISIBLE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_run)
        tracker = LocationTracker(this)
        bindViews()
        bindActions()
        startDotPulse()

        if (tracker.hasPermission()) {
            permOverlay.visibility = View.GONE
            startTracking()
        } else {
            permOverlay.visibility = View.VISIBLE
            requestLocationPermission()
        }
    }

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

        // 调试面板只在 debug 构建里存在：release 包点 GPS 胶囊不会有任何反应
        if (!isDebuggable) debugPanel.visibility = View.GONE
    }

    private fun bindActions() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnEnd).setOnClickListener {
            startActivity(Intent(this, ResultActivity::class.java))
        }
        findViewById<View>(R.id.gpsChip).setOnClickListener {
            if (!isDebuggable) return@setOnClickListener
            debugPanel.visibility =
                if (debugPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.tvDebugClose).setOnClickListener {
            debugPanel.visibility = View.GONE
        }
        findViewById<View>(R.id.btnDebugReset).setOnClickListener {
            filter.reset()
            smoothedSpeed = 0.0
            renderMetrics()
            renderDebug(null)
        }
        tvSimToggle.setOnClickListener {
            useSimulated = !useSimulated
            renderSimToggle()
            restartCollection()
        }
        findViewById<View>(R.id.btnGrant).setOnClickListener { requestLocationPermission() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { openAppSettings() }
        findViewById<View>(R.id.btnPause).setOnClickListener { togglePause() }
        renderSimToggle()
    }

    // ---------- 权限 ----------

    private fun requestLocationPermission() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    // ---------- 采集 ----------

    private fun startTracking() {
        tvSimToggle.let { if (useSimulated) renderSimToggle() }
        launchCollector()
    }

    private fun restartCollection() {
        collectJob?.cancel()
        filter.reset()
        smoothedSpeed = 0.0
        renderMetrics()
        if (tracker.hasPermission()) launchCollector()
    }

    private fun launchCollector() {
        collectJob?.cancel()
        val flow: Flow<TrackPoint> =
            if (useSimulated) SimulatedTrack.flow()
            else tracker.locationFlow().let { locationFlow ->
                kotlinx.coroutines.flow.flow {
                    locationFlow.collect { emit(it.toTrackPoint()) }
                }
            }

        collectJob = lifecycleScope.launch {
            flow.collect { point ->
                if (filter.offer(point)) {
                    smoothSpeed(point)
                }
                renderMetrics()
                renderDebug(point)
                renderSignal(point)
            }
        }
    }

    private fun smoothSpeed(point: TrackPoint) {
        val raw = point.speedMps?.toDouble()
            ?: filter.lastAccepted?.let { prev ->
                val dt = (point.timeMs - prev.timeMs) / 1000.0
                if (dt > 0) com.runclub.android.tracking.GeoMath
                    .distanceMeters(prev.lat, prev.lon, point.lat, point.lon) / dt else null
            }
            ?: return
        if (raw <= 0.2) return
        smoothedSpeed = if (smoothedSpeed <= 0.0) raw else 0.4 * raw + 0.6 * smoothedSpeed
    }

    private fun togglePause() {
        if (!tracker.hasPermission() && !useSimulated) {
            requestLocationPermission()
            return
        }
        paused = !paused
        if (paused) {
            collectJob?.cancel()
            collectJob = null
            tvStatus.text = getString(R.string.live_status_paused)
            liveDot.setBackgroundResource(R.drawable.bg_dot_orange)
            dotAnimator?.cancel()
            liveDot.alpha = 1f
            (ivPause as android.widget.ImageView).setImageResource(R.drawable.ic_play)
            tvPauseLabel.text = getString(R.string.live_resume)
        } else {
            tvStatus.text = getString(R.string.live_status_running)
            liveDot.setBackgroundResource(R.drawable.bg_dot_lime)
            startDotPulse()
            (ivPause as android.widget.ImageView).setImageResource(R.drawable.ic_pause)
            tvPauseLabel.text = getString(R.string.live_pause)
            launchCollector()
        }
    }

    // ---------- 渲染 ----------

    private fun renderMetrics() {
        val km = filter.acceptedDistanceM / 1000.0
        tvDistance.text = String.format(Locale.US, "%.2f", km)

        val paceText = paceFromSpeed(smoothedSpeed)
        tvPace.text = paceText

        val goalPercent = ((km / 5.0) * 100).coerceIn(0.0, 100.0)
        goalFill.post {
            val trackWidth = (goalFill.parent as View).width
            goalFill.layoutParams = goalFill.layoutParams.apply {
                width = (trackWidth * goalPercent / 100.0).roundToInt()
            }
            goalFill.requestLayout()
        }
    }

    private fun paceFromSpeed(speedMps: Double): String {
        if (speedMps < 0.3) return getString(R.string.placeholder_pace)
        val secPerKm = (1000.0 / speedMps).coerceIn(150.0, 900.0)
        val minutes = (secPerKm / 60).toInt()
        val seconds = (secPerKm % 60).roundToInt()
        val fixedSeconds = if (seconds == 60) 0 else seconds
        val fixedMinutes = if (seconds == 60) minutes + 1 else minutes
        return String.format(Locale.US, "%d'%02d\"", fixedMinutes, fixedSeconds)
    }

    private fun renderSignal(point: TrackPoint) {
        val accuracy = point.accuracyM
        tvSignal.text = when {
            accuracy == null -> getString(R.string.live_signal_medium)
            accuracy <= 10f -> getString(R.string.live_signal_strong)
            accuracy <= 30f -> getString(R.string.live_signal_medium)
            else -> getString(R.string.live_signal_weak)
        }
    }

    private fun renderDebug(point: TrackPoint?) {
        if (!isDebuggable) return
        val stats = filter.stats

        tvDebugProvider.text = String.format(
            Locale.US,
            "provider  %s    acc  %s",
            point?.provider?.ifEmpty { "-" } ?: "-",
            point?.accuracyM?.let { String.format(Locale.US, "%.1fm", it) } ?: "-"
        )
        tvDebugLatLon.text = point?.latLonText() ?: "---.------, ---.------"
        tvDebugCounters.text = String.format(
            Locale.US,
            "pts %d · ok %d · drop %d · %.3f km · v %.2f m/s",
            stats.total, stats.accepted, stats.rejected,
            filter.acceptedDistanceM / 1000.0, smoothedSpeed
        )
        tvDebugBreakdown.text = getString(
            R.string.debug_breakdown,
            stats.rejectedAccuracy, stats.rejectedTimestamp,
            stats.rejectedSpeed, stats.rejectedInconsistent
        )
    }

    private fun renderSimToggle() {
        tvSimToggle.text = getString(
            if (useSimulated) R.string.debug_sim_on else R.string.debug_sim_off
        )
        tvSimToggle.setTextColor(
            ContextCompat.getColor(this, if (useSimulated) R.color.accent_lime else R.color.text_muted)
        )
    }

    private fun startDotPulse() {
        dotAnimator?.cancel()
        dotAnimator = ObjectAnimator.ofFloat(liveDot, "alpha", 1f, 0.25f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    // ---------- 生命周期 ----------

    override fun onDestroy() {
        collectJob?.cancel()
        dotAnimator?.cancel()
        super.onDestroy()
    }

    private fun Location.toTrackPoint(): TrackPoint = TrackPoint(
        timeMs = time,
        lat = latitude,
        lon = longitude,
        accuracyM = if (hasAccuracy()) accuracy else null,
        altitudeM = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed else null,
        provider = provider ?: "",
        simulated = false
    )
}
