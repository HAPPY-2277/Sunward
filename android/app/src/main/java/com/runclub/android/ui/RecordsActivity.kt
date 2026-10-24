package com.runclub.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.runclub.android.R
import com.runclub.android.data.model.CompositionSlice
import com.runclub.android.data.model.ReportData
import com.runclub.android.data.model.ReportPeriod
import com.runclub.android.data.model.RunRecord
import com.runclub.android.data.repo.RunRepository
import com.runclub.android.ui.widget.TrackThumbView
import com.runclub.android.ui.widget.TrendChartView
import com.runclub.android.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * 记录页（对应原型 records.html）：历史 + 报告 双 Tab。
 *
 * 性能约定：所有数据库查询都在 [ioExecutor] 上执行，结果回主线程渲染。
 * M6 时历史列表的轨迹查询是同步的，记录多了会掉帧（K5），这里一并修掉。
 */
class RecordsActivity : AppCompatActivity() {

    private lateinit var repo: RunRepository
    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyView: View
    private lateinit var listView: RecyclerView
    private lateinit var trendChart: TrendChartView

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 历史列表筛选阈值（公里），0 表示全部 */
    private var minKm = 0.0
    private var period = ReportPeriod.WEEK
    private var reportTabActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)
        repo = RunRepository.get(this)

        emptyView = findViewById(R.id.emptyView)
        listView = findViewById(R.id.recycler)
        trendChart = findViewById(R.id.trendChart)

        adapter = HistoryAdapter(
            onDelete = { record -> confirmDelete(record) },
            onExpand = { id, expanded -> adapter.setExpanded(id, expanded) }
        )
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        bindActions()
        bindFilters()
        bindPeriods()
        showTab(report = false)
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    // ---------------- 交互 ----------------

    private fun bindActions() {
        findViewById<View>(R.id.tabHistory).setOnClickListener { showTab(report = false) }
        findViewById<View>(R.id.tabReport).setOnClickListener { showTab(report = true) }
        findViewById<View>(R.id.btnToReport).setOnClickListener {
            showTab(report = true)
            period = ReportPeriod.MONTH
            renderPeriodStyles()
            refreshReport()
        }
        findViewById<View>(R.id.tabSport).setOnClickListener {
            startActivity(
                Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            finish()
        }
    }

    private fun bindFilters() {
        listOf(R.id.filterAll to 0.0, R.id.filter5 to 5.0, R.id.filter10 to 10.0).forEach { (id, threshold) ->
            findViewById<View>(id).setOnClickListener {
                minKm = threshold
                updateFilterStyles()
                refreshHistory()
            }
        }
        updateFilterStyles()
    }

    private fun bindPeriods() {
        findViewById<View>(R.id.periodWeek).setOnClickListener { selectPeriod(ReportPeriod.WEEK) }
        findViewById<View>(R.id.periodMonth).setOnClickListener { selectPeriod(ReportPeriod.MONTH) }
        findViewById<View>(R.id.periodYear).setOnClickListener { selectPeriod(ReportPeriod.YEAR) }
    }

    private fun selectPeriod(target: ReportPeriod) {
        if (period == target) return
        period = target
        renderPeriodStyles()
        refreshReport()
    }

    private fun showTab(report: Boolean) {
        reportTabActive = report
        findViewById<View>(R.id.scrollHistory).visibility = if (report) View.GONE else View.VISIBLE
        findViewById<View>(R.id.scrollReport).visibility = if (report) View.VISIBLE else View.GONE

        // 选中态：背景 + 文字 + 图标 tint
        findViewById<View>(R.id.tabHistory).setBackgroundResource(
            if (report) 0 else R.drawable.bg_nav_active
        )
        findViewById<View>(R.id.tabReport).setBackgroundResource(
            if (report) R.drawable.bg_nav_active else 0
        )
        findViewById<TextView>(R.id.tvTabHistory).setTextColor(
            color(if (report) R.color.text_tab_inactive else R.color.bg_base)
        )
        findViewById<TextView>(R.id.tvTabReport).setTextColor(
            color(if (report) R.color.bg_base else R.color.text_tab_inactive)
        )
        findViewById<android.widget.ImageView>(R.id.ivTabHistory).setColorFilter(
            color(if (report) R.color.text_tab_inactive else R.color.bg_base)
        )
        findViewById<android.widget.ImageView>(R.id.ivTabReport).setColorFilter(
            color(if (report) R.color.bg_base else R.color.text_tab_inactive)
        )

        if (report) {
            refreshReport()
        } else {
            refreshHistory()
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun updateFilterStyles() {
        listOf(R.id.filterAll to 0.0, R.id.filter5 to 5.0, R.id.filter10 to 10.0).forEach { (id, threshold) ->
            val view = findViewById<TextView>(id)
            val active = minKm == threshold
            view.setBackgroundResource(if (active) R.drawable.bg_nav_active else R.drawable.bg_chip_glass)
            view.setTextColor(color(if (active) R.color.bg_base else R.color.text_dim))
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        }
    }

    private fun renderPeriodStyles() {
        listOf(
            R.id.periodWeek to ReportPeriod.WEEK,
            R.id.periodMonth to ReportPeriod.MONTH,
            R.id.periodYear to ReportPeriod.YEAR
        ).forEach { (id, p) ->
            val view = findViewById<TextView>(id)
            val active = period == p
            view.setBackgroundResource(
                if (active) R.drawable.bg_period_active else R.drawable.bg_period_inactive
            )
            view.setTextColor(color(if (active) R.color.bg_base else R.color.text_period_inactive))
        }
    }

    // ---------------- 数据 ----------------

    private fun refreshAll() {
        refreshHistory()
        if (reportTabActive) refreshReport()
        refreshStreak()
    }

    private fun refreshStreak() {
        ioExecutor.execute {
            // 累计运动天数（不归零）——逐日不用「连续」，避免漏一天就有负罪感
            val total = repo.activeDaysTotal()
            mainHandler.post {
                findViewById<TextView>(R.id.tvStreak).text = total.toString()
            }
        }
    }

    private fun refreshHistory() {
        ioExecutor.execute {
            val all = repo.all()
            val filtered = all.filter { it.distanceKm >= minKm }
            // 预取轨迹，避免滚动时逐条查库（K5）
            val thumbs = filtered.associate { it.id to repo.pointsOf(it.id) }
            val month = monthSummary(all)
            mainHandler.post {
                adapter.submit(filtered, thumbs)
                emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                listView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                renderMonthCard(month)
            }
        }
    }

    private data class MonthSummary(
        val km: Double,
        val runs: Int,
        val durationMs: Long,
        val avgPace: Double?,
        /** 本月动过的天数 */
        val activeDays: Int,
        /** 本月共有多少天 */
        val totalDays: Int
    )

    private fun monthSummary(all: List<RunRecord>): MonthSummary {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1)
        val inMonth = all.filter {
            val d = java.time.Instant.ofEpochMilli(it.startTimeMs).atZone(zone).toLocalDate()
            !d.isBefore(monthStart)
        }
        val km = inMonth.sumOf { it.distanceKm }
        val ms = inMonth.sumOf { it.durationMs }
        val pace = if (km * 1000.0 > 50 && ms > 0) ms / 1000.0 / km else null
        val activeDays = inMonth
            .map { java.time.Instant.ofEpochMilli(it.startTimeMs).atZone(zone).toLocalDate() }
            .toSet().size
        return MonthSummary(km, inMonth.size, ms, pace, activeDays, today.lengthOfMonth())
    }

    private fun renderMonthCard(s: MonthSummary) {
        val minutes = s.durationMs / 60_000
        // 大数字是「动过几天」——逐日的主指标
        findViewById<TextView>(R.id.tvMonthActiveDays).text = s.activeDays.toString()
        // 右上角放累计距离与天数进度
        findViewById<TextView>(R.id.tvMonthGoal).text =
            getString(R.string.records_month_km_value, s.km)
        val percent = if (s.totalDays <= 0) 0
        else ((s.activeDays * 100.0) / s.totalDays).roundToInt().coerceIn(0, 100)
        findViewById<TextView>(R.id.tvMonthProgress).text = "$percent%"
        // 底部三栏
        findViewById<TextView>(R.id.tvMonthRuns).text = s.runs.toString()
        findViewById<TextView>(R.id.tvMonthDuration).text =
            if (minutes < 1) String.format(Locale.US, "%dS", s.durationMs / 1000)
            else String.format(Locale.US, "%dH%02dM", minutes / 60, minutes % 60)
        findViewById<TextView>(R.id.tvMonthPace).text = Format.pace(s.avgPace)
    }

    private fun refreshReport() {
        val requested = period
        ioExecutor.execute {
            val data = repo.report(requested)
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                if (requested == period) renderReport(data)
            }
        }
    }

    private fun renderReport(data: ReportData) {
        findViewById<TextView>(R.id.tvReportLabel).text = data.label
        findViewById<TextView>(R.id.tvReportRange).text = data.rangeText
        // 主数字：动的天数（逐日的核心指标），距离作为副信息
        findViewById<TextView>(R.id.tvReportDistance).text = data.activeDays.toString()
        findViewById<TextView>(R.id.tvReportDistanceUnit).text = getString(R.string.unit_days)
        findViewById<TextView>(R.id.tvReportCount).text = data.runCount.toString()
        findViewById<TextView>(R.id.tvReportDuration).text = durationText(data.durationMs)
        findViewById<TextView>(R.id.tvReportPace).text = Format.pace(data.avgPaceSecPerKm)

        // 「较上期」改成比「动的天数」，而不是比里程
        val deltaView = findViewById<TextView>(R.id.tvReportDelta)
        val delta = data.deltaPercent
        if (delta == null) {
            deltaView.visibility = View.GONE
        } else {
            deltaView.visibility = View.VISIBLE
            deltaView.text = when {
                delta > 0 -> getString(R.string.records_delta_up, delta)
                delta < 0 -> getString(R.string.records_delta_down, -delta)
                else -> getString(R.string.records_delta_flat)
            }
        }

        findViewById<TextView>(R.id.tvReportProgressText).text = data.progressText
        findViewById<TextView>(R.id.tvReportProgressValue).text = "${data.progressPercent}%"
        findViewById<TextView>(R.id.tvChartTitle).text = data.chartTitle
        findViewById<TextView>(R.id.tvChartSubtitle).text = data.chartSubtitle
        findViewById<TextView>(R.id.tvInsight).text = data.insight

        // 进度条宽度
        val bar = findViewById<View>(R.id.reportProgressBar)
        bar.post {
            val track = (bar.parent as View).width
            bar.layoutParams = bar.layoutParams.apply {
                width = (track * data.progressPercent / 100.0).roundToInt()
            }
            bar.requestLayout()
        }

        // 图表
        trendChart.labels = data.labels
        trendChart.mileageKm = data.mileageKm
        trendChart.paceSecPerKm = data.paceSecPerKm
        trendChart.animateIn()

        // 训练构成
        renderComposition(data.composition)
    }

    private fun renderComposition(slices: List<CompositionSlice>) {
        val container = findViewById<LinearLayout>(R.id.compositionBar)
        container.removeAllViews()
        val present = slices.filter { it.percent > 0 }
        if (present.isEmpty()) {
            val filler = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            filler.setBackgroundColor(color(R.color.white_06))
            container.addView(filler)
        } else {
            present.forEach { slice ->
                val segment = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, slice.percent.toFloat())
                    setBackgroundColor(android.graphics.Color.parseColor(slice.colorHex))
                }
                container.addView(segment)
            }
        }
        val byName = slices.associateBy { it.name }
        findViewById<TextView>(R.id.tvEasyPercent).text = "${byName["轻松跑"]?.percent ?: 0}%"
        findViewById<TextView>(R.id.tvTempoPercent).text = "${byName["节奏跑"]?.percent ?: 0}%"
        findViewById<TextView>(R.id.tvLongPercent).text = "${byName["长距离"]?.percent ?: 0}%"
    }

    private fun durationText(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes < 1) String.format(Locale.US, "%dS", ms / 1000)
        else String.format(Locale.US, "%dH%02dM", minutes / 60, minutes % 60)
    }

    private fun confirmDelete(record: RunRecord) {
        AlertDialog.Builder(this)
            .setTitle(R.string.records_delete)
            .setMessage(R.string.records_delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.records_delete) { _, _ ->
                ioExecutor.execute {
                    repo.delete(record.id)
                    mainHandler.post { refreshAll() }
                }
            }
            .show()
    }
}

