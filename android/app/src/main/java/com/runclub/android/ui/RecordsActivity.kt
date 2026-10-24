package com.runclub.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.runclub.android.R

/**
 * 记录页（对应原型 records.html 的历史 Tab + 报告 Tab）。
 * M1 为静态骨架，M6 接入历史列表，M7 接入周/月/年报告。
 */
class RecordsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_records)

        findViewById<View>(R.id.btnBackRecords).setOnClickListener { finish() }
        findViewById<View>(R.id.tabSport).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }
}
