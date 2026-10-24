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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.runclub.android.R
import com.runclub.android.data.model.RunRecord
import com.runclub.android.data.repo.RunRepository
import com.runclub.android.ui.widget.TrackThumbView
import com.runclub.android.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录页（对应原型 records.html 的历史 Tab）。
 *
 * M6 范围：真实历史列表、距离筛选、展开详情、删除记录、月度回顾。
 * 报告 Tab 在 M7 接入。
 */
class RecordsActivity : AppCompatActivity() {

    private lateinit var repo: RunRepository
    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyView: View
    private lateinit var listView: RecyclerView

    /** 筛选阈值（公里），0 表示全部 */
    private var minKm = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)
        repo = RunRepository.get(this)

        emptyView = findViewById(R.id.emptyView)
        listView = findViewById(R.id.recycler)

        adapter = HistoryAdapter(
            onDelete = { record -> confirmDelete(record) },
            onExpand = { id, expanded -> toggleExpand(id, expanded) }
        )
        listView.layoutManager = LinearLayoutManager(this)
        listView.adapter = adapter

        bindActions()
        bindFilters()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindActions() {
        findViewById<View>(R.id.btnBackRecords).setOnClickListener { finish() }
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
        val buttons = listOf(
            R.id.filterAll to 0.0,
            R.id.filter5 to 5.0,
            R.id.filter10 to 10.0
        )
        buttons.forEach { (id, threshold) ->
            findViewById<View>(id).setOnClickListener {
                minKm = threshold
                updateFilterStyles()
                refresh()
            }
        }
        updateFilterStyles()
    }

    private fun updateFilterStyles() {
        val buttons = listOf(R.id.filterAll to 0.0, R.id.filter5 to 5.0, R.id.filter10 to 10.0)
        buttons.forEach { (id, threshold) ->
            val view = findViewById<TextView>(id)
            val active = minKm == threshold
            view.setBackgroundResource(
                if (active) R.drawable.bg_nav_active else R.drawable.bg_chip_glass
            )
            view.setTextColor(
                ContextCompatColor(if (active) R.color.bg_base else R.color.text_dim)
            )
        }
    }

    private fun ContextCompatColor(id: Int) = androidx.core.content.ContextCompat.getColor(this, id)

    private fun refresh() {
        val all = repo.all()
        val filtered = all.filter { it.distanceKm >= minKm }
        adapter.submit(filtered, repo)
        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        renderMonthlySummary(all)
    }

    /** 月度回顾卡：本月累计里程、次数、总时长、平均配速 */
    private fun renderMonthlySummary(all: List<RunRecord>) {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1)
        val inMonth = all.filter {
            val d = java.time.Instant.ofEpochMilli(it.startTimeMs).atZone(zone).toLocalDate()
            !d.isBefore(monthStart)
        }
        val km = inMonth.sumOf { it.distanceKm }
        val minutes = inMonth.sumOf { it.durationMs } / 60_000
        val paces = inMonth.mapNotNull { it.avgPaceSecPerKm }
        val avgPace = if (paces.isEmpty()) null else paces.average()

        findViewById<TextView>(R.id.tvMonthDistance).text = String.format(Locale.US, "%.1f", km)
        findViewById<TextView>(R.id.tvMonthRuns).text = inMonth.size.toString()
        findViewById<TextView>(R.id.tvMonthDuration).text =
            if (minutes < 1) {
                String.format(Locale.US, "%dS", inMonth.sumOf { it.durationMs } / 1000)
            } else {
                String.format(Locale.US, "%dH%02dM", minutes / 60, minutes % 60)
            }
        findViewById<TextView>(R.id.tvMonthPace).text = Format.pace(avgPace)
    }

    private fun toggleExpand(runId: Long, expanded: Boolean) {
        adapter.setExpanded(runId, expanded)
    }

    private fun confirmDelete(record: RunRecord) {
        AlertDialog.Builder(this)
            .setTitle(R.string.records_delete)
            .setMessage(R.string.records_delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.records_delete) { _, _ ->
                repo.delete(record.id)
                refresh()
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
    private var repo: RunRepository? = null
    private val expandedIds = mutableSetOf<Long>()

    fun submit(list: List<RunRecord>, repository: RunRepository) {
        items = list
        repo = repository
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
        val context = holder.itemView.context
        holder.title.text = "${Format.km(record.distanceM)} KM"
        holder.whenTxt.text = SimpleDateFormat("M'月'd'日' HH:mm", Locale.CHINA)
            .format(Date(record.startTimeMs))
        holder.distance.text = Format.km(record.distanceM)
        holder.duration.text = Format.duration(record.durationMs)
        holder.pace.text = Format.pace(record.avgPaceSecPerKm)
        holder.simulatedTag.visibility = if (record.simulated) View.VISIBLE else View.GONE

        val expanded = expandedIds.contains(record.id)
        holder.detail.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.thumb.setPoints(repo?.pointsOf(record.id).orEmpty())
        if (expanded) {
            holder.detailThumb.setPoints(repo?.pointsOf(record.id).orEmpty())
            renderSplits(holder, record)
        }

        holder.itemView.setOnClickListener {
            onExpand(record.id, !expandedIds.contains(record.id))
        }
        holder.btnDelete.setOnClickListener { onDelete(record) }
    }

    /** 展开后按每公里显示分段（由轨迹点重算，不入库） */
    private fun renderSplits(holder: VH, record: RunRecord) {
        val container = holder.detailSplits
        container.removeAllViews()
        val points = repo?.pointsOf(record.id).orEmpty()
        if (points.size < 2) return

        val splits = computeSplits(points, record.durationMs)
        val inflater = LayoutInflater.from(holder.itemView.context)
        splits.forEach { split ->
            val row = inflater.inflate(R.layout.item_split, container, false)
            row.findViewById<TextView>(R.id.tvSplitName).text = split.name
            row.findViewById<TextView>(R.id.tvSplitPace).text = Format.pace(split.pace)
            container.addView(row)
        }
    }

    private data class SplitRow(val name: String, val pace: Double?)

    private fun computeSplits(
        points: List<com.runclub.android.data.model.StoredTrackPoint>,
        totalMs: Long
    ): List<SplitRow> {
        val result = ArrayList<SplitRow>()
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
            val segment = kotlin.math.hypot(east, north)
            anchorDistance += segment
            if (anchorDistance >= 1000.0) {
                val pace = ((p.timeMs - anchorTime) / 1000.0).takeIf { it > 0 }
                result += SplitRow("第 ${result.size + 1} 公里", pace)
                anchorDistance = 0.0
                anchorTime = p.timeMs
            }
            prev = p
        }
        // 最后不足一公里的一段
        if (anchorDistance >= 30.0 && points.last().timeMs > anchorTime) {
            val pace = ((points.last().timeMs - anchorTime) / 1000.0).takeIf { it > 0 }
            result += SplitRow(
                String.format(Locale.US, "最后 %.2f 公里", anchorDistance / 1000.0),
                pace
            )
        }
        return result
    }
}
