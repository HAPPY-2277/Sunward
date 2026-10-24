package com.runclub.android.map

/**
 * 坐标系转换：WGS-84 → GCJ-02（俗称「火星坐标」）。
 *
 * 为什么必须有这一步：GPS 拿到的是 WGS-84，而高德地图使用 GCJ-02，
 * 在中国大陆两者直接混用会偏移 **50–500 米**（调研报告 §7.3）。
 *
 * 存储铁律不变：轨迹点一律存 WGS-84，**只在渲染/传给地图 SDK 时**转成 GCJ-02，
 * 这样将来导出 GPX 给 Strava/Garmin 才不会整体偏移。
 */
object GeoConvert {

    private const val A = 6378245.0            // Krasovsky 1940 长半轴
    private const val EE = 0.00669342162296594323  // 偏心率平方

    /** 中国大陆粗略范围判断；境外不做偏移 */
    private fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return Pair(lat, lon)

        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)

        val radLat = lat / 180.0 * Math.PI
        var magic = Math.sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = Math.sqrt(magic)

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI)

        return Pair(lat + dLat, lon + dLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}
