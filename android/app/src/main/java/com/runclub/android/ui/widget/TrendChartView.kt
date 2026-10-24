package com.runclub.android.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 报告页的「里程柱 + 配速线」双轴图，复刻原型 records.html 里 ECharts 的视觉规格：
 *
 * - 里程：荧光绿柱，宽 14dp（标签 > 8 个时 7dp），顶部圆角 [6,6,2,2]
 * - 配速：橙色折线，2dp、平滑、圆点 4dp 带 2dp 深色描边；**纵向反转**（配速越快越靠上）
 * - 左轴 3 段刻度 + 白色虚线网格；X 轴 9sp 灰字，标签 > 8 个时隔一个显示
 * - 入场动画 600ms（与原型的 animationDuration 一致）
 * - 点按柱子显示浮层（取代 ECharts tooltip）
 */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    var labels: List<String> = emptyList()
        set(value) { field = value; invalidate() }

    var mileageKm: List<Double> = emptyList()
        set(value) { field = value; invalidate() }

    var paceSecPerKm: List<Double?> = emptyList()
        set(value) { field = value; invalidate() }

    /** 数据变化后调用，重放入场动画 */
    fun animateIn() {
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private var progress = 1f
    private var animator: ValueAnimator? = null
    private var selectedIndex = -1

    // 绘图区
    private var plotLeft = 0f
    private var plotRight = 0f
    private var plotTop = 0f
    private var plotBottom = 0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9FF43") }
    private val barSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EAFF8A") }
    private val emptyBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 0x14
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FF5C35")
    }
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5C35") }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#111611")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.WHITE
        alpha = 0x0F
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#596459")
        textSize = 8f * density
        textAlign = Paint.Align.RIGHT
    }
    private val xText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6F7A6F")
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#D9FF43")
        alpha = 0x40
    }
    private val tipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0F0B") }
    private val tipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#D9FF43")
        alpha = 0x40
    }
    private val tipTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C978C")
        textSize = 9f * density
    }
    private val tipValue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DFFF4F")
        textSize = 10f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val tipPace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8A6A")
        textSize = 10f * density
    }

    private val barRect = RectF()
    private val linePath = Path()
    private val segmentPath = Path()
    private var pathMeasure: PathMeasure? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (labels.isEmpty() || labels.size != mileageKm.size) return

        plotLeft = 34f * density
        plotRight = width - 6f * density
        plotTop = 8f * density
        plotBottom = height - 22f * density
        if (plotRight <= plotLeft || plotBottom <= plotTop) return

        val maxKm = mileageKm.maxOrNull() ?: 0.0
        val hasMileage = maxKm > 0.001
        // 里程轴：至少给到 1 km 的刻度范围，避免全 0 时除零
        val axisMax = if (hasMileage) niceCeil(maxKm) else 1.0

        drawGrid(canvas, axisMax)
        drawXLabels(canvas)
        drawBars(canvas, axisMax, hasMileage)
        if (selectedIndex >= 0) drawCursor(canvas)
        drawPaceLine(canvas)
        if (!hasMileage) drawEmptyHint(canvas)
        if (selectedIndex >= 0) drawTooltip(canvas)
    }

    private fun drawGrid(canvas: Canvas, axisMax: Double) {
        for (i in 0..SPLITS) {
            val fraction = i.toFloat() / SPLITS
            val y = plotBottom - (plotBottom - plotTop) * fraction
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            val value = axisMax * fraction
            canvas.drawText(axisLabel(value, axisMax), plotLeft - 6f * density, y + 3f * density, axisText)
        }
    }

    /**
     * 轴刻度文案。小里程（不足 1 km）时必须用两位小数，
     * 否则 0.03 / 0.07 都会显示成「0.0」，看起来像 bug。
     */
    private fun axisLabel(value: Double, axisMax: Double): String {
        if (value < 1e-9) return "0"
        val text = when {
            axisMax >= 10 -> value.roundToInt().toString()
            axisMax >= 1 -> String.format(java.util.Locale.US, "%.1f", value)
            else -> String.format(java.util.Locale.US, "%.2f", value)
        }
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    private fun drawXLabels(canvas: Canvas) {
        val n = labels.size
        val step = (plotRight - plotLeft) / n
        // 标签多于 8 个时隔一个显示（与原型 interval: 1 一致）
        val skip = if (n > 8) 2 else 1
        for (i in 0 until n) {
            if (i % skip != 0) continue
            val x = plotLeft + step * (i + 0.5f)
            canvas.drawText(labels[i], x, height - 6f * density, xText)
        }
    }

    private fun drawBars(canvas: Canvas, axisMax: Double, hasMileage: Boolean) {
        val n = mileageKm.size
        val step = (plotRight - plotLeft) / n
        val barWidth = if (n > 8) 7f * density else min(14f * density, step * 0.5f)
        val radius = 6f * density

        for (i in 0 until n) {
            val km = mileageKm[i]
            val cx = plotLeft + step * (i + 0.5f)
            val usable = plotBottom - plotTop
            // 无数据时画一条 2dp 的「底座」，避免整块空白
            val rawHeight = if (hasMileage) {
                (km / axisMax).toFloat() * usable
            } else 0f
            val heightPx = max(2f * density, rawHeight) * progress

            barRect.set(
                cx - barWidth / 2f,
                plotBottom - heightPx,
                cx + barWidth / 2f,
                plotBottom
            )
            val paint = when {
                km <= 0.001 && hasMileage -> emptyBarPaint
                i == selectedIndex -> barSelectedPaint
                else -> barPaint
            }
            if (heightPx <= 2.5f * density && km <= 0.001) {
                // 零值：画一个小圆点表示「这天没跑」
                canvas.drawCircle(cx, plotBottom - 1f * density, 1.6f * density, emptyBarPaint)
            } else {
                canvas.drawRoundRect(barRect, radius, radius, paint)
                // 只保留顶部圆角：把底部两角补成直角
                barRect.top = barRect.bottom - min(radius, barRect.height())
                canvas.drawRect(barRect.left, barRect.top + radius / 2f, barRect.right, barRect.bottom, paint)
            }
        }
    }

    private fun drawPaceLine(canvas: Canvas) {
        val paces = paceSecPerKm
        if (paces.size != labels.size) return
        val valid = paces.filterNotNull()
        if (valid.size < 2) return

        // 配速轴：反向（配速小 = 位置高），留 12% 余量
        val minPace = valid.min()
        val maxPace = valid.max()
        val span = max(6.0, maxPace - minPace)
        val lo = minPace - span * 0.12
        val hi = maxPace + span * 0.12

        val n = labels.size
        val step = (plotRight - plotLeft) / n
        val usable = plotBottom - plotTop

        fun px(i: Int) = plotLeft + step * (i + 0.5f)

        /**
         * 配速轴是**反向**的（对应原型 ECharts 的 inverse: true）：
         * 配速数值越小（跑得越快）位置越高，数值越大（越慢）越靠下。
         */
        fun py(pace: Double): Float {
            val t = ((pace - lo) / (hi - lo)).coerceIn(0.0, 1.0)
            return (plotTop + usable * t).toFloat()
        }

        // 构建平滑折线（用中点贝塞尔，避免 Catmull-Rom 过冲）
        linePath.reset()
        val points = ArrayList<Pair<Float, Float>>(n)
        for (i in 0 until n) {
            val p = paces[i] ?: continue
            points += px(i) to py(p)
        }
        if (points.size >= 2) {
            linePath.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (prev.first + cur.first) / 2f
                linePath.cubicTo(midX, prev.second, midX, cur.second, cur.first, cur.second)
            }

            pathMeasure = PathMeasure(linePath, false)
            val pm = pathMeasure!!
            segmentPath.reset()
            pm.getSegment(0f, pm.length * progress.coerceIn(0f, 1f), segmentPath, true)
            canvas.drawPath(segmentPath, linePaint)

            // 圆点：按进度逐个出现
            val total = points.size
            val visible = (progress * total).roundToInt()
            for (i in 0 until min(visible, total)) {
                val (x, y) = points[i]
                canvas.drawCircle(x, y, 4f * density, dotFill)
                canvas.drawCircle(x, y, 4f * density, dotRing)
            }
        }
    }

    private fun drawCursor(canvas: Canvas) {
        val n = labels.size
        val step = (plotRight - plotLeft) / n
        val x = plotLeft + step * (selectedIndex + 0.5f)
        canvas.drawLine(x, plotTop, x, plotBottom, cursorPaint)
    }

    private fun drawEmptyHint(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6F7A6F")
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "这个周期还没有跑步数据",
            (plotLeft + plotRight) / 2f,
            (plotTop + plotBottom) / 2f,
            paint
        )
    }

    private fun drawTooltip(canvas: Canvas) {
        val i = selectedIndex
        if (i !in labels.indices) return
        val km = mileageKm.getOrNull(i) ?: 0.0
        val pace = paceSecPerKm.getOrNull(i)
        if (km <= 0.001 && pace == null) return

        val pad = 8f * density
        val lineH = 13f * density
        val lines = ArrayList<Triple<String, Paint, Float>>()
        lines += Triple(labels[i], tipTitle, 0f)
        if (km > 0.001) lines += Triple(String.format("%.2f km", km), tipValue, 0f)
        pace?.let { lines += Triple("每公里 ${paceText(it)}", tipPace, 0f) }

        val textWidth = lines.maxOf { it.second.measureText(it.first) }
        val boxW = textWidth + pad * 2
        val boxH = pad * 2 + lineH * lines.size

        val n = labels.size
        val step = (plotRight - plotLeft) / n
        var left = plotLeft + step * (i + 0.5f) - boxW / 2f
        left = left.coerceIn(0f, width - boxW)
        val top = plotTop

        val rect = RectF(left, top, left + boxW, top + boxH)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, tipBg)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, tipBorder)

        var y = top + pad + lineH * 0.75f
        lines.forEach { (text, paint, _) ->
            canvas.drawText(text, left + pad, y, paint)
            y += lineH
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val n = labels.size
                if (n == 0) return false
                val step = (plotRight - plotLeft) / n
                val idx = (((event.x - plotLeft) / step).toInt()).coerceIn(0, n - 1)
                if (idx != selectedIndex) {
                    selectedIndex = idx
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                postDelayed({
                    selectedIndex = -1
                    invalidate()
                }, 1600)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    /** 取一个「好看」的轴上限：能被 [SPLITS] 整除，且刻度值是整数/一位小数 */
    private fun niceCeil(value: Double): Double {
        if (value <= 0) return 1.0
        val exp = kotlin.math.floor(kotlin.math.log10(value))
        val base = Math.pow(10.0, exp)
        val normalized = value / base
        val nice = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 4.0 -> 4.0
            normalized <= 5.0 -> 5.0
            normalized <= 8.0 -> 8.0
            else -> 10.0
        }
        return nice * base
    }

    private companion object {
        /** 网格分几段：4 段能让 8/100/1000 这类上限正好落在整数刻度上 */
        const val SPLITS = 4
    }

    private fun paceText(secPerKm: Double): String {
        val m = (secPerKm / 60).toInt()
        val s = (secPerKm - m * 60).roundToInt()
        return String.format("%d'%02d\"", m, if (s == 60) 0 else s)
    }

    @Suppress("unused")
    private fun almostEqual(a: Float, b: Float) = abs(a - b) < 0.01f
}
