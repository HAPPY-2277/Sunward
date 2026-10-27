package com.runclub.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 切页手势判定的基准校验：方向、起手、放弃、跟手位移与切页阈值 */
class PageSwipeRuleTest {

    /** 常见机型的 scaledTouchSlop */
    private val slop = 24

    /** 1080px 宽屏，四分之一屏是 270px */
    private val width = 1080

    /** 800dp/s 在 440dpi（density 2.625）下的换算值 */
    private val fling = 2100f

    @Test
    fun `首页往左滑会起手`() {
        assertTrue(PageSwipeRule.startsDrag(-40f, 0f, slop, PageSwipeRule.DIRECTION_LEFT))
    }

    @Test
    fun `记录页往右滑会起手`() {
        assertTrue(PageSwipeRule.startsDrag(40f, 0f, slop, PageSwipeRule.DIRECTION_RIGHT))
    }

    @Test
    fun `往没有页面的一侧拖不起手`() {
        assertFalse(PageSwipeRule.startsDrag(40f, 0f, slop, PageSwipeRule.DIRECTION_LEFT))
        assertFalse(PageSwipeRule.startsDrag(-40f, 0f, slop, PageSwipeRule.DIRECTION_RIGHT))
    }

    @Test
    fun `位移没过 touchSlop 不起手`() {
        assertFalse(PageSwipeRule.startsDrag(-20f, 0f, slop, PageSwipeRule.DIRECTION_LEFT))
    }

    @Test
    fun `斜着滚列表不算切页手势`() {
        // 横向 40 与竖向 40 相等，没过 1.5 倍偏置
        assertFalse(PageSwipeRule.startsDrag(-40f, -40f, slop, PageSwipeRule.DIRECTION_LEFT))
        // 横向 100 明显压过竖向 60
        assertTrue(PageSwipeRule.startsDrag(-100f, -60f, slop, PageSwipeRule.DIRECTION_LEFT))
    }

    @Test
    fun `竖向滚动时放弃手势`() {
        assertTrue(PageSwipeRule.givesUp(0f, 40f, slop, PageSwipeRule.DIRECTION_LEFT))
    }

    @Test
    fun `反向拖时放弃手势`() {
        assertTrue(PageSwipeRule.givesUp(40f, 0f, slop, PageSwipeRule.DIRECTION_LEFT))
    }

    @Test
    fun `手势成立时不放弃`() {
        assertFalse(PageSwipeRule.givesUp(-40f, 0f, slop, PageSwipeRule.DIRECTION_LEFT))
    }

    @Test
    fun `跟手位移只往有页面的一侧走`() {
        assertEquals(0f, PageSwipeRule.clampDrag(50f, PageSwipeRule.DIRECTION_LEFT), 1e-6f)
        assertEquals(-50f, PageSwipeRule.clampDrag(-50f, PageSwipeRule.DIRECTION_LEFT), 1e-6f)
        assertEquals(0f, PageSwipeRule.clampDrag(-50f, PageSwipeRule.DIRECTION_RIGHT), 1e-6f)
        assertEquals(50f, PageSwipeRule.clampDrag(50f, PageSwipeRule.DIRECTION_RIGHT), 1e-6f)
    }

    @Test
    fun `拖过四分之一屏才切页`() {
        assertTrue(
            PageSwipeRule.shouldSwitch(-300f, width, 0f, fling, PageSwipeRule.DIRECTION_LEFT)
        )
        assertFalse(
            PageSwipeRule.shouldSwitch(-200f, width, 0f, fling, PageSwipeRule.DIRECTION_LEFT)
        )
    }

    @Test
    fun `位移不够但甩得够快也切页`() {
        assertTrue(
            PageSwipeRule.shouldSwitch(-100f, width, -2500f, fling, PageSwipeRule.DIRECTION_LEFT)
        )
    }

    @Test
    fun `反方向的速度不算甩`() {
        assertFalse(
            PageSwipeRule.shouldSwitch(-100f, width, 2500f, fling, PageSwipeRule.DIRECTION_LEFT)
        )
    }

    @Test
    fun `记录页的切页与甩动方向相反`() {
        assertTrue(
            PageSwipeRule.shouldSwitch(300f, width, 0f, fling, PageSwipeRule.DIRECTION_RIGHT)
        )
        assertTrue(
            PageSwipeRule.shouldSwitch(100f, width, 2500f, fling, PageSwipeRule.DIRECTION_RIGHT)
        )
        assertFalse(
            PageSwipeRule.shouldSwitch(-300f, width, 0f, fling, PageSwipeRule.DIRECTION_RIGHT)
        )
    }
}
