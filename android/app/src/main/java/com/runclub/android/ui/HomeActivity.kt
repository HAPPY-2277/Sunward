package com.runclub.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.runclub.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运动首页（对应原型 activity.html）。
 * M1 仅搭建骨架与导航；今日建议、本周统计等真实数据在后续里程碑接入。
 */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val today = SimpleDateFormat("M'月'd'日' · EEEE", Locale.CHINA).format(Date())
        findViewById<TextView>(R.id.tvDate).text = today

        findViewById<View>(R.id.btnGo).setOnClickListener {
            startActivity(Intent(this, LiveRunActivity::class.java))
        }
        findViewById<View>(R.id.tabRecords).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        findViewById<View>(R.id.tabSport).setOnClickListener {
            // 已在首页，无需跳转
        }
    }
}
