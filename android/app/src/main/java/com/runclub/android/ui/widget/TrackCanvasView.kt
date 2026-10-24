package com.runclub.android.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.runclub.android.tracking.TrackPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 把轨迹点画成一条线。
 *
 * 两种用途共用一个实现：
 * - [Mode.OVERLAY]：叠在实时跑步页的地图上（local 变体，无高德底图时）
 * - [Mode.RESULT]：画在成绩页的路线卡片里（起点/终点标记）
 *
 * 投影方式：以轨迹中心为原点的等距圆柱近似（跑步范围内的误差可忽略），
 * 然后等比缩放居中，四周留白，保证整条轨迹始终完整可见。
 */
class TrackCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { OVERLAY, RESULT }

    var mode: Mode = Mode.OVERLAY

    private var points: List<TrackPoint> = emptyList()

    private val density = resources.displayMetrics.density
    private val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        strokeWidth = 9f * density
        alpha = 235
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FF5C35")
        strokeWidth = 5.5f * density
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9FF43") }
    private val startRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#080B08") }
    private val endInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9FF43") }
    private val endResultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5C35") }

    /** 当前位置的脉冲圈（对应原型里 animate-pulse 的效果） */
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#D9FF43")
    }
    private var pulseProgress = 0f
    private var pulseAnimator: android.animation.ValueAnimator? = null

    fun startPulse() {
        if (pulseAnimator != null) return
        pulseAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener {
                pulseProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseProgress = 0f
        invalidate()
    }

    private val path = Path()

    fun setTrack(newPoints: List<TrackPoint>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) {
            drawStartOnly(canvas)
            return
        }

        val padding = 18f * density
        val usableW = max(1f, width - padding * 2)
        val usableH = max(1f, height - padding * 2)

        // 以轨迹中心为原点换到米制，再等比缩放到可用区域
        var minEast = Double.MAX_VALUE
        var maxEast = -Double.MAX_VALUE
        var minNorth = Double.MAX_VALUE
        var maxNorth = -Double.MAX_VALUE
        val centreLat = points.sumOf { it.lat } / points.size
        val cosLat = cos(Math.toRadians(centreLat)).coerceAtLeast(1e-6)

        fun east(p: TrackPoint) = (p.lon - points[0].lon) * 111_195.0 * cosLat
        fun north(p: TrackPoint) = (p.lat - points[0].lat) * 111_195.0

        for (p in points) {
            val x = east(p); val y = north(p)
            if (x < minEast) minEast = x
            if (x > maxEast) maxEast = x
            if (y < minNorth) minNorth = y
            if (y > maxNorth) maxNorth = y
        }

        val spanX = max(1e-6, maxEast - minEast)
        val spanY = max(1e-6, maxNorth - minNorth)
        val scale = min(usableW / spanX, usableH / spanY)
        val offsetX = padding + (usableW - spanX * scale) / 2.0
        val offsetY = padding + (usableH - spanY * scale) / 2.0

        // 屏幕 Y 轴向下，纬度向北为正，所以取负
        fun screenX(p: TrackPoint) = (offsetX + (east(p) - minEast) * scale).toFloat()
        fun screenY(p: TrackPoint) = (offsetY + (spanY - (north(p) - minNorth)) * scale).toFloat()

        path.reset()
        path.moveTo(screenX(points[0]), screenY(points[0]))
        for (i in 1 until points.size) {
            path.lineTo(screenX(points[i]), screenY(points[i]))
        }

        canvas.drawPath(path, casingPaint)
        canvas.drawPath(path, linePaint)

        // 起点
        val sx = screenX(points[0])
        val sy = screenY(points[0])
        canvas.drawCircle(sx, sy, 7f * density, startPaint)
        canvas.drawCircle(sx, sy, 7f * density, startRingPaint)

        // 终点 / 当前位置
        val last = points.last()
        val ex = screenX(last)
        val ey = screenY(last)
        if (mode == Mode.RESULT) {
            // 成绩页：终点用橙色实心 + 白环，与图例「终点」一致
            canvas.drawCircle(ex, ey, 7f * density, endResultPaint)
            canvas.drawCircle(ex, ey, 7f * density, startRingPaint)
        } else {
            // 实时页：当前位置用深色底 + 荧光绿芯，并叠一圈扩散脉冲
            if (pulseAnimator != null) {
                pulsePaint.alpha = ((1f - pulseProgress) * 150).toInt().coerceIn(0, 255)
                canvas.drawCircle(ex, ey, (8f + 14f * pulseProgress) * density, pulsePaint)
            }
            canvas.drawCircle(ex, ey, 8f * density, endPaint)
            canvas.drawCircle(ex, ey, 3.5f * density, endInnerPaint)
        }
    }

    private fun drawStartOnly(canvas: Canvas) {
        if (points.isEmpty()) return
        canvas.drawCircle(width / 2f, height / 2f, 7f * density, startPaint)
        canvas.drawCircle(width / 2f, height / 2f, 7f * density, startRingPaint)
    }
}
