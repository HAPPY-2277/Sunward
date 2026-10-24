package com.runclub.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.runclub.android.R
import com.runclub.android.data.InProgressStore
import com.runclub.android.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运动首页（对应原型 activity.html）。
 * M1 时只是骨架；M3 起承担「检测未完成跑步并提示恢复」的入口职责。
 * 真实统计卡片在 M6 接入。
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
            // 已在首页
        }

        maybePromptRestore()
    }

    /**
     * 上次跑步被强杀（进程被杀 / 崩溃）后，进入 App 时提示恢复。
     * 快照由前台服务每 5 秒写入一次（[InProgressStore]）。
     */
    private fun maybePromptRestore() {
        val store = InProgressStore(this)
        val snapshot = store.loadRestorable() ?: return

        val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(snapshot.savedAtMs))
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_title)
            .setMessage(
                getString(
                    R.string.restore_message,
                    timeText,
                    Format.km(snapshot.distanceM),
                    Format.duration(snapshot.elapsedMs)
                )
            )
            .setCancelable(false)
            .setPositiveButton(R.string.restore_yes) { _, _ ->
                startActivity(
                    Intent(this, LiveRunActivity::class.java).apply {
                        putExtra(LiveRunActivity.EXTRA_RESTORE_ELAPSED, snapshot.elapsedMs)
                        putExtra(LiveRunActivity.EXTRA_RESTORE_DISTANCE, snapshot.distanceM)
                        putExtra(LiveRunActivity.EXTRA_RESTORE_SIMULATED, snapshot.simulated)
                    }
                )
            }
            .setNegativeButton(R.string.restore_no) { _, _ ->
                store.clear()
            }
            .show()
    }
}
