package com.runclub.android.ui

import android.app.Activity
import com.runclub.android.R

/**
 * 页面切换动画：正向切页（首页 → 记录页）时新页面从右滑入、旧页面往左让位，
 * 返回时反过来。配合 [SwipeBackHelper] 让两页之间像一叠可以左右翻的卡片，
 * 而不是硬切。
 *
 * 这里用的是 overridePendingTransition 而不是 API 34 的 overrideActivityTransition：
 * 后者语义是「给目标 Activity 长期设置动画」，要在被打开/被关闭的那个 Activity 里调，
 * 而本项目两个页面互为进出方，一次性调用的老接口反而更直白，且 minSdk 26 全覆盖。
 */
object NavTransition {

    /** 往前翻一页：必须在 startActivity 之后立刻调用 */
    fun forward(activity: Activity) {
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /** 往回翻一页：必须在 finish 之后立刻调用 */
    fun backward(activity: Activity) {
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
