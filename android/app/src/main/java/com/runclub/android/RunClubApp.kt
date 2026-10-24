package com.runclub.android

import android.app.Application
import com.runclub.android.map.providePrivacyGate

class RunClubApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 高德 SDK 每次进程启动都要重调隐私接口，未同意时由首页弹窗征询。
        // local 变体没有高德 SDK，这里是个空实现。
        runCatching { providePrivacyGate().applyIfConsented(this) }
    }
}
