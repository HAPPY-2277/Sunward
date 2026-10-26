package com.runclub.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.runclub.android.R
import com.runclub.android.map.MapLayer
import com.runclub.android.map.provideMapLayer
import com.runclub.android.session.RunSession
import com.runclub.android.tracking.RunTracker
import com.runclub.android.ui.widget.PaceCurveView
import com.runclub.android.ui.widget.TrackCanvasView
import com.runclub.android.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 成绩页（对应原型 run-result.html）。
 *
 * 数据来自 [RunSession] 里刚结束的那次跑步。
 * 原型中的「累计爬升 / 平均步频 / 平均心率 / 步数 / 连续运动」卡片本次没有真实数据源，
 * 按计划附录 C 的默认方案**隐藏**，不展示假数字。
 */
class ResultActivity : AppCompatActivity() {

    /** 路线卡的底图：amap 变体是真地图，local 变体是空实现（退回手绘底图） */
    private lateinit var mapLayer: MapLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        mapLayer = provideMapLayer(this)

        val state = RunSession.current()
        val splits = RunSession.splits()
        val track = RunSession.track()

        bindHeaderAndHero(state)
        bindRoute(track, savedInstanceState)
        bindCalories(state)
        bindSplits(splits, state)
        bindActions()
    }

    private fun bindHeaderAndHero(state: com.runclub.android.tracking.RunUiState) {
        findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("M'月'd'日' · HH:mm", Locale.CHINA).format(Date())

        val avgPace = Format.pace(state.avgPaceSecPerKm)
        findViewById<TextView>(R.id.tvDistance).text = Format.km(state.distanceM)
        findViewById<TextView>(R.id.tvTime).text = Format.duration(state.elapsedMs)
        findViewById<TextView>(R.id.tvPace).text = avgPace
        findViewById<TextView>(R.id.tvAvgPace).text = avgPace
        findViewById<TextView>(R.id.tvGreetingSub).text = getString(
            R.string.result_greeting_sub,
            Format.duration(state.elapsedMs),
            avgPace
        )
    }

    /**
     * 路线图：有真实底图就画在真地图上，否则退回手绘底图 + 归一化轨迹叠加。
     *
     * 两种画法不能同时出现：叠加层是按包围盒归一化的，和真实经纬度对不上，
     * 叠在一起会看到两条错位的轨迹。手势在成绩页一律关掉——路线卡在 ScrollView 里，
     * 地图抢走触摸会让整页滚不动。
     */
    private fun bindRoute(
        track: List<com.runclub.android.tracking.TrackPoint>,
        savedInstanceState: Bundle?
    ) {
        mapLayer.attach(findViewById<FrameLayout>(R.id.resultMapHost), savedInstanceState)
        mapLayer.setGesturesEnabled(false)

        val cityMap = findViewById<View>(R.id.resultCityMap)
        val trackView = findViewById<TrackCanvasView>(R.id.trackResult)
        val useBaseMap = mapLayer.isActive && track.size >= 2

        cityMap.visibility = if (useBaseMap) View.GONE else View.VISIBLE
        trackView.visibility = if (useBaseMap) View.GONE else View.VISIBLE

        if (useBaseMap) {
            // 跑步已经结束，起终点都要标出来；再把整条轨迹缩放进 220dp 的卡片里
            mapLayer.setTrack(track, endpoints = true)
            mapLayer.fitTrack(track)
        } else {
            trackView.mode = TrackCanvasView.Mode.RESULT
            trackView.setTrack(track)
        }

        findViewById<View>(R.id.tvNoTrack).visibility =
            if (track.size < 2) View.VISIBLE else View.GONE
    }

    private fun bindCalories(state: com.runclub.android.tracking.RunUiState) {
        findViewById<TextView>(R.id.tvCalories).text = state.calories.toString()
        findViewById<TextView>(R.id.tvCaloriesSub).text =
            getString(R.string.result_calories_sub)
    }

    private fun bindSplits(
        splits: List<RunTracker.Split>,
        state: com.runclub.android.tracking.RunUiState
    ) {
        val container = findViewById<LinearLayout>(R.id.splitContainer)
        container.removeAllViews()

        val paces = splits.mapNotNull { it.paceSecPerKm }
        val fastest = paces.minOrNull() ?: 0.0
        val slowest = paces.maxOrNull() ?: 0.0

        val inflater = LayoutInflater.from(this)
        for (split in splits) {
            val row = inflater.inflate(R.layout.item_split, container, false)
            val name = if (split.partial) {
                getString(R.string.result_split_last, String.format(Locale.US, "%.2f", split.distanceM / 1000.0))
            } else {
                getString(R.string.result_split_km, split.index)
            }
            row.findViewById<TextView>(R.id.tvSplitName).text = name
            row.findViewById<TextView>(R.id.tvSplitPace).text = Format.pace(split.paceSecPerKm)

            val bar = row.findViewById<View>(R.id.vSplitBar)
            bar.setBackgroundResource(
                if (split.partial) R.drawable.bg_bar_orange else R.drawable.bg_goal_fill
            )
            // 配速越快 → 条越长（60% ~ 100%）
            val pace = split.paceSecPerKm ?: slowest
            val ratio = if (slowest - fastest < 0.5) 1.0
            else 0.6 + 0.4 * (slowest - pace) / (slowest - fastest)
            bar.post {
                val trackWidth = (bar.parent as View).width
                bar.layoutParams = bar.layoutParams.apply {
                    width = (trackWidth * ratio.coerceIn(0.12, 1.0)).roundToInt()
                }
                bar.requestLayout()
            }
            container.addView(row)
        }

        if (splits.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.result_no_splits)
                setTextColor(getColor(R.color.text_dim))
                textSize = 10f
            }
            container.addView(tv)
        }

        findViewById<PaceCurveView>(R.id.paceCurve)
            .setSplits(paces, state.distanceKm)
        findViewById<TextView>(R.id.tvAiTip).text = buildTip(splits)
    }

    /** 规则生成的一句点评，不用 AI，完全可解释 */
    private fun buildTip(splits: List<RunTracker.Split>): String {
        if (splits.size < 2) {
            return getString(R.string.result_tip_short)
        }
        val fullSplits = splits.filter { !it.partial }.mapNotNull { it.paceSecPerKm }
        val allPaces = splits.mapNotNull { it.paceSecPerKm }
        if (allPaces.size < 2) return getString(R.string.result_tip_short)

        val average = allPaces.average()
        val builder = StringBuilder()

        if (fullSplits.size >= 2) {
            val spread = (fullSplits.max() - fullSplits.min()).roundToInt()
            if (spread <= 8) {
                builder.append(getString(R.string.result_tip_stable, spread))
            } else {
                builder.append(getString(R.string.result_tip_wave, spread))
            }
        }

        val lastPace = splits.last().paceSecPerKm
        if (lastPace != null && lastPace < average - 3) {
            builder.append(getString(R.string.result_tip_negative_split))
        }

        if (builder.isEmpty()) {
            builder.append(getString(R.string.result_tip_default, Format.pace(average)))
        }
        return builder.toString()
    }

    private fun bindActions() {
        val toHome = View.OnClickListener {
            startActivity(
                Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }
        findViewById<View>(R.id.btnHomeBack).setOnClickListener(toHome)
        findViewById<View>(R.id.btnBackHome).setOnClickListener(toHome)

        findViewById<View>(R.id.btnViewRecords).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
            // 与首页进入记录页用同一套切页动画，两个入口的手感要一致
            NavTransition.forward(this)
        }

        val toast = findViewById<View>(R.id.toast)
        findViewById<View>(R.id.btnShare).setOnClickListener {
            toast.visibility = View.VISIBLE
            toast.alpha = 1f
            lifecycleScope.launch {
                delay(2_500)
                toast.animate().alpha(0f).setDuration(300).withEndAction {
                    toast.visibility = View.GONE
                    toast.alpha = 1f
                }.start()
            }
        }
    }

    // MapView 要求 Activity 四个生命周期回调都转发过去，少一个就会白屏或泄漏 GL 上下文
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
        mapLayer.onDestroy()
        super.onDestroy()
    }
}
