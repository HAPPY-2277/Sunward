package com.runclub.android.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.runclub.android.R
import java.time.LocalDate

/**
 * 首页「本周节奏」的 7 根柱子（方案三·蜜琥珀重设计）。
 *
 * 规格来自 design/explore/component-styles.html §02：
 * 方柱直角、密排居中；柱高 = 当日运动时长（分钟），按本周已发生的最大值归一化；
 * 周一→周日依次取 week_day_1..7 七色；未来日与没动的日子留空，绝不画假柱子；
 * 今日整列衬底强调：星期字深琥珀、日期数字进琥珀实心圆、柱顶挂「X 分」chip。
 *
 * 旧版是三档白透明圆胶囊按公里归一化；浅色主题下白透明几乎不可见，
 * 且「动了几天」比里程更能回答「今天动了吗」，故口径改为时长。
 */
class WeekBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    /** 周一→周日 的每日运动时长（分钟），0 表示当天没动 */
    var dailyMinutes: List<Int> = List(7) { 0 }
        set(value) {
            field = value
            invalidate()
        }

    /** 今天在本周的序号（0=周一 … 6=周日），-1 表示不强调任何一天 */
    var todayIndex: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    /** 本周一的日期，用来画每日的日期数字；null 时只画星期 */
    var weekStart: LocalDate? = null
        set(value) {
            field = value
            invalidate()
        }

    // §02 紧凑版规格（首页卡内）
    private val barMaxHeight = 92f * density
    private val barMinHeight = 6f * density   // 有值最小可见高度
    private val barWidth = 20f * density
    private val barGap = 4f * density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_muted)
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
    }
    private val labelTodayPaint = Paint(labelPaint).apply {
        color = ContextCompat.getColor(context, R.color.accent_deep)
        isFakeBoldText = true
    }
    private val labelFaintPaint = Paint(labelPaint).apply {
        color = ContextCompat.getColor(context, R.color.week_faint)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.week_faint)
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
    }
    private val dateTodayPaint = Paint(datePaint).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        isFakeBoldText = true
    }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /** 周一…周日的七色，对应 week_day_1..7 */
    private val weekColors = intArrayOf(
        R.color.week_day_1, R.color.week_day_2, R.color.week_day_3, R.color.week_day_4,
        R.color.week_day_5, R.color.week_day_6, R.color.week_day_7
    ).map { ContextCompat.getColor(context, it) }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dailyMinutes.size != 7) return

        // 竖向分区：顶部 chip 区 / 柱区 / 星期 / 日期
        val chipZone = 18f * density
        val weekdayLine = 12f * density
        val dateLine = 14f * density
        val chartBottom = height - weekdayLine - dateLine
        val chartTop = chipZone

        // 七列密排居中：柱间距 barGap，列中心距固定
        val step = barWidth + barGap
        val totalWidth = 7 * barWidth + 6 * barGap
        val startX = (width - totalWidth) / 2f
        fun centerX(i: Int) = startX + i * step + barWidth / 2f

        val maxMin = dailyMinutes.maxOrNull() ?: 0

        // 今日整列衬底（画在最底层，覆盖 chip 区到日期行）
        if (todayIndex in 0..6) {
            backingPaint.color = ContextCompat.getColor(context, R.color.week_today_bg)
            val half = step / 2f
            rect.set(
                centerX(todayIndex) - half, chartTop - 6f * density,
                centerX(todayIndex) + half, height - 2f * density
            )
            canvas.drawRoundRect(rect, 10f * density, 10f * density, backingPaint)
        }

        val weekStart = this.weekStart
        for (i in 0 until 7) {
            val cx = centerX(i)
            val minutes = dailyMinutes[i]
            val isToday = i == todayIndex
            val isFuture = weekStart != null &&
                weekStart.plusDays(i.toLong()).isAfter(LocalDate.now())

            // 柱子：有值才画；方柱直角；按本周最大分钟归一化
            if (minutes > 0 && maxMin > 0) {
                val ratio = minutes.toFloat() / maxMin.toFloat()
                val barHeight = barMinHeight + ratio * (barMaxHeight - barMinHeight)
                barPaint.color = weekColors[i]
                rect.set(cx - barWidth / 2f, chartBottom - barHeight, cx + barWidth / 2f, chartBottom)
                canvas.drawRect(rect, barPaint)
            }

            // 今日柱顶时长 chip：「X 分」
            if (isToday && minutes > 0) {
                val label = context.getString(R.string.home_week_minutes, minutes)
                val tw = chipTextPaint.measureText(label)
                val padX = 6f * density
                val ch = 15f * density
                val top = chartBottom - 15f * density - (barMinHeight +
                    (minutes.toFloat() / maxMin.toFloat()) * (barMaxHeight - barMinHeight)) - ch - 3f * density
                rect.set(cx - tw / 2f - padX, top, cx + tw / 2f + padX, top + ch)
                chipPaint.color = ContextCompat.getColor(context, R.color.accent_lime)
                canvas.drawRoundRect(rect, ch / 2f, ch / 2f, chipPaint)
                canvas.drawText(label, cx, rect.centerY() + 3.5f * density, chipTextPaint)
            }

            // 星期行：一…日；今天深琥珀加粗，未来日淡化
            val paint = when {
                isToday -> labelTodayPaint
                isFuture -> labelFaintPaint
                else -> labelPaint
            }
            canvas.drawText(WEEK_LABELS[i], cx, chartBottom + weekdayLine - 1f * density, paint)

            // 日期行：有 weekStart 才画；今天进琥珀实心圆（墨字）
            if (weekStart != null) {
                val dayNum = weekStart.plusDays(i.toLong()).dayOfMonth.toString()
                val baseY = height - 3f * density
                if (isToday) {
                    val r = 8f * density
                    circlePaint.color = ContextCompat.getColor(context, R.color.accent_lime)
                    canvas.drawCircle(cx, baseY - 3.5f * density, r, circlePaint)
                    canvas.drawText(dayNum, cx, baseY, dateTodayPaint)
                } else {
                    canvas.drawText(dayNum, cx, baseY, datePaint)
                }
            }
        }
    }

    private companion object {
        val WEEK_LABELS = arrayOf("一", "二", "三", "四", "五", "六", "日")
    }
}
