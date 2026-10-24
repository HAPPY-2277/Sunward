package com.runclub.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.runclub.android.R

/**
 * 实时跑步页（对应原型 live-run.html）。
 * M1 为静态骨架，M2 接入定位、M3 接入前台服务与状态机、M4 接入实时数据与轨迹自绘。
 */
class LiveRunActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_run)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnEnd).setOnClickListener {
            startActivity(Intent(this, ResultActivity::class.java))
        }
    }
}
