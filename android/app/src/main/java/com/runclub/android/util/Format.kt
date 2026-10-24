package com.runclub.android.util

import java.util.Locale
import kotlin.math.roundToInt

/** 时间与配速格式化。UI 与通知共用，保证两处显示完全一致。 */
object Format {

    /** 毫秒 → HH:MM:SS */
    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    /** 秒/公里 → m'ss"；无有效配速时返回 --'--" */
    fun pace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm <= 0.0) return "--'--\""
        val total = secPerKm.roundToInt()
        val m = total / 60
        var s = total % 60
        var mm = m
        if (s == 60) { s = 0; mm += 1 }
        return String.format(Locale.US, "%d'%02d\"", mm, s)
    }

    /** 米 → 公里字符串，两位小数 */
    fun km(distanceM: Double): String = String.format(Locale.US, "%.2f", distanceM / 1000.0)
}