/** 历史列表适配器 */
private class HistoryAdapter(
    private val onDelete: (RunRecord) -> Unit,
    private val onExpand: (Long, Boolean) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private var items: List<RunRecord> = emptyList()
    private var thumbs: Map<Long, List<com.runclub.android.data.model.StoredTrackPoint>> = emptyMap()
    private val expandedIds = mutableSetOf<Long>()

    fun submit(list: List<RunRecord>, thumbPoints: Map<Long, List<com.runclub.android.data.model.StoredTrackPoint>>) {
        items = list
        thumbs = thumbPoints
        expandedIds.retainAll(list.map { it.id }.toSet())
        notifyDataSetChanged()
    }

    fun setExpanded(runId: Long, expanded: Boolean) {
        if (expanded) expandedIds.add(runId) else expandedIds.remove(runId)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: TrackThumbView = view.findViewById(R.id.thumb)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val whenTxt: TextView = view.findViewById(R.id.tvWhen)
        val distance: TextView = view.findViewById(R.id.tvDistance)
        val duration: TextView = view.findViewById(R.id.tvDuration)
        val pace: TextView = view.findViewById(R.id.tvPace)
        val detail: LinearLayout = view.findViewById(R.id.detailContainer)
        val detailThumb: TrackThumbView = view.findViewById(R.id.detailThumb)
        val detailSplits: LinearLayout = view.findViewById(R.id.detailSplits)
        val btnDelete: View = view.findViewById(R.id.btnDelete)
        val simulatedTag: TextView = view.findViewById(R.id.tvSimulated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run_history, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val record = items[position]
        holder.title.text = "${Format.km(record.distanceM)} KM"
        holder.whenTxt.text = SimpleDateFormat("M'月'd'日' HH:mm", Locale.CHINA)
            .format(Date(record.startTimeMs))
        holder.distance.text = Format.km(record.distanceM)
        holder.duration.text = Format.duration(record.durationMs)
        holder.pace.text = Format.pace(record.avgPaceSecPerKm)
        holder.simulatedTag.visibility = if (record.simulated) View.VISIBLE else View.GONE

        val points = thumbs[record.id].orEmpty()
        val expanded = expandedIds.contains(record.id)
        holder.detail.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.thumb.setPoints(points)
        if (expanded) {
            holder.detailThumb.setPoints(points)
            renderSplits(holder, record, points)
        }

        holder.itemView.setOnClickListener {
            onExpand(record.id, !expandedIds.contains(record.id))
        }
        holder.btnDelete.setOnClickListener { onDelete(record) }
    }

    /** 展开后按每公里显示分段（由轨迹点重算，不入库） */
    private fun renderSplits(
        holder: VH,
        record: RunRecord,
        points: List<com.runclub.android.data.model.StoredTrackPoint>
    ) {
        val container = holder.detailSplits
        container.removeAllViews()
        if (points.size < 2) return

        val inflater = LayoutInflater.from(holder.itemView.context)
        computeSplits(points).forEach { split ->
            val row = inflater.inflate(R.layout.item_split, container, false)
            row.findViewById<TextView>(R.id.tvSplitName).text = split.first
            row.findViewById<TextView>(R.id.tvSplitPace).text = Format.pace(split.second)
            container.addView(row)
        }
    }

    private fun computeSplits(
        points: List<com.runclub.android.data.model.StoredTrackPoint>
    ): List<Pair<String, Double?>> {
        val result = ArrayList<Pair<String, Double?>>()
        var anchorDistance = 0.0
        var anchorTime = points.first().timeMs
        var prev = points.first()
        val cosLat = kotlin.math.cos(
            Math.toRadians(points.sumOf { it.lat } / points.size)
        ).coerceAtLeast(1e-6)

        for (i in 1 until points.size) {
            val p = points[i]
            val north = (p.lat - prev.lat) * 111_195.0
            val east = (p.lon - prev.lon) * 111_195.0 * cosLat
            anchorDistance += kotlin.math.hypot(east, north)
            if (anchorDistance >= 1000.0) {
                val pace = ((p.timeMs - anchorTime) / 1000.0).takeIf { it > 0 }
                result += "第 ${result.size + 1} 公里" to pace
                anchorDistance = 0.0
                anchorTime = p.timeMs
            }
            prev = p
        }
        if (anchorDistance >= 30.0 && points.last().timeMs > anchorTime) {
            val pace = ((points.last().timeMs - anchorTime) / 1000.0).takeIf { it > 0 }
            result += String.format(Locale.US, "最后 %.2f 公里", anchorDistance / 1000.0) to pace
        }
        return result
    }
}
