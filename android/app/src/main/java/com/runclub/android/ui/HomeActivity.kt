package com.runclub.android.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.runclub.android.R
import com.runclub.android.data.model.RunRecord
import com.runclub.android.data.repo.RunRepository
import com.runclub.android.map.providePrivacyGate
import com.runclub.android.ui.widget.TrackThumbView
import com.runclub.android.ui.widget.WeekBarsView
import com.runclub.android.util.Format
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 运动首页（复刻原型 activity.html）。
 *
 * 页面上的所有数字都来自**真实记录**（[RunRepository]），没有记录时显示 0 与空态，
 * 不展示任何假数据。
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var repo: RunRepository
    private var goPingAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        repo = RunRepository.get(this)

        findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("M'月'd'日' · EEEE", Locale.CHINA).format(Date())

        bindActions()
        startGoPing()

        maybeAskPrivacy()

        // 调试用：--ez sim true 时直接以「内置模拟轨迹」进入实时页
        if (intent?.getBooleanExtra(LiveRunActivity.EXTRA_SIMULATED, false) == true) {
            startActivity(
                Intent(this, LiveRunActivity::class.java)
                    .putExtra(LiveRunActivity.EXTRA_SIMULATED, true)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------- 数据渲染 ----------

    private fun refresh() {
        val weekly = repo.weeklyStats()
        renderWeekly(weekly)
        renderLastRun(repo.latest())
    }

    private fun renderWeekly(weekly: com.runclub.android.data.model.WeeklyStats) {
        findViewById<TextView>(R.id.tvWeekDistance).text =
            String.format(Locale.US, "%.1f", weekly.totalKm)

        val totalMinutes = weekly.totalDurationMs / 60_000
        findViewById<TextView>(R.id.tvWeekDuration).text =
            if (totalMinutes < 1) {
                // 不足 1 分钟时显示秒数，否则会一直显示 0H00M
                String.format(Locale.US, "%dS", weekly.totalDurationMs / 1000)
            } else {
                String.format(Locale.US, "%dH%02dM", totalMinutes / 60, totalMinutes % 60)
            }

        findViewById<TextView>(R.id.tvWeekCalories).text =
            String.format(Locale.US, "%,d", weekly.totalCalories)

        findViewById<WeekBarsView>(R.id.weekBars).dailyKm = weekly.dailyKm

        // 顶部 chip：说「动了几天」，不是「还差多少公里」
        findViewById<TextView>(R.id.tvWeekProgressChip).text =
            getString(R.string.home_week_active_days_chip, weekly.activeDays)

        findViewById<TextView>(R.id.tvWeekRuns).text = getString(
            R.string.home_week_active_days_value, weekly.activeDays, weekly.targetDays
        )

        // 底部一行只做鼓励，不提欠账
        findViewById<TextView>(R.id.tvWeekRemaining).text =
            when {
                weekly.activeDays == 0 -> getString(R.string.home_week_encourage_start)
                weekly.activeDays >= weekly.targetDays -> getString(R.string.home_week_encourage_all)
                else -> getString(R.string.home_week_encourage_more)
            }

        val progress = findViewById<View>(R.id.weekProgress)
        progress.post {
            val trackWidth = (progress.parent as View).width
            progress.layoutParams = progress.layoutParams.apply {
                width = (trackWidth * weekly.progressPercent / 100.0).roundToInt()
            }
            progress.requestLayout()
        }
    }

    private fun renderLastRun(record: RunRecord?) {
        val thumb = findViewById<TrackThumbView>(R.id.lastRunThumb)
        val title = findViewById<TextView>(R.id.tvLastRunTitle)
        val whenTxt = findViewById<TextView>(R.id.tvLastRunWhen)
        val distance = findViewById<TextView>(R.id.tvLastRunDistance)
        val duration = findViewById<TextView>(R.id.tvLastRunDuration)
        val pace = findViewById<TextView>(R.id.tvLastRunPace)
        val pb = findViewById<TextView>(R.id.tvLastRunPb)

        if (record == null) {
            title.text = getString(R.string.home_no_run)
            whenTxt.text = getString(R.string.home_no_run_hint)
            distance.text = ""
            duration.text = ""
            pace.text = ""
            pb.visibility = View.GONE
            thumb.setPoints(emptyList())
            return
        }

        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(record.startTimeMs).atZone(zone)
        title.text = "${runTitleOf(start.hour)} · ${getString(R.string.home_run_type)}"
        whenTxt.text = SimpleDateFormat("M'月'd'日' HH:mm", Locale.CHINA).format(Date(record.startTimeMs))
        distance.text = "${Format.km(record.distanceM)} KM"
        duration.text = Format.duration(record.durationMs)
        pace.text = "${Format.pace(record.avgPaceSecPerKm)}/km"
        pb.visibility = if (repo.isPersonalBest(record)) View.VISIBLE else View.GONE
        thumb.setPoints(repo.pointsOf(record.id))
    }

    private fun runTitleOf(hour: Int): String = when (hour) {
        in 5..10 -> getString(R.string.home_run_title_morning)
        in 11..13 -> getString(R.string.home_run_title_noon)
        in 14..17 -> getString(R.string.home_run_title_afternoon)
        in 18..21 -> getString(R.string.home_run_title_evening)
        else -> getString(R.string.home_run_title_night)
    }

    // ---------- 交互 ----------

    private fun bindActions() {
        findViewById<View>(R.id.btnGo).setOnClickListener {
            startActivity(Intent(this, LiveRunActivity::class.java))
        }
        findViewById<View>(R.id.tabRecords).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        findViewById<View>(R.id.tabSport).setOnClickListener {
            // 已在首页
        }
        findViewById<View>(R.id.btnAllRecords).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        findViewById<View>(R.id.lastRunCard).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
    }

    /** GO 按钮外圈的 ping 动画（对应原型 animate-ping，2.4s 一轮） */
    private fun startGoPing() {
        val ping = findViewById<View>(R.id.goPing)
        // 用 scale 同时缩放 X/Y，让圆形保持圆形（只缩放一个轴会变成椭圆/方块）
        goPingAnimator = ObjectAnimator.ofFloat(ping, View.SCALE_X, 1f, 1.16f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                ping.scaleY = v
                ping.alpha = 0.55f + 0.45f * (2f - v) / 1.16f
            }
            start()
        }
    }

    /**
     * 高德 SDK 合规：必须先征得同意再初始化地图，否则地图白屏。
     * 隐私弹窗先于「恢复未完成跑步」弹窗，避免两个对话框叠在一起。
     */
    private fun maybeAskPrivacy() {
        val gate = providePrivacyGate()
        if (gate.isAgreed(this)) {
            maybePromptRestore()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_message)
            .setCancelable(false)
            .setPositiveButton(R.string.privacy_agree) { _, _ ->
                gate.agree(this)
                maybePromptRestore()
            }
            .setNegativeButton(R.string.privacy_decline) { _, _ ->
                maybePromptRestore()
            }
            .show()
    }

    /**
     * 上次跑步被强杀（进程被杀 / 崩溃）后，进入 App 时提示恢复。
     * 快照由前台服务每 5 秒写入一次。
     */
    private fun maybePromptRestore() {
        val store = com.runclub.android.data.InProgressStore(this)
        val snapshot = store.loadRestorable() ?: return

        val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(snapshot.savedAtMs))
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_title)
            .setMessage(
                getString(
                    R.string.restore_message,
                    timeText,
                    Format.km(snapshot.distanceM),
                    Format.duration(snapshot.elapsedMs)
                )
            )
            .setCancelable(false)
            .setPositiveButton(R.string.restore_yes) { _, _ ->
                startActivity(
                    Intent(this, LiveRunActivity::class.java).apply {
                        putExtra(LiveRunActivity.EXTRA_RESTORE_ELAPSED, snapshot.elapsedMs)
                        putExtra(LiveRunActivity.EXTRA_RESTORE_DISTANCE, snapshot.distanceM)
                        putExtra(LiveRunActivity.EXTRA_RESTORE_SIMULATED, snapshot.simulated)
                    }
                )
            }
            .setNegativeButton(R.string.restore_no) { _, _ ->
                store.clear()
            }
            .show()
    }

    override fun onDestroy() {
        goPingAnimator?.cancel()
        super.onDestroy()
    }
}
