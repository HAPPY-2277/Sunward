package com.runclub.android.ui

import android.app.Activity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * 桌面式左右滑切页：手指拖动时当前页整屏跟着走，松手按「位移或速度任一达标」决定切页还是回弹。
 *
 * 与系统返回手势的差别有两点，都是刻意的：
 * 1. **不限起手位置**。整屏任意处横向拖都算，边缘和全宽行为一致 —— 两个页面是底部导航上并排的
 *    两页，往哪边拖就翻哪边，不是「从边缘把上一页拽回来」。
 * 2. **两个方向都能翻**。首页往左滑进记录页，记录页往右滑回首页，方向与底部导航的顺序一致。
 *
 * 为什么不用系统预测式返回（predictive back）：那套要 Android 13+ 且需要透明主题才看得到
 * 背后的页面，本项目主题是深色不透明底，各版本表现会不一致。这里改成「页面跟手平移 +
 * 松手交给 [NavTransition] 的窗口动画接着走完」，低版本行为相同，动画同源。
 *
 * 判定规则全在 [PageSwipeRule]（纯 Kotlin，可单测）；本类只做取事件、平移页面、回弹动画。
 *
 * 用法：Activity 里 attach 一次，再在 dispatchTouchEvent 里把事件先喂给 [onTouchEvent]；
 * 返回 true 表示这次事件已被手势吃掉，不要再往子 View 传。
 */
class PageSwipeHelper(
    private val activity: Activity,
    /** 允许的手势方向，取 [PageSwipeRule.DIRECTION_LEFT] 或 [PageSwipeRule.DIRECTION_RIGHT] */
    private val direction: Int,
    /** 手势成立后要做的切页动作，由调用方决定（一般是 startActivity/finish + NavTransition） */
    private val onSwipe: () -> Unit,
) {

    /** 页面根布局，也就是跟手平移的那一层 */
    private var page: View? = null

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop

    /** 甩动阈值，按屏幕密度换算成 px/s */
    private val flingPxPerSec = FLING_DP_PER_SEC * activity.resources.displayMetrics.density

    private var velocity: VelocityTracker? = null

    /** 抢手势时要把事件转交给子 View 补一个 CANCEL，由 Activity 提供 super.dispatchTouchEvent */
    private var superDispatch: ((MotionEvent) -> Boolean)? = null

    /** 手指已落下，还没确定是切页手势还是竖向滚动 */
    private var tracking = false

    /** 已确定是切页手势，页面正在跟手平移 */
    private var dragging = false
    private var startX = 0f
    private var startY = 0f

    fun attach(superDispatch: (MotionEvent) -> Boolean) {
        this.superDispatch = superDispatch
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        page = content.getChildAt(0) ?: return

        // 切页成功时页面会留着拖拽位移（不能归零，否则画面跳一下，见 release）。
        // 本页若没被销毁（首页压在记录页下面就是这种），回来时必须复位，否则整页一直偏着。
        // onResume 早于进场动画的第一帧，那一刻本页还在屏幕外，复位看不出来。
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) page?.translationX = 0f
            }
        )
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val target = page ?: return false
        if (activity.isFinishing) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                dragging = false
                startX = event.x
                startY = event.y
                recycleVelocity()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                // DOWN 一律放行：还判断不出意图，先让子 View 正常响应点击
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                velocity?.addMovement(event)
                if (dragging) {
                    dragTo(target, event.x - startX)
                    return true
                }
                val dx = event.x - startX
                val dy = event.y - startY
                when {
                    PageSwipeRule.startsDrag(dx, dy, touchSlop, direction) -> {
                        dragging = true
                        cancelChildren(event)
                        dragTo(target, dx)
                        return true
                    }

                    PageSwipeRule.givesUp(dx, dy, touchSlop, direction) -> tracking = false
                }
                return false
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    resetTracking()
                    return false
                }
                velocity?.addMovement(event)
                val xVelocity = velocity?.let {
                    it.computeCurrentVelocity(1000)
                    it.xVelocity
                } ?: 0f
                val dx = target.translationX
                resetTracking()
                if (PageSwipeRule.shouldSwitch(dx, target.width, xVelocity, flingPxPerSec, direction)) {
                    // 保留当前位移直接切页：窗口动画会在这段位移上继续把页面送出去，
                    // 先归零画面会跳一下。代价是动画期间两页之间会露出与位移等宽的一条窗口
                    // 背景（深色，和页面底色几乎同色），动画结束即消失。位移由 onResume 复位。
                    onSwipe()
                } else {
                    target.animate().translationX(0f).setDuration(SETTLE_MS).start()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!dragging) {
                    resetTracking()
                    return false
                }
                resetTracking()
                target.animate().translationX(0f).setDuration(SETTLE_MS).start()
                return true
            }
        }
        return false
    }

    private fun dragTo(target: View, dx: Float) {
        target.translationX = PageSwipeRule.clampDrag(dx, direction)
    }

    /** 抢下这条事件流之前先给子 View 补一个 CANCEL，否则列表会以为手指还在它身上 */
    private fun cancelChildren(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        superDispatch?.invoke(cancel)
        cancel.recycle()
    }

    private fun resetTracking() {
        tracking = false
        dragging = false
        recycleVelocity()
    }

    private fun recycleVelocity() {
        velocity?.recycle()
        velocity = null
    }

    private companion object {
        /** 甩动阈值（dp/s），与位移阈值是「或」的关系，轻轻一扫也能翻页 */
        const val FLING_DP_PER_SEC = 800f

        /** 没到阈值时页面回弹的时长 */
        const val SETTLE_MS = 180L
    }
}
