package com.runclub.android.ui

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.runclub.android.R
import kotlin.math.abs

/**
 * 右滑返回：手指从左边缘起手，页面跟着手指往右平移，松手超过阈值就交还给系统切页动画。
 *
 * 为什么不用系统预测式返回（predictive back）：那套要 Android 13+ 且需要透明主题才看得到
 * 背后的页面，本项目主题是深色不透明底，各版本表现会不一致。这里改成「页面跟手平移 +
 * 露出暗色底层」，低版本行为相同，动画也和 [NavTransition] 同源。
 *
 * 用法：Activity 里 attach 一次，再在 dispatchTouchEvent 里把事件先喂给 [onTouchEvent]；
 * 返回 true 表示这次事件已被手势吃掉，不要再往子 View 传。
 */
class SwipeBackHelper(private val activity: Activity) {

    /** 页面根布局，也就是跟手平移的那一层 */
    private var page: View? = null

    /** 页面下面那层暗色：拖出位移后露出来，制造「上一页在下面」的层次感 */
    private val scrim = View(activity).apply {
        setBackgroundColor(ContextCompat.getColor(activity, R.color.bg_outer))
        // 平时藏起来，页面根布局万一不是全不透明也不会被这层暗色影响
        visibility = View.GONE
    }

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
    private val edgePx = EDGE_DP * activity.resources.displayMetrics.density

    /** 抢手势时要把事件转交给子 View 补一个 CANCEL，由 Activity 提供 super.dispatchTouchEvent */
    private var superDispatch: ((MotionEvent) -> Boolean)? = null

    /** 手指落在左边缘，还没确定是返回手势还是竖向滚动 */
    private var tracking = false

    /** 已确定是返回手势，页面正在跟手平移 */
    private var dragging = false
    private var startX = 0f
    private var startY = 0f

    fun attach(superDispatch: (MotionEvent) -> Boolean) {
        this.superDispatch = superDispatch
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        page = content.getChildAt(0) ?: return
        content.addView(
            scrim,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val target = page ?: return false
        if (activity.isFinishing) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 只在左边缘起手，避免和列表的竖向滚动抢事件
                if (event.x <= edgePx) {
                    tracking = true
                    startX = event.x
                    startY = event.y
                }
                // DOWN 一律放行：还判断不出意图，先让子 View 正常响应点击
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    dragTo(target, event.x)
                    return true
                }
                if (tracking) {
                    val dx = event.x - startX
                    val dy = abs(event.y - startY)
                    when {
                        // 横向位移要明显压过竖向，斜着滚列表时不会误触
                        dx > touchSlop && dx > dy * HORIZONTAL_BIAS -> {
                            dragging = true
                            cancelChildren(event)
                            scrim.visibility = View.VISIBLE
                            dragTo(target, event.x)
                            return true
                        }

                        dy > touchSlop -> tracking = false
                    }
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    tracking = false
                    return false
                }
                dragging = false
                tracking = false
                release(target)
                return true
            }
        }
        return false
    }

    private fun dragTo(target: View, x: Float) {
        // 只往右跟手，往左不拖动（背后没有页面，拖出来是空的）
        target.translationX = (x - startX).coerceAtLeast(0f)
    }

    private fun release(target: View) {
        if (target.translationX >= target.width * FINISH_RATIO) {
            // 拖过阈值：保留当前位移直接 finish，系统切页动画会在这段位移上继续把页面送出去，
            // 所以不能先把 translationX 归零，否则画面会跳一下
            activity.finish()
            NavTransition.backward(activity)
        } else {
            scrim.visibility = View.GONE
            target.animate().translationX(0f).setDuration(SETTLE_MS).start()
        }
    }

    /** 抢下这条事件流之前先给子 View 补一个 CANCEL，否则列表会以为手指还在它身上 */
    private fun cancelChildren(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        superDispatch?.invoke(cancel)
        cancel.recycle()
    }

    private companion object {
        /** 左边缘起手区宽度，与系统返回手势的热区量级一致 */
        const val EDGE_DP = 48f

        /** 横向位移要超过竖向位移的这个倍数才算返回手势 */
        const val HORIZONTAL_BIAS = 1.5f

        /** 松手时拖过页面宽度的这个比例就判定为返回 */
        const val FINISH_RATIO = 0.25f

        /** 没到阈值时页面回弹的时长 */
        const val SETTLE_MS = 180L
    }
}
