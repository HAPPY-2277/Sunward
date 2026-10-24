package com.runclub.android.tracking

/**
 * 一个轨迹采样点。
 *
 * 存储铁律：经纬度一律是 **WGS-84** 原始坐标（调研报告 §7.3）。
 * 将来接入高德（GCJ-02）时只在渲染层转换，不改存储。
 */
data class TrackPoint(
    /** 采样时刻（epoch millis） */
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    /** 水平精度（米），系统未提供时为 null */
    val accuracyM: Float? = null,
    /** 海拔（米） */
    val altitudeM: Double? = null,
    /** 传感器上报的瞬时速度（m/s，通常来自 Doppler） */
    val speedMps: Float? = null,
    /** 定位来源：gps / network / passive / simulated */
    val provider: String = "",
    /** 是否来自内置模拟轨迹（调试用，不得写入真实记录） */
    val simulated: Boolean = false
) {
    /** 展示用："31.235012, 121.490013" */
    fun latLonText(digits: Int = 6): String =
        String.format("%.${digits}f, %.${digits}f", lat, lon)
}
