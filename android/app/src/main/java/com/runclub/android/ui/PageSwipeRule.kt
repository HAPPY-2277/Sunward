package com.runclub.android.ui

import kotlin.math.abs

/**
 * 左右滑切页的**判定规则**：纯 Kotlin，不碰 MotionEvent / View，因此能在 JVM 上直接单测。
 *
 * 为什么单独拆一层：手势的方向与阈值最容易写错符号（写反就是「怎么滑都没反应」或「滑错页」），
 * 而甩动速度那一条在模拟器上用注入事件测不准（`input swipe` 的速度和真实手指不一样），
 * 只有单测能覆盖。[PageSwipeHelper] 因此只负责取事件、平移页面和做动画。
 */
object PageSwipeRule {

    /** 往左滑：翻到右边那一页（首页 → 记录页） */
    const val DIRECTION_LEFT = -1

    /** 往右滑：翻回左边那一页（记录页 → 首页） */
    const val DIRECTION_RIGHT = 1

    /** 横向位移要超过竖向位移的这个倍数才算切页手势 */
    const val HORIZONTAL_BIAS = 1.5f

    /** 松手时拖过页面宽度的这个比例就切页 */
    const val SWITCH_RATIO = 0.25f

    /** 这一侧有没有页面：往没有页面的一侧拖不算切页手势 */
    fun isAllowedDirection(dx: Float, direction: Int): Boolean =
        if (direction == DIRECTION_LEFT) dx < 0f else dx > 0f

    /** 手指已经明确在切页，而不是在滚列表 */
    fun startsDrag(dx: Float, dy: Float, touchSlop: Int, direction: Int): Boolean =
        abs(dx) > touchSlop &&
            isAllowedDirection(dx, direction) &&
            abs(dx) > abs(dy) * HORIZONTAL_BIAS

    /** 这次手势该交还给子 View：明确是竖向滚动，或者往没有页面的一侧拖 */
    fun givesUp(dx: Float, dy: Float, touchSlop: Int, direction: Int): Boolean =
        (abs(dx) > touchSlop && !isAllowedDirection(dx, direction)) || abs(dy) > touchSlop

    /** 跟手位移只允许往有页面的那一侧走，另一侧拖出来是空的 */
    fun clampDrag(dx: Float, direction: Int): Float =
        if (direction == DIRECTION_LEFT) dx.coerceAtMost(0f) else dx.coerceAtLeast(0f)

    /**
     * 松手时是否切页：位移或速度**任一**达标即可。
     *
     * 速度单独算一条是为了「轻轻一扫」也能翻页 —— 那种手势位移往往不到 1/4 屏，
     * 只看位移会让人觉得要拖很远才行。速度方向必须和允许的方向一致，反向甩不算。
     *
     * @param dx 松手时页面相对原位的横向位移（px，符号与 [direction] 同向）
     * @param velocityPxPerSec 松手瞬间的横向速度（px/s，右为正）
     */
    fun shouldSwitch(
        dx: Float,
        pageWidth: Int,
        velocityPxPerSec: Float,
        flingPxPerSec: Float,
        direction: Int,
    ): Boolean {
        // 位移这一条也要判方向：clampDrag 已经保证 dx 只会往有页面的一侧走，
        // 但规则本身不该依赖调用方的前置条件，反向的位移一律不算「拖够了」。
        val farEnough = isAllowedDirection(dx, direction) && abs(dx) >= pageWidth * SWITCH_RATIO
        val fastEnough = if (direction == DIRECTION_LEFT) {
            velocityPxPerSec <= -flingPxPerSec
        } else {
            velocityPxPerSec >= flingPxPerSec
        }
        return farEnough || fastEnough
    }
}
