package com.runclub.android.ui

import android.app.Activity
import com.runclub.android.R

/**
 * 页面切换动画：正向切页（首页 → 记录页）时新页面从右边整屏滑入、旧页面同时整屏往左让位，
 * 返回时反过来。两个窗口同时长同插值器、位移互补，所以中间不留缝也不重叠，看起来是
 * 一排页面在被推着走（手机桌面的翻页效果），而不是两张纸叠着滑。
 *
 * 配合 [PageSwipeHelper]：手指拖动时当前页先跟着走，松手由这里的窗口动画接着走完。
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
