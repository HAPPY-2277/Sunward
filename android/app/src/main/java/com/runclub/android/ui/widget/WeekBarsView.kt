package com.runclub.android.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 首页「本周节奏」的 7 根柱子。
 *
 * 复刻原型的视觉规格：柱宽 ≤26dp、圆角胶囊、底色分三档透明度
 * （有运动=white/20，中等=white/15，无运动=white/10），高度按当日里程占比分配。
 */
class WeekBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    /** 周一→周日 的每日里程（公里） */
    var dailyKm: List<Double> = List(7) { 0.0 }
        set(value) {
            field = value
            invalidate()
        }

    /** 柱子的最大高度（原型 66dp） */
    private val maxBarHeight = 66f * density
    private val minBarHeight = 12f * density
    private val barWidth = 26f * density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#717C71")
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dailyKm.size != 7) return

        val labelHeight = 22f * density
        val chartTop = 0f
        val chartBottom = height - labelHeight
        val chartHeight = chartBottom - chartTop

        val maxKm = dailyKm.maxOrNull() ?: 0.0
        // 有数据时按最大当天里程归一化；全为 0 时保持原型那种高低错落的静态高度
        val hasData = maxKm > 0.01

        val gap = 12f * density
        val totalBarWidth = 7 * barWidth
        val spaceWidth = (width - totalBarWidth).coerceAtLeast(0f) / 6f
        val step = barWidth + spaceWidth.coerceAtLeast(gap * 0.2f)

        for (i in 0 until 7) {
            val km = dailyKm[i]
            val left = (width - (7 * barWidth + 6 * spaceWidth)) / 2f + i * (barWidth + spaceWidth)
            val right = left + barWidth

            val barHeight: Float
            val alpha: Int
            if (hasData) {
                // 至少保留 12dp 的可见高度；按当天/最大当天 分配
                barHeight = minBarHeight +
                    ((maxKm - km).let { 0f }.toFloat()) +
                    (km / maxKm.toDouble()).toFloat() * (maxBarHeight - minBarHeight)
                alpha = when {
                    km <= 0.01 -> 0x1A   // white/10
                    km >= maxKm * 0.8 -> 0x33  // white/20
                    else -> 0x26   // white/15
                }
            } else {
                // 无数据：用原型的静态高度序列（28/48/36/66/46/12/18 dp）
                val preset = floatArrayOf(28f, 48f, 36f, 66f, 46f, 12f, 18f)
                barHeight = preset[i] * density
                alpha = if (i == 3) 0x33 else 0x1A
            }

            barPaint.color = Color.WHITE
            barPaint.alpha = alpha

            rect.set(left, chartBottom - barHeight, right, chartBottom)
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, barPaint)

            canvas.drawText(WEEK_LABELS[i], left + barWidth / 2f, height - 4f * density, labelPaint)
        }
    }

    private companion object {
        val WEEK_LABELS = arrayOf("一", "二", "三", "四", "五", "六", "日")
    }
}
