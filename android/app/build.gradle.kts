plugins {
    // AGP 9.0 起 Kotlin 支持已内置，不再需要 org.jetbrains.kotlin.android 插件
    id("com.android.application")
}

android {
    namespace = "com.runclub.android"
    // androidx.core 1.19.1 起要求 compileSdk >= 37（本机已装 platforms;android-37.0）
    compileSdk = 37

    defaultConfig {
        applicationId = "com.runclub.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 高德 3D 地图 SDK 只带 arm64-v8a / armeabi-v7a 的 native 库，
    // 打进 APK 后 **x86_64 模拟器会直接 INSTALL_FAILED_NO_MATCHING_ABIS**。
    // 因此拆成两个变体：
    //   amap  → 真实高德底图，装真机（ARM）
    //   local → 不含高德 SDK，可在 x86_64 模拟器上跑，用原型手绘地图
    flavorDimensions += "map"

    productFlavors {
        create("amap") {
            dimension = "map"
            // 两个变体共用同一个 applicationId：高德 Key 绑定包名，不能改
        }
        create("local") {
            dimension = "map"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // 高德地图 SDK 只进 amap 变体。Key 绑定「包名 + 签名 SHA1」，
    // 见 AndroidManifest 里的 com.amap.api.v2.apikey。
    "amapImplementation"("com.amap.api:3dmap:10.0.600")

    testImplementation("junit:junit:4.13.2")
}
