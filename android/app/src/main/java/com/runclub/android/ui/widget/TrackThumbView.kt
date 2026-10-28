package com.runclub.android.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.runclub.android.data.model.StoredTrackPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 列表项里的 76dp 方形缩略轨迹图。
 *
 * 底图沿用原型 `activity.html` 里的那套 SVG（浅色纸面 + 白色道路 + 路线），
 * 路线部分用真实的轨迹点绘制。
 */
class TrackThumbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private var points: List<StoredTrackPoint> = emptyList()

    private val paperPaint = Paint().apply { color = Color.parseColor("#F0EEE3") }
    private val roadWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val roadGray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#DFDBCD")
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#B45309")
        strokeWidth = 4f * density
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EF9F27") }
    private val startRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#2C2C2A")
        strokeWidth = 2f * density
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2C2C2A") }
    private val endRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#EF9F27")
        strokeWidth = 2f * density
    }

    private val path = Path()

    fun setPoints(newPoints: List<StoredTrackPoint>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = width / 76f
        canvas.save()
        canvas.scale(s, s)

        canvas.drawRect(0f, 0f, 76f, 76f, paperPaint)

        // 原型的底图道路
        roadWhite.strokeWidth = 8f
        canvas.drawLine(-4f, 20f, 82f, 14f, roadWhite)
        roadGray.strokeWidth = 5f
        canvas.drawLine(-4f, 61f, 82f, 48f, roadGray)
        roadWhite.strokeWidth = 7f
        canvas.drawLine(18f, -4f, 25f, 82f, roadWhite)
        roadWhite.strokeWidth = 6f
        canvas.drawLine(56f, -4f, 48f, 82f, roadWhite)

        if (points.size >= 2) {
            // 真实轨迹：归一化到 76×76 内
            val centreLat = points.sumOf { it.lat } / points.size
            val cosLat = cos(Math.toRadians(centreLat)).coerceAtLeast(1e-6)
            fun east(p: StoredTrackPoint) = (p.lon - points[0].lon) * 111_195.0 * cosLat
            fun north(p: StoredTrackPoint) = (p.lat - points[0].lat) * 111_195.0

            var minE = Double.MAX_VALUE; var maxE = -Double.MAX_VALUE
            var minN = Double.MAX_VALUE; var maxN = -Double.MAX_VALUE
            points.forEach {
                val e = east(it); val n = north(it)
                minE = min(minE, e); maxE = max(maxE, e)
                minN = min(minN, n); maxN = max(maxN, n)
            }
            val spanE = max(1e-6, maxE - minE)
            val spanN = max(1e-6, maxN - minN)
            // 轨迹只占画布中间约 60%，给起终点标记留出空间（否则大圆会盖住整条轨迹）
            val scale = min(46.0 / spanE, 46.0 / spanN)
            val offX = 38.0 - (minE + maxE) / 2 * scale
            val offY = 38.0 + (minN + maxN) / 2 * scale

            fun sx(p: StoredTrackPoint) = (offX + east(p) * scale).toFloat()
            fun syOf(p: StoredTrackPoint) = (offY - north(p) * scale).toFloat()

            path.reset()
            path.moveTo(sx(points[0]), syOf(points[0]))
            for (i in 1 until points.size) path.lineTo(sx(points[i]), syOf(points[i]))
            canvas.drawPath(path, routePaint)

            // 起终点：半径按画布缩放，小图不至于糊成一团
            val markerRadius = if (width < 200) 3.4f else 5.5f
            canvas.drawCircle(sx(points[0]), syOf(points[0]), markerRadius, startPaint)
            canvas.drawCircle(sx(points[0]), syOf(points[0]), markerRadius, startRing)

            val last = points.last()
            canvas.drawCircle(sx(last), syOf(last), markerRadius, endPaint)
            canvas.drawCircle(sx(last), syOf(last), markerRadius, endRing)
        } else {
            // 没有轨迹时画一条示意路线（与原型一致）
            path.reset()
            path.moveTo(7f, 65f)
            path.cubicTo(18f, 51f, 19f, 35f, 31f, 32f)
            path.cubicTo(43f, 28f, 48f, 19f, 65f, 18f)
            canvas.drawPath(path, routePaint)
            canvas.drawCircle(7f, 65f, 4f, startPaint)
            canvas.drawCircle(7f, 65f, 4f, startRing)
            canvas.drawCircle(65f, 18f, 4f, endPaint)
            canvas.drawCircle(65f, 18f, 4f, endRing)
        }

        canvas.restore()
    }
}
