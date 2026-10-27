package com.runclub.android.ui.widget

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.runclub.android.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 全 App 统一的提示卡片。
 *
 * 系统 AlertDialog 是浅色 Material 风格，和本项目的深色荧光绿视觉不搭，
 * 所以隐私说明、恢复未完成的跑步、删除记录确认这几处提示都换成这一张卡片，
 * 外观与实时页「结束本次跑步」那张自绘卡（activity_live_run.xml 的 endModal）一致。
 *
 * 典型用法：
 * ```
 * SunwardDialog.Builder(this)
 *     .icon(R.drawable.ic_history)
 *     .title(R.string.restore_title)
 *     .message(text)
 *     .positive(R.string.restore_yes) { startLiveRun() }
 *     .negative(R.string.restore_no) { store.clear() }
 *     .cancelable(false)
 *     .show()
 * ```
 */
class SunwardDialog private constructor(private val dialog: Dialog) {

    val isShowing: Boolean
        get() = dialog.isShowing

    fun show() {
        dialog.show()
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    class Builder(private val context: Context) {

        @DrawableRes
        private var iconRes: Int = R.drawable.ic_flag

        /** true = 荧光绿底 + 深色图标（品牌/说明类），false = 橙底 + 白图标（需要用户决断的提示） */
        private var iconOnLime: Boolean = false
        private var titleText: CharSequence? = null
        private var messageText: CharSequence? = null
        private var positiveText: CharSequence? = null
        private var onPositive: (() -> Unit)? = null
        private var negativeText: CharSequence? = null
        private var onNegative: (() -> Unit)? = null
        private var cancelable: Boolean = true

        fun icon(@DrawableRes res: Int, onLime: Boolean = false) = apply {
            iconRes = res
            iconOnLime = onLime
        }

        fun title(@StringRes res: Int) = apply { titleText = context.getString(res) }

        fun title(text: CharSequence) = apply { titleText = text }

        fun message(@StringRes res: Int) = apply { messageText = context.getString(res) }

        fun message(text: CharSequence) = apply { messageText = text }

        fun positive(@StringRes res: Int, onClick: (() -> Unit)? = null) = apply {
            positiveText = context.getString(res)
            onPositive = onClick
        }

        fun negative(@StringRes res: Int, onClick: (() -> Unit)? = null) = apply {
            negativeText = context.getString(res)
            onNegative = onClick
        }

        fun cancelable(value: Boolean) = apply { cancelable = value }

        fun build(): SunwardDialog {
            val dialog = Dialog(context, R.style.Theme_RunClub_Dialog)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_sunward)
            dialog.setCancelable(cancelable)
            // 点卡片外面等同于取消：不允许取消的提示（隐私、恢复）也就点不掉
            dialog.setCanceledOnTouchOutside(cancelable)

            val iconBox = dialog.findViewById<FrameLayout>(R.id.dialogIconBox)
            val icon = dialog.findViewById<ImageView>(R.id.dialogIcon)
            iconBox.setBackgroundResource(
                if (iconOnLime) R.drawable.bg_square_lime_12 else R.drawable.bg_square_orange
            )
            icon.setImageResource(iconRes)
            icon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (iconOnLime) R.color.ink else R.color.text_primary
                )
            )

            dialog.findViewById<TextView>(R.id.dialogTitle).text = titleText

            val message = dialog.findViewById<TextView>(R.id.dialogMessage)
            message.text = messageText
            message.visibility = if (messageText.isNullOrEmpty()) View.GONE else View.VISIBLE

            val positive = dialog.findViewById<TextView>(R.id.dialogPositive)
            val negative = dialog.findViewById<TextView>(R.id.dialogNegative)

            if (negativeText == null) {
                negative.visibility = View.GONE
                // 只剩一枚按钮时去掉分隔用的 marginStart，否则胶囊会偏右 6dp
                positive.layoutParams = (positive.layoutParams as LinearLayout.LayoutParams).apply {
                    marginStart = 0
                }
            } else {
                negative.text = negativeText
                negative.setOnClickListener {
                    val action = onNegative
                    dialog.dismiss()
                    action?.invoke()
                }
            }

            if (positiveText == null) {
                positive.visibility = View.GONE
            } else {
                positive.text = positiveText
                positive.setOnClickListener {
                    val action = onPositive
                    // 先收卡片再执行回调：回调里常常要弹下一张卡或跳页面
                    dialog.dismiss()
                    action?.invoke()
                }
            }

            dialog.window?.let { window ->
                window.setBackgroundDrawableResource(android.R.color.transparent)
                val metrics = context.resources.displayMetrics
                val sideMargin = (SIDE_MARGIN_DP * metrics.density).roundToInt()
                val maxWidth = (MAX_WIDTH_DP * metrics.density).roundToInt()
                window.setLayout(
                    min(metrics.widthPixels - sideMargin * 2, maxWidth),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Activity 销毁时自动收掉，避免 window leak
            (context as? LifecycleOwner)?.lifecycle?.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
                }
            )

            return SunwardDialog(dialog)
        }

        fun show(): SunwardDialog = build().also { it.show() }
    }

    private companion object {
        /** 与 endModal 外层 padding 32dp 对齐：卡片两侧各留 32dp */
        const val SIDE_MARGIN_DP = 32
        /** 横屏 / 平板上限，避免卡片被拉成一整条 */
        const val MAX_WIDTH_DP = 420
    }
}
