package com.example.ui

import com.example.ui.screens.formatPrepTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PrepTimeFormatTest {
    @Test
    fun `minuten en uren worden leesbaar getoond`() {
        assertEquals("25 min", formatPrepTime(25))
        assertEquals("1 u", formatPrepTime(60))
        assertEquals("1 u 15 min", formatPrepTime(75))
        assertEquals("2 u", formatPrepTime(120))
    }
}
