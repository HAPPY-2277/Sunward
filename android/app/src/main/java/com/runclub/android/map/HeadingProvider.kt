package com.runclub.android.map

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.runclub.android.tracking.GeoMath

/**
 * 设备朝向（正北 0°、顺时针），用来给地图上的跑者标记兜底。
 *
 * 为什么需要它：GPS 的 bearing 来自多普勒，静止、树荫、隧道、网络定位时经常没有，
 * 这时箭头会一直指着最后一次的方向。罗盘与速度无关，能补上这个缺口。
 *
 * 刻意只在实时页可见期间注册（onResume → onPause），不做常驻：
 * 传感器常开会明显耗电，而这个 App 的用户可能一跑就是一小时。
 */
class HeadingProvider(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /**
     * 旋转矢量是融合传感器（陀螺 + 磁力 + 重力），比「加速度计 + 磁力计」自己算稳得多，
     * 也不容易被手臂摆动带偏。极老的机型没有这个传感器时 [lastHeadingDeg] 恒为 null，
     * 界面就退回「只用 GPS bearing」的行为，不做更差的兜底。
     */
    private val rotationVector: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var registered = false

    /** 最近一次朝向（已平滑）；设备不支持或还没读到数据时为 null */
    var lastHeadingDeg: Float? = null
        private set

    fun start() {
        val manager = sensorManager ?: return
        val sensor = rotationVector ?: return
        if (registered) return
        // SENSOR_DELAY_UI（约 60ms）就够：箭头转动是平滑动画，不需要游戏级采样率
        registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!registered) return
        registered = false
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuth = GeoMath.normalizeDegrees(Math.toDegrees(orientation[0].toDouble()))
        // 罗盘单次读数会跳，做 EMA 后箭头才不会左右乱摆
        lastHeadingDeg = lastHeadingDeg
            ?.let { GeoMath.smoothBearing(it, azimuth) }
            ?: azimuth
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
