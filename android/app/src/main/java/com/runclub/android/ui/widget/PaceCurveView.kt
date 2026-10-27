package com.runclub.android.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * 成绩页的配速曲线：横轴是距离、纵轴是配速（**配速越快画得越高**）。
 * 数据来自每公里分段，点太少时只画点不画线。
 */
class PaceCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    /** 每段的配速（秒/公里） */
    private var paces: List<Double> = emptyList()
    private var totalKm: Double = 0.0

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#142C2C2A")
        strokeWidth = 1f * density
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#8A5A00")
        strokeWidth = 2.5f * density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A5A00") }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6E6A61")
        textSize = 7f * density
    }

    private val path = Path()

    fun setSplits(pacesSecPerKm: List<Double>, totalKm: Double) {
        paces = pacesSecPerKm
        this.totalKm = totalKm
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 8f * density
        val right = width - 8f * density
        val top = 10f * density
        val bottom = height - 16f * density

        // 三条水平网格线
        for (i in 0..2) {
            val y = top + (bottom - top) * i / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (paces.isEmpty()) {
            canvas.drawText("--", left, bottom, labelPaint)
            return
        }

        val fast = paces.min()
        val slow = paces.max()
        val span = (slow - fast).coerceAtLeast(1.0)

        fun x(index: Int): Float =
            if (paces.size == 1) (left + right) / 2f
            else left + (right - left) * index / (paces.size - 1).toFloat()

        // 配速越慢 → y 越靠下
        fun y(index: Int): Float {
            val t = ((paces[index] - fast) / span).toFloat()
            return top + (bottom - top) * t
        }

        path.reset()
        for (i in paces.indices) {
            if (i == 0) path.moveTo(x(i), y(i)) else path.lineTo(x(i), y(i))
        }
        if (paces.size > 1) canvas.drawPath(path, linePaint)

        for (i in paces.indices) {
            canvas.drawCircle(x(i), y(i), 2.6f * density, dotPaint)
        }

        // 横轴首尾距离标注
        canvas.drawText("0 KM", left, height - 4f * density, labelPaint)
        if (totalKm > 0) {
            val endText = String.format(java.util.Locale.US, "%.1f KM", totalKm)
            canvas.drawText(endText, right - labelPaint.measureText(endText), height - 4f * density, labelPaint)
        }
    }
}
