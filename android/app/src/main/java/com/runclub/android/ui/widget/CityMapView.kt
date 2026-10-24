package com.runclub.android.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * 原型 `live-run.html` 里那张手绘城市地图的自绘版本。
 *
 * 做法：把原型 SVG 的 path / rect / text 数据原样搬过来，用 [SvgPath] 解析成 [Path] 后绘制，
 * 因此形状、配色与原型一致，而不是另画一套近似的图。
 *
 * 坐标系统一使用原型的 393×660 设计尺寸，绘制时按 `xMidYMid slice`（居中裁切）换算，
 * 与原型 `preserveAspectRatio` 的行为相同。
 */
class CityMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private companion object {
        const val DESIGN_W = 393f
        const val DESIGN_H = 660f

        const val COLOR_BG = "#E9EEE4"
        const val COLOR_DOT = "#C7D0C3"
        const val COLOR_RIVER = "#B9DAE3"
        const val COLOR_PARK = "#C9E4B4"
        const val COLOR_BLOCK_FILL = "#F7F8F3"
        const val COLOR_BLOCK_STROKE = "#D2DACD"
        const val COLOR_ROAD_OUTER = "#D0D8CB"
        const val COLOR_ROAD_INNER = "#FCFCF8"
        const val COLOR_ROAD_MINOR = "#E4E9DF"

        const val DOT_STEP = 22f
    }

    private val bgPaint = Paint().apply { color = Color.parseColor(COLOR_BG) }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLOR_DOT) }
    private val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLOR_RIVER) }
    private val parkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(COLOR_PARK) }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(COLOR_BLOCK_FILL)
        style = Paint.Style.FILL
    }
    private val blockStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(COLOR_BLOCK_STROKE)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val roadOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(COLOR_ROAD_OUTER)
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val roadInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(COLOR_ROAD_INNER)
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val roadMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(COLOR_ROAD_MINOR)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    // ---- 原型 SVG 数据 ----

    private val riverPath = SvgPath.parse(
        "M-30 0 H428 V46 C350 84 308 33 245 55 C174 79 108 42 52 72 C24 87 -6 83 -30 70 Z"
    )

    private val parkPaths = listOf(
        SvgPath.parse(
            "M-20 229 C63 211 103 244 158 226 C220 206 259 171 330 190 C365 199 393 188 414 172 " +
                "V215 C359 243 308 222 260 245 C196 275 127 248 50 268 C19 276 -3 270 -20 261 Z"
        ),
        SvgPath.parse(
            "M281 352 C320 330 368 341 408 366 V431 C350 412 313 425 276 397 Z"
        )
    )

    private val blocks = listOf(
        floatArrayOf(16f, 98f, 58f, 50f), floatArrayOf(87f, 92f, 70f, 65f),
        floatArrayOf(169f, 98f, 64f, 43f), floatArrayOf(246f, 86f, 65f, 55f),
        floatArrayOf(321f, 88f, 62f, 60f), floatArrayOf(20f, 292f, 53f, 70f),
        floatArrayOf(86f, 287f, 65f, 57f), floatArrayOf(166f, 279f, 54f, 63f),
        floatArrayOf(233f, 279f, 73f, 46f), floatArrayOf(322f, 287f, 57f, 54f),
        floatArrayOf(22f, 444f, 57f, 52f), floatArrayOf(91f, 437f, 49f, 68f),
        floatArrayOf(156f, 442f, 74f, 55f), floatArrayOf(245f, 443f, 52f, 67f),
        floatArrayOf(312f, 433f, 67f, 54f), floatArrayOf(22f, 571f, 54f, 62f),
        floatArrayOf(87f, 564f, 54f, 47f), floatArrayOf(156f, 565f, 71f, 62f),
        floatArrayOf(240f, 572f, 58f, 48f)
    )

    private val majorRoads = listOf(
        "M-20 178 C92 186 177 173 416 160",
        "M-15 376 C106 354 244 362 413 345",
        "M-10 538 C104 527 264 542 410 515",
        "M54 -8 C75 146 92 281 68 669",
        "M302 -10 C285 149 306 313 289 671",
        "M-15 630 C104 545 164 454 245 338 C301 257 353 213 411 184"
    ).map { SvgPath.parse(it) }

    private val minorRoads = listOf(
        "M-10 248 C105 232 228 248 407 225",
        "M-10 470 C126 460 286 463 404 453",
        "M180 -8 C171 132 189 294 168 668",
        "M378 -5 C371 160 393 337 376 666"
    ).map { SvgPath.parse(it) }

    /** (x, y, 文本, 旋转角度, 颜色) */
    private val labels = listOf(
        Label(174f, 172f, "滨江大道", -1f, "#7D897A"),
        Label(301f, 337f, "公园路", -1f, "#7D897A"),
        Label(34f, 527f, "西岸绿道", -2f, "#7D897A"),
        Label(69f, 98f, "RUN ST.", 84f, "#9CA79A"),
        Label(246f, 255f, "河畔公园", 0f, "#668259"),
        Label(333f, 235f, "城市体育场", 74f, "#668259"),
        Label(135f, 32f, "城 市 河", 0f, "#7193A1")
    )

    private val blockRect = RectF()
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 等价于 SVG 的 preserveAspectRatio="xMidYMid slice"
        scale = maxOf(w / DESIGN_W, h / DESIGN_H)
        offsetX = (w - DESIGN_W * scale) / 2f
        offsetY = (h - DESIGN_H * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // 底色
        canvas.drawRect(-DESIGN_W, -DESIGN_H, DESIGN_W * 2, DESIGN_H * 2, bgPaint)

        // 点阵纹理
        var y = -DOT_STEP
        while (y < DESIGN_H + DOT_STEP) {
            var x = -DOT_STEP
            while (x < DESIGN_W + DOT_STEP) {
                canvas.drawCircle(x, y, 0.8f, dotPaint)
                x += DOT_STEP
            }
            y += DOT_STEP
        }

        // 河与绿地
        canvas.drawPath(riverPath, riverPaint)
        parkPaths.forEach { canvas.drawPath(it, parkPaint) }

        // 街区
        for (b in blocks) {
            blockRect.set(b[0], b[1], b[0] + b[2], b[1] + b[3])
            canvas.drawRoundRect(blockRect, 5.5f, 5.5f, blockPaint)
            canvas.drawRoundRect(blockRect, 5.5f, 5.5f, blockStrokePaint)
        }

        // 道路（三层，与原型一致：外描边 → 内芯 → 次要道路）
        majorRoads.forEach { canvas.drawPath(it, roadOuterPaint) }
        majorRoads.forEach { canvas.drawPath(it, roadInnerPaint) }
        minorRoads.forEach { canvas.drawPath(it, roadMinorPaint) }

        // 路名 / 地名
        for (label in labels) {
            labelPaint.color = Color.parseColor(label.color)
            if (label.rotation == 0f) {
                canvas.drawText(label.text, label.x, label.y, labelPaint)
            } else {
                canvas.save()
                canvas.rotate(label.rotation, label.x, label.y)
                canvas.drawText(label.text, label.x, label.y, labelPaint)
                canvas.restore()
            }
        }

        canvas.restore()
    }

    private data class Label(
        val x: Float,
        val y: Float,
        val text: String,
        val rotation: Float,
        val color: String
    )
}
