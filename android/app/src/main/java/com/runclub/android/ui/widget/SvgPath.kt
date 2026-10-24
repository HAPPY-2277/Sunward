package com.runclub.android.ui.widget

import android.graphics.Path

/**
 * 极简 SVG path 解析器，只支持本项目用到的命令子集：
 * M/m L/l H/h V/v C/c Z/z。
 *
 * 原型里的城市地图是手写 SVG，为了 1:1 还原它的形状，这里把同一份 path 数据
 * 直接解析成 [Path]，而不是重新画一套近似的图。
 */
object SvgPath {

    private val TOKEN = Regex("[MmLlHhVvCcZz]|-?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?")

    fun parse(data: String): Path {
        val path = Path()
        val tokens = TOKEN.findAll(data).map { it.value }.toList()
        if (tokens.isEmpty()) return path

        var i = 0
        var cx = 0f
        var cy = 0f
        var startX = 0f
        var startY = 0f
        var cmd = 'M'

        fun number(): Float = tokens[i++].toFloat()

        while (i < tokens.size) {
            val token = tokens[i]
            if (token.length == 1 && token[0].isLetter()) {
                cmd = token[0]
                i++
            }
            when (cmd) {
                'M' -> {
                    cx = number(); cy = number()
                    startX = cx; startY = cy
                    path.moveTo(cx, cy)
                    cmd = 'L'          // M 之后的多组坐标按直线处理
                }
                'm' -> {
                    cx += number(); cy += number()
                    startX = cx; startY = cy
                    path.moveTo(cx, cy)
                    cmd = 'l'
                }
                'L' -> { cx = number(); cy = number(); path.lineTo(cx, cy) }
                'l' -> { cx += number(); cy += number(); path.lineTo(cx, cy) }
                'H' -> { cx = number(); path.lineTo(cx, cy) }
                'h' -> { cx += number(); path.lineTo(cx, cy) }
                'V' -> { cy = number(); path.lineTo(cx, cy) }
                'v' -> { cy += number(); path.lineTo(cx, cy) }
                'C' -> {
                    val x1 = number(); val y1 = number()
                    val x2 = number(); val y2 = number()
                    val x = number(); val y = number()
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    cx = x; cy = y
                }
                'c' -> {
                    val x1 = cx + number(); val y1 = cy + number()
                    val x2 = cx + number(); val y2 = cy + number()
                    val x = cx + number(); val y = cy + number()
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    cx = x; cy = y
                }
                'Z', 'z' -> {
                    path.close()
                    cx = startX; cy = startY
                }
            }
        }
        return path
    }
}
