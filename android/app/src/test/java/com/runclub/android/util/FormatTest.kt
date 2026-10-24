package com.runclub.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `时长始终补零到 HH_MM_SS`() {
        assertEquals("00:00:00", Format.duration(0))
        assertEquals("00:00:05", Format.duration(5_000))
        assertEquals("00:01:05", Format.duration(65_000))
        assertEquals("01:02:03", Format.duration(3_723_000))
        assertEquals("00:00:00", Format.duration(-1))
    }

    @Test
    fun `配速按 m_s_ 格式化`() {
        assertEquals("5'42\"", Format.pace(342.0))
        assertEquals("3'20\"", Format.pace(200.0))
        assertEquals("10'00\"", Format.pace(600.0))
    }

    @Test
    fun `无有效配速时显示占位`() {
        assertEquals("--'--\"", Format.pace(null))
        assertEquals("--'--\"", Format.pace(0.0))
    }

    @Test
    fun `距离保留两位小数`() {
        assertEquals("0.00", Format.km(0.0))
        assertEquals("2.41", Format.km(2_410.0))
        assertEquals("0.15", Format.km(150.0))
    }
}
