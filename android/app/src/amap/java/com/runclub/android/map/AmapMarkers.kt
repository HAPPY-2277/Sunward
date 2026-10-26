package com.runclub.android.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.runclub.android.R
import kotlin.math.roundToInt

/**
 * 高德 marker / 定位图标用的位图，全部代码绘制，不引 PNG 资源。
 *
 * 为什么不用 SDK 自带的 `defaultMarker`：那是红绿橙的水滴图钉，
 * 和本项目深色 + 荧光绿的视觉完全不搭，而且图钉的锚点在尖端，
 * 会把起终点的确切位置遮住半个身位。这里画的是与 TrackCanvasView
 * 手绘起终点一致的圆点，锚点就在圆心。
 */
internal object AmapMarkers {

    /** 白描边粗细，与 TrackCanvasView 的 startRingPaint（2.5dp）对齐 */
    private const val RING_DP = 2.5f

    /** 跑者标记直径 */
    private const val RUNNER_DP = 30

    /** 起终点标记直径 */
    private const val DOT_DP = 16

    /**
     * 跑者标记：荧光绿圆点 + 白描边 + 指向正北的方向角。
     *
     * 方向角画在正北方向，是因为高德 `LOCATION_TYPE_LOCATION_ROTATE`
     * 会按 Location.bearing 整体旋转这张图 —— 图上指北，转到 bearing 后就是前进方向。
     */
    fun runner(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (RUNNER_DP * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = size * 0.22f
        val lime = ContextCompat.getColor(context, R.color.accent_lime)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 先画方向角：底边藏在圆点里，只有露在白描边外面的尖端可见
        paint.color = lime
        val arrow = Path().apply {
            moveTo(center, size * 0.02f)
            lineTo(center - radius * 0.85f, center)
            lineTo(center + radius * 0.85f, center)
            close()
        }
        canvas.drawPath(arrow, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, radius + RING_DP * density, paint)
        paint.color = lime
        canvas.drawCircle(center, center, radius, paint)
        return bitmap
    }

    /** 起点：荧光绿实心 + 白描边（对应 TrackCanvasView 的 startPaint） */
    fun start(context: Context): Bitmap = dot(context, R.color.accent_lime)

    /** 终点：橙色实心 + 白描边（对应 RESULT 模式的 endResultPaint） */
    fun end(context: Context): Bitmap = dot(context, R.color.accent_orange)

    private fun dot(context: Context, @ColorRes fillRes: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (DOT_DP * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val ring = RING_DP * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center, paint)
        paint.color = ContextCompat.getColor(context, fillRes)
        canvas.drawCircle(center, center, center - ring, paint)
        return bitmap
    }
}
