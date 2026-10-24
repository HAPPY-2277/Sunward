package com.runclub.android.map

import android.os.Build

/**
 * 高德 native 库只提供这两个 ABI。主源码集不能引用高德类（local 变体没有该依赖），
 * 所以这里只判断 ABI；真正挂地图的逻辑在 `src/amap` 变体里。
 */
object MapSupport {

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a")

    fun isArmAbi(): Boolean = Build.SUPPORTED_ABIS.any { it in SUPPORTED_ABIS }

    fun abiText(): String = Build.SUPPORTED_ABIS.joinToString("/")
}
